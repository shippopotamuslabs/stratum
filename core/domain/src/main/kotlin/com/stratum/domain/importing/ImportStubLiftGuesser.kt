package com.stratum.domain.importing

import com.stratum.domain.lift.CustomLiftDefinition
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.onboarding.MuscleGroup

/**
 * Deterministic movement metadata for import stub custom lifts (LLM inference failed).
 * Applied when persisting stubs and when crediting weekly volume so existing imports
 * gain pull/push secondaries and correct delt tagging without re-import.
 */
object ImportStubLiftGuesser {
    fun definitionFromName(
        name: String,
        increment: Weight,
    ): CustomLiftDefinition {
        val pattern = guessMovementPattern(name) ?: MovementPattern.SquatPattern
        val normalized =
            CustomLiftMusclePolicy.withPatternSecondaryDefaults(
                InferredCustomLiftMetadata(
                    exerciseNameRaw = name,
                    movementPattern = pattern,
                    primaryMuscles = guessPrimaryMuscles(pattern),
                    secondaryMuscles = emptySet(),
                ),
            )
        val patterns = setOf(normalized.movementPattern) + normalized.movementPattern.secondaryPatterns
        return CustomLiftDefinition(
            name = name,
            movementPatterns = patterns,
            primaryMuscles = normalized.primaryMuscles,
            secondaryMuscles = normalized.secondaryMuscles,
            equipmentName = null,
            increment = increment,
        )
    }

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
            return guessPrimaryMuscles(movementPattern)
        }
        if (lift.primaryMuscles.isNotEmpty()) return lift.primaryMuscles
        if (!lift.isCustom) return lift.primaryMuscles
        return guessPrimaryMuscles(movementPattern)
    }

    /**
     * Re-classify custom lifts whose persisted metadata disagrees with name-based rules.
     * Covers stub fallbacks (e.g. lateral raises saved as SquatPattern/Quad) without a re-import.
     */
    private fun isRecoverableStub(lift: Lift): Boolean {
        if (!lift.isCustom) return false
        val guessedPattern = guessMovementPattern(lift.name) ?: return false
        val guessedPrimary = guessPrimaryMuscles(guessedPattern)
        val primaryPattern = lift.movementPatterns.firstOrNull() ?: MovementPattern.SquatPattern
        return primaryPattern != guessedPattern || lift.primaryMuscles != guessedPrimary
    }

    internal fun guessMovementPattern(name: String): MovementPattern? {
        val normalized = ImportExerciseNameNormalizer.normalizeForMatching(name)
        return when {
            containsAny(normalized, "curl", "preacher") -> MovementPattern.ElbowFlexion
            isChestFocusedDipName(normalized) -> MovementPattern.HorizontalPress
            containsAny(normalized, "tricep", "skull crusher", "skullcrusher", "pushdown") ->
                MovementPattern.ElbowExtension
            containsAny(normalized, "pulldown", "pullup", "pull up", "chinup", "chin up") ->
                MovementPattern.VerticalPull
            containsAny(normalized, "shrug") -> MovementPattern.ScapularElevation
            isRearDeltName(normalized) -> MovementPattern.ShoulderHorizontalAbduction
            isFrontDeltName(normalized) -> MovementPattern.ShoulderFlexion
            isLateralDeltName(normalized) -> MovementPattern.ShoulderAbduction
            containsAny(normalized, "row", "pullover") -> MovementPattern.HorizontalPull
            containsAny(normalized, "bench press", "benchpress", "pushup", "push up", "dip", "fly") ->
                MovementPattern.HorizontalPress
            containsAny(normalized, "incline press", "incline bench") -> MovementPattern.InclinePress
            containsAny(normalized, "overhead press", "ohp", "shoulder press", "military press") ->
                MovementPattern.VerticalPress
            containsAny(normalized, "squat", "leg press", "lunge", "step up", "stepup") ->
                MovementPattern.SquatPattern
            containsAny(normalized, "deadlift", "rdl", "good morning") ->
                MovementPattern.HipHinge
            containsAny(normalized, "hip thrust", "glute bridge") ->
                MovementPattern.HipExtension
            containsAny(normalized, "calf") -> MovementPattern.PlantarFlexion
            containsAny(normalized, "leg extension") -> MovementPattern.KneeExtension
            containsAny(normalized, "leg curl", "hamstring curl") -> MovementPattern.KneeFlexion
            else -> null
        }
    }

    internal fun guessPrimaryMuscles(pattern: MovementPattern): Set<MuscleGroup> {
        return patternPrimaryMuscles(pattern)
    }

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

    private fun patternPrimaryMuscles(pattern: MovementPattern): Set<MuscleGroup> =
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

    private fun isChestFocusedDipName(normalized: String): Boolean =
        containsAny(normalized, "dip") &&
            !containsAny(normalized, "tricep", "triceps")

    private fun containsAny(haystack: String, vararg needles: String): Boolean =
        needles.any { haystack.contains(it) }
}
