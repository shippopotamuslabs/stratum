package com.stratum.domain.workout.progression

import com.stratum.domain.training.EstimatedOneRm
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.workout.SlotHistoryEntry

/**
 * How a slot sources its progression-math baseline. The only two production modes —
 * there is no legacy fallback. The orchestrator ([com.stratum.domain.workout.
 * GenerateProgressionSnapshotUseCase]) builds a [RecentSessionBlock] per lift and
 * assigns one of these to each slot; the engine reads it instead of positional
 * `history.firstOrNull()` for its math.
 *
 * - [FromRecent] — this slot's math uses the entry at `performances[i]` of the
 *   capacity-descending recent block (so recommendations are monotonically
 *   non-increasing across slots, correcting misreported-RIR "reps going up" history).
 * - [ProjectFromPrior] — the most-recent session logged fewer sets than the current
 *   program (e.g. 5→3→5), so this slot has no recent entry. The orchestrator projects
 *   from the **immediately previous** slot recommendation via the fatigue model (one
 *   step), never from stale older-session positional history and never by re-anchoring
 *   every missing slot to the top set.
 *
 * `SlotProgressionRequest.progressionBaseline = null` is the genuine cold-start case
 * (no history for this lift at all).
 */
sealed interface ProgressionBaseline {
    data class FromRecent(val entry: SlotHistoryEntry) : ProgressionBaseline
    data object ProjectFromPrior : ProgressionBaseline
}

/**
 * One slot's performance in the most-recent session, ranked by [capacity].
 *
 * [capacity] is the apples-to-apples strength measure used for descending reorder:
 * `reps + (rir ?: 0)` when every set in the block shared the same weight (RIR-normalized
 * failure capacity), or [EstimatedOneRm] of `weight × (reps + (rir ?: 0))` when weights
 * differed (so a heavier lower-rep set can correctly outrank a lighter higher-rep one).
 */
data class RecentPerformance(
    val slotPosition: Int,
    val entry: SlotHistoryEntry,
    val capacity: Double,
)

/**
 * The most-recent session's sets for a lift, normalized and reordered by capacity for
 * progression math. [performances] is descending by [RecentPerformance.capacity]
 * (ties broken by `slotPosition` ascending — earlier slots win ties, preserving a
 * sensible order when RIR is missing or sets are identical). [positionToEntry] keeps
 * the positional actuals so the UI can show "what you did last time" honestly per slot.
 */
data class RecentSessionBlock(
    val performances: List<RecentPerformance>,
    val positionToEntry: Map<Int, SlotHistoryEntry>,
)

/**
 * From per-slot histories for one lift, find the most-recent session that logged at
 * least one set across these slots and build a capacity-ordered [RecentSessionBlock].
 *
 * Capacity measure is **uniform across the block**: if every entry shares the same
 * weight, all entries are ranked by RIR-normalized failure capacity
 * (`reps + (rir ?: 0)`); if any weight differs, all entries are ranked by
 * [EstimatedOneRm] of the failure-capacity load. This catches the "reps going up at
 * the same weight and similar RIR" pattern — those entries all share a weight, so the
 * later (higher-rep) sets correctly bubble to the top of the descending order, and the
 * engines then recommend monotonically non-increasing reps.
 *
 * Returns `null` only when [perSlotHistories] has no entries at all (cold start).
 */
fun buildRecentSessionBlock(
    perSlotHistories: Map<Int, List<SlotHistoryEntry>>,
): RecentSessionBlock? {
    val allEntries = perSlotHistories.flatMap { (position, history) ->
        history.map { position to it }
    }
    if (allEntries.isEmpty()) return null

    val recentSessionStartedAt = allEntries.maxOf { (_, entry) -> entry.session.startedAt }
    val recentByPosition = allEntries
        .filter { (_, entry) -> entry.session.startedAt == recentSessionStartedAt }
        .associate { (position, entry) -> position to entry }

    if (recentByPosition.isEmpty()) return null

    val weights = recentByPosition.values.map { it.loggedSet.weight.amount }
    val uniformWeight = weights.toSet().size == 1

    val performances = recentByPosition.entries
        .map { (position, entry) ->
            val capacity = capacityOf(entry.loggedSet, uniformWeight)
            RecentPerformance(slotPosition = position, entry = entry, capacity = capacity)
        }
        .sortedWith(
            compareByDescending<RecentPerformance> { it.capacity }
                .thenBy { it.slotPosition },
        )

    return RecentSessionBlock(
        performances = performances,
        positionToEntry = recentByPosition,
    )
}

private fun capacityOf(set: LoggedSet, uniformWeight: Boolean): Double {
    val failureReps = set.reps + (set.rir ?: 0)
    return if (uniformWeight) {
        failureReps.toDouble()
    } else {
        EstimatedOneRm.calculate(set.weight.amount, failureReps)
    }
}
