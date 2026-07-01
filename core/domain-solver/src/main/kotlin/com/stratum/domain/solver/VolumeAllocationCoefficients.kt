@file:Suppress("MagicNumber")

package com.stratum.domain.solver

import com.stratum.domain.program.generation.context.PRIMARY_SET_CREDIT
import com.stratum.domain.program.generation.prescription.FilledSlot
import com.stratum.domain.program.generation.reference.PatternSecondaryMuscleCredit

/**
 * Credit contribution of one set of [slot] to [muscle], matching
 * `muscleCreditsInSession` (`SlotSetAllocator.kt:95`) exactly:
 *  - [PRIMARY_SET_CREDIT] (1.0) when `slot.primaryMuscle == muscle`,
 *  - the [PatternSecondaryMuscleCredit.creditPerSet] for each entry on `slot.pattern` that targets
 *    `muscle` in the [credits] table,
 *  - 0 otherwise.
 *
 * CP-SAT linear expressions require integer coefficients, and credits use 0.5
 * increments, so the model scales every credit and target by [SCALE] (2×).
 * [scaledCoeff] returns the scaled integer coefficient; the caller
 * multiplies targets/caps by [SCALE] consistently.
 *
 * The [credits] table is typically [computePatternSecondaryCredits]-derived (catalog-aware) so the
 * solver's volume accounting matches the expected realized metadata volume rather than a static
 * idealization — see [computePatternSecondaryCredits] for why this matters.
 */
class VolumeAllocationCoefficients(
    private val credits: Map<String, List<PatternSecondaryMuscleCredit>>,
) {
    val scale: Long get() = SCALE

    fun coeff(slot: FilledSlot, muscle: String): Double {
        if (slot.primaryMuscle == muscle) return PRIMARY_SET_CREDIT
        return credits[slot.pattern]
            ?.orEmpty()
            ?.firstOrNull { it.muscle == muscle }
            ?.creditPerSet
            ?: 0.0
    }

    fun scaledCoeff(slot: FilledSlot, muscle: String): Long =
        (coeff(slot, muscle) * SCALE).toLong()

    fun musclesForPattern(pattern: String): List<PatternSecondaryMuscleCredit> =
        credits[pattern].orEmpty()

    companion object {
        const val SCALE: Long = 2L

        /** Static-table-only instance for tests/contexts without a lift catalog. */
        val Default: VolumeAllocationCoefficients =
            VolumeAllocationCoefficients(
                com.stratum.domain.program.generation.reference.PATTERN_SECONDARY_MUSCLE_CREDITS,
            )
    }
}
