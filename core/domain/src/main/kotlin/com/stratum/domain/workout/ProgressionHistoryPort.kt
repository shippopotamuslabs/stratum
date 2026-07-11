package com.stratum.domain.workout

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession

/** Request for the historical-priority read (research Gap 4). */
data class SlotHistoryRequest(
    val programId: ProgramId,
    val workoutId: WorkoutId,
    val workoutLiftId: WorkoutLiftId,
    val liftId: LiftId,
    val setIndex: Int,
)

/** A single historical entry for a slot, across sessions. */
data class SlotHistoryEntry(
    val session: TrainingSession,
    val loggedSet: LoggedSet,
)

/**
 * Historical-priority read port (D3, research Gap 4). Returns sets for a slot across
 * sessions, ordered n−1, n−2, … (same lift + same workout + same position), then same
 * lift + same position from other workouts / imports. **Not** scoped to
 * [SlotHistoryRequest.programId] — imported history uses a sentinel program id and must
 * still feed live-session previous/recommendation baselines. Empty if no history.
 */
interface ProgressionHistoryPort {
    suspend fun historyForSlot(request: SlotHistoryRequest): List<SlotHistoryEntry>
}
