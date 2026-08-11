package com.stratum.domain.healthconnect

import com.stratum.core.testing.healthconnect.FakeHealthConnectPreferencesRepository
import com.stratum.core.testing.healthconnect.FakeHealthConnectSyncPort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class HealthConnectUseCasesTest {

    private lateinit var preferencesRepo: FakeHealthConnectPreferencesRepository
    private lateinit var syncPort: FakeHealthConnectSyncPort
    private lateinit var setEnabledUseCase: SetHealthConnectEnabledUseCase

    @BeforeEach
    fun setup() {
        preferencesRepo = FakeHealthConnectPreferencesRepository()
        syncPort = FakeHealthConnectSyncPort()
        setEnabledUseCase = SetHealthConnectEnabledUseCase(preferencesRepo, syncPort)
    }

    @Test
    @DisplayName("Given Health Connect is available when enabling then preference is saved")
    fun enableWhenAvailableSucceeds() = runTest {
        syncPort.isAvailable = true

        val result = setEnabledUseCase(true)

        assertTrue(result.isSuccess)
        assertEquals(true, preferencesRepo.getPreferences().enabled)
    }

    @Test
    @DisplayName("Given Health Connect is unavailable when enabling then fails soft with exception")
    fun enableWhenUnavailableFails() = runTest {
        syncPort.isAvailable = false

        val result = setEnabledUseCase(true)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is HealthConnectUnavailableException)
        assertEquals(false, preferencesRepo.getPreferences().enabled)
    }
}
