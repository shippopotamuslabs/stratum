package com.stratum.core.testing.workout

import com.stratum.domain.workout.ProgressionHistoryPort
import com.stratum.domain.workout.SlotHistoryEntry
import com.stratum.domain.workout.SlotHistoryRequest

/**
 * In-memory fake for [ProgressionHistoryPort] (UNIT_TESTING.md §6). Seeds entries and
 * returns them in the historical-priority order (n−1 first, then earlier sessions, then
 * same-lift-other-workout).
 *
 * Ordering (D3): entries matching the slot keys (`programId` + `workoutLiftId` +
 * `setIndex` + `liftId`) are partitioned into same-workout (the session's workout
 * matches the request's `workoutId`) and same-lift-other-workout, each group sorted by
 * `session.startedAt` descending (newest session first). Same-workout entries precede
 * other-workout entries.
 *
 * The seed request's `workoutId` is carried onto the returned session so callers can
 * distinguish same- vs other-workout entries by `session.workoutId` — mirroring what a
 * real repository query returns (the session belongs to the workout identified by the
 * slot it was logged under).
 */
class FakeProgressionHistoryRepository : ProgressionHistoryPort {
    private val seeded = mutableListOf<Pair<SlotHistoryRequest, SlotHistoryEntry>>()

    fun seed(request: SlotHistoryRequest, entry: SlotHistoryEntry) {
        seeded.add(request to entry)
    }

    override suspend fun historyForSlot(request: SlotHistoryRequest): List<SlotHistoryEntry> {
        val matched = seeded.filter { (req, _) ->
            req.programId == request.programId &&
                req.liftId == request.liftId &&
                req.setIndex == request.setIndex
        }

        val byStartedDesc = compareByDescending<SlotHistoryEntry> { it.session.startedAt }
        val (sameWorkout, otherWorkout) = matched.partition { (req, _) ->
            req.workoutId == request.workoutId
        }

        fun normalize(pair: Pair<SlotHistoryRequest, SlotHistoryEntry>): SlotHistoryEntry {
            val (req, entry) = pair
            return entry.copy(session = entry.session.copy(workoutId = req.workoutId))
        }

        return sameWorkout.map(::normalize).sortedWith(byStartedDesc) +
            otherWorkout.map(::normalize).sortedWith(byStartedDesc)
    }
}
