package com.stratum.application.healthconnect

/**
 * Agnostic classification of a Health Connect sync failure at the application port.
 *
 * Placement note (CODE_STYLE_GOSPEL §11): this type is owned by the application
 * layer, not the domain. Infrastructure adapters translate vendor SDK outcomes
 * into this classification at the port so vendor types never cross the boundary;
 * the domain stays free of vendor failure states (STR-266; see the STR-265
 * separation map failure section).
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

/**
 * Application-port failure for Health Connect sync work.
 *
 * Product-language (no `HealthConnect` vendor prefix in the type name); the
 * message strings keep the exact user-visible wording defined here via the
 * [unavailable] and [permissionDenied] factories.
 */
class HealthSyncFailure(
    val kind: HealthSyncFailureKind,
    message: String? = null,
    cause: Throwable? = null,
) : IllegalStateException(message, cause) {
    companion object {
        fun unavailable(cause: Throwable? = null): HealthSyncFailure =
            HealthSyncFailure(
                HealthSyncFailureKind.Terminal,
                "Health Connect SDK is not available on this device",
                cause,
            )

        fun permissionDenied(cause: Throwable? = null): HealthSyncFailure =
            HealthSyncFailure(
                HealthSyncFailureKind.UserActionable,
                "Health Connect write permission is not granted",
                cause,
            )
    }
}

/**
 * Retry decision for sync work: true when the failure is terminal or only
 * fixable by user action. Unclassified throwables are transient (false).
 */
fun Throwable.isTerminalHealthSyncFailure(): Boolean =
    (this as? HealthSyncFailure)?.kind == HealthSyncFailureKind.Terminal ||
        (this as? HealthSyncFailure)?.kind == HealthSyncFailureKind.UserActionable
