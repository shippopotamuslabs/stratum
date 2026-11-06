package com.stratum.application.auth

import kotlinx.coroutines.flow.StateFlow
import com.stratum.domain.auth.AuthState
import com.stratum.domain.auth.UserProfile

/**
 * Firebase Auth boundary (STR-243). Operations are plain suspend calls: an
 * operational failure throws to the caller after the adapter has logged it
 * once at its own boundary — no raw-`Result` failure payload crosses the port.
 * Sign-in failures throw the application-owned [GoogleSignInException]
 * classification; sign-out failures throw the originating throwable.
 */
interface AuthManager {
    val authState: StateFlow<AuthState>

    suspend fun signInWithGoogle(): UserProfile

    suspend fun signOut()
}
