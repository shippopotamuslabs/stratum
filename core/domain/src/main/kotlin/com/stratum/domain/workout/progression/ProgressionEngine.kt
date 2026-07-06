package com.stratum.domain.workout.progression

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
)

/**
 * Dispatch interface consumed by `GenerateProgressionSnapshotUseCase`. Each scheme has
 * a concrete implementation ([DoubleProgressionEngine], [LinearProgressionEngine]).
 */
interface ProgressionEngine {
    val scheme: ProgressionScheme
    fun recommend(request: SlotProgressionRequest): SlotRecommendation
}
