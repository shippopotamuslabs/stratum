package com.stratum.core.inference

import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.stratum.domain.lift.IncrementScanPort
import com.stratum.domain.lift.ScanPhoto
import com.stratum.domain.lift.ScannedTiers
import com.stratum.domain.logging.LoggingPort
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Firebase Callable client for `inferEquipmentIncrements`.
 *
 * Sends the stack-label photo inline as base64 and decodes the server-sanitized
 * payload via [IncrementScanResponseParser] (tolerant: unknown unit falls back to Lb,
 * malformed tier rows and micro stops are dropped, but a structurally invalid payload
 * fails the returned [Result], never throws past it — except coroutine cancellation,
 * which is always rethrown).
 */
@Singleton
class FirebaseIncrementScanAdapter @Inject constructor(
    private val loggingPort: LoggingPort,
    private val functions: FirebaseFunctions,
    private val auth: FirebaseAuth,
) : IncrementScanPort {
    override suspend fun scanTiers(photo: ScanPhoto): Result<ScannedTiers> =
        runCatching {
            requireAuth()
            val callable =
                functions
                    .getHttpsCallable(CALLABLE_NAME)
                    .withTimeout(CALLABLE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            val payload =
                mapOf(
                    "imageBase64" to Base64.encodeToString(photo.bytes, Base64.NO_WRAP),
                    "mimeType" to photo.mimeType,
                )
            val result = callable.call(payload).await()
            parseResponse(result.data)
        }.onFailure { error ->
            if (error is CancellationException) throw error
            FirebaseCallableErrorLogger.logFailure(loggingPort, TAG, CALLABLE_NAME, error)
        }

    private fun requireAuth() {
        checkNotNull(auth.currentUser) { "Sign-in required for increment scan." }
    }

    internal companion object {
        const val TAG = "StratumIncrementScan"
        const val CALLABLE_NAME = "inferEquipmentIncrements"
        const val CALLABLE_TIMEOUT_SECONDS = 120L

        internal fun parseResponse(data: Any?): ScannedTiers =
            IncrementScanResponseParser.parseResponse(data)
    }
}
