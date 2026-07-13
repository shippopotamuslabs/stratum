package com.stratum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.core.ui.component.TrendPointUi
import com.stratum.domain.dashboard.CalloutDirection
import com.stratum.domain.dashboard.CalloutKind
import com.stratum.domain.dashboard.DashboardSummary
import com.stratum.domain.dashboard.GraphDetailSubject
import com.stratum.domain.dashboard.LIFT_E1RM_SLOPE_THRESHOLD
import com.stratum.domain.dashboard.LiftSeries
import com.stratum.domain.dashboard.LoadDashboardUseCase
import com.stratum.domain.dashboard.LoadGraphDetailUseCase
import com.stratum.domain.dashboard.MUSCLE_PROGRESS_SLOPE_THRESHOLD
import com.stratum.domain.dashboard.MuscleDailyVolumeSeries
import com.stratum.domain.dashboard.MuscleSeries
import com.stratum.domain.dashboard.TrendPoint
import com.stratum.domain.dashboard.olsSlope
import com.stratum.domain.dashboard.slopeToDirection
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.sync.ObserveCloudSyncStatusUseCase
import com.stratum.domain.sync.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val loadDashboard: LoadDashboardUseCase,
    private val loadGraphDetail: LoadGraphDetailUseCase,
    observeCloudSyncStatus: ObserveCloudSyncStatusUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _graphDetailState = MutableStateFlow<GraphDetailUiState>(GraphDetailUiState.Hidden)
    val graphDetailState: StateFlow<GraphDetailUiState> = _graphDetailState.asStateFlow()

    private var loadJob: Job? = null
    private var detailJob: Job? = null
    private var detailSession: GraphDetailSession? = null

    init {
        load()
        // Weekly series tables are rebuilt after a successful cloud pull (not synced themselves).
        // Reload when sync returns to Idle so a first-hydrate or multi-device pull fills charts
        // without requiring the user to leave and re-enter the tab.
        viewModelScope.launch {
            var sawBusy = false
            observeCloudSyncStatus().collect { status ->
                    when (status) {
                        SyncStatus.Syncing, SyncStatus.Hydrating -> sawBusy = true
                        SyncStatus.Idle -> {
                            if (sawBusy) {
                                sawBusy = false
                                load()
                            }
                        }
                        is SyncStatus.Error -> {
                            // Stay put; residual retry may still succeed and emit Idle later.
                            sawBusy = true
                        }
                    }
                }
        }
    }

    /**
     * Reloads the dashboard. Called on init, after cloud sync settles to Idle (post-pull weekly
     * rebuild), and on lifecycle resume so a workout completed while the dashboard was in the
     * background shows up. Keeps the last [DashboardUiState.Loaded] visible while refreshing so
     * tab switches do not flash the empty copy.
     */
    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (_uiState.value !is DashboardUiState.Loaded) {
                _uiState.value = DashboardUiState.Loading
            }
            val summary = loadDashboard()
            _uiState.value = summary?.toUiState() ?: DashboardUiState.Empty
        }
    }

    fun openLiftGraph(liftId: String) {
        openGraphDetail(GraphDetailSubject.Lift(LiftId(liftId)))
    }

    fun openMuscleGraph(muscleKey: String) {
        val muscle = runCatching { MuscleGroup.valueOf(muscleKey) }.getOrNull() ?: return
        openGraphDetail(GraphDetailSubject.Muscle(muscle))
    }

    fun openMuscleDailyVolumeGraph(muscleKey: String) {
        val muscle = runCatching { MuscleGroup.valueOf(muscleKey) }.getOrNull() ?: return
        openGraphDetail(GraphDetailSubject.MuscleDailyVolume(muscle))
    }

    fun dismissGraphDetail() {
        detailJob?.cancel()
        detailSession = null
        _graphDetailState.value = GraphDetailUiState.Hidden
    }

    fun selectGraphMode(mode: GraphViewMode) {
        val session = detailSession ?: return
        if (mode !in modesFor(session.kind)) return
        detailSession = session.copy(selectedMode = mode)
        publishReady()
    }

    fun selectGraphTimeRange(range: GraphTimeRangeUi) {
        val session = detailSession ?: return
        if (range !in rangesFor(session.kind)) return
        detailSession = session.copy(selectedRange = range)
        publishReady()
    }

    private fun openGraphDetail(subject: GraphDetailSubject) {
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            _graphDetailState.value = GraphDetailUiState.Loading
            val series = loadGraphDetail(subject)
            if (series == null) {
                detailSession = null
                _graphDetailState.value = GraphDetailUiState.Empty
                return@launch
            }
            detailSession = series.toSession()
            publishReady()
        }
    }

    private fun publishReady() {
        val session = detailSession ?: return
        _graphDetailState.value = session.toReadyUi()
    }
}

/** Presentation-only dashboard state — no domain types leak into composables (Detekt-enforced). */
sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data object Empty : DashboardUiState
    data class Loaded(
        val windowWeeks: Int,
        val muscles: List<MuscleSeriesUi>,
        val liftSections: List<LiftSectionUi>,
        /** 7-Day tab: per-muscle daily hard-set volume (zero-filled last 7 UTC days). */
        val runningDailyVolume: List<MuscleDailyVolumeUi> = emptyList(),
    ) : DashboardUiState
}

/**
 * High-confidence trend callout applied to a card border (stronger gate than [TrendBadge]).
 * Null = no strong signal; keep the subtle default border.
 */
enum class CalloutDirectionUi { Progressing, Regressing }

data class MuscleSeriesUi(
    val name: String,
    /** Stable [MuscleGroup.name] for opening graph detail. */
    val muscleKey: String,
    val headlineVolume: String,
    val badge: TrendBadge,
    val progressPoints: List<TrendPointUi>,
    val volumePoints: List<TrendPointUi>,
    val contributingLifts: Int,
    val highlight: CalloutDirectionUi? = null,
)

/** Presentation model for one muscle on the 7-Day volume tab. */
data class MuscleDailyVolumeUi(
    val name: String,
    /** Stable [MuscleGroup.name] for opening daily volume graph detail. */
    val muscleKey: String,
    /** e.g. "12 sets" — total hard sets over the 7-day window. */
    val headlineSets: String,
    /** Seven points (oldest → newest); [TrendPointUi.weekIndex] is day offset 0..6. */
    val volumePoints: List<TrendPointUi>,
    /**
     * Short weekday labels aligned with [volumePoints] (rolling lookback, e.g. `M`…`Su`).
     * Built from each day start in UTC to match volume bucketing.
     */
    val dayLabels: List<String>,
    val contributingLifts: Int,
)

data class LiftSectionUi(
    val title: String,
    val lifts: List<LiftSeriesUi>,
)

data class LiftSeriesUi(
    val name: String,
    /** Stable lift id for opening graph detail. */
    val liftId: String,
    val e1rmText: String?,
    val badge: TrendBadge,
    val performancePoints: List<TrendPointUi>,
    val volumePoints: List<TrendPointUi>,
    val sessions: Int,
    val highlight: CalloutDirectionUi? = null,
)

/** Coarse direction used to pick a color + arrow glyph in the UI. */
enum class TrendBadge { Up, Flat, Down, Unknown }

private fun DashboardSummary.toUiState(): DashboardUiState.Loaded {
    val muscleHighlights = callouts
        .filter { it.kind == CalloutKind.Muscle }
        .associate { it.subjectName to it.direction.toUi() }
    val liftHighlights = callouts
        .filter { it.kind == CalloutKind.Lift }
        .associate { it.subjectName to it.direction.toUi() }
    return DashboardUiState.Loaded(
        windowWeeks = windowWeeks,
        muscles = muscles.map { it.toUi(highlight = muscleHighlights[it.displayName]) },
        liftSections = lifts.toLiftSections(highlights = liftHighlights),
        runningDailyVolume = runningDailyVolume.map { it.toUi() },
    )
}

private fun MuscleDailyVolumeSeries.toUi(): MuscleDailyVolumeUi =
    MuscleDailyVolumeUi(
        name = displayName,
        muscleKey = muscle.name,
        headlineSets = formatHardSetHeadline(totalHardSets),
        volumePoints = dailyVolume.mapIndexed { index, point ->
            TrendPointUi(weekIndex = index, value = point.value.toFloat())
        },
        dayLabels = dailyVolume.map { shortWeekdayLabel(it.weekStart) },
        contributingLifts = contributingLiftCount,
    )

/**
 * Displays hard-set totals with half-set precision when needed (secondary credit = 0.5).
 * Examples: `1 set`, `12 sets`, `0.5 sets`, `12.5 sets`.
 */
internal fun formatHardSetHeadline(hardSets: Double): String {
    val amount = formatHardSetAmount(hardSets)
    val unit = if (kotlin.math.abs(hardSets - 1.0) < HARD_SET_EPS) "set" else "sets"
    return "$amount $unit"
}

internal fun formatHardSetAmount(hardSets: Double): String {
    val nearestInt = hardSets.toLong().toDouble()
    return if (kotlin.math.abs(hardSets - nearestInt) < HARD_SET_EPS) {
        nearestInt.toLong().toString()
    } else {
        // One decimal is enough for primary 1.0 / secondary 0.5 sums.
        val raw = String.format(java.util.Locale.US, "%.1f", hardSets)
        raw.trimEnd('0').trimEnd('.')
    }
}

private const val HARD_SET_EPS = 1e-9

/**
 * Compact weekday for mini-card X-axis. Thursday = R (avoids clashing with Tuesday T);
 * weekend = Sa / Su. [dayStart] is UTC midnight of the bucket day.
 */
internal fun shortWeekdayLabel(dayStart: Instant): String {
    val day = dayStart.atZone(ZoneOffset.UTC).dayOfWeek
    return when (day) {
        DayOfWeek.MONDAY -> "M"
        DayOfWeek.TUESDAY -> "T"
        DayOfWeek.WEDNESDAY -> "W"
        DayOfWeek.THURSDAY -> "R"
        DayOfWeek.FRIDAY -> "F"
        DayOfWeek.SATURDAY -> "Sa"
        DayOfWeek.SUNDAY -> "Su"
    }
}

private fun CalloutDirection.toUi(): CalloutDirectionUi = when (this) {
    CalloutDirection.Progressing -> CalloutDirectionUi.Progressing
    CalloutDirection.Regressing -> CalloutDirectionUi.Regressing
}

/**
 * Domain already returns Upper-then-Lower order; group consecutive same-region lifts into
 * sectioned presentation. Empty sections are omitted.
 */
private fun List<LiftSeries>.toLiftSections(
    highlights: Map<String, CalloutDirectionUi>,
): List<LiftSectionUi> {
    if (isEmpty()) return emptyList()
    val sections = mutableListOf<LiftSectionUi>()
    var currentIsLower: Boolean? = null
    val currentLifts = mutableListOf<LiftSeriesUi>()

    fun flush() {
        val isLower = currentIsLower ?: return
        if (currentLifts.isEmpty()) return
        sections += LiftSectionUi(
            title = if (isLower) SECTION_LOWER else SECTION_UPPER,
            lifts = currentLifts.toList(),
        )
        currentLifts.clear()
    }

    for (lift in this) {
        if (currentIsLower != null && lift.isLower != currentIsLower) {
            flush()
        }
        currentIsLower = lift.isLower
        currentLifts += lift.toUi(highlight = highlights[lift.name])
    }
    flush()
    return sections
}

private fun MuscleSeries.toUi(highlight: CalloutDirectionUi?): MuscleSeriesUi {
    val progressSlope = progressSeries?.let { olsSlope(it) }
    // Log-deviation index scale — not kg/week (see MUSCLE_PROGRESS_SLOPE_THRESHOLD).
    val progressDirection = slopeToDirection(progressSlope, MUSCLE_PROGRESS_SLOPE_THRESHOLD)
    // Align progress + volume on a shared week axis so index bars and the volume line share x.
    val (progressPoints, volumePoints) = alignMuscleSeries(progressSeries, volumeSeries)
    return MuscleSeriesUi(
        name = displayName,
        muscleKey = muscle.name,
        headlineVolume = "${meanWeeklyHardSets.toInt()} sets/wk",
        badge = progressDirection.toBadge(),
        progressPoints = progressPoints,
        volumePoints = volumePoints,
        contributingLifts = contributingLiftCount,
        highlight = highlight,
    )
}

/**
 * Maps progress (optional) and volume onto a shared [TrendPointUi.weekIndex] space ordered by
 * calendar [TrendPoint.weekStart]. Missing progress weeks are omitted from the index series only —
 * volume still plots every week with stimulus data.
 */
internal fun alignMuscleSeries(
    progressSeries: List<TrendPoint>?,
    volumeSeries: List<TrendPoint>,
): Pair<List<TrendPointUi>, List<TrendPointUi>> {
    val weekStarts = buildList {
        progressSeries?.forEach { add(it.weekStart) }
        volumeSeries.forEach { add(it.weekStart) }
    }.distinct().sorted()
    if (weekStarts.isEmpty()) return emptyList<TrendPointUi>() to emptyList()
    val indexByWeek = weekStarts.withIndex().associate { (i, week) -> week to i }
    val progressPoints = progressSeries.orEmpty().mapNotNull { point ->
        val weekIndex = indexByWeek[point.weekStart] ?: return@mapNotNull null
        TrendPointUi(weekIndex = weekIndex, value = point.value.toFloat())
    }
    val volumePoints = volumeSeries.mapNotNull { point ->
        val weekIndex = indexByWeek[point.weekStart] ?: return@mapNotNull null
        TrendPointUi(weekIndex = weekIndex, value = point.value.toFloat())
    }
    return progressPoints to volumePoints
}

private fun LiftSeries.toUi(highlight: CalloutDirectionUi?): LiftSeriesUi {
    val slope = olsSlope(performanceSeries)
    val direction = slopeToDirection(slope, LIFT_E1RM_SLOPE_THRESHOLD)
    return LiftSeriesUi(
        name = name,
        liftId = liftId.value,
        e1rmText = latestE1rm?.let { "${it.amount.toInt()} ${it.unit.shortLabel()}" },
        badge = direction.toBadge(),
        performancePoints = performanceSeries.toPoints(),
        volumePoints = volumeSeries.toPoints(),
        sessions = sessionCount,
        highlight = highlight,
    )
}

/**
 * Maps a domain series to UI points. Values are passed through as Floats; the chart normalizes
 * jointly when two series coexist (progress + volume), or solo for a single series. The weekIndex
 * is the positional index in the sorted series for the tap-to-inspect hook.
 */
private fun List<TrendPoint>?.toPoints(): List<TrendPointUi> =
    this?.sortedBy { it.weekStart }?.mapIndexed { index, point ->
        TrendPointUi(weekIndex = index, value = point.value.toFloat())
    } ?: emptyList()

private fun com.stratum.domain.dashboard.SeriesDirection.toBadge(): TrendBadge = when (this) {
    com.stratum.domain.dashboard.SeriesDirection.Up -> TrendBadge.Up
    com.stratum.domain.dashboard.SeriesDirection.Flat -> TrendBadge.Flat
    com.stratum.domain.dashboard.SeriesDirection.Down -> TrendBadge.Down
    com.stratum.domain.dashboard.SeriesDirection.Insufficient -> TrendBadge.Unknown
}

private fun Weight.WeightUnit.shortLabel(): String = when (this) {
    Weight.WeightUnit.Lb -> "lb"
    Weight.WeightUnit.Kg -> "kg"
}

/** Presentation keys for section headers — mapped to string resources in the UI layer. */
internal const val SECTION_UPPER = "Upper"
internal const val SECTION_LOWER = "Lower"
