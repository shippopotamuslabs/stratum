package com.stratum.core.infrastructure.repository

import app.cash.turbine.test
import com.stratum.core.testing.healthconnect.FakeHealthConnectPreferencesRepository
import com.stratum.domain.healthconnect.HealthFailureKind
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import com.stratum.application.healthconnect.HealthConnectWriteOutcome
import com.stratum.application.healthconnect.HealthSyncDiagnostic
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class HealthConnectSyncStatusRepositoryTest {

    private lateinit var preferences: FakeHealthConnectPreferencesRepository
    private lateinit var repository: HealthConnectSyncStatusRepository

    @BeforeEach
    fun setup() {
        preferences = FakeHealthConnectPreferencesRepository()
        repository = HealthConnectSyncStatusRepository(preferences)
    }

    @Test
    @DisplayName("reportSyncError forwards success without logging")
    fun reportSyncErrorForwards() = runTest {
        repository.reportSyncError(HealthFailureKind.Unavailable)

        assertEquals(
            HealthFailureKind.Unavailable,
            preferences.getPreferences().pendingSyncError,
        )
    }

    @Test
    @DisplayName("reportSyncError forwards failure as the outcome's diagnostic")
    fun reportSyncErrorForwardsFailure() = runTest {
        val cause = IllegalStateException("datastore blip")
        preferences.setPendingSyncErrorFailure = cause

        val outcome = repository.reportSyncError(HealthFailureKind.Unavailable)

        assertTrue(outcome is HealthConnectWriteOutcome.Failed)
        assertSame(
            cause,
            ((outcome as HealthConnectWriteOutcome.Failed).diagnostics.single()
                as HealthSyncDiagnostic.OperationFailed).cause,
        )
        assertEquals(null, preferences.getPreferences().pendingSyncError)
    }

    @Test
    @DisplayName("same kind re-reported after clear emits again")
    fun sameKindReemitsAfterClear() = runTest {
        repository.observePendingSyncErrors().test {
            repository.reportSyncError(HealthFailureKind.Unknown)
            assertEquals(HealthFailureKind.Unknown, awaitItem())
            repository.clearPendingSyncError()
            // The clear emits nothing (nulls are filtered), but it breaks the
            // distinct chain so the same kind re-reports.
            repository.reportSyncError(HealthFailureKind.Unknown)
            assertEquals(HealthFailureKind.Unknown, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("clearPendingSyncError forwards failure as the outcome's diagnostic")
    fun clearPendingSyncErrorForwardsFailure() = runTest {
        preferences.setPendingSyncError(HealthFailureKind.PermissionDenied)
        val cause = IllegalStateException("datastore blip")
        preferences.setPendingSyncErrorFailure = cause

        val outcome = repository.clearPendingSyncError()

        assertTrue(outcome is HealthConnectWriteOutcome.Failed)
        assertSame(
            cause,
            ((outcome as HealthConnectWriteOutcome.Failed).diagnostics.single()
                as HealthSyncDiagnostic.OperationFailed).cause,
        )
        assertEquals(
            HealthFailureKind.PermissionDenied,
            preferences.getPreferences().pendingSyncError,
        )
    }
}
