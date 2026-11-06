package com.stratum.application.auth

import kotlinx.coroutines.flow.StateFlow
import com.stratum.domain.auth.AuthState

/**
 * Firebase Auth boundary (STR-243 remediation, Sean's E1 ruling). Operations
 * return their sealed outcomes: no exception crosses this port in place of an
 * outcome (coroutine cancellation still propagates). The adapter logs an
 * operational failure once at its own boundary before returning the failure
 * branch, and classifies sign-in failures to
 * [com.stratum.domain.auth.SignInFailureKind] — the single classification
 * source for every consumer.
 */
interface AuthManager {
    val authState: StateFlow<AuthState>

    suspend fun signInWithGoogle(): LoginWithGoogleResult

    suspend fun signOut(): SignOutOutcome
}

/** Outcome of a Firebase sign-out attempt at the [AuthManager] port. */
sealed interface SignOutOutcome {
    data object Success : SignOutOutcome

    /** Sign-out failed; the adapter already logged the failure once. */
    data object Failed : SignOutOutcome
}
