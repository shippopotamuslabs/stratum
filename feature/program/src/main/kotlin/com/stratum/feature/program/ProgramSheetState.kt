package com.stratum.feature.program

import com.stratum.application.program.ComputeWorkoutMuscleVolumeUseCase
import com.stratum.application.program.EstimateWorkoutDurationMinutesUseCase
import com.stratum.core.ui.component.StratumMuscleVolumeCardState
import com.stratum.core.ui.component.toLowerVolumeCards
import com.stratum.core.ui.component.toUpperVolumeCards
import com.stratum.domain.lift.Lift
import com.stratum.feature.program.ui.EditWorkoutUiState

/**
 * In-memory edit state for every cycle day at once.
 * Lift mutations stay in [EditWorkoutLiftMutations]; volume and duration stay in the
 * existing workout use cases so primary 1.0 / secondary 0.5 credit is not reimplemented.
 */
internal data class ProgramSheetWorkoutMetrics(
    val dayId: String,
    val setTotal: Int,
    val estimatedDurationMinutes: Int,
    val upperVolumeByMuscle: List<StratumMuscleVolumeCardState>,
    val lowerVolumeByMuscle: List<StratumMuscleVolumeCardState>,
)

internal data class ProgramSheetMetrics(
    val workouts: List<ProgramSheetWorkoutMetrics>,
    val programUpperVolumeByMuscle: List<StratumMuscleVolumeCardState>,
    val programLowerVolumeByMuscle: List<StratumMuscleVolumeCardState>,
)

internal fun updateProgramDay(
    days: List<CreateProgramDayUi>,
    dayId: String,
    transform: (EditWorkoutUiState.Loaded) -> EditWorkoutUiState.Loaded,
): List<CreateProgramDayUi> =
    days.map { day ->
        val edit = day.editState
        if (day.id != dayId || edit == null) {
            day
        } else {
            day.copy(editState = transform(edit), isRest = false)
        }
    }

internal fun deriveProgramSheet(
    days: List<CreateProgramDayUi>,
    liftCatalog: List<Lift>,
    computeWorkoutMuscleVolume: ComputeWorkoutMuscleVolumeUseCase,
    estimateWorkoutDurationMinutes: EstimateWorkoutDurationMinutesUseCase,
): ProgramSheetMetrics {
    val workouts =
        days.mapNotNull { day ->
            val edit = day.editState ?: return@mapNotNull null
            metricsForDay(
                dayId = day.id,
                edit = edit,
                liftCatalog = liftCatalog,
                computeWorkoutMuscleVolume = computeWorkoutMuscleVolume,
                estimateWorkoutDurationMinutes = estimateWorkoutDurationMinutes,
            )
        }
    val programInputs = days.flatMap { day -> day.editState?.lifts?.toVolumeInputs().orEmpty() }
    val programVolume = computeWorkoutMuscleVolume(programInputs, liftCatalog)
    return ProgramSheetMetrics(
        workouts = workouts,
        programUpperVolumeByMuscle = programVolume.toUpperVolumeCards(),
        programLowerVolumeByMuscle = programVolume.toLowerVolumeCards(),
    )
}

private fun metricsForDay(
    dayId: String,
    edit: EditWorkoutUiState.Loaded,
    liftCatalog: List<Lift>,
    computeWorkoutMuscleVolume: ComputeWorkoutMuscleVolumeUseCase,
    estimateWorkoutDurationMinutes: EstimateWorkoutDurationMinutesUseCase,
): ProgramSheetWorkoutMetrics {
    // program = null leaves this call's program cards empty. Sheet totals are folded
    // from every edited day in deriveProgramSheet, not taken from these cards.
    val derived =
        edit.withDerivedMetrics(
            liftCatalog = liftCatalog,
            program = null,
            computeWorkoutMuscleVolume = computeWorkoutMuscleVolume,
            estimateWorkoutDurationMinutes = estimateWorkoutDurationMinutes,
        )
    return ProgramSheetWorkoutMetrics(
        dayId = dayId,
        setTotal = edit.lifts.sumOf { lift -> lift.sets.size },
        estimatedDurationMinutes = derived.estimatedDurationMinutes,
        upperVolumeByMuscle = derived.upperVolumeByMuscle,
        lowerVolumeByMuscle = derived.lowerVolumeByMuscle,
    )
}
