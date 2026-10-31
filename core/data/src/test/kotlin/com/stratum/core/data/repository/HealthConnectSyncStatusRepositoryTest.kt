package com.stratum.core.data.repository

import com.stratum.core.testing.healthconnect.FakeHealthConnectPreferencesRepository
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
        val result = repository.reportSyncError("sync blew up")

        assertTrue(result.isSuccess)
        assertEquals("sync blew up", preferences.getPreferences().pendingSyncError)
    }

    @Test
    @DisplayName("reportSyncError forwards failure as a value")
    fun reportSyncErrorForwardsFailure() = runTest {
        val cause = IllegalStateException("datastore blip")
        preferences.setPendingSyncErrorFailure = cause

        val result = repository.reportSyncError("sync blew up")

        assertTrue(result.isFailure)
        assertSame(cause, result.exceptionOrNull())
        assertEquals(null, preferences.getPreferences().pendingSyncError)
    }

    @Test
    @DisplayName("clearPendingSyncError forwards failure as a value")
    fun clearPendingSyncErrorForwardsFailure() = runTest {
        preferences.setPendingSyncError("sync blew up")
        val cause = IllegalStateException("datastore blip")
        preferences.setPendingSyncErrorFailure = cause

        val result = repository.clearPendingSyncError()

        assertTrue(result.isFailure)
        assertSame(cause, result.exceptionOrNull())
        assertEquals("sync blew up", preferences.getPreferences().pendingSyncError)
    }
}
