package com.stratum.domain.response

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup

/**
 * Bitmasks for SQL set queries that must stay aligned with
 * [com.stratum.domain.importing.CustomLiftMusclePolicy] auto-secondaries (press → Tricep,
 * pull → Bicep). Used by Room `getForMuscleCredit` and test fakes.
 */
object MuscleSetQueryBits {
    val pressPatternBits: Int =
        patternBits(
            MovementPattern.HorizontalPress,
            MovementPattern.VerticalPress,
            MovementPattern.InclinePress,
        )

    val pullPatternBits: Int =
        patternBits(
            MovementPattern.HorizontalPull,
            MovementPattern.VerticalPull,
        )

    fun muscleBit(muscle: MuscleGroup): Int = 1 shl muscle.ordinal

    /**
     * Non-zero press-pattern mask when [muscle] receives policy secondary on presses (Tricep).
     * Zero when the press branch of the SQL OR should not fire.
     */
    fun pressPatternBitsForPolicySecondary(muscle: MuscleGroup): Int =
        if (muscle == MuscleGroup.Tricep) pressPatternBits else 0

    /**
     * Non-zero pull-pattern mask when [muscle] receives policy secondary on pulls (Bicep).
     */
    fun pullPatternBitsForPolicySecondary(muscle: MuscleGroup): Int =
        if (muscle == MuscleGroup.Bicep) pullPatternBits else 0

    fun patternBits(vararg patterns: MovementPattern): Int =
        patterns.fold(0) { mask, pattern -> mask or (1 shl pattern.ordinal) }

    /**
     * Whether a catalog lift row would match the detail SQL filter for [muscle] (stored tags or
     * policy pattern). Mirrors Room `getForMuscleCredit` for fakes/tests.
     */
    fun liftCreditsMuscleInSql(
        muscle: MuscleGroup,
        primaryMusclesBitmask: Int,
        secondaryMusclesBitmask: Int,
        movementPatternsBitmask: Int,
    ): Boolean {
        val mBit = muscleBit(muscle)
        if ((primaryMusclesBitmask and mBit) != 0) return true
        if ((secondaryMusclesBitmask and mBit) != 0) return true
        val press = pressPatternBitsForPolicySecondary(muscle)
        if (press != 0 && (movementPatternsBitmask and press) != 0) return true
        val pull = pullPatternBitsForPolicySecondary(muscle)
        if (pull != 0 && (movementPatternsBitmask and pull) != 0) return true
        return false
    }
}
