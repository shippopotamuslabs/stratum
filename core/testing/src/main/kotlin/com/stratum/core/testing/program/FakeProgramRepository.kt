package com.stratum.core.testing.program

import com.stratum.domain.lift.WorkoutLiftNote
import com.stratum.domain.lift.WorkoutLiftNoteInput
import com.stratum.domain.lift.sanitizeLiftNoteBody
import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgramRepository
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.identity.WorkoutLiftNoteId
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory multi-program [ProgramRepository]. [saveProgram] upserts by id; when [Program.isActive]
 * is true, other programs are demoted. Never deletes sibling programs.
 */
class FakeProgramRepository(
    initial: Program? = null,
    private val failOnSave: Boolean = false,
) : ProgramRepository {
    private val programs = MutableStateFlow<Map<String, Program>>(
        if (initial == null) emptyMap() else mapOf(initial.id.value to initial.copy(isActive = true)),
    )
    /** Source of truth for workout note lists; [WorkoutLift.pinnedNote] mirrors the first body. */
    private val workoutNotes = MutableStateFlow<Map<String, List<WorkoutLiftNote>>>(emptyMap())
    val saveProgramCalls = mutableListOf<Program>()

    private fun findWorkoutLift(id: WorkoutLiftId): WorkoutLift? =
        programs.value.values
            .flatMap { program -> program.workouts }
            .flatMap { workout -> workout.workoutLifts }
            .firstOrNull { it.id == id }

    /**
     * Drops note-map entries for workout slots absent from every stored program.
     * Mirrors production, where deleting a program or replacing its hierarchy
     * removes the underlying rows (and their notes) with it.
     */
    private fun pruneOrphanedWorkoutNotes() {
        val liveSlotIds =
            programs.value.values
                .flatMap { program -> program.workouts }
                .flatMap { workout -> workout.workoutLifts }
                .map { it.id.value }
                .toSet()
        workoutNotes.value = workoutNotes.value.filterKeys { it in liveSlotIds }
    }

    /**
     * Reconciles per-slot note lists after a program save. If the save changed a
     * slot's scalar pin, a stale list entry must not keep overriding the newly
     * saved pin, so it is dropped and the scalar becomes authoritative again.
     * Slots whose pin is untouched keep their existing note lists.
     */
    private fun reconcileWorkoutNotesAfterSave(previous: Program?, updated: Program) {
        val previousPins =
            previous?.workouts
                ?.flatMap { workout -> workout.workoutLifts }
                ?.associate { it.id.value to it.pinnedNote }
                .orEmpty()
        val changedSlotIds =
            updated.workouts
                .flatMap { workout -> workout.workoutLifts }
                .filter { slot -> previousPins[slot.id.value] != slot.pinnedNote }
                .map { it.id.value }
                .toSet()
        if (changedSlotIds.isNotEmpty()) {
            workoutNotes.value = workoutNotes.value.filterKeys { it !in changedSlotIds }
        }
    }

    private fun updateWorkoutLiftPin(id: WorkoutLiftId, note: String?) {
        val next = programs.value.mapValues { (_, program) ->
            program.copy(
                workouts = program.workouts.map { workout ->
                    workout.copy(
                        workoutLifts = workout.workoutLifts.map { lift ->
                            if (lift.id == id) lift.copy(pinnedNote = note) else lift
                        },
                    )
                },
            )
        }
        programs.value = next
    }

    /** Active program only (matches production [getProgram]). */
    val current: Program? get() = programs.value.values.firstOrNull { it.isActive }

    override fun observeProgram(): Flow<Program?> =
        programs.map { map -> map.values.firstOrNull { program -> program.isActive } }

    override fun observeProgramById(programId: ProgramId): Flow<Program?> =
        programs.map { map -> map[programId.value] }

    override fun observeLibraryPrograms(): Flow<List<Program>> =
        programs.map { map ->
            map.values.filter { program ->
                !program.isActive && !program.id.isImportHistory
            }
        }

    override suspend fun getProgram(): Program? = current

    override suspend fun getProgramById(programId: ProgramId): Program? =
        programs.value[programId.value]

    override suspend fun getAllUserPrograms(): List<Program> =
        programs.value.values.filterNot { it.id.isImportHistory }

    override suspend fun saveProgram(program: Program) {
        check(!failOnSave) { "FakeProgramRepository configured to fail on save" }
        saveProgramCalls.add(program)
        val previous = programs.value[program.id.value]
        val next = programs.value.toMutableMap()
        if (program.isActive) {
            next.replaceAll { _, existing -> existing.copy(isActive = false) }
        }
        next[program.id.value] = program
        programs.value = next
        pruneOrphanedWorkoutNotes()
        reconcileWorkoutNotesAfterSave(previous, program)
        installCarriedWorkoutNotes(program)
    }

    /**
     * Installs ordered note lists carried through a template save (mirrors
     * production hierarchy write-through). Runs after the scalar-authoritative
     * reconcile so carried lists replace stale rows instead of being dropped.
     */
    private fun installCarriedWorkoutNotes(updated: Program) {
        val now = Instant.now()
        var next = workoutNotes.value
        var changed = false
        for (slot in updated.workouts.flatMap { workout -> workout.workoutLifts }) {
            val bodies = slot.pinnedNotes.mapNotNull(::sanitizeLiftNoteBody)
            // Idempotent: re-saving an already-installed list keeps existing row ids.
            val current = next[slot.id.value]
            val needsInstall =
                bodies.isNotEmpty() && (current == null || current.map { it.body } != bodies)
            if (needsInstall) {
                next = next + (slot.id.value to bodies.mapIndexed { index, body ->
                    WorkoutLiftNote(
                        id = WorkoutLiftNoteId(UUID.randomUUID().toString()),
                        workoutLiftId = slot.id,
                        body = body,
                        position = index,
                        updatedAt = now,
                    )
                })
                changed = true
            }
        }
        if (changed) workoutNotes.value = next
    }

    override suspend fun saveToLibrary(program: Program) {
        saveProgram(program.copy(isActive = false))
    }

    override suspend fun activateProgram(programId: ProgramId) {
        val existing = programs.value[programId.value]
            ?: error("Cannot activate unknown program: ${programId.value}")
        check(!programId.isImportHistory) {
            "Import-history program cannot be activated"
        }
        val next = programs.value.toMutableMap()
        next.replaceAll { _, program -> program.copy(isActive = false) }
        next[programId.value] = existing.copy(isActive = true)
        programs.value = next
    }

    override suspend fun deleteProgram(programId: ProgramId) {
        check(!programId.isImportHistory) {
            "Import-history program cannot be deleted"
        }
        programs.value = programs.value - programId.value
        pruneOrphanedWorkoutNotes()
    }

    override suspend fun setWorkoutLiftPinnedNote(id: WorkoutLiftId, note: String?): Boolean {
        val clean = sanitizeLiftNoteBody(note)
        if (clean == null) {
            return setWorkoutLiftPinnedNotes(id, emptyList())
        }
        val existingId = getWorkoutLiftPinnedNotes(id).firstOrNull()?.id
        return setWorkoutLiftPinnedNotes(id, listOf(WorkoutLiftNoteInput(id = existingId, body = clean)))
    }

    override suspend fun getWorkoutLiftPinnedNotes(id: WorkoutLiftId): List<WorkoutLiftNote> =
        workoutNotes.value[id.value] ?: findWorkoutLift(id)?.pinnedNote
            ?.takeIf { it.isNotBlank() }
            ?.let { body ->
                listOf(
                    WorkoutLiftNote(
                        id = WorkoutLiftNoteId("${id.value}::0"),
                        workoutLiftId = id,
                        body = body,
                        position = 0,
                        updatedAt = Instant.now(),
                    ),
                )
            }.orEmpty()

    override suspend fun setWorkoutLiftPinnedNotes(
        id: WorkoutLiftId,
        notes: List<WorkoutLiftNoteInput>,
    ): Boolean {
        if (findWorkoutLift(id) == null) return false
        val now = Instant.now()
        val currentIds = getWorkoutLiftPinnedNotes(id).map { it.id }.toSet()
        val minted =
            notes.mapNotNull { input ->
                sanitizeLiftNoteBody(input.body)?.let { body -> input to body }
            }.mapIndexed { index, (input, body) ->
                WorkoutLiftNote(
                    id = input.id?.takeIf { it in currentIds }
                        ?: WorkoutLiftNoteId(UUID.randomUUID().toString()),
                    workoutLiftId = id,
                    body = body,
                    position = index,
                    updatedAt = now,
                )
            }
        workoutNotes.value = workoutNotes.value + (id.value to minted)
        updateWorkoutLiftPin(id, minted.firstOrNull()?.body)
        return true
    }

    override suspend fun updateCyclePointer(
        programId: ProgramId,
        currentCycleNumber: Int,
        cyclePosition: Int,
        cycleStartEpochDay: Long?,
    ) {
        val existing = programs.value[programId.value] ?: return
        programs.value =
            programs.value + (
                programId.value to existing.copy(
                    currentCycleNumber = currentCycleNumber,
                    cyclePosition = cyclePosition,
                    cycleStartEpochDay = cycleStartEpochDay ?: existing.cycleStartEpochDay,
                )
            )
    }
}
