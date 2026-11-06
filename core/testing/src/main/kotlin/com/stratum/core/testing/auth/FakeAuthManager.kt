package com.stratum.core.testing.auth

import com.stratum.application.auth.AuthManager
import com.stratum.domain.auth.AuthState
import com.stratum.domain.auth.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeAuthManager(
    initial: AuthState = AuthState.Unauthenticated,
) : AuthManager {
    private val state = MutableStateFlow(initial)
    override val authState: StateFlow<AuthState> = state

    var signOutCount: Int = 0
        private set
    /** When non-null, [signOut] throws this instead of succeeding. */
    var signOutFailure: Throwable? = null
    /** When non-null, [signInWithGoogle] throws this instead of signing in. */
    var signInFailure: Throwable? = null

    fun setState(next: AuthState) {
        state.value = next
    }

    override suspend fun signInWithGoogle(): UserProfile {
        signInFailure?.let { throw it }
        val user = UserProfile("uid-test", "Test User", "test@example.com", null)
        state.value = AuthState.Authenticated(user)
        return user
    }

    override suspend fun signOut() {
        signOutCount++
        signOutFailure?.let { throw it }
        state.value = AuthState.Unauthenticated
    }
}
