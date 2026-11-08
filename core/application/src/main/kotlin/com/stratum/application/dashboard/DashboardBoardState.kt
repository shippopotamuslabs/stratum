package com.stratum.application.dashboard

import com.stratum.domain.dashboard.DashboardBoardTab
import com.stratum.domain.health.HealthMetricsPages
import com.stratum.domain.lift.Weight

/**
 * Consolidated dashboard read model (STR-208 stage 1).
 *
 * Owns the multi-source composition shown on the dashboard screen: the deterministic
 * training board ([board]), the Health Connect pages ([healthPages]), and the display-only
 * preferences that re-map presentation without a reload ([selectedTab], [displayUnit]).
 *
 * Application-layer read model: [board] carries the sealed [LoadDashboardResult] outcome
 * (STR-243) whose [LoadDashboardResult.Failed] diagnostics are drained exactly once at
 * the presentation boundary and never reach UI state. Presentation mapping (Loading /
 * Empty / Error chrome from [board], unit conversion via [displayUnit]) stays in the UI
 * layer.
 */
data class DashboardBoardState(
    /** Training board outcome; [LoadDashboardResult.Empty] is the empty board, failure is retry chrome. */
    val board: LoadDashboardResult,
    /** Health pages; [HealthMetricsPages.Hidden] when Health Connect is disabled. */
    val healthPages: HealthMetricsPages = HealthMetricsPages.Hidden,
    val selectedTab: DashboardBoardTab = DashboardBoardTab.Muscles,
    val displayUnit: Weight.WeightUnit = Weight.WeightUnit.Lb,
)
