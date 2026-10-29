package com.stratum.application.auth

/**
 * Server-side cascade delete for the signed-in user's cloud account.
 * Implemented via Firebase Callable (Admin SDK) — clients cannot hard-delete Firestore sync docs.
 *
 * Split into data vs Auth so the client can wipe Room while the Auth session is still valid
 * (Settings stays mounted and can surface local-cleanup failure messaging).
 */
interface AccountDeletionPort {
    /**
     * Deletes the Firestore user tree (all subcollections under users/uid) and related
     * Storage prefixes. Must be called while still authenticated.
     */
    suspend fun deleteRemoteUserData(): Result<Unit>

    /**
     * Deletes the Firebase Auth user for the current session (Admin).
     * Must be called while still authenticated; call after local Room wipe succeeds.
     */
    suspend fun deleteRemoteAuthUser(): Result<Unit>
}
