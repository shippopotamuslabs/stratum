package com.stratum.core.infrastructure.work

import com.stratum.application.healthconnect.HealthSyncFailureKind
import com.stratum.domain.healthconnect.HealthFailureKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class HealthConnectSyncWorkerKindTest {

    @Test
    @DisplayName("unavailable (Terminal) kind maps to Unavailable")
    fun unavailableMapsToUnavailable() {
        assertEquals(
            HealthFailureKind.Unavailable,
            HealthSyncFailureKind.Terminal.toStickyHealthFailureKind(),
        )
    }

    @Test
    @DisplayName("permission-denied (UserActionable) kind maps to PermissionDenied")
    fun permissionDeniedMapsToPermissionDenied() {
        assertEquals(
            HealthFailureKind.PermissionDenied,
            HealthSyncFailureKind.UserActionable.toStickyHealthFailureKind(),
        )
    }

    @Test
    @DisplayName("transient kind maps to Unknown")
    fun transientMapsToUnknown() {
        assertEquals(
            HealthFailureKind.Unknown,
            HealthSyncFailureKind.Transient.toStickyHealthFailureKind(),
        )
    }

    @Test
    @DisplayName("unclassified (null kind) maps to Unknown")
    fun rawThrowableMapsToUnknown() {
        assertEquals(
            HealthFailureKind.Unknown,
            (null as HealthSyncFailureKind?).toStickyHealthFailureKind(),
        )
    }
}
