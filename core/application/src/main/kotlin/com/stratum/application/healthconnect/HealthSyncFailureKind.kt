package com.stratum.application.healthconnect

import com.stratum.domain.healthconnect.HealthFailureKind

/**
 * Agnostic classification of a Health Connect sync failure at the application port.
 *
 * Placement note (CODE_STYLE_GOSPEL §11): this type is owned by the application
 * layer, not the domain. Infrastructure adapters translate vendor SDK outcomes
 * into this classification at the port so vendor types never cross the boundary;
 * the domain stays free of vendor failure states (STR-266; see the STR-265
 * separation map failure section).
 *
 * This kind is the classification VALUE carried on the sync port's failure
 * branches (STR-243 remediation, Sean's E1 ruling): the former
 * `HealthSyncFailure` Throwable no longer exists — a failure's cause rides
 * the branch only as a [HealthSyncDiagnostic] value where a catch site used it.
 */
enum class HealthSyncFailureKind {
    /**
     * Retrying unchanged cannot succeed and no user action fixes it
     * (e.g. the SDK is not available on this device).
     */
    Terminal,

    /**
     * Cannot succeed until the user acts (e.g. re-grants permission),
     * so also not retryable without user intervention.
     */
    UserActionable,

    /** May succeed on retry. */
    Transient,
}

/** User-visible wording for a [HealthSyncFailureKind.Terminal] unavailable failure. */
const val HEALTH_SYNC_UNAVAILABLE_MESSAGE =
    "Health Connect SDK is not available on this device"

/** User-visible wording for a [HealthSyncFailureKind.UserActionable] permission failure. */
const val HEALTH_SYNC_PERMISSION_DENIED_MESSAGE =
    "Health Connect write permission is not granted"

/**
 * Maps the application-port sync classification to the agnostic domain kind that
 * persists as the sticky sync error (STR-268). The only Terminal source today is
 * the unavailable failure and the only UserActionable source is the
 * permission-denied failure, so the mapping is lossless against today's texts.
 */
fun HealthSyncFailureKind.toHealthFailureKind(): HealthFailureKind =
    when (this) {
        HealthSyncFailureKind.Terminal -> HealthFailureKind.Unavailable
        HealthSyncFailureKind.UserActionable -> HealthFailureKind.PermissionDenied
        HealthSyncFailureKind.Transient -> HealthFailureKind.Unknown
    }

/**
 * Retry decision for sync work: true when the failure is terminal or only
 * fixable by user action (retrying unchanged cannot succeed).
 */
fun HealthSyncFailureKind.isTerminal(): Boolean =
    this == HealthSyncFailureKind.Terminal || this == HealthSyncFailureKind.UserActionable
