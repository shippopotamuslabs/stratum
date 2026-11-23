package com.stratum

import com.stratum.core.ui.component.TrendPointUi
import com.stratum.core.ui.component.formatWeightForDisplay
import com.stratum.domain.dashboard.MuscleDetail
import com.stratum.domain.dashboard.MuscleDetailLift
import com.stratum.domain.dashboard.MuscleDetailSession
import com.stratum.domain.dashboard.MuscleDetailTopSet
import com.stratum.domain.dashboard.SeriesDirection
import com.stratum.domain.lift.Weight
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Two-pane Dashboard detail state (STR-331). Presentation types only. Selection is
 * session-scoped in the ViewModel, so it survives folding and unfolding.
 */
data class DashboardPaneUiState(
    val selection: DashboardPaneSelectionUi = DashboardPaneSelectionUi.None,
    val muscleDetail: MuscleDetailPaneUiState = MuscleDetailPaneUiState.Loading,
)

/** What the right pane shows. Defaults to the first muscle card when nothing was picked. */
sealed interface DashboardPaneSelectionUi {
    data object None : DashboardPaneSelectionUi

    data class Muscle(val muscleKey: String) : DashboardPaneSelectionUi

    data class Lift(val liftId: String) : DashboardPaneSelectionUi
}

sealed interface MuscleDetailPaneUiState {
    data object Loading : MuscleDetailPaneUiState

    data object Error : MuscleDetailPaneUiState

    data class Loaded(
        val muscleKey: String,
        val lifts: List<MuscleDetailLiftUi>,
        val recentSessions: List<MuscleDetailSessionUi>,
    ) : MuscleDetailPaneUiState
}

/** One lift row in the muscle pane: credited sets per week plus its e1RM direction. */
data class MuscleDetailLiftUi(
    val liftId: String,
    val name: String,
    /** e.g. "4.5 sets/wk". */
    val setsPerWeekText: String,
    val isPrimary: Boolean,
    val badge: TrendBadge,
)

data class MuscleDetailSessionUi(
    val sessionId: String,
    val dateText: String,
    val workoutLabel: String?,
    val topSets: List<MuscleDetailTopSetUi>,
)

data class MuscleDetailTopSetUi(
    val liftName: String,
    /** e.g. "225 lb × 8". */
    val setText: String,
    val rir: Int?,
)

internal fun MuscleDetail.toPaneUi(
    displayUnit: Weight.WeightUnit,
    zone: ZoneId,
): MuscleDetailPaneUiState.Loaded =
    MuscleDetailPaneUiState.Loaded(
        muscleKey = muscle.name,
        lifts = lifts.map { it.toUi() },
        recentSessions = recentSessions.map { it.toUi(displayUnit, zone) },
    )

private fun MuscleDetailLift.toUi(): MuscleDetailLiftUi =
    MuscleDetailLiftUi(
        liftId = liftId.value,
        name = name,
        setsPerWeekText = "${formatHardSetAmount(setsPerWeek)} sets/wk",
        isPrimary = isPrimary,
        badge = when (direction) {
            SeriesDirection.Up -> TrendBadge.Up
            SeriesDirection.Flat -> TrendBadge.Flat
            SeriesDirection.Down -> TrendBadge.Down
            SeriesDirection.Insufficient -> TrendBadge.Unknown
        },
    )

private fun MuscleDetailSession.toUi(
    displayUnit: Weight.WeightUnit,
    zone: ZoneId,
): MuscleDetailSessionUi =
    MuscleDetailSessionUi(
        sessionId = sessionId.value,
        dateText = SESSION_DATE_FORMAT.format(completedAt.atZone(zone)),
        workoutLabel = workoutLabel,
        topSets = topSets.map { it.toUi(displayUnit) },
    )

private fun MuscleDetailTopSet.toUi(displayUnit: Weight.WeightUnit): MuscleDetailTopSetUi =
    MuscleDetailTopSetUi(
        liftName = liftName,
        setText = "${formatWeightForDisplay(weight, displayUnit = displayUnit, includeUnit = true)} × $reps",
        rir = rir,
    )

/**
 * The muscle trend for the pane: the last [weeks] aligned weeks of the card series, re-indexed
 * from zero so progress bars and the volume line keep sharing an x axis.
 */
internal fun MuscleSeriesUi.lastWeeks(weeks: Int): Pair<List<TrendPointUi>, List<TrendPointUi>> {
    val lastIndex = (progressPoints + volumePoints).maxOfOrNull { it.weekIndex }
        ?: return emptyList<TrendPointUi>() to emptyList()
    val firstKept = (lastIndex - weeks + 1).coerceAtLeast(0)
    fun List<TrendPointUi>.trim(): List<TrendPointUi> =
        filter { it.weekIndex >= firstKept }.map { it.copy(weekIndex = it.weekIndex - firstKept) }
    return progressPoints.trim() to volumePoints.trim()
}

/** Weeks of trend the muscle pane chart shows. */
internal const val MUSCLE_PANE_TREND_WEEKS = 8

private val SESSION_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")
