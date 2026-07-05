package com.stratum.feature.program

import com.stratum.core.ui.component.StratumMuscleVolumeCardState
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.MuscleVolumeSummary
import com.stratum.domain.program.Program
import com.stratum.domain.program.ScheduleDayItem
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.buildWeeklySchedule
import com.stratum.feature.program.ui.ProgramViewState
import com.stratum.feature.program.ui.ProgramsUiState
import com.stratum.feature.program.ui.ScheduleItemState
import com.stratum.feature.program.ui.WorkoutLiftLineState
import com.stratum.feature.program.ui.WorkoutViewState
import java.util.Locale

/**
 * Domain → presentation mapping for the Programs screen. `null` (no program) maps to
 * [ProgramsUiState.Empty]; a non-null [Program] maps to [ProgramsUiState.Loaded].
 */
internal fun Program?.toProgramsUiState(
    liftNamesById: Map<String, String> = emptyMap(),
    volumeSummaries: List<MuscleVolumeSummary> = emptyList(),
): ProgramsUiState =
    if (this == null) {
        ProgramsUiState.Empty
    } else {
        ProgramsUiState.Loaded(
            program = ProgramViewState(name = name),
            upperVolumeByMuscle = volumeSummaries.toUpperVolumeCards(),
            lowerVolumeByMuscle = volumeSummaries.toLowerVolumeCards(),
            scheduleItems = buildWeeklySchedule(this).map { item -> item.toScheduleItemState(liftNamesById) },
        )
    }

internal fun resolveLiftDisplayName(liftId: String, liftNamesById: Map<String, String>): String {
    liftNamesById[liftId]?.let { return it }
    if (looksLikeUuid(liftId)) {
        return "Unknown lift"
    }
    return liftId
        .split('-')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase() else char.toString()
            }
        }
}

internal fun formatRepRangeLabel(sets: List<LiftSet>): String {
    val withReps = sets.filter { set -> set.repMin > 0 || set.repMax > 0 }
    if (withReps.isEmpty()) {
        return "—"
    }
    val minRep = withReps.minOf { it.repMin }
    val maxRep = withReps.maxOf { it.repMax }
    return if (minRep == maxRep) {
        minRep.toString()
    } else {
        "$minRep\u2013$maxRep"
    }
}

internal fun formatTopSetRpeSuffix(sets: List<LiftSet>): String {
    val topSet = sets.firstOrNull { set -> set.isTopSet }
    return topSet?.let { set -> "@${10 - set.rirGoal}" } ?: ""
}

/**
 * Builds the structured [WorkoutLiftLineState] fields for a lift. The name resolves from the
 * catalog (or falls back to a title-cased slug / "Unknown lift"); sets, rep range, and top-set
 * RPE are computed by [formatRepRangeLabel] and [formatTopSetRpeSuffix].
 */
internal fun buildLiftLineState(
    workoutLiftId: WorkoutLiftId,
    liftId: LiftId,
    sets: List<LiftSet>,
    liftNamesById: Map<String, String>,
    position: Int,
): WorkoutLiftLineState =
    WorkoutLiftLineState(
        workoutLiftId = workoutLiftId.value,
        liftId = liftId.value,
        name = resolveLiftDisplayName(liftId.value, liftNamesById),
        setCount = sets.size,
        repRange = formatRepRangeLabel(sets),
        topSetRpe = formatTopSetRpeSuffix(sets),
        position = position,
    )

private fun ScheduleDayItem.toScheduleItemState(liftNamesById: Map<String, String>): ScheduleItemState =
    when (this) {
        is ScheduleDayItem.RestDay ->
            ScheduleItemState.RestDay(weekdayLabel = weekday.toDisplayLabel())
        is ScheduleDayItem.WorkoutDay ->
            ScheduleItemState.Workout(
                workout = workout.toViewState(liftNamesById, weekday),
            )
    }

private fun Workout.toViewState(liftNamesById: Map<String, String>, weekday: Weekday): WorkoutViewState =
    WorkoutViewState(
        workoutId = id.value,
        name = name,
        weekdayLabel = weekday.toDisplayLabel(),
        position = position,
        lifts =
            workoutLifts.sortedBy { lift -> lift.position }.map { lift ->
                lift.toViewState(liftNamesById)
            },
    )

private fun WorkoutLift.toViewState(liftNamesById: Map<String, String>): WorkoutLiftLineState =
    buildLiftLineState(
        workoutLiftId = id,
        liftId = liftId,
        sets = sets.sortedBy { set -> set.position },
        liftNamesById = liftNamesById,
        position = position,
    )

private fun Weekday.toDisplayLabel(): String =
    name.replaceFirstChar { char -> char.titlecase(Locale.US) }

private const val UUID_LENGTH = 36
private const val UUID_HYPHEN_COUNT = 4

private fun looksLikeUuid(value: String): Boolean =
    value.length == UUID_LENGTH && value.count { it == '-' } == UUID_HYPHEN_COUNT
