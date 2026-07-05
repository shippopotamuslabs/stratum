package com.stratum.feature.program

import com.stratum.domain.lift.Lift
import com.stratum.domain.program.ComputeWorkoutMuscleVolumeUseCase
import com.stratum.domain.program.EstimateWorkoutDurationMinutesUseCase
import com.stratum.domain.program.Program
import com.stratum.domain.program.WorkoutLiftVolumeInput
import com.stratum.domain.program.WorkoutLiftDurationInput
import com.stratum.feature.program.ui.EditLiftState
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
            WorkoutLiftDurationInput(
                restSecondsBySet = lift.sets.map { set -> set.restSeconds },
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

private const val MINUTES_PER_HOUR = 60
