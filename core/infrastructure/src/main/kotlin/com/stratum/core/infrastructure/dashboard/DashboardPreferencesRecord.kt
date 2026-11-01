package com.stratum.core.infrastructure.dashboard

import kotlinx.serialization.Serializable

/**
 * Typed DataStore root for the last-selected dashboard board tab.
 * [selectedTab] stores [com.stratum.domain.dashboard.DashboardBoardTab] enum names.
 */
@Serializable
data class DashboardPreferencesRecord(
    val selectedTab: String = DashboardBoardTabNames.MUSCLES,
)

internal object DashboardBoardTabNames {
    const val MUSCLES = "Muscles"
    const val SEVEN_DAY = "SevenDay"
    const val LIFTS = "Lifts"
}
