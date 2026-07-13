package com.stratum.domain.dashboard

/**
 * Persisted dashboard chrome preferences (board tab, etc.). Defaults match first-launch
 * Muscles board.
 */
data class DashboardPreferences(
    val selectedTab: DashboardBoardTab = DashboardBoardTab.Muscles,
)

/**
 * Board tabs on the dashboard title toggle (Muscles / 7-Day / Lifts).
 * Identity is stable for persistence — do not reorder by ordinal for storage.
 */
enum class DashboardBoardTab {
    Muscles,
    SevenDay,
    Lifts,
}
