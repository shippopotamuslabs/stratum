package com.stratum.domain.training

import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import com.stratum.domain.program.Workout

@DisplayName("TrainingSession lifecycle (STR-166)")
class TrainingSessionTest {

    private val start: Instant = Instant.parse("2025-01-01T10:00:00Z")
    private val end: Instant = Instant.parse("2025-01-01T11:00:00Z")

    private fun inProgress(): TrainingSession = WorkoutLoggingFixtures.inProgressSession(id = "ts-1")

    private fun completed(endedAt: Instant? = end): TrainingSession =
        WorkoutLoggingFixtures.completedLiveSession(id = "ts-1").copy(endedAt = endedAt)

    @Nested
    @DisplayName("constructor invariant")
    inner class ConstructorInvariant {
        @Test
        fun `endedAt before startedAt is rejected`() {
            assertThrows<IllegalArgumentException> {
                inProgress().copy(endedAt = start.minusSeconds(1))
            }
        }

        @Test
        fun `endedAt equal to startedAt is legal (skip ledger shape)`() {
            val session = completed(endedAt = start)
            assertEquals(start, session.endedAt)
        }

        @Test
        fun `completed with null endedAt is legal (imported, end unknown)`() {
            val session = completed(endedAt = null)
            assertEquals(TrainingSessionStatus.COMPLETED, session.status)
            assertNull(session.endedAt)
        }
    }

    @Nested
    @DisplayName("endedAt semantics")
    inner class EndedAtSemantics {
        @Test
        fun `completedAt is endedAt when known`() {
            assertEquals(end, completed().completedAt)
        }

        @Test
        fun `completedAt falls back to startedAt when end unknown`() {
            assertEquals(start, completed(endedAt = null).completedAt)
        }

        @Test
        fun `liveEnd is endedAt when present`() {
            assertEquals(end, completed().liveEnd(now = end.plusSeconds(3600)))
        }

        @Test
        fun `liveEnd falls back to now while not ended`() {
            val now = Instant.parse("2025-01-01T10:30:00Z")
            assertEquals(now, inProgress().liveEnd(now = now))
        }

        @Test
        fun `endOr uses the caller fallback only when end unknown`() {
            val fallback = Instant.parse("2025-01-01T12:00:00Z")
            assertEquals(end, completed().endOr(fallback))
            assertEquals(fallback, completed(endedAt = null).endOr(fallback))
        }
    }

    @Nested
    @DisplayName("guards")
    inner class Guards {
        @Test
        fun `requireInProgress returns the in-progress session`() {
            assertEquals(inProgress(), inProgress().requireInProgress())
        }

        @Test
        fun `requireInProgress throws NotInProgress for a completed session`() {
            val error = assertThrows<TrainingSessionNotInProgressException> {
                completed().requireInProgress()
            }
            assertEquals(TrainingSessionStatus.COMPLETED, error.actualStatus)
        }

        @Test
        fun `requireCompleted returns the completed session`() {
            assertEquals(completed(), completed().requireCompleted())
        }

        @Test
        fun `requireCompleted throws for an in-progress session`() {
            assertThrows<IllegalArgumentException> { inProgress().requireCompleted() }
        }
    }

    @Nested
    @DisplayName("complete")
    inner class Complete {
        @Test
        fun `complete marks COMPLETED and stamps the end`() {
            val done = inProgress().complete(at = end)
            assertEquals(TrainingSessionStatus.COMPLETED, done.status)
            assertEquals(end, done.endedAt)
            assertEquals(end, done.completedAt)
        }

        @Test
        fun `complete on a completed session throws NotInProgress`() {
            assertThrows<TrainingSessionNotInProgressException> { completed().complete(at = end) }
        }

        @Test
        fun `complete before the start throws`() {
            assertThrows<IllegalArgumentException> {
                inProgress().complete(at = start.minusSeconds(1))
            }
        }

        @Test
        fun `complete at exactly the start is legal`() {
            assertEquals(start, inProgress().complete(at = start).endedAt)
        }
    }

    @Nested
    @DisplayName("retime")
    inner class Retime {
        @Test
        fun `retime moves both timestamps on a completed session`() {
            val moved = completed().retime(
                newStartedAt = Instant.parse("2025-06-01T14:00:00Z"),
                newEndedAt = Instant.parse("2025-06-01T15:00:00Z"),
            )
            assertEquals(Instant.parse("2025-06-01T14:00:00Z"), moved.startedAt)
            assertEquals(Instant.parse("2025-06-01T15:00:00Z"), moved.endedAt)
        }

        @Test
        fun `retime start after finish throws with the user-facing message`() {
            val newStart = Instant.parse("2025-06-01T16:00:00Z")
            val newEnd = Instant.parse("2025-06-01T15:00:00Z")
            val error = assertThrows<IllegalArgumentException> {
                completed().retime(newStartedAt = newStart, newEndedAt = newEnd)
            }
            assertEquals(
                "Start time ($newStart) cannot be after finish time ($newEnd)",
                error.message,
            )
        }

        @Test
        fun `retime on an in-progress session throws`() {
            assertThrows<IllegalArgumentException> {
                inProgress().retime(newStartedAt = start, newEndedAt = end)
            }
        }
    }

    @Nested
    @DisplayName("factories")
    inner class Factories {
        @Test
        fun `started builds a live in-progress session with no end`() {
            val session = TrainingSession.started(
                id = TrainingSessionId("ts-new"),
                programId = ProgramId("prog-1"),
                workoutId = WorkoutId("wkt-a"),
                kind = TrainingSessionKind.PROGRAM_WORKOUT,
                cycleNumberSnapshot = 2,
                weekdaySnapshot = Weekday.Monday,
                cycleSlotIndexSnapshot = 0,
                startedAt = start,
                programNameSnapshot = "Test Program",
                workoutNameSnapshot = "Workout A",
            )
            assertEquals(TrainingSessionStatus.IN_PROGRESS, session.status)
            assertEquals(TrainingSessionOrigin.LIVE, session.origin)
            assertNull(session.endedAt)
            assertEquals(start, session.startedAt)
        }

        @Test
        fun `skipped builds a completed row that starts and ends at the same instant`() {
            val session = TrainingSession.skipped(
                id = TrainingSessionId("ts-skip"),
                programId = ProgramId("prog-1"),
                workoutId = null,
                kind = TrainingSessionKind.REST_DAY,
                cycleNumberSnapshot = 1,
                weekdaySnapshot = Weekday.Tuesday,
                cycleSlotIndexSnapshot = 1,
                at = start,
                programNameSnapshot = "Test Program",
            )
            assertEquals(TrainingSessionStatus.COMPLETED, session.status)
            assertEquals(TrainingSessionKind.REST_DAY, session.kind)
            assertEquals(start, session.startedAt)
            assertEquals(start, session.endedAt)
        }

        @Test
        fun `imported builds a completed ad-hoc import session`() {
            val session = TrainingSession.imported(
                id = TrainingSessionId("ts-import"),
                programId = ProgramId("import-history"),
                startedAt = start,
                endedAt = end,
                sourceApp = "liftlab",
                programNameSnapshot = "Import History",
            )
            assertEquals(TrainingSessionStatus.COMPLETED, session.status)
            assertEquals(TrainingSessionKind.AD_HOC, session.kind)
            assertEquals(TrainingSessionOrigin.IMPORT, session.origin)
            assertEquals(end, session.completedAt)
        }

        @Test
        fun `imported with unknown end is legitimate and completes at its start`() {
            val session = TrainingSession.imported(
                id = TrainingSessionId("ts-import-unknown-end"),
                programId = ProgramId("import-history"),
                startedAt = start,
                endedAt = null,
                sourceApp = null,
                programNameSnapshot = "Import History",
            )
            assertNull(session.endedAt)
            assertEquals(start, session.completedAt)
        }

        @Test
        fun `imported with end before start is rejected`() {
            assertThrows<IllegalArgumentException> {
                TrainingSession.imported(
                    id = TrainingSessionId("ts-import-bad"),
                    programId = ProgramId("import-history"),
                    startedAt = end,
                    endedAt = start,
                    sourceApp = null,
                )
            }
        }
    }
}
