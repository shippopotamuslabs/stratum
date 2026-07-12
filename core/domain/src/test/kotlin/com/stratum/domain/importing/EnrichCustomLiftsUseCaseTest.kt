package com.stratum.domain.importing

import com.stratum.core.testing.importing.FakeCustomLiftInferencePort
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.SeedEquipmentIds
import com.stratum.domain.onboarding.MuscleGroup
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class EnrichCustomLiftsUseCaseTest {
    @Test
    @DisplayName("Given more candidates than batch size when enriching then inference is chunked")
    fun chunksLargeCandidateLists() = runTest {
        val inference = FakeCustomLiftInferencePort()
        val subject = EnrichCustomLiftsUseCase(inference)
        val candidates =
            (1..80).map { index ->
                UnmatchedExerciseCandidate(
                    exerciseNameRaw = "Custom Lift $index",
                    loggedSetCount = index,
                )
            }

        val outcome = subject(candidates)

        assertEquals(2, inference.invocations.size)
        assertEquals(75, inference.invocations[0].size)
        assertEquals(5, inference.invocations[1].size)
        assertEquals(2, outcome.batchesAttempted)
        assertEquals(0, outcome.batchesFailed)
    }

    @Test
    @DisplayName("Given a failed batch when enriching then other batches still contribute results")
    fun toleratesPartialBatchFailures() = runTest {
        val inference =
            object : CustomLiftInferencePort {
                var calls = 0

                override suspend fun inferMetadata(
                    candidates: List<UnmatchedExerciseCandidate>,
                ): Result<List<InferredCustomLiftMetadata>> {
                    calls += 1
                    return if (calls == 1) {
                        Result.failure(IllegalStateException("timeout"))
                    } else {
                        Result.success(
                            candidates.map { candidate ->
                                InferredCustomLiftMetadata(
                                    exerciseNameRaw = candidate.exerciseNameRaw,
                                    movementPattern = MovementPattern.ShoulderAbduction,
                                    primaryMuscles = setOf(MuscleGroup.LateralDeltoid),
                                    secondaryMuscles = emptySet(),
                                    loading = InferredEquipmentLoading.Seed(SeedEquipmentIds.Cable),
                                )
                            },
                        )
                    }
                }
            }
        val subject = EnrichCustomLiftsUseCase(inference)
        val candidates =
            (1..80).map { index ->
                UnmatchedExerciseCandidate(
                    exerciseNameRaw = "Custom Lift $index",
                    loggedSetCount = index,
                )
            }

        val outcome = subject(candidates)

        assertEquals(5, outcome.inferredByName.size)
        assertEquals(2, outcome.batchesAttempted)
        assertEquals(1, outcome.batchesFailed)
        assertEquals("timeout", outcome.lastFailureMessage)
    }
}
