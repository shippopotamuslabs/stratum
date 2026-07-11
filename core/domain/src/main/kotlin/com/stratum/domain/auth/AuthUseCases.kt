package com.stratum.domain.auth

import com.stratum.domain.sync.EndSessionAndWipeUseCase
import com.stratum.domain.sync.StartCloudSyncUseCase
import com.stratum.domain.sync.StopCloudSyncUseCase
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

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

/**
 * Cloud data was deleted, but local Room wipe failed. The Auth user is intentionally still
 * present so the user can see guidance and clear app storage or retry.
 */
class LocalCleanupAfterAccountDeletionException(
    cause: Throwable? = null,
) : Exception(
        "Cloud account data was deleted, but this device could not clear local data.",
        cause,
    )

/**
 * Local wipe succeeded and cloud data is gone, but Firebase Auth user removal failed.
 * Auth is left intact so the user can retry deletion without re-uploading wiped cloud data.
 */
class AuthDeleteAfterAccountWipeException(
    cause: Throwable? = null,
) : Exception(
        "Local and cloud training data were removed, but the account could not be fully closed.",
        cause,
    )

/**
 * Permanently deletes the signed-in user's account:
 * 1. Stop cloud sync (blocks Room re-upload during wipe)
 * 2. Cloud Firestore + Storage (Auth still valid — Settings stays mounted)
 * 3. Local Room wipe
 * 4. Firebase Auth user + best-effort credential clear
 *
 * Sync is stopped before remote data so an in-flight [CloudSyncPort.requestSync] cannot resurrect
 * Firestore rows after Admin recursive delete. Cloud data is deleted before local wipe so a network
 * failure leaves local data for retry. Auth is deleted only after local wipe so a local-cleanup
 * failure can surface as [LocalCleanupAfterAccountDeletionException] without unmounting Settings.
 * Local wipe / Auth steps run under [NonCancellable]. [signOut] after Auth delete is best-effort.
 */
class DeleteAccountUseCase @Inject constructor(
    private val authManager: AuthManager,
    private val accountDeletionPort: AccountDeletionPort,
    private val stopCloudSync: StopCloudSyncUseCase,
    private val startCloudSync: StartCloudSyncUseCase,
    private val endSessionAndWipe: EndSessionAndWipeUseCase,
) {
    suspend operator fun invoke(): Result<Unit> {
        val uid = (authManager.authState.value as? AuthState.Authenticated)?.user?.uid
            ?: return Result.failure(IllegalStateException("Not signed in."))
        var remoteDataDeleted = false
        return try {
            // Freeze uploads before Admin cascade so WorkManager cannot re-push Room residuals.
            stopCloudSync()
            accountDeletionPort.deleteRemoteUserData().getOrThrow()
            remoteDataDeleted = true
            withContext(NonCancellable) {
                wipeLocalOrThrow(uid)
                deleteAuthOrThrow()
                // Auth user is already gone; clear local session/credentials if possible.
                runCatching { authManager.signOut().getOrThrow() }
            }
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            // Restore sync only when cloud data was not removed (retryable pre-cascade failure).
            if (
                !remoteDataDeleted &&
                authManager.authState.value is AuthState.Authenticated
            ) {
                runCatching { startCloudSync(uid) }
            }
            Result.failure(error)
        }
    }

    private suspend fun wipeLocalOrThrow(uid: String) {
        endSessionAndWipe(uid).getOrElse { wipeError ->
            throw LocalCleanupAfterAccountDeletionException(wipeError)
        }
    }

    private suspend fun deleteAuthOrThrow() {
        accountDeletionPort.deleteRemoteAuthUser().getOrElse { error ->
            if (error is CancellationException) throw error
            throw AuthDeleteAfterAccountWipeException(error)
        }
    }
}
