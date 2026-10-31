package com.stratum.application.healthconnect

import com.stratum.core.testing.healthconnect.FakeHealthConnectPreferencesRepository
import com.stratum.core.testing.healthconnect.FakeHealthConnectSyncPort
import com.stratum.core.testing.healthconnect.FakeHealthConnectSyncStatusPort
import com.stratum.core.testing.logging.FakeLoggingPort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.application.healthconnect.ApplyGrantedPermissionsDiagnostic
import com.stratum.domain.healthconnect.HealthConnectPreferences
import com.stratum.domain.healthconnect.HealthConnectUnavailableException

class HealthConnectUseCasesTest {

    private lateinit var preferencesRepo: FakeHealthConnectPreferencesRepository
    private lateinit var syncPort: FakeHealthConnectSyncPort
    private lateinit var loggingPort: FakeLoggingPort
    private lateinit var setEnabledUseCase: SetHealthConnectEnabledUseCase

    @BeforeEach
    fun setup() {
        preferencesRepo = FakeHealthConnectPreferencesRepository()
        syncPort = FakeHealthConnectSyncPort()
        loggingPort = FakeLoggingPort()
        setEnabledUseCase = SetHealthConnectEnabledUseCase(preferencesRepo, syncPort, loggingPort)
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
        assertTrue(result.getOrThrow().diagnostics.isEmpty())
        assertEquals(true, preferencesRepo.getPreferences().enabled)
    }

    @Test
    @DisplayName("STR-257: grant query failure returns a success outcome with a diagnostic")
    fun applyGrantedPermissionsQueryFailureCarriesDiagnostic() = runTest {
        preferencesRepo.setPreferences(HealthConnectPreferences(enabled = true))
        val cause = IllegalStateException("sdk blip")
        syncPort.grantedPermissionsFailure = cause

        val result = setEnabledUseCase.applyGrantedPermissions(setOf("write"))

        assertTrue(result.isSuccess)
        val outcome = result.getOrThrow()
        assertEquals(1, outcome.diagnostics.size)
        val diagnostic = outcome.diagnostics.single()
        assertTrue(diagnostic is ApplyGrantedPermissionsDiagnostic.PermissionQueryFailed)
        assertSame(
            cause,
            (diagnostic as ApplyGrantedPermissionsDiagnostic.PermissionQueryFailed).cause,
        )
        assertEquals(true, preferencesRepo.getPreferences().enabled)
    }

    @Test
    @DisplayName("STR-257: grant query cancellation rethrows without touching the preference")
    fun applyGrantedPermissionsCancellationPropagates() = runTest {
        val cancellation = kotlinx.coroutines.CancellationException("cancelled")
        syncPort.grantedPermissionsFailure = cancellation

        assertThrows<kotlinx.coroutines.CancellationException> {
            setEnabledUseCase.applyGrantedPermissions(setOf("write"))
        }
        assertEquals(false, preferencesRepo.getPreferences().enabled)
    }

    @Test
    @DisplayName("permissionsStillNeeded returns requested types that are not granted")
    fun permissionsStillNeededSubtractsGranted() = runTest {
        syncPort.granted = setOf("write")

        val result = setEnabledUseCase.permissionsStillNeeded(setOf("write", "read_steps"))

        assertEquals(setOf("read_steps"), result.getOrThrow())
    }

    @Test
    @DisplayName("permissionsStillNeeded fails when the grant query fails")
    fun permissionsStillNeededQueryFailure() = runTest {
        syncPort.grantedPermissionsFailure = IllegalStateException("sdk blip")

        val result = setEnabledUseCase.permissionsStillNeeded(setOf("write"))

        assertTrue(result.isFailure)
    }

    @Test
    @DisplayName("grantedPermissions returns the current SDK grant set")
    fun grantedPermissionsPassthrough() = runTest {
        syncPort.granted = setOf("write", "read_steps")

        val result = setEnabledUseCase.grantedPermissions()

        assertEquals(setOf("write", "read_steps"), result.getOrThrow())
    }

    @Test
    @DisplayName("STR-263: failing prefs write returns failure and logs exactly once")
    fun setEnabledFailureLogsOnce() = runTest {
        syncPort.isAvailable = true
        val cause = IllegalStateException("datastore blip")
        preferencesRepo.setEnabledFailure = cause

        val result = setEnabledUseCase(true)

        assertTrue(result.isFailure)
        assertSame(cause, result.exceptionOrNull())
        assertEquals(false, preferencesRepo.getPreferences().enabled)
        assertEquals(1, loggingPort.errors.size)
        val entry = loggingPort.errors.single()
        assertEquals("HealthConnectPrefs", entry.tag)
        assertTrue(entry.message.startsWith("Failed to set Health Connect enabled"))
        assertSame(cause, entry.throwable)
    }

    @Test
    @DisplayName("STR-263: SDK-unavailable short-circuit fails without logging")
    fun setEnabledUnavailableShortCircuitDoesNotLog() = runTest {
        syncPort.isAvailable = false

        val result = setEnabledUseCase(true)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is HealthConnectUnavailableException)
        assertTrue(loggingPort.errors.isEmpty())
    }

    @Test
    @DisplayName("STR-263: successful write logs nothing")
    fun setEnabledSuccessDoesNotLog() = runTest {
        syncPort.isAvailable = true

        val result = setEnabledUseCase(false)

        assertTrue(result.isSuccess)
        assertTrue(loggingPort.errors.isEmpty())
    }

    @Test
    @DisplayName("STR-263: clear failure logs exactly once with the sync-error message")
    fun clearSyncErrorFailureLogsOnce() = runTest {
        val syncStatus = FakeHealthConnectSyncStatusPort()
        val clearLogging = FakeLoggingPort()
        val clearUseCase = ClearHealthConnectSyncErrorUseCase(syncStatus, clearLogging)
        val cause = IllegalStateException("datastore blip")
        syncStatus.failure = cause

        val result = clearUseCase()

        assertTrue(result.isFailure)
        assertSame(cause, result.exceptionOrNull())
        assertEquals(1, clearLogging.errors.size)
        val entry = clearLogging.errors.single()
        assertEquals("HealthConnectPrefs", entry.tag)
        assertTrue(entry.message.startsWith("Failed to set Health Connect sync error"))
        assertSame(cause, entry.throwable)
    }

    @Test
    @DisplayName("STR-263: clear success logs nothing")
    fun clearSyncErrorSuccessDoesNotLog() = runTest {
        val syncStatus = FakeHealthConnectSyncStatusPort()
        val clearLogging = FakeLoggingPort()
        val clearUseCase = ClearHealthConnectSyncErrorUseCase(syncStatus, clearLogging)

        val result = clearUseCase()

        assertTrue(result.isSuccess)
        assertTrue(clearLogging.errors.isEmpty())
    }
}
