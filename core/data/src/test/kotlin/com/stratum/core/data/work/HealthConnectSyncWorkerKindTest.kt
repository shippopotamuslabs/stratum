package com.stratum.core.data.work

import com.stratum.application.healthconnect.HealthSyncFailure
import com.stratum.application.healthconnect.HealthSyncFailureKind
import com.stratum.domain.healthconnect.HealthFailureKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class HealthConnectSyncWorkerKindTest {

    @Test
    @DisplayName("unavailable factory maps to Unavailable")
    fun unavailableMapsToUnavailable() {
        assertEquals(
            HealthFailureKind.Unavailable,
            HealthSyncFailure.unavailable().toHealthFailureKind(),
        )
    }

    @Test
    @DisplayName("permission-denied factory maps to PermissionDenied")
    fun permissionDeniedMapsToPermissionDenied() {
        assertEquals(
            HealthFailureKind.PermissionDenied,
            HealthSyncFailure.permissionDenied().toHealthFailureKind(),
        )
    }

    @Test
    @DisplayName("transient failure maps to Unknown")
    fun transientMapsToUnknown() {
        assertEquals(
            HealthFailureKind.Unknown,
            HealthSyncFailure(HealthSyncFailureKind.Transient, "retryable blip")
                .toHealthFailureKind(),
        )
    }

    @Test
    @DisplayName("unclassified throwable maps to Unknown")
    fun rawThrowableMapsToUnknown() {
        assertEquals(
            HealthFailureKind.Unknown,
            IllegalStateException("vendor blip").toHealthFailureKind(),
        )
    }
}
