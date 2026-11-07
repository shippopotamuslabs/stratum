package com.stratum.application.auth

import kotlinx.coroutines.flow.StateFlow
import com.stratum.application.diagnostics.OperationalDiagnostic
import com.stratum.domain.auth.AuthState
import com.stratum.domain.auth.SignInFailureKind
import com.stratum.domain.auth.UserProfile

/**
 * Firebase Auth boundary (STR-243 remediation, Sean's E1 ruling). Operations
 * return their sealed outcomes: no exception crosses this port in place of an
 * outcome (coroutine cancellation still propagates). A failure branch
 * carries the failure as §10 [OperationalDiagnostic]s (STR-238): the adapter
 * records nothing, and the terminal boundary (Main shell, Settings) drains
 * the diagnostics exactly once. The adapter classifies sign-in failures to
 * [com.stratum.domain.auth.SignInFailureKind] — the single classification
 * source for every consumer.
 */
interface AuthManager {
    val authState: StateFlow<AuthState>

    suspend fun signInWithGoogle(): LoginWithGoogleResult

    suspend fun signOut(): SignOutOutcome
}

/**
 * Outcome of a Google sign-in attempt (STR-243). [Failed] carries the
 * adapter's [SignInFailureKind] classification plus the originating message
 * for unclassified failures (the shipped message-carrying pattern, cf.
 * `BillingPurchaseResult.Failed`), and — STR-238 — the failure as §10
 * [diagnostics] carrying the real cause for the terminal boundary's
 * exactly-once drain. [Failed.diagnostics] is non-empty by construction
 * (Thermos round 1): a failure branch that carried no diagnostic would
 * drain to zero records at the boundary.
 */
sealed interface LoginWithGoogleResult {
    data class Success(val profile: UserProfile) : LoginWithGoogleResult

    data class Failed(
        val kind: SignInFailureKind,
        val message: String?,
        val diagnostics: List<OperationalDiagnostic>,
    ) : LoginWithGoogleResult {
        init {
            require(diagnostics.isNotEmpty()) {
                "LoginWithGoogleResult.Failed requires at least one diagnostic."
            }
        }
    }
}

/** Outcome of a Firebase sign-out attempt at the [AuthManager] port. */
sealed interface SignOutOutcome {
    data object Success : SignOutOutcome

    /**
     * Sign-out failed. [diagnostics] carries the real cause as §10
     * diagnostics for the terminal boundary's exactly-once drain
     * (STR-238); the adapter records nothing.
     */
    data class Failed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : SignOutOutcome {
        init {
            require(diagnostics.isNotEmpty()) {
                "SignOutOutcome.Failed requires at least one diagnostic."
            }
        }
    }
}
