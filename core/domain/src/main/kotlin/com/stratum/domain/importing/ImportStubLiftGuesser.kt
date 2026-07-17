package com.stratum.domain.importing

import com.stratum.domain.lift.CustomLiftDefinition
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.SeedEquipmentIds
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.inferFatigueProfile
import com.stratum.domain.onboarding.MuscleGroup

/**
 * Deterministic movement metadata for import custom lifts.
 *
 * Used when:
 * - Name rules classify an unmatched import name without calling the LLM
 * - LLM returns only a [MovementPattern] and muscles are derived here
 * - Crediting weekly volume self-heals known stub mis-tags (e.g. lateral raise as Quad)
 */
object ImportStubLiftGuesser {
    fun definitionFromName(
        name: String,
        increment: Weight,
    ): CustomLiftDefinition {
        val pattern = guessMovementPattern(name) ?: MovementPattern.SquatPattern
        val normalized =
            metadataFromPattern(
                exerciseNameRaw = name,
                movementPattern = pattern,
                loading = null,
            )
        val patterns = setOf(normalized.movementPattern) + normalized.movementPattern.secondaryPatterns
        return CustomLiftDefinition(
            name = name,
            movementPatterns = patterns,
            primaryMuscles = normalized.primaryMuscles,
            secondaryMuscles = normalized.secondaryMuscles,
            equipmentName = null,
            increment = increment,
            fatigueProfile = inferFatigueProfile(normalized.movementPattern, name),
        )
    }

    /**
     * Name-rule classification for import customs. Null when the name is too ambiguous —
     * callers should send residuals to the LLM for pattern (+ equipment) only.
     */
    fun inferredMetadataFromName(name: String): InferredCustomLiftMetadata? {
        val pattern = guessMovementPattern(name) ?: return null
        return metadataFromPattern(
            exerciseNameRaw = name,
            movementPattern = pattern,
            loading = guessLoading(name),
        )
    }

    /**
     * Builds import metadata with **deterministic** primary/secondary muscles from [movementPattern].
     * Any free-form muscle tags from an LLM are ignored at this boundary.
     */
    fun metadataFromPattern(
        exerciseNameRaw: String,
        movementPattern: MovementPattern,
        loading: InferredEquipmentLoading?,
    ): InferredCustomLiftMetadata =
        CustomLiftMusclePolicy.withPatternSecondaryDefaults(
            InferredCustomLiftMetadata(
                exerciseNameRaw = exerciseNameRaw,
                movementPattern = movementPattern,
                primaryMuscles = primaryMusclesFor(movementPattern),
                secondaryMuscles = emptySet(),
                loading = loading,
            ),
        )

    fun effectiveMovementPattern(lift: Lift): MovementPattern {
        if (isRecoverableStub(lift)) {
            return guessMovementPattern(lift.name) ?: (lift.movementPatterns.firstOrNull() ?: MovementPattern.SquatPattern)
        }
        return lift.movementPatterns.firstOrNull() ?: MovementPattern.SquatPattern
    }

    fun effectivePrimaryMuscles(
        lift: Lift,
        movementPattern: MovementPattern,
    ): Set<MuscleGroup> {
        if (isRecoverableStub(lift)) {
            return primaryMusclesFor(movementPattern)
        }
        if (lift.primaryMuscles.isNotEmpty()) return lift.primaryMuscles
        if (!lift.isCustom) return lift.primaryMuscles
        return primaryMusclesFor(movementPattern)
    }

    /**
     * Re-classify custom lifts whose persisted metadata disagrees with name-based rules.
     * Covers stub fallbacks (e.g. lateral raises saved as SquatPattern/Quad) without a re-import.
     */
    private fun isRecoverableStub(lift: Lift): Boolean {
        if (!lift.isCustom) return false
        val guessedPattern = guessMovementPattern(lift.name) ?: return false
        val guessedPrimary = primaryMusclesFor(guessedPattern)
        val primaryPattern = lift.movementPatterns.firstOrNull() ?: MovementPattern.SquatPattern
        return primaryPattern != guessedPattern || lift.primaryMuscles != guessedPrimary
    }

    internal fun guessMovementPattern(name: String): MovementPattern? {
        val normalized = ImportExerciseNameNormalizer.normalizeForMatching(name)
        return when {
            // Abs / spinal flexion before generic "machine" or lower-body tokens.
            isSpinalFlexionName(normalized) -> MovementPattern.SpinalFlexion
            // Leg curl / wrist curl before generic "curl" → biceps.
            containsAny(normalized, "leg curl", "hamstring curl") -> MovementPattern.KneeFlexion
            containsAny(normalized, "wrist curl", "wrist extension", "forearm") ->
                MovementPattern.WristFlexionExtension
            containsAny(normalized, "curl", "preacher") -> MovementPattern.ElbowFlexion
            isChestFocusedDipName(normalized) -> MovementPattern.HorizontalPress
            containsAny(
                normalized,
                "tricep",
                "triceps",
                "skull crusher",
                "skullcrusher",
                "pushdown",
                "push down",
            ) -> MovementPattern.ElbowExtension
            containsAny(
                normalized,
                "pulldown",
                "pull down",
                "pullup",
                "pull up",
                "chinup",
                "chin up",
                "lat pull",
            ) -> MovementPattern.VerticalPull
            containsAny(normalized, "shrug") -> MovementPattern.ScapularElevation
            isRearDeltName(normalized) -> MovementPattern.ShoulderHorizontalAbduction
            isFrontDeltName(normalized) -> MovementPattern.ShoulderFlexion
            isLateralDeltName(normalized) -> MovementPattern.ShoulderAbduction
            containsAny(normalized, "row", "pullover") -> MovementPattern.HorizontalPull
            containsAny(normalized, "incline press", "incline bench") -> MovementPattern.InclinePress
            // Chest flies before general press tokens so "cable fly" is not a press.
            containsAny(normalized, "fly", "flye", "pec deck", "crossover") &&
                !containsAny(normalized, "reverse", "rear") ->
                MovementPattern.ShoulderAdduction
            containsAny(
                normalized,
                "bench press",
                "benchpress",
                "pushup",
                "push up",
                "dip",
                "chest press",
            ) -> MovementPattern.HorizontalPress
            containsAny(
                normalized,
                "overhead press",
                "ohp",
                "shoulder press",
                "military press",
                "arnold press",
            ) -> MovementPattern.VerticalPress
            containsAny(
                normalized,
                "squat",
                "leg press",
                "lunge",
                "step up",
                "stepup",
                "split squat",
            ) -> MovementPattern.SquatPattern
            // Hinge family before bare "hip" tokens used by abduction machines.
            isHipHingeName(normalized) -> MovementPattern.HipHinge
            containsAny(normalized, "hip thrust", "glute bridge", "kickback") ->
                MovementPattern.HipExtension
            containsAny(normalized, "calf") -> MovementPattern.PlantarFlexion
            containsAny(normalized, "leg extension", "knee extension") ->
                MovementPattern.KneeExtension
            containsAny(normalized, "abduct", "hip open") -> MovementPattern.HipAbduction
            containsAny(normalized, "adduct", "hip close", "hip adduction") ->
                MovementPattern.HipAdduction
            else -> null
        }
    }

    fun primaryMusclesFor(pattern: MovementPattern): Set<MuscleGroup> =
        when (pattern) {
            MovementPattern.ElbowFlexion -> setOf(MuscleGroup.Bicep)
            MovementPattern.ElbowExtension -> setOf(MuscleGroup.Tricep)
            MovementPattern.ShoulderAdduction -> setOf(MuscleGroup.Chest)
            MovementPattern.ShoulderAbduction -> setOf(MuscleGroup.LateralDeltoid)
            MovementPattern.ShoulderHorizontalAbduction -> setOf(MuscleGroup.PosteriorDeltoid)
            MovementPattern.ShoulderFlexion -> setOf(MuscleGroup.AnteriorDeltoid)
            MovementPattern.HorizontalPull,
            MovementPattern.VerticalPull,
            -> setOf(MuscleGroup.Back)
            MovementPattern.HorizontalPress,
            MovementPattern.InclinePress,
            -> setOf(MuscleGroup.Chest)
            MovementPattern.VerticalPress -> setOf(MuscleGroup.AnteriorDeltoid)
            MovementPattern.SquatPattern -> setOf(MuscleGroup.Quad)
            MovementPattern.HipHinge -> setOf(MuscleGroup.Glute, MuscleGroup.Hamstring)
            MovementPattern.KneeExtension -> setOf(MuscleGroup.Quad)
            MovementPattern.KneeFlexion -> setOf(MuscleGroup.Hamstring)
            MovementPattern.PlantarFlexion -> setOf(MuscleGroup.Calf)
            MovementPattern.HipExtension -> setOf(MuscleGroup.Glute)
            MovementPattern.ScapularElevation -> setOf(MuscleGroup.Trap)
            MovementPattern.WristFlexionExtension -> setOf(MuscleGroup.Forearm)
            MovementPattern.SpinalFlexion -> setOf(MuscleGroup.Ab)
            MovementPattern.HipAbduction -> setOf(MuscleGroup.Abductor)
            MovementPattern.HipAdduction -> setOf(MuscleGroup.Adductor)
        }

    /** @deprecated Use [primaryMusclesFor]. */
    internal fun guessPrimaryMuscles(pattern: MovementPattern): Set<MuscleGroup> =
        primaryMusclesFor(pattern)

    /**
     * Best-effort loading lattice from name tokens. Unknown → shared Default equipment.
     * Prefer explicit implement tokens over vague "machine".
     */
    internal fun guessLoading(name: String): InferredEquipmentLoading {
        val normalized = ImportExerciseNameNormalizer.normalizeForMatching(name)
        return when {
            containsAny(normalized, "cable") ->
                InferredEquipmentLoading.Seed(SeedEquipmentIds.Cable)
            containsAny(normalized, "dumbbell", "db ") || normalized.startsWith("db ") ->
                InferredEquipmentLoading.Seed(SeedEquipmentIds.Dumbbell)
            containsAny(normalized, "kettlebell", "kb ") || normalized.startsWith("kb ") ->
                InferredEquipmentLoading.Seed(SeedEquipmentIds.Kettlebell)
            containsAny(normalized, "smith") ->
                InferredEquipmentLoading.Seed(SeedEquipmentIds.FixedBarbell)
            containsAny(normalized, "landmine", "t bar", "t-bar") ->
                InferredEquipmentLoading.Seed(SeedEquipmentIds.SingleWeightPlate)
            containsAny(
                normalized,
                "plate loaded",
                "plate-loaded",
                "barbell",
                "bb ",
            ) || normalized.startsWith("bb ") ->
                InferredEquipmentLoading.Seed(SeedEquipmentIds.DoubleWeightPlates)
            containsAny(
                normalized,
                "bodyweight",
                "pullup",
                "pull up",
                "chinup",
                "chin up",
                "pushup",
                "push up",
            ) && !containsAny(normalized, "weighted") ->
                InferredEquipmentLoading.Seed(SeedEquipmentIds.Bodyweight)
            containsAny(
                normalized,
                "pin loaded",
                "pin-loaded",
                "selectorized",
                "stack",
                "machine",
            ) ->
                InferredEquipmentLoading.Seed(SeedEquipmentIds.PinLoadedMachine)
            else -> InferredEquipmentLoading.defaultEquipment()
        }
    }

    private fun isSpinalFlexionName(normalized: String): Boolean =
        containsAny(
            normalized,
            "crunch",
            "sit up",
            "situp",
            "sit-up",
            "ab wheel",
            "ab rollout",
            "ab roller",
            "abdominal",
            "abs ",
            " abs",
            "torso rotation",
            "oblique",
        ) ||
            (containsAny(normalized, "roman chair") &&
                containsAny(normalized, "sit", "crunch"))

    private fun isHipHingeName(normalized: String): Boolean =
        containsAny(
            normalized,
            "deadlift",
            "rdl",
            "good morning",
            "hip hinge",
            "hyperextension",
            "hyper extension",
            "back extension",
        ) ||
            (containsAny(normalized, "roman chair") &&
                !containsAny(normalized, "sit", "crunch"))

    private fun isRearDeltName(normalized: String): Boolean =
        containsAny(
            normalized,
            "rear delt",
            "reardelt",
            "reverse fly",
            "reverse pec",
            "face pull",
            "facepull",
            "bent over",
            "bent-over",
        )

    private fun isFrontDeltName(normalized: String): Boolean =
        containsAny(normalized, "front raise", "frontraise", "front delt")

    private fun isLateralDeltName(normalized: String): Boolean =
        containsAny(
            normalized,
            "lateral raise",
            "side raise",
            "side lateral",
            "cuffed lateral",
            "y raise",
            "iron cross",
        )

    private fun isChestFocusedDipName(normalized: String): Boolean =
        containsAny(normalized, "dip") &&
            !containsAny(normalized, "tricep", "triceps")

    private fun containsAny(haystack: String, vararg needles: String): Boolean =
        needles.any { haystack.contains(it) }
}
