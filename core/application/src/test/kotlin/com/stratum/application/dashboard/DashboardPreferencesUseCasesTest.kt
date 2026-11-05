package com.stratum.application.dashboard

import com.stratum.core.testing.dashboard.FakeDashboardPreferencesRepository
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.domain.dashboard.DashboardBoardTab
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.coroutines.cancellation.CancellationException

class DashboardPreferencesUseCasesTest {

    private val fake = FakeDashboardPreferencesRepository()

    @Test
    @DisplayName("setSelectedTab writes the tab and returns Success (STR-243)")
    fun setTabSuccess() = runTest {
        val result = SetDashboardBoardTabUseCase(fake, FakeLoggingPort())(DashboardBoardTab.Lifts)
        assertEquals(DashboardPreferenceResult.Success, result)
        assertEquals(DashboardBoardTab.Lifts, fake.current.selectedTab)
    }

    @Test
    @DisplayName("setSelectedTab returns PersistenceFailed when the write throws (STR-243)")
    fun setTabPersistenceFailure() = runTest {
        val throwing =
            object : DashboardPreferencesRepository by fake {
                override suspend fun setSelectedTab(tab: DashboardBoardTab) {
                    throw RuntimeException("store unavailable")
                }
            }
        val result = SetDashboardBoardTabUseCase(throwing, FakeLoggingPort())(DashboardBoardTab.Lifts)
        assertEquals(DashboardPreferenceResult.PersistenceFailed, result)
    }

    @Test
    @DisplayName("setSelectedTab propagates CancellationException instead of capturing it (STR-243)")
    fun setTabCancellationPropagates() {
        val cancelling =
            object : DashboardPreferencesRepository by fake {
                override suspend fun setSelectedTab(tab: DashboardBoardTab) {
                    throw CancellationException("cancelled")
                }
            }
        val subject = SetDashboardBoardTabUseCase(cancelling, FakeLoggingPort())
        assertThrows(CancellationException::class.java) {
            runBlocking { subject(DashboardBoardTab.Lifts) }
        }
    }
}
