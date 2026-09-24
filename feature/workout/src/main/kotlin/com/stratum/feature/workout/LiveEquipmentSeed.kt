package com.stratum.feature.workout

import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.Weight

/**
 * Tap-time inference inputs for one live lift card: the sets logged so far this session
 * (canonical Lb) with the lift's current effective tiers. Pure domain data — the UI
 * layer builds the editor request's routing from its rendered lift and seeds it from
 * this via `liveEquipmentEditRequestFor`, so lift routing has a single source of truth.
 */
data class LiveEquipmentSeed(
    val currentTiers: List<IncrementTier>,
    val loadType: LoadType,
    val loggedWeights: List<Weight>,
)
