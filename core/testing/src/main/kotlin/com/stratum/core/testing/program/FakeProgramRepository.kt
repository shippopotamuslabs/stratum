package com.stratum.core.testing.program

import com.stratum.domain.lift.WorkoutLiftNote
import com.stratum.domain.lift.WorkoutLiftNoteInput
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.sanitizeLiftNoteBody
import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgramRepository
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.identity.WorkoutLiftNoteId
import com.stratum.domain.workout.SlotLiftPair
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    /**
     * Source of truth for workout note lists, keyed by (slot, lift) pair (STR-127);
     * [WorkoutLift.pinnedNote] mirrors the first body of the slot's current pair.
     */
    private val workoutNotes =
        MutableStateFlow<Map<SlotLiftPair, List<WorkoutLiftNote>>>(emptyMap())

    private fun pairKey(id: WorkoutLiftId, liftId: LiftId) = SlotLiftPair(id, liftId)
    /**
     * Serializes note-map check-then-write sequences (lazy-migrate, replace),
     * mirroring Room's write-lock semantics so concurrent first reads converge
     * on one migrated row instead of minting competing ids.
     */
    private val notesMutex = Mutex()
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
    private suspend fun pruneOrphanedWorkoutNotes() {
        notesMutex.withLock {
            val liveSlotIds =
                programs.value.values
                    .flatMap { program -> program.workouts }
                    .flatMap { workout -> workout.workoutLifts }
                    .map { it.id.value }
                    .toSet()
            workoutNotes.value =
                workoutNotes.value.filterKeys { it.workoutLiftId.value in liveSlotIds }
        }
    }

    /**
     * Reconciles per-pair note lists after a program save. If the save changed a
     * slot's scalar pin *without* changing the lift on the slot, that pair's stale
     * list entry must not keep overriding the newly saved pin, so it is dropped and
     * the scalar becomes authoritative again. A lift change stashes the outgoing
     * pair instead (STR-127): its entry survives for a later swap-back.
     */
    private suspend fun reconcileWorkoutNotesAfterSave(previous: Program?, updated: Program) {
        notesMutex.withLock {
            val previousSlots =
                previous?.workouts
                    ?.flatMap { workout -> workout.workoutLifts }
                    ?.associate { it.id.value to (it.liftId to it.pinnedNote) }
                    .orEmpty()
            val changedPairKeys =
                updated.workouts
                    .flatMap { workout -> workout.workoutLifts }
                    .filter { slot ->
                        val prev = previousSlots[slot.id.value]
                        prev != null && prev.first == slot.liftId && prev.second != slot.pinnedNote
                    }
                    .map { slot -> pairKey(slot.id, slot.liftId) }
                    .toSet()
            if (changedPairKeys.isNotEmpty()) {
                workoutNotes.value = workoutNotes.value.filterKeys { it !in changedPairKeys }
            }
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
    private suspend fun installCarriedWorkoutNotes(updated: Program) {
        notesMutex.withLock {
            val now = Instant.now()
            var next = workoutNotes.value
            var changed = false
            for (slot in updated.workouts.flatMap { workout -> workout.workoutLifts }) {
                val bodies = slot.pinnedNotes.mapNotNull(::sanitizeLiftNoteBody)
                // Idempotent: re-saving an already-installed list keeps existing row ids.
                val key = pairKey(slot.id, slot.liftId)
                val current = next[key]
                val needsInstall =
                    bodies.isNotEmpty() && (current == null || current.map { it.body } != bodies)
                if (needsInstall) {
                    next = next + (key to bodies.mapIndexed { index, body ->
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
        val slot = findWorkoutLift(id) ?: return false
        return setWorkoutLiftPinnedNote(id, slot.liftId, note)
    }

    override suspend fun setWorkoutLiftPinnedNote(
        id: WorkoutLiftId,
        liftId: LiftId,
        note: String?,
    ): Boolean {
        val clean = sanitizeLiftNoteBody(note)
        if (clean == null) {
            return setWorkoutLiftPinnedNotes(id, liftId, emptyList())
        }
        val existingId = getWorkoutLiftPinnedNotes(id, liftId).firstOrNull()?.id
        return setWorkoutLiftPinnedNotes(
            id,
            liftId,
            listOf(WorkoutLiftNoteInput(id = existingId, body = clean)),
        )
    }

    override suspend fun getWorkoutLiftPinnedNotes(id: WorkoutLiftId): List<WorkoutLiftNote> {
        val slot = findWorkoutLift(id) ?: return emptyList()
        return getWorkoutLiftPinnedNotes(id, slot.liftId)
    }

    override suspend fun getWorkoutLiftPinnedNotes(
        id: WorkoutLiftId,
        liftId: LiftId,
    ): List<WorkoutLiftNote> {
        notesMutex.withLock {
            val key = pairKey(id, liftId)
            workoutNotes.value[key]?.let { return it }
            val slot = findWorkoutLift(id) ?: return emptyList()
            // The legacy scalar belongs to the slot's current pair only.
            if (slot.liftId != liftId) return emptyList()
            // Sanitizer parity with Room: trim and enforce the length cap before
            // creating the row; a blank legacy pin migrates to nothing.
            val clean = sanitizeLiftNoteBody(slot.pinnedNote)
                ?: return emptyList()
            // Client lazy-migrate parity: persist the legacy pin as the
            // position-0 row (written inline — routing through the setter would
            // recurse via its id-retention read). The mirror already matches,
            // so it is untouched.
            val migrated =
                listOf(
                    WorkoutLiftNote(
                        id = WorkoutLiftNoteId(UUID.randomUUID().toString()),
                        workoutLiftId = id,
                        body = clean,
                        position = 0,
                        updatedAt = Instant.now(),
                    ),
                )
            workoutNotes.value = workoutNotes.value + (key to migrated)
            return migrated
        }
    }

    override suspend fun setWorkoutLiftPinnedNotes(
        id: WorkoutLiftId,
        notes: List<WorkoutLiftNoteInput>,
    ): Boolean {
        val slot = findWorkoutLift(id) ?: return false
        return setWorkoutLiftPinnedNotes(id, slot.liftId, notes)
    }

    override suspend fun setWorkoutLiftPinnedNotes(
        id: WorkoutLiftId,
        liftId: LiftId,
        notes: List<WorkoutLiftNoteInput>,
    ): Boolean {
        val slot = findWorkoutLift(id) ?: return false
        notesMutex.withLock {
            val now = Instant.now()
            val key = pairKey(id, liftId)
            // Read the map directly: the getter lazy-migrates legacy pins, which
            // would mint a throwaway row just to compute id retention here.
            val currentIds = workoutNotes.value[key]?.map { it.id }?.toSet().orEmpty()
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
            workoutNotes.value = workoutNotes.value + (key to minted)
            // The scalar mirror tracks the slot's current pair only.
            if (slot.liftId == liftId) {
                updateWorkoutLiftPin(id, minted.firstOrNull()?.body)
            }
            return true
        }
    }

    /** Template slots explicitly assigned [equipmentId] (STR-105 sweep). */
    fun countEquipmentSlots(equipmentId: com.stratum.domain.lift.identity.EquipmentId): Int =
        programs.value.values
            .flatMap { it.workouts }
            .flatMap { it.workoutLifts }
            .count { it.equipmentId == equipmentId }

    /** STR-105 sweep: re-point template slots off deleted equipment. */
    fun repointEquipment(
        deletedId: com.stratum.domain.lift.identity.EquipmentId,
        replacementId: com.stratum.domain.lift.identity.EquipmentId,
    ) {
        programs.value = programs.value.mapValues { (_, program) ->
            program.copy(
                workouts = program.workouts.map { workout ->
                    workout.copy(
                        workoutLifts = workout.workoutLifts.map { slot ->
                            if (slot.equipmentId == deletedId) {
                                slot.copy(equipmentId = replacementId)
                            } else {
                                slot
                            }
                        },
                    )
                },
            )
        }
    }

    /** Drops every pair-note entry scoped to [liftId] (merge tombstone sweep parity). */
    suspend fun dropPairNotesForLift(liftId: LiftId) {
        notesMutex.withLock {
            workoutNotes.value =
                workoutNotes.value.filterKeys { it.liftId != liftId }
        }
    }

    /**
     * STR-131 sweep: remove every template slot pointing at [liftId] across all
     * programs (workouts survive, possibly empty) and drop every pair-note
     * entry scoped to the lift, including stashes on surviving slots.
     */
    suspend fun removeSlotsForLift(liftId: LiftId) {
        programs.value = programs.value.mapValues { (_, program) ->
            program.copy(
                workouts = program.workouts.map { workout ->
                    workout.copy(
                        workoutLifts = workout.workoutLifts.filterNot { it.liftId == liftId },
                    )
                },
            )
        }
        notesMutex.withLock {
            workoutNotes.value =
                workoutNotes.value.filterKeys { it.liftId != liftId }
        }
        pruneOrphanedWorkoutNotes()
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
