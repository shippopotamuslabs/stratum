package com.stratum.domain.workout.dropset

import com.stratum.domain.lift.Weight
import com.stratum.domain.program.SetType
import com.stratum.domain.workout.WorkoutLiftProjection
import com.stratum.domain.workout.fatigue.FatigueModel
import javax.inject.Inject

/** Constraint violation for an invalid drop-set placement (D7). */
enum class DropSetConstraintViolation { DropSetNotFollowingAnotherSet }

sealed interface DropSetConstraintResult {
    data object Valid : DropSetConstraintResult
    data class Invalid(val reasons: Set<DropSetConstraintViolation>) : DropSetConstraintResult
}

/**
 * Enforces the must-follow constraint (D7). A drop set MUST immediately follow another
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
 * Drop-set weight reduction (D7). The preceding set's weight is reduced by the
 * fatigue-model projection. The use case queries the [FatigueModel] with a 1:1
 * `baselineOneRm`-to-`targetWeight` marker (both equal to [precedingWeight]) to select
 * the drop-set reduction pathway; the model returns the **effective load percent** of
 * the preceding weight (an integer 1..100), which the use case scales the preceding
 * weight by. Per-lift slope/intercept override the population default when supplied.
 *
 * Pure.
 */
class EstimateDropSetWeightUseCase @Inject constructor(
    private val fatigueModel: FatigueModel,
) {
    operator fun invoke(
        precedingWeight: Weight,
        nextSetIndex: Int,
        perLiftSlope: Double?,
        perLiftIntercept: Double?,
    ): Weight {
        val loadPercent = fatigueModel.expectedRepsForAddedSet(
            baselineOneRm = precedingWeight.amount,
            targetWeight = precedingWeight,
            nextSetIndex = nextSetIndex,
            perLiftSlope = perLiftSlope,
            perLiftIntercept = perLiftIntercept,
        )
        val dropAmount = precedingWeight.amount * loadPercent / 100.0
        return Weight(dropAmount, precedingWeight.unit)
    }
}
