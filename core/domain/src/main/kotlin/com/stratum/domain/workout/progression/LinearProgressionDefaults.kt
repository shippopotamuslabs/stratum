package com.stratum.domain.workout.progression

import com.stratum.domain.lift.Weight

/** Production default tuning knobs for [LinearProgressionEngine] (D8 Q1a). */
object LinearProgressionDefaults {
    private fun lb(amount: Double) = Weight(amount, Weight.WeightUnit.Lb)

    val config: LinearProgressionConfig =
        LinearProgressionConfig(
            lowerBodyIncrement = lb(5.0),
            upperBodyIncrement = lb(2.5),
            minimumIncrement = lb(2.5),
            perLiftIncrementOverride = emptyMap(),
            stallThresholdSessions = 2,
            resetFraction = 0.10,
            perLiftResetFractionOverride = emptyMap(),
            resetsBeforeTransition = 2,
            stallStrictness = StallStrictness.RirBased,
        )
}
