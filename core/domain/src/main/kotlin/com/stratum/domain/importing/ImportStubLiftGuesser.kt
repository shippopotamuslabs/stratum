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
        val pattern = guessMovementPattern(name) ?: MovementPattern.LegPush
        val normalized =
            CustomLiftMusclePolicy.withPatternSecondaryDefaults(
                InferredCustomLiftMetadata(
                    exerciseNameRaw = name,
                    movementPattern = pattern,
                    primaryMuscles = guessPrimaryMuscles(pattern),
                    secondaryMuscles = emptySet(),
                ),
            )
        return CustomLiftDefinition(
            name = name,
            movementPattern = normalized.movementPattern,
            primaryMuscles = normalized.primaryMuscles,
            secondaryMuscles = normalized.secondaryMuscles,
            equipment = null,
            increment = increment,
        )
    }

    fun effectiveMovementPattern(lift: Lift): MovementPattern {
        if (isRecoverableStub(lift)) {
            return guessMovementPattern(lift.name) ?: lift.movementPattern
        }
        return lift.movementPattern
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
     * Covers stub fallbacks (e.g. lateral raises saved as LegPush/Quad) without a re-import.
     */
    private fun isRecoverableStub(lift: Lift): Boolean {
        if (!lift.isCustom) return false
        val guessedPattern = guessMovementPattern(lift.name) ?: return false
        val guessedPrimary = guessPrimaryMuscles(guessedPattern)
        return lift.movementPattern != guessedPattern || lift.primaryMuscles != guessedPrimary
    }

    internal fun guessMovementPattern(name: String): MovementPattern? {
        val normalized = ImportExerciseNameNormalizer.normalizeForMatching(name)
        return when {
            containsAny(normalized, "curl", "preacher") -> MovementPattern.BicepIso
            containsAny(normalized, "tricep", "skull crusher", "skullcrusher", "pushdown") ->
                MovementPattern.TricepIso
            containsAny(normalized, "pulldown", "pullup", "pull up", "chinup", "chin up") ->
                MovementPattern.VerticalPull
            containsAny(normalized, "shrug") -> MovementPattern.TrapIso
            isRearDeltName(normalized) -> MovementPattern.RearDeltIso
            isFrontDeltName(normalized) -> MovementPattern.AnteriorDeltIso
            isLateralDeltName(normalized) -> MovementPattern.LateralDeltIso
            containsAny(normalized, "row", "pullover") -> MovementPattern.HorizontalPull
            containsAny(normalized, "bench press", "benchpress", "pushup", "push up", "dip", "fly") ->
                MovementPattern.HorizontalPush
            containsAny(normalized, "incline press", "incline bench") -> MovementPattern.InclinePush
            containsAny(normalized, "overhead press", "ohp", "shoulder press", "military press") ->
                MovementPattern.VerticalPush
            containsAny(normalized, "squat", "leg press", "lunge", "step up", "stepup") ->
                MovementPattern.LegPush
            containsAny(normalized, "deadlift", "rdl", "hip thrust", "good morning") ->
                MovementPattern.HipHinge
            containsAny(normalized, "calf") -> MovementPattern.Calves
            containsAny(normalized, "leg extension") -> MovementPattern.QuadIso
            containsAny(normalized, "leg curl", "hamstring curl") -> MovementPattern.HamstringIso
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
            MovementPattern.BicepIso -> setOf(MuscleGroup.Bicep)
            MovementPattern.TricepIso -> setOf(MuscleGroup.Tricep)
            MovementPattern.ChestIso -> setOf(MuscleGroup.Chest)
            MovementPattern.LateralDeltIso -> setOf(MuscleGroup.LateralDeltoid)
            MovementPattern.RearDeltIso -> setOf(MuscleGroup.PosteriorDeltoid)
            MovementPattern.AnteriorDeltIso -> setOf(MuscleGroup.AnteriorDeltoid)
            MovementPattern.HorizontalPull,
            MovementPattern.VerticalPull,
            -> setOf(MuscleGroup.Back)
            MovementPattern.HorizontalPush,
            MovementPattern.InclinePush,
            -> setOf(MuscleGroup.Chest)
            MovementPattern.VerticalPush -> setOf(MuscleGroup.AnteriorDeltoid)
            MovementPattern.LegPush -> setOf(MuscleGroup.Quad)
            MovementPattern.HipHinge -> setOf(MuscleGroup.Glute, MuscleGroup.Hamstring)
            MovementPattern.QuadIso -> setOf(MuscleGroup.Quad)
            MovementPattern.HamstringIso -> setOf(MuscleGroup.Hamstring)
            MovementPattern.Calves -> setOf(MuscleGroup.Calf)
            MovementPattern.GluteIso -> setOf(MuscleGroup.Glute)
            MovementPattern.TrapIso -> setOf(MuscleGroup.Trap)
            MovementPattern.ForearmIso -> setOf(MuscleGroup.Forearm)
            MovementPattern.AbIso -> setOf(MuscleGroup.Ab)
        }

    private fun containsAny(haystack: String, vararg needles: String): Boolean =
        needles.any { haystack.contains(it) }
}
