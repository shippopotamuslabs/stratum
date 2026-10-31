package com.stratum.core.data.healthconnect

import com.stratum.application.healthconnect.HealthSyncFailure
import com.stratum.application.healthconnect.HealthSyncFailureKind
import kotlin.coroutines.cancellation.CancellationException

/** Health Connect SDK is missing or not usable on this device — not worth retrying. */
internal class HealthConnectUnavailableException : IllegalStateException()

/** Write permission missing or revoked — not worth retrying until the user re-grants. */
internal class HealthConnectPermissionDeniedException : IllegalStateException()

/**
 * Maps vendor SDK-state failures to the agnostic application-port classification
 * at the adapter boundary so vendor types never cross the port.
 */
internal fun Throwable.toHealthSyncFailure(): Throwable = when (this) {
    is HealthConnectUnavailableException -> HealthSyncFailure.unavailable(cause = this)
    is HealthConnectPermissionDeniedException -> HealthSyncFailure.permissionDenied(cause = this)
    is HealthSyncFailure -> this
    is CancellationException -> this
    else -> HealthSyncFailure(
        HealthSyncFailureKind.Transient,
        this.message,
        this,
    )
}
