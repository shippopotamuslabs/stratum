package com.stratum.application.dashboard

import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.training.LoggedLiftContext
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class LoadMuscleDetailUseCaseTest {
    private val bench = lift("bench", "Bench Press", MovementPattern.HorizontalPress, MuscleGroup.Chest)
    private val squat = lift("squat", "Squat", MovementPattern.SquatPattern, MuscleGroup.Quad)

    @Test
    @DisplayName("loads lift rows and labels recent sessions from the session snapshot")
    fun loadsDetail() = runTest {
        val trainingLog = trainingLog()
        trainingLog.seedSession(WorkoutLoggingFixtures.completedLiveSession(id = "s1"))
        repeat(3) { index -> trainingLog.seedSet(set("s1", bench, index, NOW.minus(1, ChronoUnit.DAYS))) }
        repeat(2) { index -> trainingLog.seedSet(set("s1", squat, index, NOW.minus(1, ChronoUnit.DAYS))) }

        val result = useCase(trainingLog)(MuscleGroup.Chest, NOW)

        val detail = (result as LoadMuscleDetailResult.Loaded).detail
        assertEquals(listOf("Bench Press"), detail.lifts.map { it.name })
        assertEquals(3.0, detail.lifts.single().setsPerWeek, 1e-9)
        assertEquals("Workout A", detail.recentSessions.single().workoutLabel)
    }

    @Test
    @DisplayName("sets older than the window are not read into the pane")
    fun ignoresOldSets() = runTest {
        val trainingLog = trainingLog()
        trainingLog.seedSet(set("old", bench, 0, NOW.minus(120, ChronoUnit.DAYS)))

        val detail = (useCase(trainingLog)(MuscleGroup.Chest, NOW) as LoadMuscleDetailResult.Loaded).detail

        assertTrue(detail.lifts.isEmpty())
        assertTrue(detail.recentSessions.isEmpty())
    }

    @Test
    @DisplayName("a session lookup failure becomes a diagnostic, not a crash")
    fun failureIsDiagnostic() = runTest {
        val trainingLog = trainingLog()
        repeat(2) { index -> trainingLog.seedSet(set("s1", bench, index, NOW.minus(1, ChronoUnit.DAYS))) }
        trainingLog.getSessionError = IllegalStateException("db closed")

        val result = useCase(trainingLog)(MuscleGroup.Chest, NOW)

        val failed = result as LoadMuscleDetailResult.Failed
        assertEquals("LoadMuscleDetail", failed.diagnostics.single().tag)
    }

    private fun trainingLog(): FakeTrainingLogRepository =
        FakeTrainingLogRepository().apply {
            seedLiftForMuscleFilter(bench)
            seedLiftForMuscleFilter(squat)
        }

    private fun useCase(trainingLog: FakeTrainingLogRepository): LoadMuscleDetailUseCase {
        val lifts = FakeLiftRepository().apply { seedLifts(listOf(bench, squat)) }
        return LoadMuscleDetailUseCase(
            trainingLogRepository = trainingLog,
            liftRepository = lifts,
            programRepository = FakeProgramRepository(),
        )
    }

    private fun set(sessionId: String, lift: Lift, index: Int, at: Instant): LoggedSet =
        LoggedSet(
            id = LoggedSetId("$sessionId-${lift.id.value}-$index"),
            sessionId = TrainingSessionId(sessionId),
            exerciseNameRaw = lift.name,
            liftId = lift.id,
            setIndex = index,
            weight = Weight(100.0, Weight.WeightUnit.Kg),
            reps = 8,
            rir = 2,
            completedAt = at.plusSeconds(index.toLong()),
            liftContext = LoggedLiftContext(liftName = lift.name),
        )

    private fun lift(id: String, name: String, pattern: MovementPattern, primary: MuscleGroup): Lift =
        Lift(
            id = LiftId(id),
            name = name,
            movementPatterns = setOf(pattern),
            primaryMuscles = setOf(primary),
            secondaryMuscles = emptySet(),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = true,
        )

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-18T18:00:00Z")
    }
}
