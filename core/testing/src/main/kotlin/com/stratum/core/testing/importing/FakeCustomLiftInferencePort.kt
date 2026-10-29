package com.stratum.core.testing.importing

import com.stratum.application.importing.CustomLiftInferencePort
import com.stratum.domain.importing.InferredCustomLiftMetadata
import com.stratum.application.importing.UnmatchedExerciseCandidate

class FakeCustomLiftInferencePort(
    private val results: List<InferredCustomLiftMetadata> = emptyList(),
    private val fail: Boolean = false,
) : CustomLiftInferencePort {
    val invocations = mutableListOf<List<UnmatchedExerciseCandidate>>()

    override suspend fun inferMetadata(
        candidates: List<UnmatchedExerciseCandidate>,
    ): Result<List<InferredCustomLiftMetadata>> {
        invocations += candidates
        return if (fail) {
            Result.failure(IllegalStateException("inference failed"))
        } else {
            Result.success(
                results.filter { inferred ->
                    candidates.any { it.exerciseNameRaw == inferred.exerciseNameRaw }
                },
            )
        }
    }
}
