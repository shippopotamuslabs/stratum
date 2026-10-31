package com.stratum.domain.subscription

/**
 * Diagnostic value for a degraded-but-successful entitlement sync (diagnostics as
 * values, CODE_STYLE_RULES § "Partial success"). The repository returns these on
 * its diagnostics flow instead of logging them; the app-shell boundary drains
 * each one through `LoggingPort` exactly once. Diagnostics never reach the UI.
 * The Play purchase token is deliberately not carried: it must never be logged.
 */
sealed interface EntitlementDiagnostic {
    /** The sync failure that left server-side entitlement unconfirmed. */
    val cause: Throwable

    /**
     * Fire-and-forget Play entitlement sync of an active purchase token failed;
     * local entitlement is unaffected.
     */
    data class PlaySyncFailed(
        override val cause: Throwable,
    ) : EntitlementDiagnostic
}
