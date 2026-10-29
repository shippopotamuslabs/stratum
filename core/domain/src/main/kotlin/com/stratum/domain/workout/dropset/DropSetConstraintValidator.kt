package com.stratum.domain.workout.dropset

import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.incrementForAmount
import com.stratum.domain.lift.roundToIncrement
import com.stratum.domain.lift.toStorageLb
import com.stratum.domain.program.SetType
import com.stratum.domain.workout.BACKOFF_REP_FLOOR
import com.stratum.domain.workout.WorkoutLiftProjection
import com.stratum.domain.workout.fatigue.defaultLoadBumpAmount
import com.stratum.domain.workout.fatigue.reduceWeightForLastSetFloor
import javax.inject.Inject
import kotlin.math.floor

/** Constraint violation for an invalid drop-set placement. */
enum class DropSetConstraintViolation { DropSetNotFollowingAnotherSet }

sealed interface DropSetConstraintResult {
    data object Valid : DropSetConstraintResult
    data class Invalid(val reasons: Set<DropSetConstraintViolation>) : DropSetConstraintResult
}

/**
 * Enforces the must-follow constraint. A drop set MUST immediately follow another
 * set of the same lift — i.e. a `DropSet` slot may not occupy position 0. Pure.
 */

class DropSetConstraintValidator @Inject constructor() {
    operator fun invoke(lift: WorkoutLiftProjection): DropSetConstraintResult {
        val firstIsDrop = lift.slots.firstOrNull()?.setType == SetType.DropSet
        return if (firstIsDrop) {
            DropSetConstraintResult.Invalid(setOf(DropSetConstraintViolation.DropSetNotFollowingAnotherSet))
        } else {
            DropSetConstraintResult.Valid
        }
    }
}

/**
 * Drop-set weight: **20% off** [precedingWeight], rounded **down** onto the load lattice
 * so the result is ≤ 80% of prior. Optionally further reduces when [capacityAtRir] reports
 * last-set capacity below [BACKOFF_REP_FLOOR] (5) at RIR 0.
 *
 * Replaces the old fatigue-percent primary formula.
 */

class EstimateDropSetWeightUseCase @Inject constructor() {
    operator fun invoke(
        precedingWeight: Weight,
        incrementTiers: List<IncrementTier> = emptyList(),
        loadType: LoadType = LoadType.Weighted,
        capacityAtRir: ((weightAmountLb: Double) -> Int)? = null,
        micro: List<Weight> = emptyList(),
    ): Weight {
        if (loadType == LoadType.Bodyweight || loadType == LoadType.Assisted) {
            return precedingWeight
        }
        if (precedingWeight.amount <= 0.0) return precedingWeight

        val raw = Weight(precedingWeight.amount * DROP_FRACTION, precedingWeight.unit)
        val roundedDown = roundDownToIncrement(raw, incrementTiers, loadType, micro)
        val capped = if (roundedDown.amount > raw.amount) {
            stepDown(roundedDown, incrementTiers, loadType, micro)
        } else {
            roundedDown
        }

        if (capacityAtRir == null) return capped

        val capacity = capacityAtRir(capped.toStorageLb().amount)
        if (capacity >= BACKOFF_REP_FLOOR) return capped

        return reduceWeightForLastSetFloor(
            startFrom = capped,
            incrementTiers = incrementTiers,
            loadType = loadType,
            capacityAtRir = capacityAtRir,
            defaultBumpAmount = ::defaultLoadBumpAmount,
            micro = micro,
        )
    }

    private fun roundDownToIncrement(
        weight: Weight,
        incrementTiers: List<IncrementTier>,
        loadType: LoadType,
        micro: List<Weight> = emptyList(),
    ): Weight {
        if (incrementTiers.isEmpty() || loadType == LoadType.Bodyweight) return weight
        val amount = weight.amount
        val inc = incrementTiers.incrementForAmount(amount)
        if (inc <= 0.0) return weight
        val floored = floor(amount / inc) * inc
        val candidate = Weight(floored.coerceAtLeast(0.0), weight.unit)
        // Guard: nearest-round helpers can land above target; enforce ≤ raw.
        val nearest = incrementTiers.roundToIncrement(candidate, loadType, micro)
        return if (nearest.amount <= weight.amount) nearest else candidate
    }

    private fun stepDown(
        weight: Weight,
        incrementTiers: List<IncrementTier>,
        loadType: LoadType,
        micro: List<Weight> = emptyList(),
    ): Weight {
        if (incrementTiers.isEmpty()) {
            val step = defaultLoadBumpAmount(weight.unit)
            return Weight((weight.amount - step).coerceAtLeast(step), weight.unit)
        }
        val inc = incrementTiers.incrementForAmount(weight.amount).takeIf { it > 0.0 }
            ?: defaultLoadBumpAmount(weight.unit)
        val lowered = Weight((weight.amount - inc).coerceAtLeast(0.0), weight.unit)
        return incrementTiers.roundToIncrement(lowered, loadType, micro)
    }

    companion object {
        /** Fraction of prior load retained after a drop (20% reduction → 80% load). */
        const val DROP_FRACTION: Double = 0.80
    }
}
