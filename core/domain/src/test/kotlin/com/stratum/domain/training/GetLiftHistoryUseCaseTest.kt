package com.stratum.domain.training

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.Program
import com.stratum.domain.program.Workout
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class GetLiftHistoryUseCaseTest {

    private val liftId = LiftId("bench-press")
    private val programId = ProgramId("prog-1")
    private val workoutId = WorkoutId("w-push")

    @Test
    @DisplayName("Given no sets when loaded then history is empty with null PR")
    fun emptyHistory() = runTest {
        val useCase =
            GetLiftHistoryUseCase(
                trainingLogRepository = FakeTrainingLogRepository(),
                programRepository = FakeProgramRepository(),
             FakeLoggingPort())

        val history = useCase(liftId)

        assertNull(history.personalRecord)
        assertEquals(emptyList<LiftHistorySession>(), history.sessions)
    }

    @Test
    @DisplayName("Given sets across sessions when loaded then newest first with best set and PR")
    fun groupsSessionsNewestFirst() = runTest {
        val olderSession =
            session(
                id = "s-old",
                started = Instant.parse("2026-01-01T10:00:00Z"),
                ended = Instant.parse("2026-01-01T11:00:00Z"),
            )
        val newerSession =
            session(
                id = "s-new",
                started = Instant.parse("2026-02-01T10:00:00Z"),
                ended = Instant.parse("2026-02-01T11:00:00Z"),
            )
        val prSet =
            loggedSet(
                id = "set-pr",
                sessionId = newerSession.id,
                setIndex = 1,
                weight = 225.0,
                reps = 5,
                rir = 1,
                e1rm = 260.0,
                isPr = true,
                at = Instant.parse("2026-02-01T10:30:00Z"),
            )
        val olderBest =
            loggedSet(
                id = "set-old",
                sessionId = olderSession.id,
                setIndex = 0,
                weight = 200.0,
                reps = 5,
                rir = 2,
                e1rm = 230.0,
                at = Instant.parse("2026-01-01T10:30:00Z"),
            )
        val olderWeak =
            loggedSet(
                id = "set-old-2",
                sessionId = olderSession.id,
                setIndex = 1,
                weight = 185.0,
                reps = 8,
                rir = 2,
                e1rm = 220.0,
                at = Instant.parse("2026-01-01T10:40:00Z"),
            )
        val program =
            Program(
                id = programId,
                name = "Main",
                createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                cycleLength = 4,
                isActive = true,
                workouts =
                    listOf(
                        Workout(
                            id = workoutId,
                            programId = programId,
                            name = "Push A",
                            weekday = null,
                            position = 0,
                            workoutLifts = emptyList(),
                        ),
                    ),
            )
        val logRepo =
            FakeTrainingLogRepository(
                initialSessions = listOf(olderSession, newerSession),
                initialSets = listOf(olderBest, olderWeak, prSet),
            )
        val useCase =
            GetLiftHistoryUseCase(
                trainingLogRepository = logRepo,
                programRepository = FakeProgramRepository(program),
             FakeLoggingPort())

        val history = useCase(liftId)

        assertEquals(prSet.id, history.personalRecord?.id)
        assertEquals(2, history.sessions.size)
        assertEquals(newerSession.id, history.sessions[0].sessionId)
        assertEquals("Push A", history.sessions[0].workoutLabel)
        assertEquals(prSet.id, history.sessions[0].bestSet.id)
        assertEquals(olderSession.id, history.sessions[1].sessionId)
        assertEquals(olderBest.id, history.sessions[1].bestSet.id)
        assertEquals(2, history.sessions[1].sets.size)
    }

    private fun session(
        id: String,
        started: Instant,
        ended: Instant,
    ): TrainingSession =
        TrainingSession(
            id = TrainingSessionId(id),
            programId = programId,
            workoutId = workoutId,
            status = TrainingSessionStatus.COMPLETED,
            kind = TrainingSessionKind.PROGRAM_WORKOUT,
            origin = TrainingSessionOrigin.LIVE,
            cycleNumberSnapshot = 1,
            weekdaySnapshot = null,
            startedAt = started,
            endedAt = ended,
            sourceApp = null,
        )

    private fun loggedSet(
        id: String,
        sessionId: TrainingSessionId,
        setIndex: Int,
        weight: Double,
        reps: Int,
        rir: Int?,
        e1rm: Double?,
        isPr: Boolean = false,
        at: Instant,
    ): LoggedSet =
        LoggedSet(
            id = LoggedSetId(id),
            sessionId = sessionId,
            exerciseNameRaw = "Bench Press",
            liftId = liftId,
            setIndex = setIndex,
            weight = Weight(weight, Weight.WeightUnit.Lb),
            reps = reps,
            rir = rir,
            completedAt = at,
            liftContext = LoggedLiftContext(liftName = "Bench Press"),
            isPersonalRecord = isPr,
            estimatedOneRm = e1rm,
        )
}
