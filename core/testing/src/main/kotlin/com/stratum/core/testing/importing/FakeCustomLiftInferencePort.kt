package com.stratum.core.testing.importing

import com.stratum.application.importing.CustomLiftInferencePort
import com.stratum.domain.importing.InferredCustomLiftMetadata
import com.stratum.application.importing.UnmatchedExerciseCandidate

class FakeCustomLiftInferencePort(
    private val results: List<InferredCustomLiftMetadata> = emptyList(),
    private val fail: Boolean = false,
    /** Exact failure instance to return; takes precedence over [fail]. */
    private val failure: Throwable? = null,
) : CustomLiftInferencePort {
    val invocations = mutableListOf<List<UnmatchedExerciseCandidate>>()

    override suspend fun inferMetadata(
        candidates: List<UnmatchedExerciseCandidate>,
    ): Result<List<InferredCustomLiftMetadata>> {
        invocations += candidates
        return if (failure != null) {
            Result.failure(failure)
        } else if (fail) {
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
