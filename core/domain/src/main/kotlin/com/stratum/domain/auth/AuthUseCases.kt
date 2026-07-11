package com.stratum.domain.auth

import com.stratum.domain.sync.EndSessionAndWipeUseCase
import com.stratum.domain.sync.StartCloudSyncUseCase
import com.stratum.domain.sync.StopCloudSyncUseCase
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
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

/**
 * Ends the local sync session (atomic stop + Room wipe when signed in), then signs out of
 * Firebase. On failure after teardown begins, re-starts sync for the still-authenticated user
 * so the shell can recover. [CancellationException] is rethrown (not wrapped in Result).
 */
class LogoutUseCase @Inject constructor(
    private val authManager: AuthManager,
    private val endSessionAndWipe: EndSessionAndWipeUseCase,
    private val stopCloudSync: StopCloudSyncUseCase,
    private val startCloudSync: StartCloudSyncUseCase,
) {
    suspend operator fun invoke(): Result<Unit> {
        val uid = (authManager.authState.value as? AuthState.Authenticated)?.user?.uid
        return try {
            if (uid != null) {
                endSessionAndWipe(uid).getOrThrow()
            } else {
                stopCloudSync()
            }
            authManager.signOut().getOrThrow()
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            if (uid != null && authManager.authState.value is AuthState.Authenticated) {
                runCatching { startCloudSync(uid) }
            }
            Result.failure(error)
        }
    }
}
