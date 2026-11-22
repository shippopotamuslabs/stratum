package com.stratum.feature.workout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumSegmentedControl
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.formatWeightAmount
import com.stratum.core.ui.component.formatWorkoutDuration
import kotlinx.coroutines.delay
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.workout.R

/**
 * The right pane of the two-pane workout screen (STR-323). The left pane stays
 * the workout screen itself; this pane follows the workout: Coach for the
 * selected lift, Overview for the session, Rest while the timer runs.
 */
@Composable
internal fun WorkoutCoachPane(
    state: WorkoutUiState.Loaded,
    primaryLiftId: String?,
    restTimer: RestTimerChrome,
    tab: WorkoutCoachTab,
    onTabChange: (WorkoutCoachTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val restLabel =
        if (restTimer.isActive) {
            stringResource(R.string.workout_coach_tab_rest_timed, formatRestDisplay(restTimer.remainingSeconds))
        } else {
            stringResource(R.string.workout_coach_tab_rest)
        }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(StratumThemeAccessor.colors.surfaceBase)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(StratumSpacing.s16),
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s16),
    ) {
        StratumSegmentedControl(
            segments =
                listOf(
                    stringResource(R.string.workout_coach_tab_coach),
                    stringResource(R.string.workout_coach_tab_overview),
                    restLabel,
                ),
            selectedIndex = tab.ordinal,
            onSelectedIndexChange = { index -> onTabChange(WorkoutCoachTab.entries[index]) },
            modifier = Modifier.fillMaxWidth(),
        )
        when (tab) {
            WorkoutCoachTab.Coach -> CoachPaneBody(state = state, primaryLiftId = primaryLiftId)
            WorkoutCoachTab.Overview -> OverviewPaneBody(state = state)
            WorkoutCoachTab.Rest -> RestPaneBody(state = state, primaryLiftId = primaryLiftId, restTimer = restTimer)
        }
    }
}

/** The coach pane's three states. The tab name appears only on the toggle. */
internal enum class WorkoutCoachTab {
    Coach,
    Overview,
    Rest,
}

/** Last session's bests for one lift, from the sets' previous-session values. */
internal data class CoachLiftBests(
    val topSetWeight: Double?,
    val totalReps: Int,
    val estimatedOneRepMax: Double?,
)

internal fun coachLiftBests(sets: List<RenderedSetUi>): CoachLiftBests =
    coachBestsOf(
        sets.mapNotNull { set ->
            val reps = set.previousReps ?: return@mapNotNull null
            set.previousWeightAmount to reps
        },
    )

/**
 * Today's bests for one lift, from its completed sets (STR-328). Logged
 * amounts arrive as display magnitudes from the mapper, so assisted sets
 * contribute their assistance amount.
 */
internal fun coachTodayBests(sets: List<RenderedSetUi>): CoachLiftBests =
    coachBestsOf(
        sets.mapNotNull { set ->
            if (!set.isComplete) return@mapNotNull null
            val reps = set.loggedReps ?: return@mapNotNull null
            set.loggedWeightAmount to reps
        },
    )

/**
 * Shared bests derivation over performed (weight, reps) pairs. A weight only
 * counts when it is positive: bodyweight sets log 0.0 with the weight column
 * hidden, and a zero top set would render as a blank amount plus the unit.
 */
private fun coachBestsOf(performed: List<Pair<Double?, Int>>): CoachLiftBests {
    val topSet =
        performed.mapNotNull { (weight, _) -> weight?.takeIf { it > 0.0 } }.maxOrNull()
    val totalReps = performed.sumOf { (_, reps) -> reps }
    val oneRepMax =
        performed
            .mapNotNull { (weight, reps) ->
                val w = weight ?: return@mapNotNull null
                if (w <= 0.0 || reps <= 0) null else w * (1.0 + reps / 30.0)
            }.maxOrNull()
    return CoachLiftBests(topSetWeight = topSet, totalReps = totalReps, estimatedOneRepMax = oneRepMax)
}

/** True when the bests carry anything worth a tile: a top set or any reps. */
internal fun CoachLiftBests.hasData(): Boolean = topSetWeight != null || totalReps > 0

/**
 * The set the recommendation panel speaks for (STR-328): the first
 * incomplete set carrying a recommendation. A completed set's
 * recommendation is spent; when every set is done there is nothing to
 * explain.
 */
internal fun coachRecommendationSet(lift: RenderedLiftUi): RenderedSetUi? =
    lift.sets.firstOrNull { set -> !set.isComplete && set.recommendedWeightLabel.isNotBlank() }

@Composable
private fun CoachPaneBody(
    state: WorkoutUiState.Loaded,
    primaryLiftId: String?,
) {
    val lift =
        state.lifts.firstOrNull { lift -> lift.workoutLiftId == primaryLiftId }
            ?: state.lifts.firstOrNull()
            ?: return
    val firstSet = lift.sets.firstOrNull()
    val previousSets =
        lift.sets.filter { set -> set.previousWeightAmount != null || set.previousReps != null }
    val historyBests = coachLiftBests(lift.sets)
    val todayBests = coachTodayBests(lift.sets)
    // History leads when it exists; today's completed sets fill the pane
    // when the lift has none (STR-328).
    val bestsFromToday = previousSets.isEmpty() && todayBests.hasData()
    val bests = if (previousSets.isNotEmpty()) historyBests else todayBests
    val unit = firstSet?.unitLabel ?: "lb"
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s16),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(StratumSpacing.s4)) {
            StratumText(
                text = lift.liftName,
                style = StratumThemeAccessor.typography.titleLarge,
            )
            firstSet?.let { set ->
                StratumText(
                    text =
                        stringResource(
                            R.string.workout_coach_target,
                            set.repMin,
                            set.repMax,
                            set.rirGoal,
                        ),
                    muted = true,
                )
            }
        }
        if (previousSets.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(StratumSpacing.s8)) {
                StratumText(
                    text = stringResource(R.string.workout_coach_last_session),
                    style = StratumThemeAccessor.typography.titleSmall,
                )
                for (set in previousSets) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        StratumText(
                            text = stringResource(R.string.workout_coach_set_label, set.positionLabel),
                            muted = true,
                            modifier = Modifier.weight(1f),
                        )
                        StratumText(text = set.previousWeightLabel.orEmpty())
                        set.previousRirLabel?.let { rir ->
                            StratumText(text = " · $rir", muted = true)
                        }
                    }
                }
            }
            CoachBestsTiles(bests = bests, unit = unit)
        }
        if (bestsFromToday) {
            Column(verticalArrangement = Arrangement.spacedBy(StratumSpacing.s8)) {
                StratumText(
                    text = stringResource(R.string.workout_coach_today),
                    style = StratumThemeAccessor.typography.titleSmall,
                )
                CoachBestsTiles(bests = bests, unit = unit)
            }
        }
        coachRecommendationSet(lift)?.let { set ->
            CoachRecommendationPanel(
                headingSuffix = recommendationHeadingSuffix(set, bests.topSetWeight, unit),
                body =
                    when {
                        bestsFromToday && bests.topSetWeight != null ->
                            stringResource(
                                R.string.workout_coach_recommendation_body_today,
                                "${formatWeightAmount(bests.topSetWeight)} $unit",
                            )
                        !bestsFromToday && bests.topSetWeight != null ->
                            stringResource(
                                R.string.workout_coach_recommendation_body_history,
                                "${formatWeightAmount(bests.topSetWeight)} $unit",
                            )
                        else -> stringResource(R.string.workout_coach_recommendation_body)
                    },
            )
        }
    }
}

@Composable
private fun CoachBestsTiles(
    bests: CoachLiftBests,
    unit: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s8)) {
        PaneStatTile(
            label = stringResource(R.string.workout_coach_top_set),
            value = bests.topSetWeight?.let { weight -> "${formatWeightAmount(weight)} $unit" } ?: "—",
            modifier = Modifier.weight(1f),
        )
        PaneStatTile(
            label = stringResource(R.string.workout_coach_total_reps),
            value = bests.totalReps.toString(),
            modifier = Modifier.weight(1f),
        )
        PaneStatTile(
            label = stringResource(R.string.workout_coach_est_1rm),
            value = bests.estimatedOneRepMax?.let { weight -> "${formatWeightAmount(weight)} $unit" } ?: "—",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PaneStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    sublabel: String? = null,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    Column(
        modifier =
            modifier
                .background(colors.surfaceRaised, shapes.panel)
                .padding(StratumSpacing.s12),
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s4),
    ) {
        StratumText(text = value, style = StratumThemeAccessor.typography.titleMedium)
        StratumText(text = label, muted = true, style = StratumThemeAccessor.typography.labelMedium)
        sublabel?.let { sub ->
            StratumText(text = sub, muted = true, style = StratumThemeAccessor.typography.labelMedium)
        }
    }
}

/**
 * The recommendation heading's suffix: the signed change against last
 * session's top set when there is one, otherwise the recommended weight.
 * A zero change falls back to the weight.
 */
internal fun recommendationHeadingSuffix(
    firstSet: RenderedSetUi,
    topSetWeight: Double?,
    unit: String,
): String {
    val delta =
        if (topSetWeight != null && firstSet.recommendedWeightAmount > 0.0) {
            firstSet.recommendedWeightAmount - topSetWeight
        } else {
            null
        }
    if (delta == null || delta == 0.0) return firstSet.recommendedWeightLabel
    val formatted = formatWeightAmount(kotlin.math.abs(delta))
    // A delta under display precision rounds to zero; the weight reads better.
    if (formatted.isEmpty() || formatted == "0") return firstSet.recommendedWeightLabel
    val sign = if (delta > 0) "+" else "-"
    return "$sign$formatted $unit"
}

@Composable
private fun CoachRecommendationPanel(
    headingSuffix: String,
    body: String,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.surfaceRaised, shapes.panel)
                .padding(StratumSpacing.s16),
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
    ) {
        StratumText(
            text = stringResource(R.string.workout_coach_recommendation, headingSuffix),
            style = StratumThemeAccessor.typography.titleSmall,
        )
        StratumText(text = body)
        StratumText(
            text = stringResource(R.string.workout_coach_recommendation_source),
            muted = true,
            style = StratumThemeAccessor.typography.labelMedium,
        )
    }
}

/** Average seconds of work per set in the remaining-time estimate. Rests come from the sets. */
internal const val ESTIMATED_SET_SECONDS = 40

/**
 * Estimated seconds left in the workout: every incomplete set's work plus the
 * configured rest after each set except the final one.
 */
internal fun estimateRemainingSeconds(lifts: List<RenderedLiftUi>): Int {
    val incomplete = lifts.flatMap { lift -> lift.sets }.filter { set -> !set.isComplete }
    if (incomplete.isEmpty()) return 0
    val rests = incomplete.dropLast(1).sumOf { set -> set.restSeconds }
    return rests + incomplete.size * ESTIMATED_SET_SECONDS
}

/** Volume tile text. formatWeightAmount blanks zero by design, so zero is spelled out. */
internal fun volumeLabel(
    volume: Double,
    unit: String,
): String = "${if (volume == 0.0) "0" else formatWeightAmount(volume)} $unit"

/** Total logged volume: sum of weight magnitude times reps over completed sets. */
internal fun sessionVolume(lifts: List<RenderedLiftUi>): Double =
    lifts.sumOf { lift ->
        lift.sets.sumOf { set ->
            if (set.isComplete) (set.loggedWeightAmount ?: 0.0) * (set.loggedReps ?: 0) else 0.0
        }
    }

/**
 * The next incomplete sets at or after the primary lift, in workout order.
 * Falls back to the first incomplete sets anywhere when the primary lift and
 * everything after it are done.
 */
internal fun upNextSets(
    lifts: List<RenderedLiftUi>,
    primaryLiftId: String?,
    count: Int = 2,
): List<Pair<RenderedLiftUi, RenderedSetUi>> {
    val startIndex =
        lifts.indexOfFirst { lift -> lift.workoutLiftId == primaryLiftId }
            .takeIf { index -> index >= 0 } ?: 0
    val forward =
        lifts.drop(startIndex).flatMap { lift ->
            lift.sets.filter { set -> !set.isComplete }.map { set -> lift to set }
        }
    if (forward.isNotEmpty()) return forward.take(count)
    return lifts
        .flatMap { lift -> lift.sets.filter { set -> !set.isComplete }.map { set -> lift to set } }
        .take(count)
}

@Composable
private fun OverviewPaneBody(state: WorkoutUiState.Loaded) {
    val totalSets = state.lifts.sumOf { lift -> lift.sets.size }
    val loggedSets = state.lifts.sumOf { lift -> lift.sets.count { set -> set.isComplete } }
    val remainingSets = totalSets - loggedSets
    val unit = state.lifts.firstOrNull()?.sets?.firstOrNull()?.unitLabel ?: "lb"
    val elapsed = rememberElapsedMillis(state.sessionStartedAtEpochMilli)
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s16),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(StratumSpacing.s8)) {
            Row(horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s8)) {
                PaneStatTile(
                    label = stringResource(R.string.workout_coach_elapsed),
                    value = formatWorkoutDuration(elapsed),
                    modifier = Modifier.weight(1f),
                )
                PaneStatTile(
                    label = stringResource(R.string.workout_coach_sets_label),
                    value = "$loggedSets/$totalSets",
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s8)) {
                PaneStatTile(
                    label = stringResource(R.string.workout_coach_volume),
                    value = volumeLabel(sessionVolume(state.lifts), unit),
                    modifier = Modifier.weight(1f),
                )
                PaneStatTile(
                    label = stringResource(R.string.workout_coach_est_remaining),
                    value = "≈ ${formatWorkoutDuration(estimateRemainingSeconds(state.lifts) * 1000L)}",
                    sublabel = stringResource(R.string.workout_coach_sets_left, remainingSets),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(StratumSpacing.s8)) {
            for (lift in state.lifts) {
                val done = lift.sets.count { set -> set.isComplete }
                Row(modifier = Modifier.fillMaxWidth()) {
                    StratumText(text = lift.liftName, modifier = Modifier.weight(1f))
                    StratumText(text = "$done/${lift.sets.size}", muted = true)
                }
            }
        }
    }
}

@Composable
private fun rememberElapsedMillis(sessionStartedAtEpochMilli: Long): Long {
    var elapsed by remember(sessionStartedAtEpochMilli) { mutableStateOf(0L) }
    LaunchedEffect(sessionStartedAtEpochMilli) {
        while (true) {
            elapsed = (System.currentTimeMillis() - sessionStartedAtEpochMilli).coerceAtLeast(0)
            delay(1000)
        }
    }
    return elapsed
}

/** The up-next card's detail parts, from the numeric recommendation. */
internal data class UpNextDetail(
    val weightLabel: String?,
    val repsLabel: String,
    val rirGoal: Int,
)

/**
 * Reps read the mapper's labels under its canonical gating: the concrete
 * count only when [RenderedSetUi.showConcreteReps] says the recommendation
 * is concrete, otherwise the configured range label. A cold-start snapshot
 * can carry a baseline rep count that is not a concrete recommendation.
 */
internal fun upNextDetail(set: RenderedSetUi): UpNextDetail {
    val reps =
        when {
            set.showConcreteReps -> set.recommendedRepsLabel
            !set.repRangeLabel.isNullOrBlank() -> set.repRangeLabel
            else -> set.recommendedRepsLabel
        }
    return UpNextDetail(
        weightLabel = set.recommendedWeightLabel.takeIf { label -> label.isNotBlank() },
        repsLabel = reps,
        rirGoal = set.rirGoal,
    )
}

@Composable
private fun upNextDetailText(set: RenderedSetUi): String {
    val detail = upNextDetail(set)
    return when {
        detail.weightLabel != null && detail.repsLabel.isNotEmpty() ->
            stringResource(
                R.string.workout_coach_set_detail,
                detail.weightLabel,
                detail.repsLabel,
                detail.rirGoal,
            )
        detail.weightLabel != null ->
            stringResource(R.string.workout_coach_set_detail_weight_only, detail.weightLabel, detail.rirGoal)
        detail.repsLabel.isNotEmpty() ->
            stringResource(R.string.workout_coach_set_detail_bodyweight, detail.repsLabel, detail.rirGoal)
        else -> stringResource(R.string.workout_coach_set_detail_rir_only, detail.rirGoal)
    }
}

@Composable
private fun RestPaneBody(
    state: WorkoutUiState.Loaded,
    primaryLiftId: String?,
    restTimer: RestTimerChrome,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val upNext = upNextSets(state.lifts, primaryLiftId)
    Column(verticalArrangement = Arrangement.spacedBy(StratumSpacing.s16)) {
        if (restTimer.isActive) {
            StratumText(
                text = formatRestDisplay(restTimer.remainingSeconds),
                style = StratumThemeAccessor.typography.displayLarge,
            )
            upNext.firstOrNull()?.let { (lift, set) ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceRaised, shapes.panel)
                            .padding(StratumSpacing.s16),
                    verticalArrangement = Arrangement.spacedBy(StratumSpacing.s4),
                ) {
                    StratumText(
                        text = stringResource(R.string.workout_coach_up_next),
                        muted = true,
                        style = StratumThemeAccessor.typography.labelMedium,
                    )
                    StratumText(
                        text = stringResource(R.string.workout_coach_set_line, set.positionLabel, lift.liftName),
                        style = StratumThemeAccessor.typography.titleMedium,
                    )
                    StratumText(
                        text = upNextDetailText(set),
                        muted = true,
                    )
                }
            }
            upNext.getOrNull(1)?.let { (lift, set) ->
                StratumText(
                    text =
                        stringResource(
                            R.string.workout_coach_after_that,
                            set.positionLabel,
                            lift.liftName,
                        ),
                    muted = true,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s8)) {
                StratumButton(
                    text = stringResource(R.string.workout_coach_rest_minus),
                    onClick = restTimer.onSubtract10s,
                )
                StratumButton(
                    text = stringResource(R.string.workout_coach_rest_plus),
                    onClick = restTimer.onAdd10s,
                )
                StratumButton(
                    text = stringResource(R.string.workout_coach_rest_skip),
                    onClick = restTimer.onSkip,
                )
            }
        } else {
            StratumText(
                text = stringResource(R.string.workout_coach_rest_idle),
                muted = true,
            )
        }
    }
}
