package com.stratum.core.data.repository

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Robolectric tests for [DataStoreHealthConnectPreferencesRepository]. The DataStore file is
 * process-scoped — each test resets preference state in [setup]. Asserts values only; the
 * repository never logs, so no logging fake appears anywhere near it.
 */
@ExtendWith(RobolectricExtension::class)
@DisplayName("DataStoreHealthConnectPreferencesRepository")
class DataStoreHealthConnectPreferencesRepositoryTest {

    private lateinit var repository: DataStoreHealthConnectPreferencesRepository

    @BeforeEach
    fun setup() = runTest {
        repository = DataStoreHealthConnectPreferencesRepository(RuntimeEnvironment.getApplication())
        repository.setEnabled(false)
        repository.setPendingSyncError(null)
    }

    @Test
    @DisplayName("setEnabled returns success and persists the preference")
    fun setEnabledPersists() = runTest {
        val result = repository.setEnabled(true)

        assertTrue(result.isSuccess)
        assertEquals(true, repository.getPreferences().enabled)
    }

    @Test
    @DisplayName("setPendingSyncError returns success and persists the sticky error")
    fun setPendingSyncErrorPersists() = runTest {
        val result = repository.setPendingSyncError("sync blew up")

        assertTrue(result.isSuccess)
        assertEquals("sync blew up", repository.getPreferences().pendingSyncError)
    }

    @Test
    @DisplayName("clearing the sticky error with null persists null")
    fun clearPendingSyncErrorPersists() = runTest {
        repository.setPendingSyncError("sync blew up")

        val result = repository.setPendingSyncError(null)

        assertTrue(result.isSuccess)
        assertEquals(null, repository.getPreferences().pendingSyncError)
    }
}
