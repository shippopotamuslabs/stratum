package com.stratum.application.dashboard

import com.stratum.core.testing.dashboard.FakeDashboardPreferencesRepository
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
        val result = SetDashboardBoardTabUseCase(fake)(DashboardBoardTab.Lifts)
        assertEquals(DashboardPreferenceResult.Success, result)
        assertEquals(DashboardBoardTab.Lifts, fake.current.selectedTab)
    }

    @Test
    @DisplayName("setSelectedTab returns PersistenceFailed carrying the diagnostic when the write throws (STR-243/STR-237)")
    fun setTabPersistenceFailure() = runTest {
        val cause = RuntimeException("store unavailable")
        val throwing =
            object : DashboardPreferencesRepository by fake {
                override suspend fun setSelectedTab(tab: DashboardBoardTab) {
                    throw cause
                }
            }
        val result = SetDashboardBoardTabUseCase(throwing)(DashboardBoardTab.Lifts)
        val failed = result as DashboardPreferenceResult.PersistenceFailed
        assertEquals(1, failed.diagnostics.size)
        val diagnostic = failed.diagnostics.single()
        assertEquals("SetDashboardBoardTab", diagnostic.tag)
        assertEquals("Failed to set dashboard board tab: store unavailable", diagnostic.message)
        org.junit.jupiter.api.Assertions.assertSame(cause, diagnostic.cause)
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
        val subject = SetDashboardBoardTabUseCase(cancelling)
        assertThrows(CancellationException::class.java) {
            runBlocking { subject(DashboardBoardTab.Lifts) }
        }
    }
}
