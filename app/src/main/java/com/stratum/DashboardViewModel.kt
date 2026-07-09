package com.stratum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.core.ui.component.TrendPointUi
import com.stratum.domain.dashboard.CalloutDirection
import com.stratum.domain.dashboard.CalloutKind
import com.stratum.domain.dashboard.DashboardSummary
import com.stratum.domain.dashboard.LIFT_E1RM_SLOPE_THRESHOLD
import com.stratum.domain.dashboard.LiftSeries
import com.stratum.domain.dashboard.LoadDashboardUseCase
import com.stratum.domain.dashboard.MUSCLE_PROGRESS_SLOPE_THRESHOLD
import com.stratum.domain.dashboard.MuscleSeries
import com.stratum.domain.dashboard.TrendPoint
import com.stratum.domain.dashboard.olsSlope
import com.stratum.domain.dashboard.slopeToDirection
import com.stratum.domain.lift.Weight
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val loadDashboard: LoadDashboardUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    /**
     * Reloads the dashboard. Called on init and again on lifecycle resume so a workout completed
     * while the dashboard was in the background (and refreshed the weekly tables on finalize) shows
     * up. Keeps the last [DashboardUiState.Loaded] visible while refreshing so tab switches do not
     * flash the empty copy.
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
}

/** Presentation-only dashboard state — no domain types leak into composables (Detekt-enforced). */
sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data object Empty : DashboardUiState
    data class Loaded(
        val windowWeeks: Int,
        val muscles: List<MuscleSeriesUi>,
        val liftSections: List<LiftSectionUi>,
    ) : DashboardUiState
}

/**
 * High-confidence trend callout applied to a card border (stronger gate than [TrendBadge]).
 * Null = no strong signal; keep the subtle default border.
 */
enum class CalloutDirectionUi { Progressing, Regressing }

data class MuscleSeriesUi(
    val name: String,
    val headlineVolume: String,
    val badge: TrendBadge,
    val progressPoints: List<TrendPointUi>,
    val volumePoints: List<TrendPointUi>,
    val contributingLifts: Int,
    val highlight: CalloutDirectionUi? = null,
)

data class LiftSectionUi(
    val title: String,
    val lifts: List<LiftSeriesUi>,
)

data class LiftSeriesUi(
    val name: String,
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
    )
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
