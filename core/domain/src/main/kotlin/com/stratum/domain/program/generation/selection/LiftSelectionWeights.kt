package com.stratum.domain.program.generation.selection

object LiftSelectionWeights {
    private var overrides: Map<String, Double>? = null

    fun setOverrides(newOverrides: Map<String, Double>?) {
        overrides = newOverrides
    }

    private fun getWeight(key: String, default: Double): Double {
        return overrides?.get(key) ?: default
    }

    // Core weights
    val PROVEN_TIER_WEIGHT: Double get() = getWeight("PROVEN_TIER_WEIGHT", 0.30)
    val RECENCY_WEIGHT: Double get() = getWeight("RECENCY_WEIGHT", 0.20)
    val EFFECTIVENESS_WEIGHT: Double get() = getWeight("EFFECTIVENESS_WEIGHT", 0.20)
    val PROGRESS_TREND_WEIGHT: Double get() = getWeight("PROGRESS_TREND_WEIGHT", 0.15)
    val EVIDENCE_WEIGHT: Double get() = getWeight("EVIDENCE_WEIGHT", 0.10)
    val HAS_INSIGHTS_WEIGHT: Double get() = getWeight("HAS_INSIGHTS_WEIGHT", 0.05)
    val MAINSTREAM_WEIGHT: Double get() = getWeight("MAINSTREAM_WEIGHT", 0.12)

    // Role bonuses
    val ANCHOR_MAINSTREAM_BONUS: Double get() = getWeight("ANCHOR_MAINSTREAM_BONUS", 0.30)
    val ANCHOR_BARBELL_BONUS: Double get() = getWeight("ANCHOR_BARBELL_BONUS", 0.15)
    val ANCHOR_BRAND_PENALTY: Double get() = getWeight("ANCHOR_BRAND_PENALTY", -0.20)
    val ANCHOR_MACHINE_PENALTY: Double get() = getWeight("ANCHOR_MACHINE_PENALTY", -0.10)
    val ACCESSORY_CABLE_MACHINE_BONUS: Double
        get() =
            getWeight(
                "ACCESSORY_CABLE_MACHINE_BONUS",
                getWeight("ISOLATION_CABLE_MACHINE_BONUS", 0.05),
            )
    /**
     * When an accessory slot also admits Medium fatigue (quad-iso/ham-iso), prefer true Low
     * fatigue lifts over Medium machines so leg press fills variety, not first pick.
     * Override keys accept legacy Isolation_* names for stored weight maps.
     */
    val ACCESSORY_TRUE_LOW_FATIGUE_BONUS: Double
        get() =
            getWeight(
                "ACCESSORY_TRUE_LOW_FATIGUE_BONUS",
                getWeight("ISOLATION_TRUE_KINEMATIC_BONUS", 0.25),
            )
    val ACCESSORY_MEDIUM_FALLBACK_PENALTY: Double
        get() =
            getWeight(
                "ACCESSORY_MEDIUM_FALLBACK_PENALTY",
                getWeight("ISOLATION_NON_AXIAL_FALLBACK_PENALTY", -0.08),
            )

    // Length bias
    val LENGTH_BIAS_BONUS: Double get() = getWeight("LENGTH_BIAS_BONUS", 0.10)

    // Cycle variety
    val CYCLE_VARIETY_USED_PENALTY: Double get() = getWeight("CYCLE_VARIETY_USED_PENALTY", -0.35)
    val CYCLE_VARIETY_FRESH_BONUS: Double get() = getWeight("CYCLE_VARIETY_FRESH_BONUS", 0.20)

    // Biomechanical adjustments
    val SYSTEMIC_FATIGUE_PENALTY: Double get() = getWeight("SYSTEMIC_FATIGUE_PENALTY", -0.35)
}
