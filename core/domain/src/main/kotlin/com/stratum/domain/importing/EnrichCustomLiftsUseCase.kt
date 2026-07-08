package com.stratum.domain.importing

import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Batches unmatched import exercise names and requests LLM-inferred catalog metadata.
 * Batches run concurrently to reduce import latency; partial batch failures are tolerated and
 * callers skip custom-lift creation for names with no inferred metadata.
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
        val batches = sorted.chunked(MAX_BATCH)
        val results =
            coroutineScope {
                batches.map { batch -> async { customLiftInferencePort.inferMetadata(batch) } }.awaitAll()
            }
        val inferredByName = linkedMapOf<String, InferredCustomLiftMetadata>()
        var batchesFailed = 0
        var lastFailureMessage: String? = null
        results.forEach { result ->
            result
                .onSuccess { lifts ->
                    lifts.forEach { lift -> inferredByName[lift.exerciseNameRaw] = lift }
                }.onFailure { error ->
                    batchesFailed += 1
                    lastFailureMessage = error.message ?: error::class.simpleName
                }
        }
        return EnrichCustomLiftsOutcome(
            inferredByName = inferredByName,
            batchesAttempted = batches.size,
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
