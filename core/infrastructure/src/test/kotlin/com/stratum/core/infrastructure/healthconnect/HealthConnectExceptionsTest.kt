package com.stratum.core.infrastructure.healthconnect

import com.stratum.application.healthconnect.HealthSyncFailureKind
import com.stratum.application.healthconnect.isTerminal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class HealthConnectExceptionsTest {

    @Test
    @DisplayName("unavailable maps to Terminal with the exact message")
    fun unavailableMapsToTerminal() {
        val vendor = HealthConnectUnavailableException()

        val mapped = vendor.toClassifiedHealthSyncFailure()

        assertEquals(HealthSyncFailureKind.Terminal, mapped.kind)
        assertEquals("Health Connect SDK is not available on this device", mapped.message)
    }

    @Test
    @DisplayName("permissionDenied maps to UserActionable with the exact message")
    fun permissionDeniedMapsToUserActionable() {
        val vendor = HealthConnectPermissionDeniedException()

        val mapped = vendor.toClassifiedHealthSyncFailure()

        assertEquals(HealthSyncFailureKind.UserActionable, mapped.kind)
        assertEquals("Health Connect write permission is not granted", mapped.message)
    }

    @Test
    @DisplayName("other throwables map to Transient preserving message")
    fun otherThrowableMapsToTransient() {
        val error = IllegalStateException("network blip")

        val mapped = error.toClassifiedHealthSyncFailure()

        assertEquals(HealthSyncFailureKind.Transient, mapped.kind)
        assertEquals("network blip", mapped.message)
    }

    @Test
    @DisplayName("throwable with null message maps to Transient with null message")
    fun nullMessageMapsToTransientWithNullMessage() {
        val error = IllegalStateException(null as String?)

        val mapped = error.toClassifiedHealthSyncFailure()

        assertEquals(HealthSyncFailureKind.Transient, mapped.kind)
        assertNull(mapped.message)
    }

    @Test
    @DisplayName("Terminal and UserActionable kinds are terminal; Transient is not")
    fun terminalClassification() {
        assertTrue(HealthSyncFailureKind.Terminal.isTerminal())
        assertTrue(HealthSyncFailureKind.UserActionable.isTerminal())
        assertFalse(HealthSyncFailureKind.Transient.isTerminal())
    }
}
