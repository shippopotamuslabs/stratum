package com.stratum.core.testing.workout

import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.workout.ProgressionHistoryPort
import com.stratum.domain.workout.SlotHistoryEntry
import com.stratum.domain.workout.SlotHistoryRequest

/**
 * In-memory fake for [ProgressionHistoryPort] (UNIT_TESTING.md §6). Seeds entries and
 * returns them in the historical-priority order (n−1 first, then earlier sessions, then
 * same-lift-other-workout / import).
 *
 * Ordering (D3): entries matching the slot keys (`liftId` + `setIndex`) are partitioned
 * into same-workout (the seed request's `workoutId` matches the query's `workoutId`) and
 * same-lift-other-workout, each group sorted by `session.startedAt` descending (newest
 * first). Same-workout entries precede other-workout entries. **Program id is ignored** so
 * imported history (sentinel program `import-history`) is visible to live program sessions
 * — mirrors [com.stratum.core.data.repository.ProgressionHistoryRepository].
 *
 * Only [TrainingSessionStatus.COMPLETED] entries are returned (matches Room history query).
 *
 * The seed request's `workoutId` is carried onto the returned session so callers can
 * distinguish same- vs other-workout entries by `session.workoutId` — mirroring what a
 * real repository query returns (the session belongs to the workout identified by the
 * slot it was logged under). Seeds with a null/`import` workout id keep the entry's own
 * session workout id (typically null for imports).
 */
class FakeProgressionHistoryRepository : ProgressionHistoryPort {
    private val seeded = mutableListOf<Pair<SlotHistoryRequest, SlotHistoryEntry>>()

    fun seed(request: SlotHistoryRequest, entry: SlotHistoryEntry) {
        seeded.add(request to entry)
    }

    override suspend fun historyForSlot(request: SlotHistoryRequest): List<SlotHistoryEntry> {
        val matched = seeded.filter { (req, entry) ->
            req.liftId == request.liftId &&
                req.setIndex == request.setIndex &&
                entry.session.status == TrainingSessionStatus.COMPLETED
        }

        val byStartedDesc = compareByDescending<SlotHistoryEntry> { it.session.startedAt }
        val (sameWorkout, otherWorkout) = matched.partition { (req, _) ->
            req.workoutId == request.workoutId
        }

        fun normalize(pair: Pair<SlotHistoryRequest, SlotHistoryEntry>): SlotHistoryEntry {
            val (req, entry) = pair
            // Real imports keep null workoutId (other-workout bucket for any live request).
            // Seeds that already carry a workout id get the seed request's workout stamped
            // so same- vs other-workout partitioning mirrors Room.
            if (entry.session.workoutId == null) {
                return entry
            }
            return entry.copy(session = entry.session.copy(workoutId = req.workoutId))
        }

        return sameWorkout.map(::normalize).sortedWith(byStartedDesc) +
            otherWorkout.map(::normalize).sortedWith(byStartedDesc)
    }
}
