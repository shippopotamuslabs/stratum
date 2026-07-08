package com.stratum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.core.ui.component.TrendPointUi
import com.stratum.domain.dashboard.DashboardSummary
import com.stratum.domain.dashboard.LiftSeries
import com.stratum.domain.dashboard.LoadDashboardUseCase
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
        val lifts: List<LiftSeriesUi>,
    ) : DashboardUiState
}

data class MuscleSeriesUi(
    val name: String,
    val headlineVolume: String,
    val badge: TrendBadge,
    val progressPoints: List<TrendPointUi>,
    val volumePoints: List<TrendPointUi>,
    val contributingLifts: Int,
)

data class LiftSeriesUi(
    val name: String,
    val e1rmText: String?,
    val badge: TrendBadge,
    val performancePoints: List<TrendPointUi>,
    val volumePoints: List<TrendPointUi>,
    val tenureText: String?,
    val sessions: Int,
)

/** Coarse direction used to pick a color + arrow glyph in the UI. */
enum class TrendBadge { Up, Flat, Down, Unknown }

private const val E1RM_SLOPE_THRESHOLD = 0.5

private fun DashboardSummary.toUiState(): DashboardUiState.Loaded =
    DashboardUiState.Loaded(
        windowWeeks = windowWeeks,
        muscles = muscles.map { it.toUi() },
        lifts = lifts.map { it.toUi() },
    )

private fun MuscleSeries.toUi(): MuscleSeriesUi {
    val progressSlope = progressSeries?.let { olsSlope(it) }
    val progressDirection = slopeToDirection(progressSlope, E1RM_SLOPE_THRESHOLD)
    return MuscleSeriesUi(
        name = displayName,
        headlineVolume = "${meanWeeklyHardSets.toInt()} sets/wk",
        badge = progressDirection.toBadge(),
        progressPoints = progressSeries.toPoints(),
        volumePoints = volumeSeries.toPoints(),
        contributingLifts = contributingLiftCount,
    )
}

private fun LiftSeries.toUi(): LiftSeriesUi {
    val slope = olsSlope(performanceSeries)
    val direction = slopeToDirection(slope, E1RM_SLOPE_THRESHOLD)
    return LiftSeriesUi(
        name = name,
        e1rmText = latestE1rm?.let { "${it.amount.toInt()} ${it.unit.shortLabel()}" },
        badge = direction.toBadge(),
        performancePoints = performanceSeries.toPoints(),
        volumePoints = volumeSeries.toPoints(),
        tenureText = tenureWeeks.takeIf { it > 0 }?.let { "$it wks" },
        sessions = sessionCount,
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
