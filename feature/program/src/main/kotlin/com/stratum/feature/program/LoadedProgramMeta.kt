package com.stratum.feature.program

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.Program
import com.stratum.domain.program.SetType
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.identity.WorkoutSupersetGroupId
import com.stratum.domain.program.schedule.CycleSlot
import com.stratum.domain.program.schedule.resolvedCycleSlots
import java.time.Instant
import java.util.UUID

/** Snapshot of persisted program fields that must survive edit → save. */
data class LoadedProgramMeta(
    val createdAt: Instant,
    val isActive: Boolean,
    val currentCycleNumber: Int,
    val cyclePosition: Int,
    val cycleLength: Int,
    val cycleStartEpochDay: Long? = null,
)

internal fun Program.toLoadedProgramMeta(): LoadedProgramMeta =
    LoadedProgramMeta(
        createdAt = createdAt,
        isActive = isActive,
        currentCycleNumber = currentCycleNumber,
        cyclePosition = cyclePosition,
        cycleLength = cycleLength,
        cycleStartEpochDay = cycleStartEpochDay,
    )

/**
 * Expands a [Program] into editor cycle-slot tabs (train or rest).
 *
 * Always projects from [resolvedCycleSlots] — slot index + [Program.restSlotIndices] are
 * schedule identity. Weekday-bound templates with empty rest indices and
 * `cycleLength == workouts.size` load as train slots in order (no Mon–Sun padding).
 */
internal fun Program.toCreateProgramDays(
    liftNamesById: Map<String, String>,
    compoundByLiftId: Map<String, Boolean>,
): List<CreateProgramDayUi> =
    resolvedCycleSlots().map { slot ->
        when (slot) {
            is CycleSlot.Rest ->
                CreateProgramDayUi(
                    id = "rest-${slot.slotIndex}-$id",
                    name = "Rest",
                    position = slot.slotIndex,
                    isRest = true,
                    editState = null,
                    weekday = null,
                )
            is CycleSlot.Training ->
                CreateProgramDayUi(
                    id = slot.workout.id.value,
                    name = slot.workout.name,
                    position = slot.slotIndex,
                    isRest = false,
                    editState =
                        slot.workout.toEditWorkoutUiState(liftNamesById, compoundByLiftId),
                    // Projection cache only — not schedule identity.
                    weekday = slot.workout.weekday,
                )
        }
    }

/**
 * Maps ordered editor tabs → domain [Program].
 *
 * - [Program.cycleLength] = train + rest slot count (all tabs)
 * - [Program.restSlotIndices] = tab indices marked rest (or missing edit state)
 * - Workouts are training slots only; [Workout.position] is 0..T-1 among train slots
 * - Tab index is **not** [Weekday.entries] identity — weekday fields stay null / empty
 */
internal fun CreateProgramUiState.toDomainProgram(meta: LoadedProgramMeta?): Program {
    val programId = ProgramId(this.programId.ifBlank { UUID.randomUUID().toString() })
    val orderedDays = days.sortedBy { day -> day.position }
    val restSlotIndices = linkedSetOf<Int>()
    val workouts = mutableListOf<Workout>()

    orderedDays.forEachIndexed { slotIndex, day ->
        if (day.isRest || day.editState == null) {
            restSlotIndices += slotIndex
            return@forEachIndexed
        }
        val edit = day.editState
        workouts +=
            Workout(
                id = WorkoutId(edit.workoutId),
                programId = programId,
                name = day.name,
                // Weekday is optional projection only — never derived from tab index.
                weekday = null,
                position = workouts.size,
                workoutLifts =
                    edit.lifts.mapIndexed { liftIndex, lift ->
                        WorkoutLift(
                            id = WorkoutLiftId(lift.workoutLiftId),
                            workoutId = WorkoutId(edit.workoutId),
                            liftId = LiftId(lift.liftId),
                            progressionScheme = lift.progressionScheme,
                            position = liftIndex,
                            sets =
                                lift.sets.mapIndexed { setIndex, set ->
                                    LiftSet(
                                        id = LiftSetId(set.setId),
                                        workoutLiftId = WorkoutLiftId(lift.workoutLiftId),
                                        position = setIndex,
                                        setType = SetType.Standard,
                                        repMin = set.repMin,
                                        repMax = set.repMax,
                                        rirGoal = set.rirGoal,
                                        isTopSet = set.isTopSet,
                                        restSeconds = set.restSeconds,
                                    )
                                },
                            supersetGroupId = lift.supersetGroupId?.let { WorkoutSupersetGroupId(it) },
                            useRestTimerBetweenLifts = lift.useRestTimerBetweenLifts,
                            pinnedNote = lift.pinnedNote,
                        )
                    },
            )
    }

    val cycleLength = orderedDays.size.coerceAtLeast(1)
    val cyclePosition =
        meta?.cyclePosition?.coerceIn(0, (cycleLength - 1).coerceAtLeast(0)) ?: 0
    return Program(
        id = programId,
        name = programName.ifBlank { "New Program" },
        createdAt = meta?.createdAt ?: Instant.now(),
        cycleLength = cycleLength,
        // Slot index + restSlotIndices are authoritative; clear weekday projection cache.
        trainingWeekdays = emptySet(),
        workouts = workouts,
        currentCycleNumber = meta?.currentCycleNumber ?: 1,
        cyclePosition = cyclePosition,
        // Preserve active when editing; create drafts default inactive until Activate/Save paths set it.
        isActive = meta?.isActive ?: false,
        restSlotIndices = restSlotIndices,
        cycleStartEpochDay = meta?.cycleStartEpochDay,
    )
}
