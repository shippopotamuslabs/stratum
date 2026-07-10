package com.stratum.feature.program

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.Program
import com.stratum.domain.program.ScheduleDayItem
import com.stratum.domain.program.SetType
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.buildWeeklyScheduleOrFallback
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.identity.WorkoutSupersetGroupId
import java.time.Instant
import java.util.UUID

/** Snapshot of persisted program fields that must survive edit → save. */
data class LoadedProgramMeta(
    val createdAt: Instant,
    val isActive: Boolean,
    val currentCycleNumber: Int,
    val cyclePosition: Int,
    val cycleLength: Int,
)

internal fun Program.toLoadedProgramMeta(): LoadedProgramMeta =
    LoadedProgramMeta(
        createdAt = createdAt,
        isActive = isActive,
        currentCycleNumber = currentCycleNumber,
        cyclePosition = cyclePosition,
        cycleLength = cycleLength,
    )

internal fun Program.toCreateProgramDays(
    liftNamesById: Map<String, String>,
    compoundByLiftId: Map<String, Boolean>,
): List<CreateProgramDayUi> {
    val scheduleDays = buildWeeklyScheduleOrFallback(this)
    return scheduleDays.mapIndexed { index, item ->
        when (item) {
            is ScheduleDayItem.RestDay ->
                CreateProgramDayUi(
                    id = "rest-${item.weekday.name}-$id",
                    name = "Rest",
                    position = index,
                    isRest = true,
                    editState = null,
                    weekday = item.weekday,
                )
            is ScheduleDayItem.WorkoutDay ->
                CreateProgramDayUi(
                    id = item.workout.id.value,
                    name = item.workout.name,
                    position = index,
                    isRest = false,
                    editState =
                        item.workout.toEditWorkoutUiState(liftNamesById, compoundByLiftId),
                    weekday = item.weekday,
                )
        }
    }
}

internal fun CreateProgramUiState.toDomainProgram(meta: LoadedProgramMeta?): Program {
    val programId = ProgramId(this.programId.ifBlank { UUID.randomUUID().toString() })
    val workouts =
        days
            .filter { day -> !day.isRest && day.editState != null }
            .mapIndexed { index, day ->
                val edit = day.editState!!
                Workout(
                    id = WorkoutId(edit.workoutId),
                    programId = programId,
                    name = day.name,
                    weekday = day.weekday,
                    position = index,
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
                            )
                        },
                )
            }
    val trainingWeekdays =
        workouts.mapNotNull { workout -> workout.weekday }.toSet().ifEmpty {
            days.mapNotNull { day -> day.weekday.takeIf { !day.isRest } }.toSet()
        }
    val workoutCount = workouts.size.coerceAtLeast(1)
    val cyclePosition =
        meta?.cyclePosition?.coerceIn(0, (workoutCount - 1).coerceAtLeast(0)) ?: 0
    return Program(
        id = programId,
        name = programName.ifBlank { "New Program" },
        createdAt = meta?.createdAt ?: Instant.now(),
        cycleLength = workoutCount,
        trainingWeekdays = trainingWeekdays,
        workouts = workouts,
        currentCycleNumber = meta?.currentCycleNumber ?: 1,
        cyclePosition = cyclePosition,
        // Preserve active when editing; create drafts default inactive until Activate/Save paths set it.
        isActive = meta?.isActive ?: false,
    )
}
