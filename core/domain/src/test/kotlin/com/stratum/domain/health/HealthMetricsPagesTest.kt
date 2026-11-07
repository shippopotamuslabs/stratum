package com.stratum.domain.health

import com.stratum.domain.healthconnect.HealthConnectDiagnostic
import com.stratum.domain.healthconnect.HealthFailureKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class HealthMetricsPagesTest {

    private val failure = IllegalStateException("sdk blip")
    private val diagnostic =
        HealthConnectDiagnostic.SnapshotReadFailed(
            kind = HealthFailureKind.Unknown,
            cause = failure,
        )

    @Test
    fun `degraded factory builds the canonical degraded state`() {
        val pages = HealthMetricsPages.degraded(diagnostic)

        assertTrue(pages.visible)
        assertEquals(HealthMetricsAvailability.Degraded, pages.availability)
        assertEquals(listOf(diagnostic), pages.diagnostics)
        assertEquals(ActivityMetrics(), pages.activity)
        assertEquals(WeightMetrics(), pages.weight)
        assertEquals(RecoveryMetrics(), pages.recovery)
    }

    @Test
    fun `degraded without a snapshot read failure is rejected`() {
        assertThrows<IllegalArgumentException> {
            HealthMetricsPages(
                visible = true,
                availability = HealthMetricsAvailability.Degraded,
            )
        }
    }

    @Test
    fun `degraded with populated metrics is rejected`() {
        assertThrows<IllegalArgumentException> {
            HealthMetricsPages(
                visible = true,
                activity = ActivityMetrics(todaySteps = 100L),
                diagnostics = listOf(diagnostic),
                availability = HealthMetricsAvailability.Degraded,
            )
        }
    }

    @Test
    fun `available carrying a snapshot read failure is rejected`() {
        assertThrows<IllegalArgumentException> {
            HealthMetricsPages(
                visible = true,
                diagnostics = listOf(diagnostic),
            )
        }
    }

    @Test
    fun `hidden and plain available pages are valid`() {
        assertEquals(HealthMetricsAvailability.Available, HealthMetricsPages.Hidden.availability)
        assertTrue(HealthMetricsPages.Hidden.diagnostics.isEmpty())
        assertTrue(HealthMetricsPages(visible = true).diagnostics.isEmpty())
    }
}
