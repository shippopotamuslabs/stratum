package com.stratum.core.inference

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.stratum.domain.importing.CustomLiftInferencePort
import com.stratum.domain.importing.ImportStubLiftGuesser
import com.stratum.domain.importing.InferredCustomLiftMetadata
import com.stratum.domain.importing.InferredEquipmentLoading
import com.stratum.domain.importing.UnmatchedExerciseCandidate
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.SeedEquipmentIds
import com.stratum.domain.logging.LoggingPort
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Firebase Callable client for `inferCustomLifts`.
 *
 * Decodes the **server-normalized** payload (`loading: { type, ... }` + movementPattern).
 * Primary/secondary muscles are always derived from pattern via [ImportStubLiftGuesser]
 * (LLM free-form muscle tags are ignored). Unknown loading becomes the shared Default lattice.
 */
@Singleton
class FirebaseCustomLiftInferenceAdapter @Inject constructor(
    private val loggingPort: LoggingPort,
) : CustomLiftInferencePort {
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    override suspend fun inferMetadata(
        candidates: List<UnmatchedExerciseCandidate>,
    ): Result<List<InferredCustomLiftMetadata>> = runCatching {
        requireAuth()
        val callable =
            functions
                .getHttpsCallable("inferCustomLifts")
                .withTimeout(CALLABLE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val payload =
            mapOf(
                "exercises" to
                    candidates.map { candidate ->
                        mapOf(
                            "name" to candidate.exerciseNameRaw,
                            "loggedSetCount" to candidate.loggedSetCount,
                        )
                    },
            )
        val result = callable.call(payload).await()
        parseResponse(result.data)
    }.onFailure { error ->
        FirebaseCallableErrorLogger.logFailure(loggingPort, TAG, CALLABLE_NAME, error)
    }

    private fun requireAuth() {
        checkNotNull(auth.currentUser) { "Sign-in required for custom lift inference." }
    }

    internal companion object {
        const val TAG = "StratumCustomLiftInference"
        const val CALLABLE_NAME = "inferCustomLifts"
        const val CALLABLE_TIMEOUT_SECONDS = 300L

        @Suppress("UNCHECKED_CAST")
        internal fun parseResponse(data: Any?): List<InferredCustomLiftMetadata> {
            val map = data as? Map<*, *> ?: error("Invalid inferCustomLifts response.")
            val lifts =
                map["lifts"] as? List<*> ?: error("Missing lifts in inferCustomLifts response.")
            return lifts.mapNotNull { entry -> parseLift(entry as? Map<*, *>) }
        }

        private fun parseLift(entry: Map<*, *>?): InferredCustomLiftMetadata? {
            if (entry == null) return null
            val name = entry["name"] as? String ?: return null
            val movementPattern = parseMovementPattern(entry["movementPattern"] as? String)
                ?: return null
            return ImportStubLiftGuesser.metadataFromPattern(
                exerciseNameRaw = name,
                movementPattern = movementPattern,
                loading = parseCallableLoading(entry["loading"]),
            )
        }

        /**
         * Decodes server `loading` objects produced by `parseInferredCustomLifts`.
         * Malformed / missing → shared Default equipment (+5 lb).
         */
        private fun parseCallableLoading(raw: Any?): InferredEquipmentLoading {
            val loading = raw as? Map<*, *> ?: return InferredEquipmentLoading.defaultEquipment()
            return when ((loading["type"] as? String)?.trim()) {
                "seed" -> {
                    val wireId = (loading["equipmentId"] as? String)?.trim().orEmpty()
                    SeedEquipmentIds.fromWireId(wireId)?.let { InferredEquipmentLoading.Seed(it) }
                        ?: InferredEquipmentLoading.defaultEquipment()
                }
                "custom" -> {
                    val rawName = (loading["name"] as? String)?.trim().orEmpty()
                    val name =
                        rawName.ifEmpty { InferredEquipmentLoading.DEFAULT_EQUIPMENT_NAME }
                    val increment =
                        InferredEquipmentLoading.sanitizeIncrementLb(
                            parseNumber(loading["incrementLb"]),
                        )
                    InferredEquipmentLoading.Custom(name = name, incrementLb = increment)
                }
                else -> InferredEquipmentLoading.defaultEquipment()
            }
        }

        private fun parseNumber(raw: Any?): Double? =
            when (raw) {
                is Number -> raw.toDouble()
                is String -> raw.toDoubleOrNull()
                else -> null
            }

        private fun parseMovementPattern(value: String?): MovementPattern? =
            value?.let { runCatching { MovementPattern.valueOf(it) }.getOrNull() }
    }
}
