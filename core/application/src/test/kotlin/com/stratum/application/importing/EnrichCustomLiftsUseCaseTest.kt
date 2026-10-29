package com.stratum.application.importing

import com.stratum.core.testing.importing.FakeCustomLiftInferencePort
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.SeedEquipmentIds
import com.stratum.domain.onboarding.MuscleGroup
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.importing.InferredCustomLiftMetadata
import com.stratum.domain.importing.InferredEquipmentLoading

class EnrichCustomLiftsUseCaseTest {
    @Test
    @DisplayName("Given more candidates than batch size when enriching then inference is chunked")
    fun chunksLargeCandidateLists() = runTest {
        val inference = FakeCustomLiftInferencePort()
        val subject = EnrichCustomLiftsUseCase(inference)
        // Ambiguous names so name rules do not short-circuit the LLM path.
        val candidates =
            (1..80).map { index ->
                UnmatchedExerciseCandidate(
                    exerciseNameRaw = "BrandX Widget $index",
                    loggedSetCount = index,
                )
            }

        val outcome = subject(candidates)

        assertEquals(2, inference.invocations.size)
        assertEquals(75, inference.invocations[0].size)
        assertEquals(5, inference.invocations[1].size)
        assertEquals(2, outcome.batchesAttempted)
        assertEquals(0, outcome.batchesFailed)
        assertEquals(0, outcome.nameRuleResolvedNames.size)
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
                    exerciseNameRaw = "BrandX Widget $index",
                    loggedSetCount = index,
                )
            }

        val outcome = subject(candidates)

        assertEquals(5, outcome.inferredByName.size)
        assertEquals(2, outcome.batchesAttempted)
        assertEquals(1, outcome.batchesFailed)
        assertEquals("timeout", outcome.lastFailureMessage)
    }

    @Test
    @DisplayName("Given name-rule matches when enriching then LLM is not called for those names")
    fun nameRulesSkipLlm() = runTest {
        val inference = FakeCustomLiftInferencePort()
        val subject = EnrichCustomLiftsUseCase(inference)

        val outcome =
            subject(
                listOf(
                    UnmatchedExerciseCandidate("Machine Crunch", loggedSetCount = 10),
                    UnmatchedExerciseCandidate("Weighted Roman Chair Hip Hinge", loggedSetCount = 8),
                    UnmatchedExerciseCandidate("BrandX Widget", loggedSetCount = 3),
                ),
            )

        assertEquals(setOf("Machine Crunch", "Weighted Roman Chair Hip Hinge"), outcome.nameRuleResolvedNames)
        assertEquals(1, inference.invocations.size)
        assertEquals(listOf("BrandX Widget"), inference.invocations.single().map { it.exerciseNameRaw })
        assertEquals(
            setOf(MuscleGroup.Ab),
            outcome.inferredByName.getValue("Machine Crunch").primaryMuscles,
        )
        assertEquals(
            setOf(MuscleGroup.Glute, MuscleGroup.Hamstring),
            outcome.inferredByName.getValue("Weighted Roman Chair Hip Hinge").primaryMuscles,
        )
    }

    @Test
    @DisplayName("Given LLM returns wrong primary muscles when enriching then pattern-derived muscles win")
    fun llmMusclesAreOverriddenByPattern() = runTest {
        val inference =
            FakeCustomLiftInferencePort(
                results =
                    listOf(
                        InferredCustomLiftMetadata(
                            exerciseNameRaw = "BrandX Widget",
                            movementPattern = MovementPattern.SpinalFlexion,
                            primaryMuscles = setOf(MuscleGroup.Quad),
                            secondaryMuscles = emptySet(),
                            loading = InferredEquipmentLoading.Seed(SeedEquipmentIds.PinLoadedMachine),
                        ),
                    ),
            )
        val subject = EnrichCustomLiftsUseCase(inference)

        val outcome = subject(listOf(UnmatchedExerciseCandidate("BrandX Widget", 1)))

        assertEquals(
            setOf(MuscleGroup.Ab),
            outcome.inferredByName.getValue("BrandX Widget").primaryMuscles,
        )
    }
}
