package com.stratum.core.infrastructure.repository

import com.stratum.core.infrastructure.db.StratumDatabase
import com.stratum.domain.lift.identity.LiftId

/** Ids of the template rows a lift-reference sweep removed (for sync delete records). */
internal data class LiftReferenceSweep(
    val slotIds: List<String>,
    val setIds: List<String>,
    val noteIds: List<String>,
)

/**
 * The STR-131 reference sweep, shared by the local Gym delete, the sync-receive
 * tombstone path, and the received-hard-delete path so all three converge on
 * identical local state:
 *
 * - every template slot (`workout_lifts`) pointing at [liftId] across all
 *   programs is deleted (its template sets and slot notes CASCADE);
 * - every `workout_lift_notes` row scoped to the lift by its pair `lift_id`
 *   is deleted regardless of which slot currently occupies it — the STR-127
 *   stash sweep (a stashed note for a deleted lift must not outlive it,
 *   because the lift can never be swapped back in).
 *
 * Logged sets, catalog notes, and session notes are never touched. Callers
 * run this inside their own transaction; ids are captured before deletion so
 * the local-delete caller can enqueue sync delete records for the swept rows.
 */
internal suspend fun sweepLiftTemplateReferences(
    database: StratumDatabase,
    liftId: LiftId,
): LiftReferenceSweep {
    val workoutLiftDao = database.workoutLiftDao()
    val liftSetDao = database.liftSetDao()
    val noteDao = database.workoutLiftNoteDao()

    val slots = workoutLiftDao.getByLiftId(liftId)
    val slotIds = slots.map { it.id }
    val setIds = slotIds.flatMap { liftSetDao.idsForWorkoutLift(it.value) }
    val slotNoteIds = slotIds.flatMap { noteDao.idsForWorkoutLift(it) }
    val stashNoteIds = noteDao.getForLiftId(liftId).map { it.id }

    noteDao.deleteByLiftId(liftId)
    workoutLiftDao.deleteByLiftId(liftId)

    return LiftReferenceSweep(
        slotIds = slotIds.map { it.value },
        setIds = setIds,
        noteIds = (slotNoteIds + stashNoteIds).distinct().map { it.value },
    )
}
