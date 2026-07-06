package com.stratum.domain.workout.progression

import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.Weight
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.workout.SetSlot
import com.stratum.domain.workout.SlotHistoryEntry
import com.stratum.domain.workout.SlotRecommendation

/** Request for a [ProgressionEngine] per-slot recommendation. */
data class SlotProgressionRequest(
    val slot: SetSlot,
    val history: List<SlotHistoryEntry>,
    val fallbackOneRm: Weight?,
    val loadType: LoadType = LoadType.Weighted,
)

/**
 * Dispatch interface consumed by `GenerateProgressionSnapshotUseCase`. Each scheme has
 * a concrete implementation ([DoubleProgressionEngine], [LinearProgressionEngine]).
 */
interface ProgressionEngine {
    val scheme: ProgressionScheme
    fun recommend(request: SlotProgressionRequest): SlotRecommendation
}

/**
 * Clamps an assisted progression result at 0 so the weight never crosses into positive
 * (a positive assisted weight would mean the lifter is now *adding* load, which is
 * weighted territory — graduation from assistance is signaled by reaching 0, after which
 * the lifter should swap to the bodyweight or weighted variant). No-op for non-assisted
 * load types. Shared by both progression engines so the cap logic is canonical.
 */
fun Weight.capAssistedAtZero(loadType: LoadType): Weight =
    if (loadType == LoadType.Assisted && amount > 0.0) Weight(0.0, unit) else this
