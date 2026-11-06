package com.stratum.core.infrastructure.healthconnect

import com.stratum.application.healthconnect.HEALTH_SYNC_PERMISSION_DENIED_MESSAGE
import com.stratum.application.healthconnect.HEALTH_SYNC_UNAVAILABLE_MESSAGE
import com.stratum.application.healthconnect.HealthSyncFailureKind

/** Health Connect SDK is missing or not usable on this device — not worth retrying. */
internal class HealthConnectUnavailableException : IllegalStateException()

/** Write permission missing or revoked — not worth retrying until the user re-grants. */
internal class HealthConnectPermissionDeniedException : IllegalStateException()

/**
 * A vendor SDK failure classified at the adapter boundary (STR-243
 * remediation): the [kind] and user-safe [message] that ride the sync port's
 * failure branch as values. The originating throwable itself rides only as
 * the branch's diagnostic cause, constructed by the adapter.
 */
internal data class ClassifiedHealthSyncFailure(
    val kind: HealthSyncFailureKind,
    val message: String?,
)

/**
 * Maps vendor SDK-state failures to the agnostic application-port
 * classification at the adapter boundary so vendor types never cross the
 * port. Cancellation is never classified — callers propagate it first.
 */
internal fun Throwable.toClassifiedHealthSyncFailure(): ClassifiedHealthSyncFailure =
    when (this) {
        is HealthConnectUnavailableException -> ClassifiedHealthSyncFailure(
            HealthSyncFailureKind.Terminal,
            HEALTH_SYNC_UNAVAILABLE_MESSAGE,
        )
        is HealthConnectPermissionDeniedException -> ClassifiedHealthSyncFailure(
            HealthSyncFailureKind.UserActionable,
            HEALTH_SYNC_PERMISSION_DENIED_MESSAGE,
        )
        else -> ClassifiedHealthSyncFailure(
            HealthSyncFailureKind.Transient,
            message,
        )
    }
