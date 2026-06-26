package com.stratum.domain.importing

import com.stratum.domain.lift.CustomLiftDefinition
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.onboarding.MuscleGroup

/** An imported exercise name that did not match the bundled seed catalog. */
data class UnmatchedExerciseCandidate(
    val exerciseNameRaw: String,
    val loggedSetCount: Int,
)

/** LLM-inferred catalog metadata for a custom lift. */
data class InferredCustomLiftMetadata(
    val exerciseNameRaw: String,
    val movementPattern: MovementPattern,
    val primaryMuscles: Set<MuscleGroup>,
    val secondaryMuscles: Set<MuscleGroup> = emptySet(),
    val equipment: String? = null,
) {
    fun toCustomLiftDefinition(defaultIncrement: Weight): CustomLiftDefinition =
        CustomLiftDefinition(
            name = exerciseNameRaw,
            movementPattern = movementPattern,
            primaryMuscles = primaryMuscles,
            secondaryMuscles = secondaryMuscles,
            equipment = equipment,
            increment = defaultIncrement,
        )
}

/** Cloud LLM port for inferring custom-lift metadata from exercise names. */
interface CustomLiftInferencePort {
    suspend fun inferMetadata(
        candidates: List<UnmatchedExerciseCandidate>,
    ): Result<List<InferredCustomLiftMetadata>>
}
