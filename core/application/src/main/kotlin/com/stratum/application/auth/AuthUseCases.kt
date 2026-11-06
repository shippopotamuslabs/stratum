package com.stratum.application.auth

import com.stratum.application.sync.CloudSyncDiagnostic
import com.stratum.application.sync.CloudSyncOutcome
import com.stratum.application.sync.EndSessionAndWipeUseCase
import com.stratum.application.sync.StartCloudSyncUseCase
import com.stratum.application.sync.StopCloudSyncUseCase
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import com.stratum.domain.auth.AuthState
import com.stratum.application.sync.CloudSyncPort

class GetAuthStateUseCase @Inject constructor(
    private val authManager: AuthManager,
) {
    operator fun invoke(): Flow<AuthState> = authManager.authState
}

class LoginWithGoogleUseCase @Inject constructor(
    private val authManager: AuthManager,
) {
    suspend operator fun invoke(): LoginWithGoogleResult =
        authManager.signInWithGoogle()
}

/**
 * Outcome of a logout attempt (STR-243). A failure branch carries the
 * compensating sync restart's failures as [CloudSyncDiagnostic]s (empty when
 * the restart succeeded or was not attempted) so the terminal boundary
 * (Settings, shell) drains each exactly once through its own log wiring —
 * the STR-256 suppressed-throwable mechanism, carried as diagnostics.
 * [WipeFailed] additionally carries the primary wipe failure's own
 * diagnostics (STR-238) so the same boundary drains them exactly once too.
 */
sealed interface LogoutResult {
    data object Success : LogoutResult

    /** The session teardown / Room wipe failed; sign-out was not attempted. */
    data class WipeFailed(
        val restartDiagnostics: List<CloudSyncDiagnostic>,
        val wipeDiagnostics: List<CloudSyncDiagnostic> = emptyList(),
    ) : LogoutResult

    /** The wipe succeeded but Firebase sign-out failed. */
    data class SignOutFailed(
        val restartDiagnostics: List<CloudSyncDiagnostic>,
    ) : LogoutResult
}

/**
 * Ends the local sync session (atomic stop + Room wipe when signed in), then signs out of
 * Firebase. On failure after teardown begins, re-starts sync for the still-authenticated user
 * so the shell can recover. [CancellationException] is rethrown (never captured as a branch).
 */
class LogoutUseCase @Inject constructor(
    private val authManager: AuthManager,
    private val endSessionAndWipe: EndSessionAndWipeUseCase,
    private val stopCloudSync: StopCloudSyncUseCase,
    private val startCloudSync: StartCloudSyncUseCase,
) {
    suspend operator fun invoke(): LogoutResult {
        val uid = (authManager.authState.value as? AuthState.Authenticated)?.user?.uid
        if (uid != null) {
            val wipeOutcome = endSessionAndWipe(uid)
            if (wipeOutcome is CloudSyncOutcome.Failed) {
                val diagnostics = if (authManager.authState.value is AuthState.Authenticated) {
                    startCloudSync.restartDiagnosticsAfterFailure(uid)
                } else {
                    emptyList()
                }
                return LogoutResult.WipeFailed(
                    restartDiagnostics = diagnostics,
                    wipeDiagnostics = wipeOutcome.diagnostics,
                )
            }
        } else {
            stopCloudSync()
        }

        if (authManager.signOut() is SignOutOutcome.Failed) {
            val diagnostics = if (
                uid != null && authManager.authState.value is AuthState.Authenticated
            ) {
                startCloudSync.restartDiagnosticsAfterFailure(uid)
            } else {
                emptyList()
            }
            return LogoutResult.SignOutFailed(diagnostics)
        }
        return LogoutResult.Success
    }
}

/**
 * Outcome of an account-deletion attempt (STR-243). Presentation branches on
 * the kind for its copy: [LocalCleanupFailed] and [AuthDeleteFailed] are the
 * two partial-deletion states that previously crossed as typed exceptions.
 * [RemoteDataFailed] carries the compensating sync restart's failures as
 * [CloudSyncDiagnostic]s for the terminal boundary's exactly-once drain
 * (empty when the restart succeeded or was not attempted).
 * [LocalCleanupFailed] carries the primary wipe failure's own diagnostics
 * (STR-238) for the same boundary's exactly-once drain.
 */
sealed interface DeleteAccountResult {
    data object Success : DeleteAccountResult

    /** No signed-in user; nothing was attempted. */
    data object NotSignedIn : DeleteAccountResult

    /** Cloud Firestore + Storage deletion failed; local data is intact for retry. */
    data class RemoteDataFailed(
        val restartDiagnostics: List<CloudSyncDiagnostic>,
    ) : DeleteAccountResult

    /**
     * Cloud data was deleted, but the local Room wipe failed. The Auth user is
     * intentionally still present so the user can see guidance and clear app
     * storage or retry. [wipeDiagnostics] carries the wipe failure's cause
     * for the terminal boundary's exactly-once drain (STR-238).
     */
    data class LocalCleanupFailed(
        val wipeDiagnostics: List<CloudSyncDiagnostic> = emptyList(),
    ) : DeleteAccountResult

    /**
     * Local wipe succeeded and cloud data is gone, but Firebase Auth user
     * removal failed. Auth is left intact so the user can retry deletion
     * without re-uploading wiped cloud data.
     */
    data object AuthDeleteFailed : DeleteAccountResult
}

/**
 * Best-effort sync restart after a logout/account-deletion failure left sync stopped for a
 * still-authenticated user (STR-256). Returns the restart's failure diagnostics (empty on
 * success), produced by the start use case's dedicated restart seam: the terminal boundary
 * (Settings, shell) drains each exactly once through its own log wiring. The primary
 * outcome branch is never retyped by a restart failure, so callers branching on
 * [LogoutResult] / [DeleteAccountResult] kinds are unaffected. [CancellationException]
 * from the restart propagates (the start use case rethrows it), never rides as a
 * diagnostic.
 */
private suspend fun StartCloudSyncUseCase.restartDiagnosticsAfterFailure(
    uid: String,
): List<CloudSyncDiagnostic> = restartDiagnostics(uid)

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
 * failure can surface as [DeleteAccountResult.LocalCleanupFailed] without unmounting Settings.
 * Local wipe / Auth steps run under [NonCancellable]. [signOut][AuthManager.signOut] after Auth
 * delete is best-effort.
 */
class DeleteAccountUseCase @Inject constructor(
    private val authManager: AuthManager,
    private val accountDeletionPort: AccountDeletionPort,
    private val stopCloudSync: StopCloudSyncUseCase,
    private val startCloudSync: StartCloudSyncUseCase,
    private val endSessionAndWipe: EndSessionAndWipeUseCase,
) {
    suspend operator fun invoke(): DeleteAccountResult {
        val uid = (authManager.authState.value as? AuthState.Authenticated)?.user?.uid
            ?: return DeleteAccountResult.NotSignedIn
        // Freeze uploads before Admin cascade so WorkManager cannot re-push Room residuals.
        stopCloudSync()
        if (accountDeletionPort.deleteRemoteUserData() is AccountDeletionOutcome.Failed) {
            // Runs before the NonCancellable block below, so a restart CancellationException
            // propagates to the caller instead of being swallowed.
            val diagnostics = if (authManager.authState.value is AuthState.Authenticated) {
                startCloudSync.restartDiagnosticsAfterFailure(uid)
            } else {
                emptyList()
            }
            return DeleteAccountResult.RemoteDataFailed(diagnostics)
        }
        return deleteAfterRemoteData(uid)
    }

    /** Local wipe, Auth delete, and best-effort sign-out once cloud data is gone. */
    private suspend fun deleteAfterRemoteData(uid: String): DeleteAccountResult =
        withContext(NonCancellable) {
            val wipeOutcome = endSessionAndWipe(uid)
            if (wipeOutcome is CloudSyncOutcome.Failed) {
                return@withContext DeleteAccountResult.LocalCleanupFailed(
                    wipeDiagnostics = wipeOutcome.diagnostics,
                )
            }
            if (accountDeletionPort.deleteRemoteAuthUser() is AccountDeletionOutcome.Failed) {
                return@withContext DeleteAccountResult.AuthDeleteFailed
            }
            // Auth user is already gone; clear local session/credentials if possible.
            // Best-effort: deletion has already succeeded, so a sign-out failure never
            // retypes the outcome. Sign-out failures are recorded exactly once by the
            // AuthManager adapter (FirebaseGoogleAuthManager.signOut) — that adapter is
            // the single recording owner (handle-exactly-once, CODE-STYLE-GOSPEL §11),
            // so the outcome is simply consumed here. A CancellationException from
            // the port still propagates (it is never an outcome branch).
            authManager.signOut()
            DeleteAccountResult.Success
        }
}
