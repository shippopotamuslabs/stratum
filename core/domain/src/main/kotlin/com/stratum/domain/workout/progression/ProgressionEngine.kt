package com.stratum.domain.workout.progression

import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.Weight
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.workout.BACKOFF_REP_FLOOR
import com.stratum.domain.workout.SetSlot
import com.stratum.domain.workout.SlotHistoryEntry
import com.stratum.domain.workout.SlotRecommendation

/** Request for a [ProgressionEngine] per-slot recommendation. */
data class SlotProgressionRequest(
    val slot: SetSlot,
    val history: List<SlotHistoryEntry>,
    val fallbackOneRm: Weight?,
    val loadType: LoadType = LoadType.Weighted,
    val currentTopSetWeight: Weight? = null,
    val priorSlotRecommendation: SlotRecommendation? = null,
    val priorSlot: SetSlot? = null,
    val perLiftSlope: Double? = null,
    val perLiftIntercept: Double? = null,
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

/**
 * RIR-aware rep projection for a held weight (D8). When the load lever is unchanged
 * across sessions, the reps lever advances instead: a set logged at [prevReps] with
 * [prevRir] reps-in-reserve implies a projected-to-failure capacity of
 * `prevReps + prevRir`; the next session targets `failureCapacity − slot.rirGoal`.
 *
 * - **Top sets** (rep range present, [SetSlot.isTopSet] = true): the target is coerced
 *   into `[slot.repMin, slot.repMax]` so it never leaves the prescribed band.
 * - **Backoff sets** (RIR-only slots): the target is floored at [BACKOFF_REP_FLOOR]
 *   so a wide gap doesn't prescribe an unreasonable last-set rep count.
 *
 * Returns `null` when RIR data is absent — callers fall back to the previous rep count
 * (no rep-projection signal available). Pure and shared by both progression engines so
 * the rep-scaling math is canonical.
 */
fun rirAdjustedReps(
    prevReps: Int,
    prevRir: Int?,
    slot: SetSlot,
): Int? {
    if (prevRir == null) return null
    val failureCapacity = prevReps + prevRir
    val targetAtRir = failureCapacity - slot.rirGoal
    return if (slot.isTopSet) {
        targetAtRir.coerceIn(slot.repMin, slot.repMax)
    } else {
        targetAtRir.coerceAtLeast(BACKOFF_REP_FLOOR)
    }
}
