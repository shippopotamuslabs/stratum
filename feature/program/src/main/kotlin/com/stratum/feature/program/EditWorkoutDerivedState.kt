package com.stratum.feature.program

import com.stratum.core.ui.component.toLowerVolumeCards
import com.stratum.core.ui.component.toUpperVolumeCards
import com.stratum.domain.lift.Lift
import com.stratum.domain.program.ComputeWorkoutMuscleVolumeUseCase
import com.stratum.domain.program.EstimateWorkoutDurationMinutesUseCase
import com.stratum.domain.program.Program
import com.stratum.domain.program.WorkoutLiftVolumeInput
import com.stratum.domain.program.WorkoutLiftDurationInput
import com.stratum.feature.program.ui.EditLiftState
import com.stratum.feature.program.ui.EditSetState
import com.stratum.feature.program.ui.EditWorkoutUiState

internal fun EditWorkoutUiState.Loaded.withDerivedMetrics(
    liftCatalog: List<Lift>,
    program: Program?,
    computeWorkoutMuscleVolume: ComputeWorkoutMuscleVolumeUseCase,
    estimateWorkoutDurationMinutes: EstimateWorkoutDurationMinutesUseCase,
): EditWorkoutUiState.Loaded {
    val volumeInputs = lifts.toVolumeInputs()
    val durationInputs =
        lifts.map { lift ->
            val inheritedReps = lift.sets.firstNotNullOfOrNull { set -> midpointRepsOrNull(set) }
            WorkoutLiftDurationInput(
                restSecondsBySet = lift.sets.map { set -> set.restSeconds },
                repsBySet =
                    lift.sets.map { set ->
                        midpointRepsOrNull(set) ?: inheritedReps ?: DEFAULT_DURATION_REPS
                    },
                position = lift.position,
                supersetGroupId = lift.supersetGroupId,
                useRestTimerBetweenLifts = lift.useRestTimerBetweenLifts,
            )
        }
    val volumeSummaries = computeWorkoutMuscleVolume(volumeInputs, liftCatalog)
    val programVolumeSummaries =
        program?.let { activeProgram ->
            val programVolumeInputs =
                buildProgramVolumeInputsWithEditedWorkout(
                    program = activeProgram,
                    editedWorkoutId = workoutId,
                    editedLifts = lifts,
                )
            computeWorkoutMuscleVolume(programVolumeInputs, liftCatalog)
        }.orEmpty()
    return copy(
        upperVolumeByMuscle = volumeSummaries.toUpperVolumeCards(),
        lowerVolumeByMuscle = volumeSummaries.toLowerVolumeCards(),
        programUpperVolumeByMuscle = programVolumeSummaries.toUpperVolumeCards(),
        programLowerVolumeByMuscle = programVolumeSummaries.toLowerVolumeCards(),
        estimatedDurationMinutes = estimateWorkoutDurationMinutes(durationInputs),
    )
}

internal fun buildProgramVolumeInputsWithEditedWorkout(
    program: Program,
    editedWorkoutId: String,
    editedLifts: List<EditLiftState>,
): List<WorkoutLiftVolumeInput> =
    program.workouts.flatMap { workout ->
        if (workout.id.value == editedWorkoutId) {
            editedLifts.toVolumeInputs()
        } else {
            workout.workoutLifts.map { workoutLift ->
                WorkoutLiftVolumeInput(
                    liftId = workoutLift.liftId.value,
                    setCount = workoutLift.sets.size,
                )
            }
        }
    }

private fun List<EditLiftState>.toVolumeInputs(): List<WorkoutLiftVolumeInput> =
    map { lift ->
        WorkoutLiftVolumeInput(
            liftId = lift.liftId,
            setCount = lift.sets.size,
        )
    }

internal fun formatEstimatedWorkoutDuration(minutes: Int): String =
    when {
        minutes <= 0 -> "0m"
        minutes < MINUTES_PER_HOUR -> "${minutes}m"
        minutes % MINUTES_PER_HOUR == 0 -> "${minutes / MINUTES_PER_HOUR}h"
        else -> "${minutes / MINUTES_PER_HOUR}h ${minutes % MINUTES_PER_HOUR}m"
    }

/** Midpoint of a set's rep band; null when Double back-off stores 0/0. */
internal fun midpointRepsOrNull(set: EditSetState): Int? {
    if (set.repMin <= 0 && set.repMax <= 0) return null
    val min = if (set.repMin > 0) set.repMin else set.repMax
    val max = if (set.repMax > 0) set.repMax else set.repMin
    return ((min + max) / 2).coerceAtLeast(1)
}

private const val MINUTES_PER_HOUR = 60

/** Mid-band of default prescription (8–12); matches domain duration fallback. */
private const val DEFAULT_DURATION_REPS = 10
