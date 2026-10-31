package com.stratum.core.infrastructure.repository

import app.cash.turbine.test
import com.stratum.core.testing.healthconnect.FakeHealthConnectPreferencesRepository
import com.stratum.domain.healthconnect.HealthFailureKind
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
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
        val result = repository.reportSyncError(HealthFailureKind.Unavailable)

        assertTrue(result.isSuccess)
        assertEquals(
            HealthFailureKind.Unavailable,
            preferences.getPreferences().pendingSyncError,
        )
    }

    @Test
    @DisplayName("reportSyncError forwards failure as a value")
    fun reportSyncErrorForwardsFailure() = runTest {
        val cause = IllegalStateException("datastore blip")
        preferences.setPendingSyncErrorFailure = cause

        val result = repository.reportSyncError(HealthFailureKind.Unavailable)

        assertTrue(result.isFailure)
        assertSame(cause, result.exceptionOrNull())
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
    @DisplayName("clearPendingSyncError forwards failure as a value")
    fun clearPendingSyncErrorForwardsFailure() = runTest {
        preferences.setPendingSyncError(HealthFailureKind.PermissionDenied)
        val cause = IllegalStateException("datastore blip")
        preferences.setPendingSyncErrorFailure = cause

        val result = repository.clearPendingSyncError()

        assertTrue(result.isFailure)
        assertSame(cause, result.exceptionOrNull())
        assertEquals(
            HealthFailureKind.PermissionDenied,
            preferences.getPreferences().pendingSyncError,
        )
    }
}
