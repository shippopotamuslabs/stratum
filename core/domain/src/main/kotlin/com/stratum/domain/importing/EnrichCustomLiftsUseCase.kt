package com.stratum.domain.importing

import javax.inject.Inject

/**
 * Batches unmatched import exercise names and requests LLM-inferred catalog metadata.
 * Partial batch failures are tolerated; callers fall back to stub customs per exercise.
 */
class EnrichCustomLiftsUseCase @Inject constructor(
    private val customLiftInferencePort: CustomLiftInferencePort,
) {
    suspend operator fun invoke(
        candidates: List<UnmatchedExerciseCandidate>,
    ): EnrichCustomLiftsOutcome {
        if (candidates.isEmpty()) {
            return EnrichCustomLiftsOutcome.EMPTY
        }
        val sorted = candidates.sortedByDescending { it.loggedSetCount }
        val inferredByName = linkedMapOf<String, InferredCustomLiftMetadata>()
        var batchesAttempted = 0
        var batchesFailed = 0
        var lastFailureMessage: String? = null
        sorted.chunked(MAX_BATCH).forEach { batch ->
            batchesAttempted += 1
            customLiftInferencePort
                .inferMetadata(batch)
                .onSuccess { lifts ->
                    lifts.forEach { lift -> inferredByName[lift.exerciseNameRaw] = lift }
                }.onFailure { error ->
                    batchesFailed += 1
                    lastFailureMessage = error.message ?: error::class.simpleName
                }
        }
        return EnrichCustomLiftsOutcome(
            inferredByName = inferredByName,
            batchesAttempted = batchesAttempted,
            batchesFailed = batchesFailed,
            lastFailureMessage = lastFailureMessage,
        )
    }

    companion object {
        const val MAX_BATCH = 75
    }
}

data class EnrichCustomLiftsOutcome(
    val inferredByName: Map<String, InferredCustomLiftMetadata>,
    val batchesAttempted: Int,
    val batchesFailed: Int,
    val lastFailureMessage: String?,
) {
    companion object {
        val EMPTY =
            EnrichCustomLiftsOutcome(
                inferredByName = emptyMap(),
                batchesAttempted = 0,
                batchesFailed = 0,
                lastFailureMessage = null,
            )
    }
}
