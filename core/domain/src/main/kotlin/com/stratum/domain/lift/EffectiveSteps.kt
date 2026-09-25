package com.stratum.domain.lift

/**
 * A lift's effective increment steps as one value: major bands plus adder stops.
 *
 * Distinct from [EffectiveIncrementTiers] (which records override-resolution
 * provenance for editors): this is the lean snapshot consumers compare and pass
 * around — chip fingerprinting, per-lift runtime lookups, snapshot inputs — so
 * tiers and micro cannot drift out of sync across parallel maps. Data-class
 * equality is the comparison semantics; no custom fingerprint type needed.
 */
data class EffectiveSteps(
    val tiers: List<IncrementTier>,
    val microIncrements: List<Weight>,
)
