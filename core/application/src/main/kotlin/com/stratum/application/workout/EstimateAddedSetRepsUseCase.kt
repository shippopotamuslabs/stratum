package com.stratum.application.workout

import com.stratum.domain.lift.WeightConversion
import com.stratum.domain.training.EstimatedOneRm
import com.stratum.domain.workout.fatigue.FatigueModel
import javax.inject.Inject
import com.stratum.domain.workout.SlotRecommendation

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
        val loadMagnitudeLb =
            WeightConversion.loadMagnitudeLb(previousRecommendation.recommendedWeight)
        if (loadMagnitudeLb <= 0.0 || previousRecommendation.baselineExpectedReps <= 0) {
            return previousRecommendation.baselineExpectedReps.coerceAtLeast(1)
        }
        val baselineOneRm = EstimatedOneRm.calculate(
            loadMagnitudeLb,
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
