package com.stratum.feature.program

import com.stratum.core.ui.component.StratumMuscleVolumeCardState
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.feature.program.ui.EditLiftState
import com.stratum.feature.program.ui.EditWorkoutUiState
import java.util.Locale

internal data class ProgramSheetMuscleSets(
    val muscle: MuscleGroup,
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
    val total: Double,
)

internal data class ProgramSheetVolumePivotRow(
    val muscle: MuscleGroup,
    val muscleName: String,
    val programTotal: Double,
    val cells: List<Double>,
)

internal data class ProgramSheetVolumePivot(
    val columns: List<ProgramSheetVolumeColumn>,
    val rows: List<ProgramSheetVolumePivotRow>,
    val cornerTotal: Double,
)

/** One muscle chip. [countText] is [formatSheetSetCount]. */
internal data class ProgramSheetVolumeChip(
    val muscleName: String,
    val countText: String,
)

/**
 * Sheet metrics from cards and set counts already stored on the days.
 * Program cards come from the first day whose edit state holds program cards;
 * a day with empty program cards does not speak for the whole program.
 */
internal fun programSheetMetricsFromEditorDays(days: List<CreateProgramDayUi>): ProgramSheetMetrics {
    val programEdit =
        days.firstNotNullOfOrNull { day ->
            day.sheetEditOrNull()?.takeIf { edit ->
                edit.programUpperVolumeByMuscle.isNotEmpty() ||
                    edit.programLowerVolumeByMuscle.isNotEmpty()
            }
        } ?: days.firstNotNullOfOrNull { day -> day.sheetEditOrNull() }
    val workouts =
        days.mapNotNull { day ->
            day.sheetEditOrNull()?.let { edit -> edit.toSheetWorkoutMetrics(day.id) }
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
    val workoutByDayId = metrics.workouts.associateBy { workout -> workout.dayId }
    val columnDays = days.filter { day -> day.sheetEditOrNull() != null }
    val rows =
        muscles.map { muscle ->
            ProgramSheetVolumePivotRow(
                muscle = muscle.muscle,
                muscleName = muscle.muscleName,
                programTotal = muscle.sets,
                cells =
                    columnDays.map { day ->
                        workoutByDayId[day.id].setCountFor(muscle.muscle)
                    },
            )
        }
    val columns =
        columnDays.mapIndexed { index, day ->
            ProgramSheetVolumeColumn(
                dayId = day.id,
                workoutName = day.name,
                total = rows.sumOf { row -> row.cells[index] },
            )
        }
    return ProgramSheetVolumePivot(
        columns = columns,
        rows = rows,
        cornerTotal = rows.sumOf { row -> row.programTotal },
    )
}

internal fun formatSheetSetCount(sets: Double): String =
    String.format(Locale.US, ONE_DECIMAL, sets)

/**
 * Ordered chip model for the pinned strip and each workout header.
 * Same muscles, order, and counts as [muscles]. [ProgramSheetVolumeChip.countText]
 * is [formatSheetSetCount], the one sheet set-count formatter.
 */
internal fun programSheetVolumeChips(muscles: List<ProgramSheetMuscleSets>): List<ProgramSheetVolumeChip> =
    muscles.map { muscle ->
        ProgramSheetVolumeChip(
            muscleName = muscle.muscleName,
            countText = formatSheetSetCount(muscle.sets),
        )
    }

internal fun programSheetMuscleSets(cards: List<StratumMuscleVolumeCardState>): List<ProgramSheetMuscleSets> =
    cards
        .filter { card -> card.setCount != ZERO_SETS }
        .map { card ->
            ProgramSheetMuscleSets(
                muscle = card.muscle,
                muscleName = card.displayName,
                sets = card.setCount,
            )
        }

private fun ProgramSheetMetrics.programMuscleSets(): List<ProgramSheetMuscleSets> =
    programSheetMuscleSets(programUpperVolumeByMuscle + programLowerVolumeByMuscle)

/** The one set-counting formula for the sheet; every builder reads totals from here. */
internal fun sheetSetTotal(lifts: List<EditLiftState>): Int = lifts.sumOf { lift -> lift.sets.size }

private fun EditWorkoutUiState.Loaded.toSheetWorkoutMetrics(dayId: String): ProgramSheetWorkoutMetrics =
    ProgramSheetWorkoutMetrics(
        dayId = dayId,
        setTotal = sheetSetTotal(lifts),
        estimatedDurationMinutes = estimatedDurationMinutes,
        upperVolumeByMuscle = upperVolumeByMuscle,
        lowerVolumeByMuscle = lowerVolumeByMuscle,
    )

private fun ProgramSheetWorkoutMetrics?.setCountFor(muscle: MuscleGroup): Double {
    val cards = this?.upperVolumeByMuscle.orEmpty() + this?.lowerVolumeByMuscle.orEmpty()
    return cards.firstOrNull { card -> card.muscle == muscle }?.setCount ?: ZERO_SETS
}

private const val ZERO_SETS = 0.0
private const val ONE_DECIMAL = "%.1f"
