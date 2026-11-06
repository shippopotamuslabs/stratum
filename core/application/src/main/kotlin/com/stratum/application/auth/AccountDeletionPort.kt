package com.stratum.application.auth

/**
 * Server-side cascade delete for the signed-in user's cloud account.
 * Implemented via Firebase Callable (Admin SDK) — clients cannot hard-delete Firestore sync docs.
 *
 * Split into data vs Auth so the client can wipe Room while the Auth session is still valid
 * (Settings stays mounted and can surface local-cleanup failure messaging).
 *
 * Operations return [AccountDeletionOutcome] (STR-243 remediation, Sean's E1
 * ruling): no exception crosses this port in place of an outcome (coroutine
 * cancellation still propagates). The adapter logs an operational failure
 * once at its own boundary (FirebaseCallableErrorLogger) before returning
 * [AccountDeletionOutcome.Failed], so the branch needs no diagnostic payload
 * for a second drain.
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

    /** The phase failed; the adapter already logged it exactly once. */
    data object Failed : AccountDeletionOutcome
}
