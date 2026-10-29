package com.stratum.domain.importing

import com.stratum.domain.importing.ImportStubLiftGuesser
import com.stratum.domain.lift.CustomLiftDefinition
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.SeedEquipmentIds
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.alignedWithEquipment
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.inferFatigueProfile
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.lift.DEFAULT_INCREMENT_LB
import com.stratum.domain.lift.Lift

sealed class InferredEquipmentLoading {
    data class Seed(val equipmentId: EquipmentId) : InferredEquipmentLoading()

    data class Custom(
        val name: String,
        val incrementLb: Double,
    ) : InferredEquipmentLoading()

    companion object {
        /** Display name of the seed Default implement (unknown-loading fallback). */
        const val DEFAULT_EQUIPMENT_NAME = "Default"

        /** Sanitize absurd custom brand-stack steps; not the seed Default increment. */
        const val DEFAULT_INCREMENT_LB = 5.0

        /** Reject absurd LLM steps; use [DEFAULT_INCREMENT_LB] instead. */
        const val MAX_INCREMENT_LB = 100.0

        fun defaultEquipment(): Seed = Seed(SeedEquipmentIds.Default)

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
     * [InferredEquipmentLoading.defaultEquipment] so the lift always inherits seed Default.
     */
    val loading: InferredEquipmentLoading? = null,
) {
    /**
     * Maps inferred metadata to a [CustomLiftDefinition].
     *
     * Lift-level [CustomLiftDefinition.increment] is left null so the lift inherits equipment
     * default tiers. Seed loading sets [CustomLiftDefinition.equipmentId] directly; custom
     * loading sets free-text [CustomLiftDefinition.equipmentName] plus
     * [CustomLiftDefinition.equipmentCreateIncrement] for find-or-create (reuse by name/alias,
     * or mint with that open band).
     */
    fun toCustomLiftDefinition(): CustomLiftDefinition {
        // Muscles are always derived from movementPattern — never trust free-form tags.
        val normalized =
            ImportStubLiftGuesser.metadataFromPattern(
                exerciseNameRaw = exerciseNameRaw,
                movementPattern = movementPattern,
                loading = loading,
            )
        val patterns = setOf(normalized.movementPattern) + normalized.movementPattern.secondaryPatterns
        val fatigue =
            inferFatigueProfile(normalized.movementPattern, normalized.exerciseNameRaw)
        val resolvedLoading =
            when (val loading = normalized.loading ?: InferredEquipmentLoading.defaultEquipment()) {
                is InferredEquipmentLoading.Custom if loading.name.isBlank() ->
                    InferredEquipmentLoading.defaultEquipment()
                else -> loading
            }
        return when (resolvedLoading) {
            is InferredEquipmentLoading.Seed ->
                CustomLiftDefinition(
                    name = normalized.exerciseNameRaw,
                    movementPatterns = patterns,
                    primaryMuscles = normalized.primaryMuscles,
                    secondaryMuscles = normalized.secondaryMuscles,
                    equipmentId = resolvedLoading.equipmentId,
                    equipmentName = null,
                    equipmentCreateIncrement = null,
                    increment = null,
                    fatigueProfile = fatigue,
                    loadType =
                        LoadType.Weighted.alignedWithEquipment(resolvedLoading.equipmentId),
                )
            is InferredEquipmentLoading.Custom ->
                CustomLiftDefinition(
                    name = normalized.exerciseNameRaw,
                    movementPatterns = patterns,
                    primaryMuscles = normalized.primaryMuscles,
                    secondaryMuscles = normalized.secondaryMuscles,
                    equipmentId = null,
                    equipmentName = resolvedLoading.name.trim(),
                    equipmentCreateIncrement =
                        Weight(
                            InferredEquipmentLoading.sanitizeIncrementLb(
                                resolvedLoading.incrementLb,
                            ),
                            Weight.WeightUnit.Lb,
                        ),
                    increment = null,
                    fatigueProfile = fatigue,
                    loadType = LoadType.Weighted,
                )
        }
    }
}
