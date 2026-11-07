package com.stratum.core.infrastructure.inference

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.stratum.application.auth.AccountDeletionOutcome
import com.stratum.application.auth.AccountDeletionPort
import com.stratum.application.diagnostics.OperationalDiagnostic
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Thin Firebase Callable client for the `deleteAccount` endpoint.
 * Uses `phase=data` then `phase=auth` so local Room wipe can run while Auth is still valid.
 *
 * A failed phase is returned as [AccountDeletionOutcome.Failed] carrying the
 * real cause as a §10 diagnostic (STR-238): this adapter records nothing —
 * the terminal boundary (Settings) drains the diagnostic exactly once, with
 * the message rendered in the shape the adapter's former log line used.
 */
@Singleton
class FirebaseAccountDeletionAdapter @Inject constructor(
    private val functions: FirebaseFunctions,
    private val auth: FirebaseAuth,
) : AccountDeletionPort {
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
            return AccountDeletionOutcome.Failed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = FirebaseCallableErrorLogger.failureMessage(
                            "$CALLABLE_NAME($phase)",
                            error,
                        ),
                        cause = error,
                    ),
                ),
            )
        }
        return AccountDeletionOutcome.Success
    }

    internal companion object {
        const val TAG = "StratumDeleteAccount"
        const val CALLABLE_NAME = "deleteAccount"
        const val PHASE_DATA = "data"
        const val PHASE_AUTH = "auth"
        const val CALLABLE_TIMEOUT_SECONDS = 120L
    }
}
