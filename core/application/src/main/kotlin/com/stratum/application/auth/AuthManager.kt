package com.stratum.application.auth

import kotlinx.coroutines.flow.StateFlow
import com.stratum.domain.auth.AuthState
import com.stratum.domain.auth.UserProfile

interface AuthManager {
    val authState: StateFlow<AuthState>

    suspend fun signInWithGoogle(): Result<UserProfile>

    suspend fun signOut(): Result<Unit>
}
