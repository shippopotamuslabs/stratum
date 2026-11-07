package com.stratum.core.testing.auth

import com.stratum.application.auth.AuthManager
import com.stratum.application.auth.LoginWithGoogleResult
import com.stratum.application.auth.SignOutOutcome
import com.stratum.application.diagnostics.OperationalDiagnostic
import com.stratum.domain.auth.AuthState
import com.stratum.domain.auth.SignInFailureKind
import com.stratum.domain.auth.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeAuthManager(
    initial: AuthState = AuthState.Unauthenticated,
) : AuthManager {
    private val state = MutableStateFlow(initial)
    override val authState: StateFlow<AuthState> = state

    var signOutCount: Int = 0
        private set

    /**
     * When non-null, [signOut] returns [SignOutOutcome.Failed] instead of
     * succeeding. A [CancellationException] still throws — cancellation is
     * never an outcome branch.
     */
    var signOutFailure: Throwable? = null

    /**
     * When non-null, [signInWithGoogle] returns the failure branch instead of
     * signing in: the throwable maps to [SignInFailureKind.Other] with its
     * message. A [CancellationException] still throws — cancellation is never
     * an outcome branch. For a classified failure, set [signInFailureKind] /
     * [signInFailureMessage] / [signInFailureCause] instead.
     */
    var signInFailure: Throwable? = null

    /** Classified sign-in failure kind for [signInWithGoogle]; null = succeed. */
    var signInFailureKind: SignInFailureKind? = null

    /** Message carried alongside [signInFailureKind]. */
    var signInFailureMessage: String? = null

    /**
     * The classified vendor exception the classified failure in
     * [signInFailureKind] stands for — the fake's analogue of the real
     * throwable the production adapter holds when it classifies a failure
     * (Thermos round 1: [LoginWithGoogleResult.Failed] diagnostics are
     * non-empty, so a classified failure must carry its cause). Required
     * when [signInFailureKind] is set.
     */
    var signInFailureCause: Throwable? = null

    fun setState(next: AuthState) {
        state.value = next
    }

    override suspend fun signInWithGoogle(): LoginWithGoogleResult {
        val failure = signInFailure
        if (failure != null) {
            if (failure is CancellationException) throw failure
            val message = failure.message
            state.value = AuthState.Error(SignInFailureKind.Other, message ?: "")
            return LoginWithGoogleResult.Failed(
                kind = SignInFailureKind.Other,
                message = message,
                diagnostics = diagnosticsFor(failure, "sign-in"),
            )
        }
        val classifiedKind = signInFailureKind
        if (classifiedKind != null) {
            val cause = requireNotNull(signInFailureCause) {
                "signInFailureKind requires signInFailureCause: the classified " +
                    "vendor exception the failure carries as its diagnostic cause."
            }
            state.value = AuthState.Error(classifiedKind, signInFailureMessage ?: "")
            return LoginWithGoogleResult.Failed(
                kind = classifiedKind,
                message = signInFailureMessage,
                diagnostics = diagnosticsFor(cause, "sign-in"),
            )
        }
        val user = UserProfile("uid-test", "Test User", "test@example.com", null)
        state.value = AuthState.Authenticated(user)
        return LoginWithGoogleResult.Success(user)
    }

    override suspend fun signOut(): SignOutOutcome {
        signOutCount++
        val failure = signOutFailure
        if (failure != null) {
            if (failure is CancellationException) throw failure
            return SignOutOutcome.Failed(
                diagnostics = diagnosticsFor(failure, "sign-out"),
            )
        }
        state.value = AuthState.Unauthenticated
        return SignOutOutcome.Success
    }

    /**
     * Mirrors the real adapter's §10 carrier shape for a Google auth
     * failure. The adapter's TAG is private, so the tag literal lives in
     * this one place inside the fake.
     */
    private fun diagnosticsFor(failure: Throwable, operation: String): List<OperationalDiagnostic> =
        listOf(
            OperationalDiagnostic(
                tag = "StratumGoogleAuth",
                message = "Google $operation failed: ${failure.message}",
                cause = failure,
            ),
        )
}

