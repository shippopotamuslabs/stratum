package com.stratum.core.testing.healthconnect

import com.stratum.application.healthconnect.HEALTH_SYNC_PERMISSION_DENIED_MESSAGE
import com.stratum.application.healthconnect.HEALTH_SYNC_UNAVAILABLE_MESSAGE
import com.stratum.application.healthconnect.HealthConnectGrantedPermissionsOutcome
import com.stratum.application.healthconnect.HealthConnectPushOutcome
import com.stratum.application.healthconnect.HealthSyncDiagnostic
import com.stratum.application.healthconnect.HealthSyncFailureKind
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Self-test for [FakeHealthConnectSyncPort]'s documented branches (B1): each
 * remaining field produces exactly the branch its KDoc promises.
 */
class FakeHealthConnectSyncPortTest {

    private val session = WorkoutLoggingFixtures.completedLiveSession(id = "ts-fake-1")

    private fun HealthConnectPushOutcome.Failed.cause(): Throwable =
        (diagnostics.single() as HealthSyncDiagnostic.OperationFailed).cause

    @Test
    @DisplayName("Default state: push succeeds and records the session; granted returns the grant set")
    fun defaultStateSucceeds() = runTest {
        val fake = FakeHealthConnectSyncPort()
        fake.granted = setOf("write")

        val push = fake.pushSession(session, emptyList())
        val granted = fake.grantedPermissions()

        assertEquals(HealthConnectPushOutcome.Success, push)
        assertEquals(1, fake.pushedSessions.size)
        assertEquals(
            HealthConnectGrantedPermissionsOutcome.Success(setOf("write")),
            granted,
        )
    }

    @Test
    @DisplayName("pushFailure drives Failed as Transient carrying the configured throwable itself")
    fun pushFailureDrivesTransientFailed() = runTest {
        val fake = FakeHealthConnectSyncPort()
        val failure = IllegalStateException("network blip")
        fake.pushFailure = failure

        val outcome = fake.pushSession(session, emptyList()) as HealthConnectPushOutcome.Failed

        assertEquals(HealthSyncFailureKind.Transient, outcome.kind)
        assertEquals("network blip", outcome.message)
        assertSame(failure, outcome.cause())
        assertTrue(fake.pushedSessions.isEmpty())
    }

    @Test
    @DisplayName("pushFailureKind and pushFailureMessage override classification and message; cause stays the configured throwable")
    fun pushFailureOverridePair() = runTest {
        val fake = FakeHealthConnectSyncPort()
        val failure = IllegalStateException("denied by sdk")
        fake.pushFailure = failure
        fake.pushFailureKind = HealthSyncFailureKind.UserActionable
        fake.pushFailureMessage = "user actionable copy"

        val outcome = fake.pushSession(session, emptyList()) as HealthConnectPushOutcome.Failed

        assertEquals(HealthSyncFailureKind.UserActionable, outcome.kind)
        assertEquals("user actionable copy", outcome.message)
        assertSame(failure, outcome.cause())
    }

    @Test
    @DisplayName("pushFailure as CancellationException throws — the only throwing value")
    fun pushFailureCancellationThrows() = runTest {
        val fake = FakeHealthConnectSyncPort()
        fake.pushFailure = CancellationException("cancelled")

        assertThrows<CancellationException> {
            fake.pushSession(session, emptyList())
        }
    }

    @Test
    @DisplayName("Unavailable state drives Terminal Failed; no-permission state drives UserActionable Failed")
    fun stateBranches() = runTest {
        val unavailable = FakeHealthConnectSyncPort().apply { isAvailable = false }
        val unavailableOutcome =
            unavailable.pushSession(session, emptyList()) as HealthConnectPushOutcome.Failed
        assertEquals(HealthSyncFailureKind.Terminal, unavailableOutcome.kind)
        assertEquals(HEALTH_SYNC_UNAVAILABLE_MESSAGE, unavailableOutcome.message)

        val noPermission = FakeHealthConnectSyncPort().apply { hasPermission = false }
        val deniedOutcome =
            noPermission.pushSession(session, emptyList()) as HealthConnectPushOutcome.Failed
        assertEquals(HealthSyncFailureKind.UserActionable, deniedOutcome.kind)
        assertEquals(HEALTH_SYNC_PERMISSION_DENIED_MESSAGE, deniedOutcome.message)
    }

    @Test
    @DisplayName("Configured failure is checked before availability state in both operations")
    fun failureFieldPrecedesAvailability() = runTest {
        val fake = FakeHealthConnectSyncPort()
        val failure = IllegalStateException("configured")
        fake.isAvailable = false
        fake.pushFailure = failure
        fake.grantedPermissionsFailure = failure

        val push = fake.pushSession(session, emptyList()) as HealthConnectPushOutcome.Failed
        assertEquals(HealthSyncFailureKind.Transient, push.kind)
        assertSame(failure, push.cause())

        val granted =
            fake.grantedPermissions() as HealthConnectGrantedPermissionsOutcome.Failed
        assertEquals(HealthSyncFailureKind.Transient, granted.kind)
        assertSame(
            failure,
            (granted.diagnostics.single() as HealthSyncDiagnostic.OperationFailed).cause,
        )
    }
}
