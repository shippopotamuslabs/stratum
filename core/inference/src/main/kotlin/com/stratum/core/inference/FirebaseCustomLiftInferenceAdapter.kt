package com.stratum.core.inference

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.stratum.domain.importing.CustomLiftInferencePort
import com.stratum.domain.importing.CustomLiftMusclePolicy
import com.stratum.domain.importing.InferredCustomLiftMetadata
import com.stratum.domain.importing.UnmatchedExerciseCandidate
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

@Singleton
class FirebaseCustomLiftInferenceAdapter @Inject constructor() : CustomLiftInferencePort {
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
        runCatching {
            Log.w(
                TAG,
                "inferCustomLifts failed for ${candidates.size} exercises: ${error.message}",
                error,
            )
        }
    }

    private companion object {
        const val TAG = "StratumCustomLiftInference"
        const val CALLABLE_TIMEOUT_SECONDS = 300L
    }

    private fun requireAuth() {
        checkNotNull(auth.currentUser) { "Sign-in required for custom lift inference." }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseResponse(data: Any?): List<InferredCustomLiftMetadata> {
        val map = data as? Map<*, *> ?: error("Invalid inferCustomLifts response.")
        val lifts = map["lifts"] as? List<*> ?: error("Missing lifts in inferCustomLifts response.")
        return lifts.mapNotNull { entry -> parseLift(entry as? Map<*, *>) }
    }

    private fun parseLift(entry: Map<*, *>?): InferredCustomLiftMetadata? {
        if (entry == null) return null
        val name = entry["name"] as? String
        val movementPattern = parseMovementPattern(entry["movementPattern"] as? String)
        val primary = parseMuscleGroups(entry["primaryMuscles"], maxItems = 2)
        if (name == null || movementPattern == null || primary.isEmpty()) {
            return null
        }
        val secondary =
            parseMuscleGroups(entry["secondaryMuscles"], maxItems = 4)
                .filterNot { it in primary }
                .toSet()
        val equipment = (entry["equipment"] as? String)?.takeIf { it.isNotBlank() }
        return InferredCustomLiftMetadata(
            exerciseNameRaw = name,
            movementPattern = movementPattern,
            primaryMuscles = primary,
            secondaryMuscles = secondary,
            equipment = equipment,
        ).let(CustomLiftMusclePolicy::withPatternSecondaryDefaults)
    }

    private fun parseMuscleGroups(value: Any?, maxItems: Int): Set<MuscleGroup> {
        val list = value as? List<*> ?: return emptySet()
        return list.mapNotNull { parseMuscleGroup(it as? String) }.take(maxItems).toSet()
    }

    private fun parseMovementPattern(value: String?): MovementPattern? =
        value?.let { runCatching { MovementPattern.valueOf(it) }.getOrNull() }

    private fun parseMuscleGroup(value: String?): MuscleGroup? =
        value?.let { runCatching { MuscleGroup.valueOf(it) }.getOrNull() }
}
