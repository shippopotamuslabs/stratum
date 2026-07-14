package com.stratum.core.data.dashboard

import com.stratum.domain.dashboard.DashboardBoardTab
import com.stratum.domain.dashboard.DashboardPreferences

internal fun DashboardPreferencesRecord.toDomain(): DashboardPreferences =
    DashboardPreferences(selectedTab = selectedTab.toDashboardBoardTab())

internal fun DashboardPreferences.toRecord(): DashboardPreferencesRecord =
    DashboardPreferencesRecord(selectedTab = selectedTab.toStorageName())

internal fun DashboardBoardTab.toStorageName(): String =
    when (this) {
        DashboardBoardTab.Muscles -> DashboardBoardTabNames.MUSCLES
        DashboardBoardTab.SevenDay -> DashboardBoardTabNames.SEVEN_DAY
        DashboardBoardTab.Lifts -> DashboardBoardTabNames.LIFTS
    }

internal fun String.toDashboardBoardTab(): DashboardBoardTab =
    when (this) {
        DashboardBoardTabNames.SEVEN_DAY -> DashboardBoardTab.SevenDay
        DashboardBoardTabNames.LIFTS -> DashboardBoardTab.Lifts
        else -> DashboardBoardTab.Muscles
    }
