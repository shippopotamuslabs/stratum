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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

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

    private fun encodeRequest(request: LiftSwapRequest): Map<String, Any?> {
        val review = request.reviewPayload
        val programJson = buildJsonObject {
            put("promptVersion", review.promptVersion)
            putJsonObject("intent") {
                put("trainingAge", review.intent.trainingAge)
                review.intent.sessionLengthMinutes?.let { put("sessionLengthMinutes", it) }
                put("trainingDaysPerWeek", review.intent.trainingDaysPerWeek)
                put("cycleLength", review.intent.cycleLength)
                putJsonObject("specialization") {
                    review.intent.specialization.forEach { (muscle, level) ->
                        put(muscle, level)
                    }
                }
                putJsonArray("specializedMuscles") {
                    review.intent.specializedMuscles.forEach { add(JsonPrimitive(it)) }
                }
            }
            putJsonObject("program") {
                put("cycleLength", review.program.cycleLength)
                putJsonArray("workouts") {
                    review.program.workouts.forEach { workout ->
                        add(
                            buildJsonObject {
                                put("position", workout.position)
                                put("name", workout.name)
                                putJsonArray("workoutLifts") {
                                    workout.workoutLifts.forEach { lift ->
                                        add(
                                            buildJsonObject {
                                                put("position", lift.position)
                                                put("liftId", lift.liftId)
                                                put("name", lift.name)
                                                put("movementPattern", lift.movementPattern)
                                                putJsonArray("primaryMuscles") {
                                                    lift.primaryMuscles.forEach { add(JsonPrimitive(it)) }
                                                }
                                                putJsonArray("secondaryMuscles") {
                                                    lift.secondaryMuscles.forEach { add(JsonPrimitive(it)) }
                                                }
                                                put("setCount", lift.setCount)
                                                lift.repMin?.let { put("repMin", it) }
                                                lift.repMax?.let { put("repMax", it) }
                                                putJsonArray("candidates") {
                                                    lift.candidates.forEach { candidate ->
                                                        add(
                                                            buildJsonObject {
                                                                put("liftId", candidate.liftId)
                                                                put("name", candidate.name)
                                                                put("movementPattern", candidate.movementPattern)
                                                                putJsonArray("primaryMuscles") {
                                                                    candidate.primaryMuscles.forEach {
                                                                        add(JsonPrimitive(it))
                                                                    }
                                                                }
                                                            },
                                                        )
                                                    }
                                                }
                                            },
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
        return mapOf("reviewPayload" to Json.parseToJsonElement(programJson.toString()))
    }

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
