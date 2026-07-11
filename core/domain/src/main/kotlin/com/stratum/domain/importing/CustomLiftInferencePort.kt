package com.stratum.domain.importing

import com.stratum.domain.lift.CustomLiftDefinition
import com.stratum.domain.lift.MovementPattern
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
    /**
     * Maps inferred metadata to a [CustomLiftDefinition].
     *
     * [increment] is left null so the lift inherits equipment default tiers (or global
     * fallback) via [com.stratum.domain.lift.Lift.usesEquipmentDefaultIncrements]. Free-text
     * [equipment] is stored as [CustomLiftDefinition.equipmentName] for find-or-create.
     */
    fun toCustomLiftDefinition(): CustomLiftDefinition {
        val normalized = CustomLiftMusclePolicy.withPatternSecondaryDefaults(this)
        val patterns = setOf(normalized.movementPattern) + normalized.movementPattern.secondaryPatterns
        return CustomLiftDefinition(
            name = normalized.exerciseNameRaw,
            movementPatterns = patterns,
            primaryMuscles = normalized.primaryMuscles,
            secondaryMuscles = normalized.secondaryMuscles,
            equipmentName = normalized.equipment,
            increment = null,
        )
    }
}

/** Cloud LLM port for inferring custom-lift metadata from exercise names. */
interface CustomLiftInferencePort {
    suspend fun inferMetadata(
        candidates: List<UnmatchedExerciseCandidate>,
    ): Result<List<InferredCustomLiftMetadata>>
}
