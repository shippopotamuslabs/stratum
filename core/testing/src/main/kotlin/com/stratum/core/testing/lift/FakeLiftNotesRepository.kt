package com.stratum.core.testing.lift

import com.stratum.application.lift.LiftNotesRepository
import com.stratum.domain.lift.LiftSessionNote
import com.stratum.domain.lift.PastSessionLiftNote
import com.stratum.domain.lift.SessionNoteInput
import com.stratum.domain.lift.sanitizeLiftNoteBody
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.identity.LiftSessionNoteId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow

class FakeLiftNotesRepository : LiftNotesRepository {
    private val notes = MutableStateFlow<List<LiftSessionNote>>(emptyList())
    private val completedSessionIds = mutableSetOf<String>()
    private val sessionStartedAt = mutableMapOf<String, Instant>()
    private val sessionWorkoutNames = mutableMapOf<String, String?>()

    fun seed(list: List<LiftSessionNote>) {
        notes.value = list
    }

    fun markSessionCompleted(
        sessionId: TrainingSessionId,
        startedAt: Instant,
        workoutName: String?,
    ) {
        completedSessionIds += sessionId.value
        sessionStartedAt[sessionId.value] = startedAt
        sessionWorkoutNames[sessionId.value] = workoutName
    }

    override suspend fun getSessionNote(
        sessionId: TrainingSessionId,
        workoutLiftId: WorkoutLiftId,
    ): LiftSessionNote? = getSessionNotes(sessionId, workoutLiftId).firstOrNull()

    /**
     * Surgical single-row write: updates or inserts only this note's row, retaining
     * sibling rows in the slot (mirrors Room).
     */
    override suspend fun upsertSessionNote(note: LiftSessionNote) {
        notes.value = notes.value.filterNot { it.id == note.id } + note
    }

    override suspend fun deleteSessionNote(
        sessionId: TrainingSessionId,
        workoutLiftId: WorkoutLiftId,
    ) {
        notes.value =
            notes.value.filterNot {
                it.sessionId == sessionId && it.workoutLiftId == workoutLiftId
            }
    }

    override suspend fun getSessionNotes(
        sessionId: TrainingSessionId,
        workoutLiftId: WorkoutLiftId,
    ): List<LiftSessionNote> =
        notes.value
            .filter { it.sessionId == sessionId && it.workoutLiftId == workoutLiftId }
            .sortedBy { it.position }

    override suspend fun replaceSessionNotes(
        sessionId: TrainingSessionId,
        workoutLiftId: WorkoutLiftId,
        liftId: LiftId,
        notes: List<SessionNoteInput>,
    ) {
        val now = Instant.now()
        // Id retention is scoped to the target slot: an id minted for another
        // slot is unknown here, so it mints a new row (port contract).
        val currentIds =
            this.notes.value
                .filter { it.sessionId == sessionId && it.workoutLiftId == workoutLiftId }
                .map { it.id }
                .toSet()
        val minted =
            notes.mapNotNull { input ->
                // Port contract: blank bodies are dropped, overlong bodies truncate.
                sanitizeLiftNoteBody(input.body)?.let { body -> input to body }
            }.mapIndexed { index, (input, body) ->
                LiftSessionNote(
                    id = input.id?.takeIf { it in currentIds }
                        ?: LiftSessionNoteId(UUID.randomUUID().toString()),
                    sessionId = sessionId,
                    liftId = liftId,
                    workoutLiftId = workoutLiftId,
                    body = body,
                    updatedAt = now,
                    position = index,
                )
            }
        this.notes.value =
            this.notes.value.filterNot {
                it.sessionId == sessionId && it.workoutLiftId == workoutLiftId
            } + minted
    }

    override suspend fun getPastNotesForLift(
        liftId: LiftId,
        excludeSessionId: TrainingSessionId?,
    ): List<PastSessionLiftNote> =
        notes.value
            .filter { note ->
                note.liftId == liftId &&
                    note.body.isNotBlank() &&
                    note.sessionId.value in completedSessionIds &&
                    note.sessionId != excludeSessionId
            }
            .sortedWith(
                compareByDescending<LiftSessionNote> {
                    sessionStartedAt[it.sessionId.value] ?: Instant.EPOCH
                }.thenBy { it.position },
            )
            .map { note ->
                PastSessionLiftNote(
                    sessionId = note.sessionId,
                    workoutLiftId = note.workoutLiftId,
                    body = note.body,
                    sessionStartedAt = sessionStartedAt[note.sessionId.value] ?: Instant.EPOCH,
                    workoutName = sessionWorkoutNames[note.sessionId.value],
                )
            }

    override suspend fun remapLiftIds(sourceLiftIds: Set<LiftId>, targetLiftId: LiftId) {
        if (sourceLiftIds.isEmpty()) return
        notes.value =
            notes.value.map { note ->
                if (note.liftId in sourceLiftIds) note.copy(liftId = targetLiftId) else note
            }
    }
}
