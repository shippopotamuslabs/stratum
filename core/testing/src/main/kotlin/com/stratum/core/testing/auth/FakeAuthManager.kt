package com.stratum.core.testing.auth

import com.stratum.domain.auth.AuthManager
import com.stratum.domain.auth.AuthState
import com.stratum.domain.auth.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeAuthManager(
    initial: AuthState = AuthState.Unauthenticated,
) : AuthManager {
    private val state = MutableStateFlow(initial)
    override val authState: StateFlow<AuthState> = state

    override suspend fun signInWithGoogle(): Result<UserProfile> {
        val user = UserProfile("uid-test", "Test User", "test@example.com", null)
        state.value = AuthState.Authenticated(user)
        return Result.success(user)
    }

    override suspend fun signOut(): Result<Unit> {
        state.value = AuthState.Unauthenticated
        return Result.success(Unit)
    }
}
