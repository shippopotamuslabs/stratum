package com.stratum.core.testing.lift

import com.stratum.domain.lift.LiftNotesRepository
import com.stratum.domain.lift.LiftSessionNote
import com.stratum.domain.lift.PastSessionLiftNote
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
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
    ): LiftSessionNote? =
        notes.value.firstOrNull {
            it.sessionId == sessionId && it.workoutLiftId == workoutLiftId
        }

    override suspend fun upsertSessionNote(note: LiftSessionNote) {
        notes.value =
            notes.value.filterNot {
                it.sessionId == note.sessionId && it.workoutLiftId == note.workoutLiftId
            } + note
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
            .map { note ->
                PastSessionLiftNote(
                    sessionId = note.sessionId,
                    workoutLiftId = note.workoutLiftId,
                    body = note.body,
                    sessionStartedAt = sessionStartedAt[note.sessionId.value] ?: Instant.EPOCH,
                    workoutName = sessionWorkoutNames[note.sessionId.value],
                )
            }
            .sortedByDescending { it.sessionStartedAt }

    override suspend fun remapLiftIds(sourceLiftIds: Set<LiftId>, targetLiftId: LiftId) {
        if (sourceLiftIds.isEmpty()) return
        notes.value =
            notes.value.map { note ->
                if (note.liftId in sourceLiftIds) note.copy(liftId = targetLiftId) else note
            }
    }
}
