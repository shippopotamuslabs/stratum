package com.stratum

import com.stratum.domain.dashboard.DashboardBoardTab

/**
 * Presentation model for the dashboard board title toggle.
 * Domain [DashboardBoardTab] is mapped in the ViewModel — composables never take domain types.
 */
enum class DashboardBoardTabUi(val index: Int) {
    Muscles(0),
    SevenDay(1),
    Lifts(2),
    ;

    companion object {
        fun fromIndex(index: Int): DashboardBoardTabUi =
            entries.firstOrNull { it.index == index } ?: Muscles
    }
}

internal fun DashboardBoardTab.toUi(): DashboardBoardTabUi =
    when (this) {
        DashboardBoardTab.Muscles -> DashboardBoardTabUi.Muscles
        DashboardBoardTab.SevenDay -> DashboardBoardTabUi.SevenDay
        DashboardBoardTab.Lifts -> DashboardBoardTabUi.Lifts
    }

internal fun DashboardBoardTabUi.toDomain(): DashboardBoardTab =
    when (this) {
        DashboardBoardTabUi.Muscles -> DashboardBoardTab.Muscles
        DashboardBoardTabUi.SevenDay -> DashboardBoardTab.SevenDay
        DashboardBoardTabUi.Lifts -> DashboardBoardTab.Lifts
    }
