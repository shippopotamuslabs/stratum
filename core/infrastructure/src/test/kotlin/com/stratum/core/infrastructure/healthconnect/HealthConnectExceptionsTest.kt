package com.stratum.core.infrastructure.healthconnect

import com.stratum.application.healthconnect.HealthSyncFailure
import com.stratum.application.healthconnect.HealthSyncFailureKind
import com.stratum.application.healthconnect.isTerminalHealthSyncFailure
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class HealthConnectExceptionsTest {

    @Test
    @DisplayName("unavailable maps to Terminal with the exact message and cause")
    fun unavailableMapsToTerminal() {
        val vendor = HealthConnectUnavailableException()

        val mapped = vendor.toHealthSyncFailure()

        assertTrue(mapped is HealthSyncFailure)
        assertEquals(HealthSyncFailureKind.Terminal, (mapped as HealthSyncFailure).kind)
        assertEquals("Health Connect SDK is not available on this device", mapped.message)
        assertSame(vendor, mapped.cause)
    }

    @Test
    @DisplayName("permissionDenied maps to UserActionable with the exact message and cause")
    fun permissionDeniedMapsToUserActionable() {
        val vendor = HealthConnectPermissionDeniedException()

        val mapped = vendor.toHealthSyncFailure()

        assertTrue(mapped is HealthSyncFailure)
        assertEquals(HealthSyncFailureKind.UserActionable, (mapped as HealthSyncFailure).kind)
        assertEquals("Health Connect write permission is not granted", mapped.message)
        assertSame(vendor, mapped.cause)
    }

    @Test
    @DisplayName("other throwables map to Transient preserving message and cause")
    fun otherThrowableMapsToTransient() {
        val error = IllegalStateException("network blip")

        val mapped = error.toHealthSyncFailure()

        assertTrue(mapped is HealthSyncFailure)
        assertEquals(HealthSyncFailureKind.Transient, (mapped as HealthSyncFailure).kind)
        assertEquals("network blip", mapped.message)
        assertSame(error, mapped.cause)
    }

    @Test
    @DisplayName("throwable with null message maps to Transient with null message")
    fun nullMessageMapsToTransientWithNullMessage() {
        val error = IllegalStateException(null as String?)

        val mapped = error.toHealthSyncFailure()

        assertTrue(mapped is HealthSyncFailure)
        assertEquals(HealthSyncFailureKind.Transient, (mapped as HealthSyncFailure).kind)
        assertNull(mapped.message)
        assertSame(error, mapped.cause)
    }

    @Test
    @DisplayName("HealthSyncFailure passes through as the same instance")
    fun agnosticPassthrough() {
        val failure = HealthSyncFailure.permissionDenied()

        assertSame(failure, failure.toHealthSyncFailure())
    }

    @Test
    @DisplayName("CancellationException passes through as the same instance")
    fun cancellationPassthrough() {
        val cancellation = CancellationException("cancelled")

        assertSame(cancellation, cancellation.toHealthSyncFailure())
    }

    @Test
    @DisplayName("Terminal and UserActionable are terminal; Transient and raw are not")
    fun terminalClassification() {
        assertTrue(HealthSyncFailure.unavailable().isTerminalHealthSyncFailure())
        assertTrue(HealthSyncFailure.permissionDenied().isTerminalHealthSyncFailure())
        assertFalse(
            HealthSyncFailure(HealthSyncFailureKind.Transient, "network blip")
                .isTerminalHealthSyncFailure(),
        )
        assertFalse(IllegalStateException("network blip").isTerminalHealthSyncFailure())
    }
}
