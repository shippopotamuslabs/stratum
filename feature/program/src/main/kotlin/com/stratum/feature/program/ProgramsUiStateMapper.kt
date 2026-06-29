package com.stratum.feature.program

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.Program
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.feature.program.ui.ProgramViewState
import com.stratum.feature.program.ui.ProgramsUiState
import com.stratum.feature.program.ui.WorkoutLiftViewState
import com.stratum.feature.program.ui.WorkoutViewState

/**
 * Domain → presentation mapping for the Programs screen. `null` (no program) maps to
 * [ProgramsUiState.Empty]; a non-null [Program] maps to [ProgramsUiState.Loaded].
 *
 * Lift display names resolve through [liftNamesById] (catalog id → human name). Slug ids fall
 * back to title-cased words; unrecognized UUIDs fall back to a generic label.
 */
internal fun Program?.toProgramsUiState(liftNamesById: Map<String, String> = emptyMap()): ProgramsUiState =
    if (this == null) {
        ProgramsUiState.Empty
    } else {
        ProgramsUiState.Loaded(
            program = ProgramViewState(
                name = name,
                workouts = workouts.sortedBy { it.position }.map { it.toViewState(liftNamesById) },
            ),
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

internal fun formatLiftSummaryLine(
    liftId: LiftId,
    sets: List<LiftSet>,
    liftNamesById: Map<String, String>,
): String {
    val name = resolveLiftDisplayName(liftId.value, liftNamesById)
    val setCount = sets.size
    val repRange = formatRepRangeLabel(sets)
    return "$name - $setCount x $repRange"
}

private fun Workout.toViewState(liftNamesById: Map<String, String>): WorkoutViewState = WorkoutViewState(
    name = name,
    weekdayLabel = weekday?.name,
    position = position,
    workoutLifts = workoutLifts.sortedBy { it.position }.map { it.toViewState(liftNamesById) },
)

private fun WorkoutLift.toViewState(liftNamesById: Map<String, String>): WorkoutLiftViewState =
    WorkoutLiftViewState(
        summaryLine = formatLiftSummaryLine(
            liftId = liftId,
            sets = sets.sortedBy { it.position },
            liftNamesById = liftNamesById,
        ),
        position = position,
    )

private const val UUID_LENGTH = 36
private const val UUID_HYPHEN_COUNT = 4

private fun looksLikeUuid(value: String): Boolean =
    value.length == UUID_LENGTH && value.count { it == '-' } == UUID_HYPHEN_COUNT
