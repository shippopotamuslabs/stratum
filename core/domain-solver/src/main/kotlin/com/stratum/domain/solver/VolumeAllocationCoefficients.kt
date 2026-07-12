@file:Suppress("MagicNumber")

package com.stratum.domain.solver

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.program.generation.context.PRIMARY_SET_CREDIT
import com.stratum.domain.program.generation.prescription.FilledSlot
import com.stratum.domain.program.generation.reference.LiftStressClass
import com.stratum.domain.program.generation.reference.MAJOR_COMPOUND_PATTERNS
import com.stratum.domain.program.generation.reference.PatternSecondaryMuscleCredit
import com.stratum.domain.program.generation.reference.RpRepProfile
import kotlin.math.round

/**
 * Credit contribution of one set of [slot] to [muscle].
 *  - [PRIMARY_SET_CREDIT] (1.0) when `slot.primaryMuscle == muscle`,
 *  - the [PatternSecondaryMuscleCredit.creditPerSet] for each catalog entry on `slot.pattern`
 *    that targets `muscle`,
 *  - 0 otherwise.
 *
 * CP-SAT linear expressions require integer coefficients, but catalog-averaged secondary credits
 * are fractional (e.g. 0.1875/set when 3 of 8 lifts tag a muscle). [SCALE] is therefore 100 (not 2)
 * so those fractions survive scaling: `round(0.1875 * 100) = 19`. Callers multiply every credit,
 * cap, and target by [scale] consistently, so raising [SCALE] shifts everything in lockstep.
 *
 * Note: `muscleCreditsInSession` (`SessionSlotPlan.kt`) uses the *static*
 * `PATTERN_SECONDARY_MUSCLE_CREDITS` table, not this catalog-aware table — it is a separate,
 * coarser volume model, kept for contexts without a lift catalog.
 */
class VolumeAllocationCoefficients(
    private val credits: Map<MovementPattern, List<PatternSecondaryMuscleCredit>>,
) {
    val scale: Long get() = SCALE

    fun coeff(slot: FilledSlot, muscle: String): Double {
        if (slot.primaryMuscle.name == muscle) return PRIMARY_SET_CREDIT
        return credits[slot.pattern]
            ?.orEmpty()
            ?.firstOrNull { it.muscle.name == muscle }
            ?.creditPerSet
            ?: 0.0
    }

    fun scaledCoeff(slot: FilledSlot, muscle: String): Long =
        round(coeff(slot, muscle) * SCALE).toLong()

    fun musclesForPattern(pattern: MovementPattern): List<PatternSecondaryMuscleCredit> =
        credits[pattern].orEmpty()

    /** Movement-stress class for a slot from its pre-classified [RpRepProfile] and pattern. */
    fun slotLiftStressClass(slot: FilledSlot): LiftStressClass {
        if (slot.repProfile == RpRepProfile.LowerFreeWeightCompound) {
            return LiftStressClass.HeavyAxial
        }
        if (slot.repProfile == RpRepProfile.Isolation) {
            return LiftStressClass.Isolation
        }
        if (slot.pattern in MAJOR_COMPOUND_PATTERNS) {
            return LiftStressClass.UpperCompound
        }
        return LiftStressClass.Isolation
    }

    fun isHeavyAxialSlot(slot: FilledSlot): Boolean =
        slotLiftStressClass(slot) == LiftStressClass.HeavyAxial

    fun isUpperCompoundSlot(slot: FilledSlot): Boolean =
        slotLiftStressClass(slot) == LiftStressClass.UpperCompound

    companion object {
        const val SCALE: Long = 100L

        /** Static-table-only instance for tests/contexts without a lift catalog. */
        val Default: VolumeAllocationCoefficients =
            VolumeAllocationCoefficients(
                com.stratum.domain.program.generation.reference.PATTERN_SECONDARY_MUSCLE_CREDITS,
            )
    }
}
