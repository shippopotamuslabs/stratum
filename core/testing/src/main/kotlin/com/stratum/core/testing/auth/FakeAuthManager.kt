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
     * [signInFailureMessage] instead.
     */
    var signInFailure: Throwable? = null

    /** Classified sign-in failure kind for [signInWithGoogle]; null = succeed. */
    var signInFailureKind: SignInFailureKind? = null

    /** Message carried alongside [signInFailureKind]. */
    var signInFailureMessage: String? = null

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
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = "StratumGoogleAuth",
                        message = "Google sign-in failed: ${failure.message}",
                        cause = failure,
                    ),
                ),
            )
        }
        val classifiedKind = signInFailureKind
        if (classifiedKind != null) {
            state.value = AuthState.Error(classifiedKind, signInFailureMessage ?: "")
            return LoginWithGoogleResult.Failed(classifiedKind, signInFailureMessage)
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
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = "StratumGoogleAuth",
                        message = "Google sign-out failed: ${failure.message}",
                        cause = failure,
                    ),
                ),
            )
        }
        state.value = AuthState.Unauthenticated
        return SignOutOutcome.Success
    }
}

