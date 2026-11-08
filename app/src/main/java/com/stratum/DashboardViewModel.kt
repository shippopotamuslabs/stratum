package com.stratum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.core.ui.component.TrendPointUi
import com.stratum.app.DiagnosticDrain
import com.stratum.application.dashboard.DashboardPreferenceResult
import com.stratum.application.dashboard.LoadDashboardResult
import com.stratum.application.dashboard.LoadGraphDetailResult
import com.stratum.application.dashboard.LoadGraphDetailUseCase
import com.stratum.application.dashboard.ObserveDashboardStateUseCase
import com.stratum.application.dashboard.SetDashboardBoardTabUseCase
import com.stratum.application.healthconnect.HEALTH_CONNECT_METRICS_TAG
import com.stratum.application.sync.CloudSyncOperation
import com.stratum.application.sync.CloudSyncOutcome
import com.stratum.application.sync.RequestCloudSyncUseCase
import com.stratum.application.sync.isNotStarted
import com.stratum.domain.dashboard.CalloutDirection
import com.stratum.domain.dashboard.CalloutKind
import com.stratum.application.dashboard.DashboardBoardState
import com.stratum.domain.dashboard.DashboardSummary
import com.stratum.domain.dashboard.GraphDetailSeries
import com.stratum.domain.dashboard.GraphDetailSubject
import com.stratum.domain.dashboard.LIFT_E1RM_SLOPE_THRESHOLD
import com.stratum.domain.dashboard.LiftSeries
import com.stratum.domain.dashboard.MUSCLE_PROGRESS_SLOPE_THRESHOLD
import com.stratum.domain.dashboard.MuscleDailyVolumeSeries
import com.stratum.domain.dashboard.MuscleSeries
import com.stratum.domain.dashboard.TrendPoint
import com.stratum.domain.dashboard.olsSlope
import com.stratum.domain.dashboard.slopeToDirection
import com.stratum.domain.health.HealthMetricsPages
import com.stratum.domain.healthconnect.HealthConnectDiagnostic
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.WeightConversion
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.shortLabel
import com.stratum.domain.onboarding.MuscleGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch

@HiltViewModel
class DashboardViewModel(
    observeDashboardState: ObserveDashboardStateUseCase,
    private val loadGraphDetail: LoadGraphDetailUseCase,
    private val setDashboardBoardTab: SetDashboardBoardTabUseCase,
    private val requestCloudSync: RequestCloudSyncUseCase,
    private val diagnosticDrain: DiagnosticDrain,
    /**
     * Device calendar zone for 7-Day buckets + labels. Captured once so load and presentation
     * cannot drift. Unit tests pass a fixed zone via the primary constructor.
     */
    private val zone: ZoneId,
    /** Wall clock for board/detail "as of"; tests pin a fixed Instant. */
    private val clock: () -> Instant = Instant::now,
) : ViewModel() {
    /**
     * Hilt entry: device-local [ZoneId.systemDefault]. Tests use the primary constructor with a
     * pinned zone (same pattern as [com.stratum.feature.settings.ExportLogsViewModel]).
     */
    @Inject
    constructor(
        observeDashboardState: ObserveDashboardStateUseCase,
        loadGraphDetail: LoadGraphDetailUseCase,
        setDashboardBoardTab: SetDashboardBoardTabUseCase,
        requestCloudSync: RequestCloudSyncUseCase,
        diagnosticDrain: DiagnosticDrain,
    ) : this(
        observeDashboardState,
        loadGraphDetail,
        setDashboardBoardTab,
        requestCloudSync,
        diagnosticDrain,
        ZoneId.systemDefault(),
        Instant::now,
    )

    /** Reload trigger for the board pipeline; buffered so resume/load never suspend. */
    private val reloads = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val _isRefreshing = MutableStateFlow(false)

    private val detailStatus = MutableStateFlow<GraphDetailUiState>(GraphDetailUiState.Hidden)

    /**
     * Presentation-only graph-detail cache. Mode/range chips re-derive without reloading and
     * without storing domain series — the domain [GraphDetailSession] lives only transiently
     * inside the load coroutine that builds these variants.
     */
    private val detailStore = MutableStateFlow<GraphDetailStore?>(null)

    private var detailJob: Job? = null

    /**
     * Last requested detail key, kept across load failures so [retryGraphDetail] can re-invoke
     * the load after an [GraphDetailUiState.Error] (the presentation [detailStore] only lands
     * on success). Cleared on [dismissGraphDetail].
     */
    private var pendingDetailKey: GraphDetailRequestKey? = null

    /**
     * Board pipeline mapped to presentation. The pipeline starts eagerly (the init load) and
     * reloads on every [load]. The last Loaded board stays visible while reloading: only fresh
     * mapped states are emitted, never a synthetic Loading.
     *
     * Diagnostics drain with log-once semantics: the interactor re-emits the last loaded board
     * on every prefs change, so the drain fires only when the health payload itself changes (the
     * dedup lives inside this operator, not in a field). `SnapshotReadFailed` logs nothing in
     * the drain (see [logHealthDiagnostics]).
     */
    private val boardParts: StateFlow<BoardParts> = run {
        var lastDrainedPages: HealthMetricsPages? = null
        var lastDrainedBoard: LoadDashboardResult? = null
        observeDashboardState(reloads, zone, clock)
            .withIndex()
            .transform { (generation, state) ->
                val pages = state.healthPages
                if (pages !== lastDrainedPages) {
                    lastDrainedPages = pages
                    logHealthDiagnostics(pages.diagnostics)
                }
                val board = state.board
                if (board !== lastDrainedBoard) {
                    lastDrainedBoard = board
                    if (board is LoadDashboardResult.Failed) {
                        diagnosticDrain.drain(board.diagnostics)
                    }
                }
                emit(state.toParts(generation))
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, BoardParts())
    }

    private val detailView: Flow<GraphDetailUiState> =
        combine(detailStatus, detailStore, boardParts) { status, store, parts ->
            store?.ready(parts.unitIsLb) ?: status
        }

    /**
     * The single public dashboard state (STR-208 stage 2). Board composition (board + health +
     * tab + unit) is owned by [ObserveDashboardStateUseCase] — including reloads on explicit
     * [load], on sync busy → Idle, and on the health-enable rising edge. Transient presentation
     * holders (refreshing, graph detail) are combined here. No domain model is cached in
     * ViewModel fields: the interactor flow is mapped to presentation before [stateIn], and the
     * graph-detail cache holds only precomputed presentation [GraphDetailUiState.Ready] variants.
     */
    val uiState: StateFlow<DashboardUiState> =
        combine(boardParts, _isRefreshing, detailView) { parts, refreshing, detail ->
            DashboardUiState(
                board = parts.board,
                health = parts.health,
                graphDetail = detail,
                selectedTab = parts.tab,
                isRefreshing = refreshing,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, DashboardUiState())

    /**
     * Reloads the dashboard. Called on init (via the eagerly-started pipeline), after cloud
     * sync settles to Idle (post-pull weekly rebuild, owned by the interactor), and on
     * lifecycle resume so a workout completed while the dashboard was in the background shows
     * up. Keeps the last [DashboardBoardUiState.Loaded] visible while refreshing so tab
     * switches do not flash the empty copy.
     */
    fun load() {
        reloads.tryEmit(Unit)
    }

    /**
     * Pull-to-refresh: reloads the board and Health Connect pages without flashing the empty
     * loading chrome. No-ops while a pull is already in flight. A fresh pipeline emission —
     * board and health reload together per reload — ends the pull.
     */
    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        viewModelScope.launch {
            val outcome = requestCloudSync()
            if (outcome is CloudSyncOutcome.Failed) {
                val recordable = outcome.diagnostics.filterNot { it.isNotStarted }
                diagnosticDrain.drainSync(recordable, CloudSyncOperation.RequestSync)
            }
        }
        val startedGeneration = boardParts.value.generation
        load()
        viewModelScope.launch {
            try {
                boardParts.first { it.generation > startedGeneration }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun retry() = refresh()

    /**
     * Requests a board-tab change. UI updates only when prefs re-emit after a successful write
     * (single source of truth — no optimistic local patch that can snap back on intermediate
     * DataStore emissions).
     */
    fun selectTab(tab: DashboardBoardTabUi) {
        if (tab == boardParts.value.tab) return
        viewModelScope.launch {
            val result = setDashboardBoardTab(tab.toDomain())
            if (result is DashboardPreferenceResult.PersistenceFailed) {
                diagnosticDrain.drain(result.diagnostics)
            }
        }
    }

    /**
     * STR-257: single drain for metrics-load diagnostics. Diagnostics are log-only —
     * never surfaced in the UI. This drain is the single recorder for
     * `SnapshotReadFailed` (AQ1 ruling, 2026-11-06, amending STR-268): the cause
     * rides the diagnostic from the use case, which records nothing, and is recorded
     * exactly once here at the dashboard boundary.
     */
    internal fun logHealthDiagnostics(diagnostics: List<HealthConnectDiagnostic>) {
        for (diagnostic in diagnostics) {
            when (diagnostic) {
                is HealthConnectDiagnostic.SnapshotReadFailed ->
                    diagnosticDrain.recordError(
                        HEALTH_CONNECT_METRICS_TAG,
                        "Failed to read Health Connect snapshot — dashboard shows metrics unavailable",
                        diagnostic.cause,
                    )
            }
        }
    }

    fun openLiftGraph(liftId: String) {
        openGraphDetail(
            subject = GraphDetailSubject.Lift(LiftId(liftId)),
            key = GraphDetailRequestKey(kind = GraphDetailKindUi.Lift, id = liftId),
        )
    }

    fun openMuscleGraph(muscleKey: String) {
        val muscle = runCatching { MuscleGroup.valueOf(muscleKey) }.getOrNull() ?: return
        openGraphDetail(
            subject = GraphDetailSubject.Muscle(muscle),
            key = GraphDetailRequestKey(kind = GraphDetailKindUi.Muscle, id = muscleKey),
        )
    }

    fun openMuscleDailyVolumeGraph(muscleKey: String) {
        val muscle = runCatching { MuscleGroup.valueOf(muscleKey) }.getOrNull() ?: return
        openGraphDetail(
            subject = GraphDetailSubject.MuscleDailyVolume(muscle),
            key = GraphDetailRequestKey(kind = GraphDetailKindUi.MuscleDailyVolume, id = muscleKey),
        )
    }

    /**
     * Re-invokes the detail load from the presentation request key (kind + stable string id),
     * not from a cached domain subject. Uses the last requested key so retry works after a
     * failed load, when no Ready store exists yet.
     */
    fun retryGraphDetail() {
        val key = pendingDetailKey ?: return
        val subject = key.toSubject() ?: return
        openGraphDetail(subject, key)
    }

    fun dismissGraphDetail() {
        detailJob?.cancel()
        pendingDetailKey = null
        detailStore.value = null
        detailStatus.value = GraphDetailUiState.Hidden
    }

    /**
     * Mode/range chips re-derive the Ready variant without reloading and without touching
     * domain state — the selection just picks another precomputed presentation variant.
     */
    fun selectGraphMode(mode: GraphViewMode) {
        val store = detailStore.value ?: return
        if (mode !in modesFor(store.key.kind)) return
        if (mode == store.selectedMode) return
        detailStore.value = store.copy(selectedMode = mode)
    }

    fun selectGraphTimeRange(range: GraphTimeRangeUi) {
        val store = detailStore.value ?: return
        if (range !in rangesFor(store.key.kind)) return
        if (range == store.selectedRange) return
        detailStore.value = store.copy(selectedRange = range)
    }

    private fun openGraphDetail(subject: GraphDetailSubject, key: GraphDetailRequestKey) {
        detailJob?.cancel()
        pendingDetailKey = key
        detailJob = viewModelScope.launch {
            detailStore.value = null
            detailStatus.value = GraphDetailUiState.Loading
            when (val result = loadGraphDetail(subject, zone = zone)) {
                is LoadGraphDetailResult.Loaded -> {
                    detailStore.value = result.series.toStore(key = key, now = clock())
                }
                LoadGraphDetailResult.Empty -> {
                    detailStatus.value = GraphDetailUiState.Empty
                }
                is LoadGraphDetailResult.Failed -> {
                    diagnosticDrain.drain(result.diagnostics)
                    detailStatus.value = GraphDetailUiState.Error
                }
            }
        }
    }

    /**
     * Builds every presentation variant for the loaded subject up front. The domain session
     * lives only inside this call; the stored result is presentation-only. Both display units
     * are precomputed so a unit toggle re-derives without reloading.
     */
    private fun GraphDetailSeries.toStore(
        key: GraphDetailRequestKey,
        now: Instant,
    ): GraphDetailStore {
        val session = toSession(zone = zone)
        val variants = buildMap {
            for (mode in modesFor(session.kind)) {
                for (range in rangesFor(session.kind)) {
                    for (unitIsLb in listOf(true, false)) {
                        val unit = if (unitIsLb) Weight.WeightUnit.Lb else Weight.WeightUnit.Kg
                        val ready = session.copy(selectedMode = mode, selectedRange = range)
                            .toReadyUi(now = now, displayUnit = unit)
                        put(GraphDetailVariantKey(mode, range, unitIsLb), ready)
                    }
                }
            }
        }
        return GraphDetailStore(
            key = key,
            variants = variants,
            selectedMode = session.selectedMode,
            selectedRange = session.selectedRange,
        )
    }

    private fun GraphDetailRequestKey.toSubject(): GraphDetailSubject? = when (kind) {
        GraphDetailKindUi.Lift -> GraphDetailSubject.Lift(LiftId(id))
        GraphDetailKindUi.Muscle ->
            runCatching { MuscleGroup.valueOf(id) }.getOrNull()?.let { GraphDetailSubject.Muscle(it) }
        GraphDetailKindUi.MuscleDailyVolume ->
            runCatching { MuscleGroup.valueOf(id) }.getOrNull()
                ?.let { GraphDetailSubject.MuscleDailyVolume(it) }
    }

    private fun DashboardBoardState.toParts(generation: Int): BoardParts {
        val board = when (val result = board) {
            is LoadDashboardResult.Loaded -> result.summary.toBoardUi(displayUnit, zone)
            LoadDashboardResult.Empty -> DashboardBoardUiState.Empty
            is LoadDashboardResult.Failed -> DashboardBoardUiState.Error
        }
        return BoardParts(
            board = board,
            health = healthPages.toHealthUi(displayUnit, zone),
            tab = selectedTab.toUi(),
            unitIsLb = displayUnit == Weight.WeightUnit.Lb,
            generation = generation,
        )
    }

    private data class BoardParts(
        val board: DashboardBoardUiState = DashboardBoardUiState.Loading,
        val health: DashboardHealthUiState = DashboardHealthUiState(),
        val tab: DashboardBoardTabUi = DashboardBoardTabUi.Muscles,
        val unitIsLb: Boolean = true,
        val generation: Int = -1,
    )

    /**
     * Presentation-only graph-detail cache: the retry request key, every precomputed Ready
     * variant, and the current chip selection. No domain types.
     */
    private data class GraphDetailStore(
        val key: GraphDetailRequestKey,
        val variants: Map<GraphDetailVariantKey, GraphDetailUiState.Ready>,
        val selectedMode: GraphViewMode,
        val selectedRange: GraphTimeRangeUi,
    ) {
        fun ready(unitIsLb: Boolean): GraphDetailUiState =
            variants[GraphDetailVariantKey(selectedMode, selectedRange, unitIsLb)]
                ?: GraphDetailUiState.Error
    }

    /** Presentation request key: the detail kind plus the stable lift-id or muscle-key string. */
    private data class GraphDetailRequestKey(
        val kind: GraphDetailKindUi,
        val id: String,
    )

    private data class GraphDetailVariantKey(
        val mode: GraphViewMode,
        val range: GraphTimeRangeUi,
        val unitIsLb: Boolean,
    )
}

/** Single dashboard screen state — presentation types only (Detekt-enforced). */
data class DashboardUiState(
    val board: DashboardBoardUiState = DashboardBoardUiState.Loading,
    val health: DashboardHealthUiState = DashboardHealthUiState(),
    val graphDetail: GraphDetailUiState = GraphDetailUiState.Hidden,
    val selectedTab: DashboardBoardTabUi = DashboardBoardTabUi.Muscles,
    val isRefreshing: Boolean = false,
)

/**
 * Presentation-only dashboard board — no domain types leak into composables (Detekt-enforced).
 * The single screen state is [DashboardUiState]; this sealed type is its `board` field.
 */
sealed interface DashboardBoardUiState {
    data object Loading : DashboardBoardUiState
    data object Empty : DashboardBoardUiState
    data object Error : DashboardBoardUiState
    data class Loaded(
        val windowWeeks: Int,
        val muscles: List<MuscleSeriesUi>,
        val liftSections: List<LiftSectionUi>,
        /** 7-Day tab: per-muscle daily hard-set volume (zero-filled last 7 local days). */
        val runningDailyVolume: List<MuscleDailyVolumeUi> = emptyList(),
    ) : DashboardBoardUiState
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
     * Built from each day start in the device zone to match volume bucketing.
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

private fun DashboardSummary.toBoardUi(
    displayUnit: Weight.WeightUnit,
    zone: ZoneId,
): DashboardBoardUiState.Loaded {
    val muscleHighlights = callouts
        .filter { it.kind == CalloutKind.Muscle }
        .associate { it.subjectName to it.direction.toUi() }
    val liftHighlights = callouts
        .filter { it.kind == CalloutKind.Lift }
        .associate { it.subjectName to it.direction.toUi() }
    return DashboardBoardUiState.Loaded(
        windowWeeks = windowWeeks,
        muscles = muscles.map { it.toUi(highlight = muscleHighlights[it.displayName]) },
        liftSections = lifts.toLiftSections(
            highlights = liftHighlights,
            displayUnit = displayUnit,
        ),
        // Use the zone that bucketed the series — never re-resolve systemDefault here.
        runningDailyVolume = runningDailyVolume.map { it.toUi(zone = zone) },
    )
}

private fun MuscleDailyVolumeSeries.toUi(zone: ZoneId): MuscleDailyVolumeUi =
    MuscleDailyVolumeUi(
        name = displayName,
        muscleKey = muscle.name,
        headlineSets = formatHardSetHeadline(totalHardSets),
        volumePoints = dailyVolume.mapIndexed { index, point ->
            TrendPointUi(weekIndex = index, value = point.value.toFloat())
        },
        dayLabels = dailyVolume.map { shortWeekdayLabel(it.weekStart, zone) },
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
 * weekend = Sa / Su. [dayStart] is local midnight of the bucket day in [zone].
 */
internal fun shortWeekdayLabel(dayStart: Instant, zone: ZoneId): String {
    val day = dayStart.atZone(zone).dayOfWeek
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
    displayUnit: Weight.WeightUnit,
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
        currentLifts += lift.toUi(highlight = highlights[lift.name], displayUnit = displayUnit)
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

private fun LiftSeries.toUi(
    highlight: CalloutDirectionUi?,
    displayUnit: Weight.WeightUnit,
): LiftSeriesUi {
    // Slope stays on kg domain series (threshold is kg/week).
    val slope = olsSlope(performanceSeries)
    val direction = slopeToDirection(slope, LIFT_E1RM_SLOPE_THRESHOLD)
    val e1rmDisplay = latestE1rm?.let { weight ->
        // Analytics tag peak e1RM as kg; convert for the preferred display unit.
        val amount =
            WeightConversion.convertAmount(weight.amount, weight.unit, displayUnit)
        "${amount.toInt()} ${displayUnit.shortLabel()}"
    }
    return LiftSeriesUi(
        name = name,
        liftId = liftId.value,
        e1rmText = e1rmDisplay,
        badge = direction.toBadge(),
        performancePoints = performanceSeries.toPoints(
            scaleFromKg = true,
            displayUnit = displayUnit,
        ),
        volumePoints = volumeSeries.toPoints(scaleFromKg = false, displayUnit = displayUnit),
        sessions = sessionCount,
        highlight = highlight,
    )
}

/**
 * Maps a domain series to UI points. Values are passed through as Floats; the chart normalizes
 * jointly when two series coexist (progress + volume), or solo for a single series. The weekIndex
 * is the positional index in the sorted series for the tap-to-inspect hook.
 *
 * When [scaleFromKg] is true (lift e1RM series), values convert from kg into [displayUnit].
 */
private fun List<TrendPoint>?.toPoints(
    scaleFromKg: Boolean,
    displayUnit: Weight.WeightUnit,
): List<TrendPointUi> =
    this?.sortedBy { it.weekStart }?.mapIndexed { index, point ->
        val value =
            if (scaleFromKg) {
                WeightConversion.convertAmount(
                    point.value,
                    Weight.WeightUnit.Kg,
                    displayUnit,
                ).toFloat()
            } else {
                point.value.toFloat()
            }
        TrendPointUi(weekIndex = index, value = value)
    } ?: emptyList()

private fun com.stratum.domain.dashboard.SeriesDirection.toBadge(): TrendBadge = when (this) {
    com.stratum.domain.dashboard.SeriesDirection.Up -> TrendBadge.Up
    com.stratum.domain.dashboard.SeriesDirection.Flat -> TrendBadge.Flat
    com.stratum.domain.dashboard.SeriesDirection.Down -> TrendBadge.Down
    com.stratum.domain.dashboard.SeriesDirection.Insufficient -> TrendBadge.Unknown
}

/** Presentation keys for section headers — mapped to string resources in the UI layer. */
internal const val SECTION_UPPER = "Upper"
internal const val SECTION_LOWER = "Lower"
