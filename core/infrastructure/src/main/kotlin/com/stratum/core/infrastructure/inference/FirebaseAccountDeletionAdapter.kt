package com.stratum.core.infrastructure.inference

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.stratum.application.auth.AccountDeletionOutcome
import com.stratum.application.auth.AccountDeletionPort
import com.stratum.core.infrastructure.logging.LoggingPort
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Thin Firebase Callable client for the `deleteAccount` endpoint.
 * Uses `phase=data` then `phase=auth` so local Room wipe can run while Auth is still valid.
 */
@Singleton
class FirebaseAccountDeletionAdapter @Inject constructor(
    private val loggingPort: LoggingPort,
) : AccountDeletionPort {
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    override suspend fun deleteRemoteUserData(): AccountDeletionOutcome =
        callPhase(PHASE_DATA)

    override suspend fun deleteRemoteAuthUser(): AccountDeletionOutcome =
        callPhase(PHASE_AUTH)

    private suspend fun callPhase(phase: String): AccountDeletionOutcome {
        try {
            checkNotNull(auth.currentUser) { "Sign-in required to delete account." }
            val callable =
                functions
                    .getHttpsCallable(CALLABLE_NAME)
                    .withTimeout(CALLABLE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            callable.call(mapOf("phase" to phase)).await()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            FirebaseCallableErrorLogger.logFailure(
                loggingPort,
                TAG,
                "$CALLABLE_NAME($phase)",
                error,
            )
            return AccountDeletionOutcome.Failed
        }
        return AccountDeletionOutcome.Success
    }

    private companion object {
        private const val TAG = "StratumDeleteAccount"
        const val CALLABLE_NAME = "deleteAccount"
        const val PHASE_DATA = "data"
        const val PHASE_AUTH = "auth"
        const val CALLABLE_TIMEOUT_SECONDS = 120L
    }
}
