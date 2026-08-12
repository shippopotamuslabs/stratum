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

    @Test
    @DisplayName("applyGrantedPermissions enables when any requested grant is present")
    fun applyGrantedPermissionsEnablesFromReads() = runTest {
        syncPort.granted = setOf("read_steps")

        val result = setEnabledUseCase.applyGrantedPermissions(setOf("write", "read_steps"))

        assertTrue(result.isSuccess)
        assertEquals(true, preferencesRepo.getPreferences().enabled)
    }

    @Test
    @DisplayName("applyGrantedPermissions leaves prefs unchanged when the grant query fails")
    fun applyGrantedPermissionsQueryFailureKeepsState() = runTest {
        preferencesRepo.setPreferences(HealthConnectPreferences(enabled = true))
        syncPort.grantedPermissionsFailure = IllegalStateException("sdk blip")

        val result = setEnabledUseCase.applyGrantedPermissions(setOf("write"))

        assertTrue(result.isSuccess)
        assertEquals(true, preferencesRepo.getPreferences().enabled)
    }
}
