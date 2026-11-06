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
import app.cash.turbine.test
import com.stratum.application.healthconnect.ApplyGrantedPermissionsDiagnostic
import com.stratum.domain.healthconnect.HealthConnectPreferences
import com.stratum.domain.healthconnect.HealthFailureKind

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

        assertEquals(SetHealthConnectEnabledResult.Success, result)
        assertEquals(true, preferencesRepo.getPreferences().enabled)
    }

    @Test
    @DisplayName("Given Health Connect is unavailable when enabling then SdkUnavailable branch")
    fun enableWhenUnavailableFails() = runTest {
        syncPort.isAvailable = false

        val result = setEnabledUseCase(true)

        assertEquals(SetHealthConnectEnabledResult.SdkUnavailable, result)
        assertEquals(false, preferencesRepo.getPreferences().enabled)
    }

    @Test
    @DisplayName("applyGrantedPermissions enables when any requested grant is present")
    fun applyGrantedPermissionsEnablesFromReads() = runTest {
        syncPort.granted = setOf("read_steps")

        val result = setEnabledUseCase.applyGrantedPermissions(setOf("write", "read_steps"))

        val applied = result as ApplyGrantedPermissionsOutcome.Applied
        assertTrue(applied.diagnostics.isEmpty())
        assertEquals(true, preferencesRepo.getPreferences().enabled)
    }

    @Test
    @DisplayName("STR-257: grant query failure returns an applied outcome with a diagnostic")
    fun applyGrantedPermissionsQueryFailureCarriesDiagnostic() = runTest {
        preferencesRepo.setPreferences(HealthConnectPreferences(enabled = true))
        val cause = IllegalStateException("sdk blip")
        syncPort.grantedPermissionsFailure = cause

        val result = setEnabledUseCase.applyGrantedPermissions(setOf("write"))

        val applied = result as ApplyGrantedPermissionsOutcome.Applied
        assertEquals(1, applied.diagnostics.size)
        val diagnostic = applied.diagnostics.single()
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
    @DisplayName("applyGrantedPermissions maps a failing preference write to PersistenceFailed")
    fun applyGrantedPermissionsWriteFailure() = runTest {
        syncPort.granted = setOf("write")
        preferencesRepo.setEnabledFailure = IllegalStateException("datastore blip")

        val result = setEnabledUseCase.applyGrantedPermissions(setOf("write"))

        assertEquals(ApplyGrantedPermissionsOutcome.PersistenceFailed, result)
    }

    @Test
    @DisplayName("permissionsStillNeeded returns requested types that are not granted")
    fun permissionsStillNeededSubtractsGranted() = runTest {
        syncPort.granted = setOf("write")

        val result = setEnabledUseCase.permissionsStillNeeded(setOf("write", "read_steps"))

        assertEquals(
            HealthConnectPermissionsQueryResult.Success(setOf("read_steps")),
            result,
        )
    }

    @Test
    @DisplayName("permissionsStillNeeded returns QueryFailed when the grant query fails")
    fun permissionsStillNeededQueryFailure() = runTest {
        syncPort.grantedPermissionsFailure = IllegalStateException("sdk blip")

        val result = setEnabledUseCase.permissionsStillNeeded(setOf("write"))

        assertEquals(HealthConnectPermissionsQueryResult.QueryFailed, result)
    }

    @Test
    @DisplayName("grantedPermissions returns the current SDK grant set")
    fun grantedPermissionsPassthrough() = runTest {
        syncPort.granted = setOf("write", "read_steps")

        val result = setEnabledUseCase.grantedPermissions()

        assertEquals(
            HealthConnectPermissionsQueryResult.Success(setOf("write", "read_steps")),
            result,
        )
    }

    @Test
    @DisplayName("grantedPermissions returns QueryFailed when the grant query fails")
    fun grantedPermissionsQueryFailure() = runTest {
        syncPort.grantedPermissionsFailure = IllegalStateException("sdk blip")

        val result = setEnabledUseCase.grantedPermissions()

        assertEquals(HealthConnectPermissionsQueryResult.QueryFailed, result)
    }

    @Test
    @DisplayName("STR-263: failing prefs write returns PersistenceFailed and logs exactly once")
    fun setEnabledFailureLogsOnce() = runTest {
        syncPort.isAvailable = true
        val cause = IllegalStateException("datastore blip")
        preferencesRepo.setEnabledFailure = cause

        val result = setEnabledUseCase(true)

        assertEquals(SetHealthConnectEnabledResult.PersistenceFailed, result)
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

        assertEquals(SetHealthConnectEnabledResult.SdkUnavailable, result)
        assertTrue(loggingPort.errors.isEmpty())
    }

    @Test
    @DisplayName("STR-263: successful write logs nothing")
    fun setEnabledSuccessDoesNotLog() = runTest {
        syncPort.isAvailable = true

        val result = setEnabledUseCase(false)

        assertEquals(SetHealthConnectEnabledResult.Success, result)
        assertTrue(loggingPort.errors.isEmpty())
    }

    @Test
    @DisplayName("STR-268: observe sync errors emits the reported kind")
    fun observeSyncErrorsEmitsReportedKind() = runTest {
        val syncStatus = FakeHealthConnectSyncStatusPort()
        val observeUseCase = ObserveHealthConnectSyncErrorsUseCase(syncStatus)

        observeUseCase().test {
            syncStatus.reportSyncError(HealthFailureKind.PermissionDenied)
            assertEquals(HealthFailureKind.PermissionDenied, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
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

        assertEquals(ClearHealthConnectSyncErrorResult.PersistenceFailed, result)
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

        assertEquals(ClearHealthConnectSyncErrorResult.Success, result)
        assertTrue(clearLogging.errors.isEmpty())
    }
}
