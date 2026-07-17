package com.stratum.domain.importing

import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Resolves unmatched import exercise names to custom-lift metadata.
 *
 * Order:
 * 1. Deterministic name rules ([ImportStubLiftGuesser]) — pattern + muscles + best-effort loading
 * 2. Cloud LLM for residuals — **movement pattern + equipment only**; muscles always derived
 *    from pattern via [ImportStubLiftGuesser.metadataFromPattern]
 *
 * Partial batch failures are tolerated; callers skip custom-lift creation for names with no
 * metadata.
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
        val nameRuleResolvedNames = linkedSetOf<String>()
        val residual = mutableListOf<UnmatchedExerciseCandidate>()

        for (candidate in sorted) {
            val fromRules = ImportStubLiftGuesser.inferredMetadataFromName(candidate.exerciseNameRaw)
            if (fromRules != null) {
                inferredByName[candidate.exerciseNameRaw] = fromRules
                nameRuleResolvedNames += candidate.exerciseNameRaw
            } else {
                residual += candidate
            }
        }

        if (residual.isEmpty()) {
            return EnrichCustomLiftsOutcome(
                inferredByName = inferredByName,
                nameRuleResolvedNames = nameRuleResolvedNames,
                batchesAttempted = 0,
                batchesFailed = 0,
                lastFailureMessage = null,
            )
        }

        val batches = residual.chunked(MAX_BATCH)
        val results =
            coroutineScope {
                batches.map { batch -> async { customLiftInferencePort.inferMetadata(batch) } }.awaitAll()
            }
        var batchesFailed = 0
        var lastFailureMessage: String? = null
        results.forEach { result ->
            result
                .onSuccess { lifts ->
                    lifts.forEach { lift ->
                        // Force pattern-derived muscles even if the wire payload still includes tags.
                        inferredByName[lift.exerciseNameRaw] =
                            ImportStubLiftGuesser.metadataFromPattern(
                                exerciseNameRaw = lift.exerciseNameRaw,
                                movementPattern = lift.movementPattern,
                                loading = lift.loading,
                            )
                    }
                }.onFailure { error ->
                    batchesFailed += 1
                    lastFailureMessage = error.message ?: error::class.simpleName
                }
        }
        return EnrichCustomLiftsOutcome(
            inferredByName = inferredByName,
            nameRuleResolvedNames = nameRuleResolvedNames,
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
    /** Names classified by [ImportStubLiftGuesser] without calling the LLM. */
    val nameRuleResolvedNames: Set<String> = emptySet(),
    val batchesAttempted: Int,
    val batchesFailed: Int,
    val lastFailureMessage: String?,
) {
    companion object {
        val EMPTY =
            EnrichCustomLiftsOutcome(
                inferredByName = emptyMap(),
                nameRuleResolvedNames = emptySet(),
                batchesAttempted = 0,
                batchesFailed = 0,
                lastFailureMessage = null,
            )
    }
}
