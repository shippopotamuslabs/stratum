package com.stratum.domain.lift

/**
 * A weight magnitude together with its unit. Introduced in OS-4 as the unit for increment
 * tiers (§2.2 mandates a domain `Weight` type). Unit selection is not exposed in onboarding
 * — the seed catalog ships lb tiers (the §10 "lb vs kg default" question is still open);
 * [Kg] exists so a future units setting can hold the same amount in either system.
 *
 * Pure value type — no arithmetic surface beyond what tiers need; comparisons go through the
 * amount in the lift's own unit (tiers on a single lift share a unit).
 */
data class Weight(
    val amount: Double,
    val unit: WeightUnit,
) {
    enum class WeightUnit { Lb, Kg }
}
