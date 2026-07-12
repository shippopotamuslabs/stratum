package com.stratum.domain.importing

import com.stratum.domain.lift.CustomLiftDefinition
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.inferKinematicProfile
import com.stratum.domain.onboarding.MuscleGroup

/** An imported exercise name that did not match the bundled seed catalog. */
data class UnmatchedExerciseCandidate(
    val exerciseNameRaw: String,
    val loggedSetCount: Int,
)

/**
 * How a custom import lift is loaded for weight increments.
 *
 * Seed ids map 1:1 onto bundled [com.stratum.domain.lift.Equipment] rows. Custom names go through
 * find-or-create (reuse existing by name / alias, else mint with an open band). Unknown loading
 * uses the shared [DEFAULT_EQUIPMENT_NAME] row at +[DEFAULT_INCREMENT_LB] lb so progression always
 * has a lattice.
 */
sealed class InferredEquipmentLoading {
    data class Seed(val equipmentId: EquipmentId) : InferredEquipmentLoading()

    data class Custom(
        val name: String,
        val incrementLb: Double,
    ) : InferredEquipmentLoading()

    companion object {
        /** Display name for the shared unknown-loading custom equipment row. */
        const val DEFAULT_EQUIPMENT_NAME = "Default"

        const val DEFAULT_INCREMENT_LB = 5.0

        /** Reject absurd LLM steps; use [DEFAULT_INCREMENT_LB] instead. */
        const val MAX_INCREMENT_LB = 100.0

        fun defaultEquipment(): Custom =
            Custom(name = DEFAULT_EQUIPMENT_NAME, incrementLb = DEFAULT_INCREMENT_LB)

        fun sanitizeIncrementLb(raw: Double?): Double =
            if (raw != null && isValidIncrementLb(raw)) raw else DEFAULT_INCREMENT_LB

        private fun isValidIncrementLb(value: Double): Boolean =
            value.isFinite() && value > 0.0 && value <= MAX_INCREMENT_LB
    }
}

/** LLM-inferred catalog metadata for a custom lift. */
data class InferredCustomLiftMetadata(
    val exerciseNameRaw: String,
    val movementPattern: MovementPattern,
    val primaryMuscles: Set<MuscleGroup>,
    val secondaryMuscles: Set<MuscleGroup> = emptySet(),
    /**
     * Structured loading from `inferCustomLifts`. Null is treated as
     * [InferredEquipmentLoading.defaultEquipment] so the lift always inherits a +5 lattice.
     */
    val loading: InferredEquipmentLoading? = null,
) {
    /**
     * Maps inferred metadata to a [CustomLiftDefinition].
     *
     * Lift-level [CustomLiftDefinition.increment] is left null so the lift inherits equipment
     * default tiers via [com.stratum.domain.lift.Lift.usesEquipmentDefaultIncrements]. Seed
     * loading sets [CustomLiftDefinition.equipmentId] directly; custom loading sets free-text
     * [CustomLiftDefinition.equipmentName] plus [CustomLiftDefinition.equipmentCreateIncrement]
     * for find-or-create (reuse by name/alias, or mint with that open band).
     */
    fun toCustomLiftDefinition(): CustomLiftDefinition {
        val normalized = CustomLiftMusclePolicy.withPatternSecondaryDefaults(this)
        val patterns = setOf(normalized.movementPattern) + normalized.movementPattern.secondaryPatterns
        val kinematic =
            inferKinematicProfile(normalized.movementPattern, normalized.exerciseNameRaw)
        return when (val loading = normalized.loading ?: InferredEquipmentLoading.defaultEquipment()) {
            is InferredEquipmentLoading.Seed ->
                CustomLiftDefinition(
                    name = normalized.exerciseNameRaw,
                    movementPatterns = patterns,
                    primaryMuscles = normalized.primaryMuscles,
                    secondaryMuscles = normalized.secondaryMuscles,
                    equipmentId = loading.equipmentId,
                    equipmentName = null,
                    equipmentCreateIncrement = null,
                    increment = null,
                    kinematicProfile = kinematic,
                )
            is InferredEquipmentLoading.Custom ->
                CustomLiftDefinition(
                    name = normalized.exerciseNameRaw,
                    movementPatterns = patterns,
                    primaryMuscles = normalized.primaryMuscles,
                    secondaryMuscles = normalized.secondaryMuscles,
                    equipmentId = null,
                    equipmentName = loading.name.ifBlank { InferredEquipmentLoading.DEFAULT_EQUIPMENT_NAME },
                    equipmentCreateIncrement =
                        Weight(
                            InferredEquipmentLoading.sanitizeIncrementLb(loading.incrementLb),
                            Weight.WeightUnit.Lb,
                        ),
                    increment = null,
                    kinematicProfile = kinematic,
                )
        }
    }
}

/** Cloud LLM port for inferring custom-lift metadata from exercise names. */
interface CustomLiftInferencePort {
    suspend fun inferMetadata(
        candidates: List<UnmatchedExerciseCandidate>,
    ): Result<List<InferredCustomLiftMetadata>>
}
