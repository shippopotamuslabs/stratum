package com.stratum.application.auth

import com.stratum.application.diagnostics.OperationalDiagnostic

/**
 * Server-side cascade delete for the signed-in user's cloud account.
 * Implemented via Firebase Callable (Admin SDK) — clients cannot hard-delete Firestore sync docs.
 *
 * Split into data vs Auth so the client can wipe Room while the Auth session is still valid
 * (Settings stays mounted and can surface local-cleanup failure messaging).
 *
 * Operations return [AccountDeletionOutcome] (STR-243 remediation, Sean's E1
 * ruling): no exception crosses this port in place of an outcome (coroutine
 * cancellation still propagates). A failed phase returns
 * [AccountDeletionOutcome.Failed] carrying the failure as §10
 * [OperationalDiagnostic]s (STR-238): the adapter records nothing, and the
 * terminal boundary (Settings) drains the diagnostics exactly once.
 */
interface AccountDeletionPort {
    /**
     * Deletes the Firestore user tree (all subcollections under users/uid) and related
     * Storage prefixes. Must be called while still authenticated.
     */
    suspend fun deleteRemoteUserData(): AccountDeletionOutcome

    /**
     * Deletes the Firebase Auth user for the current session (Admin).
     * Must be called while still authenticated; call after local Room wipe succeeds.
     */
    suspend fun deleteRemoteAuthUser(): AccountDeletionOutcome
}

/** Outcome of an account-deletion phase at the [AccountDeletionPort]. */
sealed interface AccountDeletionOutcome {
    data object Success : AccountDeletionOutcome

    /**
     * The phase failed. [diagnostics] carries the real cause as §10
     * diagnostics for the terminal boundary's exactly-once drain
     * (STR-238); the adapter records nothing.
     */
    data class Failed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : AccountDeletionOutcome {
        init {
            require(diagnostics.isNotEmpty()) {
                "AccountDeletionOutcome.Failed requires at least one diagnostic."
            }
        }
    }
}
