package com.stratum.application.lift

import com.stratum.core.testing.lift.FakeLiftNotesRepository
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.identity.LiftSessionNoteId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.lift.LiftSessionNote

class GetPastSessionNotesForLiftUseCaseTest {

    @Test
    @DisplayName("past notes are completed, this lift, exclude current, newest first")
    fun filtersAndOrders() = runTest {
        val notes = FakeLiftNotesRepository()
        val liftId = LiftId("squat")
        val otherLift = LiftId("bench")
        val current = TrainingSessionId("ts-current")
        val older = TrainingSessionId("ts-old")
        val newer = TrainingSessionId("ts-new")
        val inProgress = TrainingSessionId("ts-live")

        notes.upsertSessionNote(note("n-old", older, liftId, "old body"))
        notes.upsertSessionNote(note("n-new", newer, liftId, "new body"))
        notes.upsertSessionNote(note("n-blank", newer, liftId, "  ").copy(body = ""))
        notes.upsertSessionNote(note("n-other", newer, otherLift, "other lift"))
        notes.upsertSessionNote(note("n-live", inProgress, liftId, "live"))
        notes.upsertSessionNote(note("n-cur", current, liftId, "current"))

        notes.markSessionCompleted(older, Instant.parse("2026-01-01T00:00:00Z"), "Lower A")
        notes.markSessionCompleted(newer, Instant.parse("2026-08-01T00:00:00Z"), "Lower B")
        notes.markSessionCompleted(current, Instant.parse("2026-08-20T00:00:00Z"), "Today")

        val useCase = GetPastSessionNotesForLiftUseCase(notes)
        val past = useCase(liftId, excludeSessionId = current)

        assertEquals(listOf("new body", "old body"), past.map { it.body })
        assertEquals("Lower B", past.first().workoutName)
    }

    private fun note(
        id: String,
        sessionId: TrainingSessionId,
        liftId: LiftId,
        body: String,
    ): LiftSessionNote =
        LiftSessionNote(
            id = LiftSessionNoteId(id),
            sessionId = sessionId,
            liftId = liftId,
            workoutLiftId = WorkoutLiftId("wl-$id"),
            body = body,
            updatedAt = Instant.EPOCH,
        )
}
