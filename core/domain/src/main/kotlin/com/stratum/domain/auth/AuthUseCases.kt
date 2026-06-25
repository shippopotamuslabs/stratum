package com.stratum.domain.auth

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class GetAuthStateUseCase @Inject constructor(
    private val authManager: AuthManager,
) {
    operator fun invoke(): Flow<AuthState> = authManager.authState
}

class LoginWithGoogleUseCase @Inject constructor(
    private val authManager: AuthManager,
) {
    suspend operator fun invoke(): Result<UserProfile> = authManager.signInWithGoogle()
}

class LogoutUseCase @Inject constructor(
    private val authManager: AuthManager,
) {
    suspend operator fun invoke(): Result<Unit> = authManager.signOut()
}
