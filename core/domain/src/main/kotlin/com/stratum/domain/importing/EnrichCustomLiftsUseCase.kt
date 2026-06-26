package com.stratum.domain.importing

import javax.inject.Inject

/**
 * Batches unmatched import exercise names and requests LLM-inferred catalog metadata.
 * Returns an empty map when inference fails so callers can fall back to stub customs.
 */
class EnrichCustomLiftsUseCase @Inject constructor(
    private val customLiftInferencePort: CustomLiftInferencePort,
) {
    suspend operator fun invoke(
        candidates: List<UnmatchedExerciseCandidate>,
    ): Map<String, InferredCustomLiftMetadata> {
        if (candidates.isEmpty()) return emptyMap()
        val batch =
            candidates
                .sortedByDescending { it.loggedSetCount }
                .take(MAX_BATCH)
        return customLiftInferencePort
            .inferMetadata(batch)
            .getOrElse { emptyList() }
            .associateBy { it.exerciseNameRaw }
    }

    companion object {
        const val MAX_BATCH = 75
    }
}
