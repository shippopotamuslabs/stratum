package com.stratum.domain.workout

import com.stratum.domain.importing.EstimatedOneRm
import com.stratum.domain.workout.fatigue.FatigueModel
import javax.inject.Inject

/**
 * Add-set rep estimation (D5): copy previous recommended weight; estimate expected
 * reps via the [FatigueModel] projected one set-index forward. The baseline e1RM is
 * derived from the previous recommendation's weight and baseline expected reps.
 */
class EstimateAddedSetRepsUseCase @Inject constructor(
    private val fatigueModel: FatigueModel,
) {
    operator fun invoke(
        previousRecommendation: SlotRecommendation,
        nextSetIndex: Int,
        perLiftSlope: Double?,
        perLiftIntercept: Double?,
    ): Int {
        val baselineOneRm = EstimatedOneRm.calculate(
            previousRecommendation.recommendedWeight.amount,
            previousRecommendation.baselineExpectedReps,
        )
        return fatigueModel.expectedRepsForAddedSet(
            baselineOneRm = baselineOneRm,
            targetWeight = previousRecommendation.recommendedWeight,
            nextSetIndex = nextSetIndex,
            perLiftSlope = perLiftSlope,
            perLiftIntercept = perLiftIntercept,
        )
    }
}
