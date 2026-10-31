package com.stratum.application.healthconnect

import com.stratum.domain.healthconnect.HealthFailureKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class HealthSyncFailureMappingTest {

    @Test
    @DisplayName("Terminal maps to Unavailable")
    fun terminalMapsToUnavailable() {
        assertEquals(
            HealthFailureKind.Unavailable,
            HealthSyncFailureKind.Terminal.toHealthFailureKind(),
        )
    }

    @Test
    @DisplayName("UserActionable maps to PermissionDenied")
    fun userActionableMapsToPermissionDenied() {
        assertEquals(
            HealthFailureKind.PermissionDenied,
            HealthSyncFailureKind.UserActionable.toHealthFailureKind(),
        )
    }

    @Test
    @DisplayName("Transient maps to Unknown")
    fun transientMapsToUnknown() {
        assertEquals(
            HealthFailureKind.Unknown,
            HealthSyncFailureKind.Transient.toHealthFailureKind(),
        )
    }
}
