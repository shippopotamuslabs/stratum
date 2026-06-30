package com.stratum.core.inference

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.program.generation.swap.LiftSwapPort
import com.stratum.domain.program.generation.swap.LiftSwapRequest
import com.stratum.domain.program.generation.swap.LiftSwapResult
import com.stratum.domain.program.generation.swap.SwapRequest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Thin Firebase Callable client for the `swapProgramLifts` endpoint. Sends the bounded lift-swap
 * review payload and parses structured swap suggestions for the domain pipeline to apply.
 */
@Singleton
class FirebaseLiftSwapAdapter @Inject constructor(
    private val loggingPort: LoggingPort,
) : LiftSwapPort {
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    override suspend fun swapLifts(request: LiftSwapRequest): Result<LiftSwapResult> =
        runCatching {
            requireAuth()
            val callable =
                functions
                    .getHttpsCallable(CALLABLE_NAME)
                    .withTimeout(CALLABLE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            val payload = encodeRequest(request)
            val result = callable.call(payload).await()
            parseLiftSwapResponse(result.data)
        }.onFailure { error ->
            FirebaseCallableErrorLogger.logFailure(loggingPort, TAG, CALLABLE_NAME, error)
        }

    private fun requireAuth() {
        checkNotNull(auth.currentUser) { "Sign-in required for lift swap." }
    }

    private fun encodeRequest(request: LiftSwapRequest): Map<String, Any?> =
        mapOf("reviewPayload" to FirebaseCallablePayloadEncoder.encodeSwapReviewPayload(request.reviewPayload))

    private companion object {
        private const val TAG = "StratumLiftSwap"
        const val CALLABLE_NAME = "swapProgramLifts"
        const val CALLABLE_TIMEOUT_SECONDS = 120L
    }
}

@Suppress("UNCHECKED_CAST")
internal fun parseLiftSwapResponse(data: Any?): LiftSwapResult {
    val map = data as? Map<*, *> ?: error("Invalid swapProgramLifts response")
    val swapsRaw = map["swaps"] as? List<*> ?: return LiftSwapResult(swaps = emptyList())
    val swaps =
        swapsRaw.mapNotNull { entry ->
            val row = entry as? Map<*, *> ?: return@mapNotNull null
            val workoutPosition = (row["workoutPosition"] as? Number)?.toInt() ?: return@mapNotNull null
            val liftPosition = (row["liftPosition"] as? Number)?.toInt() ?: return@mapNotNull null
            val newLiftId = row["newLiftId"] as? String ?: return@mapNotNull null
            SwapRequest(
                workoutPosition = workoutPosition,
                liftPosition = liftPosition,
                newLiftId = newLiftId,
            )
        }
    return LiftSwapResult(swaps = swaps)
}
