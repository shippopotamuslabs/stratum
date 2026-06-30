@file:Suppress("MagicNumber")

package com.stratum.domain.solver

import com.stratum.domain.program.generation.context.PRIMARY_SET_CREDIT
import com.stratum.domain.program.generation.context.SECONDARY_SET_CREDIT
import com.stratum.domain.program.generation.prescription.FilledSlot
import com.stratum.domain.program.generation.reference.PATTERN_SECONDARY_MUSCLE_CREDITS

/**
 * Credit contribution of one set of [slot] to [muscle], matching
 * `muscleCreditsInSession` (`SlotSetAllocator.kt:95`) exactly:
 *  - [PRIMARY_SET_CREDIT] (1.0) when `slot.primaryMuscle == muscle`,
 *  - [SECONDARY_SET_CREDIT] (0.5) for each `PATTERN_SECONDARY_MUSCLE_CREDITS`
 *    entry on `slot.pattern` that targets `muscle`,
 *  - 0 otherwise.
 *
 * CP-SAT linear expressions require integer coefficients, and credits use 0.5
 * increments, so the model scales every credit and target by [SCALE] (2×).
 * [scaledCoeff] returns the scaled integer coefficient (2 or 1); the caller
 * multiplies targets/caps by [SCALE] consistently.
 */
object VolumeAllocationCoefficients {
    const val SCALE: Long = 2L

    fun coeff(slot: FilledSlot, muscle: String): Double {
        if (slot.primaryMuscle == muscle) return PRIMARY_SET_CREDIT
        return PATTERN_SECONDARY_MUSCLE_CREDITS[slot.pattern]
            ?.orEmpty()
            ?.firstOrNull { it.muscle == muscle }
            ?.creditPerSet
            ?: 0.0
    }

    fun scaledCoeff(slot: FilledSlot, muscle: String): Long =
        (coeff(slot, muscle) * SCALE).toLong()
}
