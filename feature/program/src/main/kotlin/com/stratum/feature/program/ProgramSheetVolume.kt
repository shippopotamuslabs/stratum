package com.stratum.feature.program

import com.stratum.core.ui.component.StratumMuscleVolumeCardState
import com.stratum.feature.program.ui.EditWorkoutUiState
import java.util.Locale

internal data class ProgramSheetMuscleSets(
    val muscleName: String,
    val sets: Double,
)

internal data class ProgramSheetVolumeStrip(
    val programMuscles: List<ProgramSheetMuscleSets>,
    val programSetTotal: Int,
)

internal data class ProgramSheetVolumeColumn(
    val dayId: String,
    val workoutName: String,
    val cells: List<Double>,
)

internal data class ProgramSheetVolumePivot(
    val muscleNames: List<String>,
    val workoutColumns: List<ProgramSheetVolumeColumn>,
    val programTotals: List<Double>,
    val columnTotals: List<Double>,
    val cornerTotal: Double,
)

/**
 * Sheet metrics from cards and set counts already stored on the days.
 * Program cards come from the first day that has an edit state.
 */
internal fun programSheetMetricsFromEditorDays(days: List<CreateProgramDayUi>): ProgramSheetMetrics {
    val programEdit = days.firstNotNullOfOrNull { day -> day.editState }
    val workouts =
        days.mapNotNull { day ->
            val edit = day.editState
            if (day.isRest || edit == null) {
                null
            } else {
                edit.toSheetWorkoutMetrics(day.id)
            }
        }
    return ProgramSheetMetrics(
        workouts = workouts,
        programUpperVolumeByMuscle = programEdit?.programUpperVolumeByMuscle.orEmpty(),
        programLowerVolumeByMuscle = programEdit?.programLowerVolumeByMuscle.orEmpty(),
    )
}

internal fun programSheetVolumeStrip(metrics: ProgramSheetMetrics): ProgramSheetVolumeStrip =
    ProgramSheetVolumeStrip(
        programMuscles = metrics.programMuscleSets(),
        programSetTotal = metrics.workouts.sumOf { workout -> workout.setTotal },
    )

internal fun programSheetVolumePivot(
    metrics: ProgramSheetMetrics,
    days: List<CreateProgramDayUi>,
): ProgramSheetVolumePivot {
    val muscles = metrics.programMuscleSets()
    val names = muscles.map { muscle -> muscle.muscleName }
    val programTotals = muscles.map { muscle -> muscle.sets }
    val workoutColumns =
        days.mapNotNull { day ->
            if (day.isRest || day.editState == null) {
                null
            } else {
                val workout = metrics.workouts.firstOrNull { it.dayId == day.id }
                ProgramSheetVolumeColumn(
                    dayId = day.id,
                    workoutName = day.name,
                    cells = names.map { name -> workout.setCountFor(name) },
                )
            }
        }
    val columnTotals = workoutColumns.map { column -> column.cells.sum() }
    return ProgramSheetVolumePivot(
        muscleNames = names,
        workoutColumns = workoutColumns,
        programTotals = programTotals,
        columnTotals = columnTotals,
        cornerTotal = programTotals.sum(),
    )
}

internal fun programSheetVolumeMuscleLabel(name: String, sets: Double): String {
    val count = String.format(Locale.US, ONE_DECIMAL, sets)
    return "$name $count"
}

/** Plaintext per-muscle sets, same fractional credit as the volume cards. */
internal fun programSheetVolumeMuscleLine(muscles: List<ProgramSheetMuscleSets>): String =
    muscles.joinToString(separator = VOLUME_ITEM_GAP) { muscle ->
        programSheetVolumeMuscleLabel(muscle.muscleName, muscle.sets)
    }

internal fun programSheetMuscleSets(cards: List<StratumMuscleVolumeCardState>): List<ProgramSheetMuscleSets> =
    cards
        .filter { card -> card.setCount != ZERO_SETS }
        .map { card -> ProgramSheetMuscleSets(muscleName = card.displayName, sets = card.setCount) }

private fun ProgramSheetMetrics.programMuscleSets(): List<ProgramSheetMuscleSets> =
    programSheetMuscleSets(programUpperVolumeByMuscle + programLowerVolumeByMuscle)

private fun EditWorkoutUiState.Loaded.toSheetWorkoutMetrics(dayId: String): ProgramSheetWorkoutMetrics =
    ProgramSheetWorkoutMetrics(
        dayId = dayId,
        setTotal = lifts.sumOf { lift -> lift.sets.size },
        estimatedDurationMinutes = estimatedDurationMinutes,
        upperVolumeByMuscle = upperVolumeByMuscle,
        lowerVolumeByMuscle = lowerVolumeByMuscle,
    )

private fun ProgramSheetWorkoutMetrics?.setCountFor(muscleName: String): Double {
    val cards = this?.upperVolumeByMuscle.orEmpty() + this?.lowerVolumeByMuscle.orEmpty()
    return cards.firstOrNull { card -> card.displayName == muscleName }?.setCount ?: ZERO_SETS
}

private const val ZERO_SETS = 0.0
private const val ONE_DECIMAL = "%.1f"
private const val VOLUME_ITEM_GAP = " - "
