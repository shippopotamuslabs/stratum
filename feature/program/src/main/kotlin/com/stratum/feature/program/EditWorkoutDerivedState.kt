package com.stratum.feature.program

import com.stratum.domain.lift.Lift
import com.stratum.domain.program.ComputeWorkoutMuscleVolumeUseCase
import com.stratum.domain.program.EstimateWorkoutDurationMinutesUseCase
import com.stratum.domain.program.WorkoutLiftDurationInput
import com.stratum.domain.program.WorkoutLiftVolumeInput
import com.stratum.feature.program.ui.EditWorkoutUiState

internal fun EditWorkoutUiState.Loaded.withDerivedMetrics(
    liftCatalog: List<Lift>,
    computeWorkoutMuscleVolume: ComputeWorkoutMuscleVolumeUseCase,
    estimateWorkoutDurationMinutes: EstimateWorkoutDurationMinutesUseCase,
): EditWorkoutUiState.Loaded {
    val volumeInputs =
        lifts.map { lift ->
            WorkoutLiftVolumeInput(
                liftId = lift.liftId,
                setCount = lift.sets.size,
            )
        }
    val durationInputs =
        lifts.map { lift ->
            WorkoutLiftDurationInput(
                restSecondsBySet = lift.sets.map { set -> set.restSeconds },
            )
        }
    val volumeSummaries = computeWorkoutMuscleVolume(volumeInputs, liftCatalog)
    return copy(
        upperVolumeByMuscle = volumeSummaries.toUpperVolumeCards(),
        lowerVolumeByMuscle = volumeSummaries.toLowerVolumeCards(),
        estimatedDurationMinutes = estimateWorkoutDurationMinutes(durationInputs),
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
