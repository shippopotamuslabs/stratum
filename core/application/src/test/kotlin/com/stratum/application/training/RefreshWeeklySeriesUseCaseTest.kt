package com.stratum.application.training

import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.response.FakeResponseProfilePersistencePort
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.core.testing.training.withStart
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.domain.importing.identity.LoggedSetId
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.stratum.domain.response.Epoch
import com.stratum.domain.response.EpochSplitReason
import com.stratum.domain.response.identity.EpochId
import com.stratum.domain.training.LoggedLiftContext
import com.stratum.domain.training.LoggedSet

class RefreshWeeklySeriesUseCaseTest {
    @Test
    fun `no sets yields no weekly series`() = runTest {
        val repo = FakeTrainingLogRepository()
        val useCase = buildUseCase(repo)

        val result = useCase()

        assertTrue(result.isSuccess)
        assertTrue(
            persistence.weeklyStimulusRepository.getAll().isEmpty(),
            "no sets → no stimulus",
        )
    }

    @Test
    fun `RIR greater than 3 is excluded as warmup, not counted as volume`() = runTest {
        val liftId = LiftId("bench")
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(listOf(lift(liftId, MuscleGroup.Chest)))
        val weekStart = Instant.parse("2025-05-05T00:00:00Z") // Monday
        val repo = FakeTrainingLogRepository()
        val session = WorkoutLoggingFixtures.completedLiveSession(id = "s1").withStart(weekStart)
        repo.seedSession(session)
        // A hard set (RIR 2) and a warmup (RIR 5) on the same day.
        repo.seedSet(set(liftId, "hard", session.id, weekStart, rir = 2, weight = 200.0))
        repo.seedSet(set(liftId, "warmup", session.id, weekStart, rir = 5, weight = 95.0))
        val useCase = buildUseCase(repo, liftRepo)

        useCase()

        val stimulus = persistence.weeklyStimulusRepository.getAll()
        assertEquals(1, stimulus.size, "one muscle-week")
        // Only the hard set counts — 1.0 primary credit.
        assertEquals(1.0, stimulus.first().hardSets, 0.01, "warmup excluded by RIR>=4 predicate")
    }

    @Test
    fun `secondary muscles receive half credit`() = runTest {
        val benchId = LiftId("bench")
        val liftRepo = FakeLiftRepository()
        // Bench: primary Chest, secondary Tricep.
        liftRepo.seedLifts(
            listOf(
                Lift(
                    id = benchId,
                    name = "Bench Press",
                    movementPatterns = setOf(MovementPattern.HorizontalPress),
                    primaryMuscles = setOf(MuscleGroup.Chest),
                    secondaryMuscles = setOf(MuscleGroup.Tricep),
                    incrementTiers = emptyList(),
                    presetTags = emptySet(),
                    enabled = true,
                ),
            ),
        )
        val weekStart = Instant.parse("2025-05-05T00:00:00Z")
        val repo = FakeTrainingLogRepository()
        val session = WorkoutLoggingFixtures.completedLiveSession(id = "s1").withStart(weekStart)
        repo.seedSession(session)
        repo.seedSet(set(benchId, "set-1", session.id, weekStart, rir = 2, weight = 200.0))
        val useCase = buildUseCase(repo, liftRepo)

        useCase()

        val byMuscle = persistence.weeklyStimulusRepository.getAll().associateBy { it.muscle }
        assertEquals(1.0, byMuscle.getValue(MuscleGroup.Chest).hardSets, 0.01, "primary = 1.0")
        assertEquals(0.5, byMuscle.getValue(MuscleGroup.Tricep).hardSets, 0.01, "secondary = 0.5")
    }

    @Test
    fun `weekly series are bucketed by Monday-anchored week`() = runTest {
        val liftId = LiftId("squat")
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(listOf(lift(liftId, MuscleGroup.Quad)))
        val w1 = Instant.parse("2025-05-05T10:00:00Z") // Monday week 1
        val w2 = Instant.parse("2025-05-12T10:00:00Z") // Monday week 2
        val repo = FakeTrainingLogRepository()
        val s1 = WorkoutLoggingFixtures.completedLiveSession(id = "s1").withStart(w1)
        val s2 = WorkoutLoggingFixtures.completedLiveSession(id = "s2").withStart(w2)
        repo.seedSession(s1)
        repo.seedSession(s2)
        repo.seedSet(set(liftId, "a", s1.id, w1, rir = 2, weight = 200.0))
        repo.seedSet(set(liftId, "b", s1.id, w1.plus(2, ChronoUnit.HOURS), rir = 1, weight = 210.0))
        repo.seedSet(set(liftId, "c", s2.id, w2, rir = 2, weight = 215.0))
        val useCase = buildUseCase(repo, liftRepo)

        useCase()

        val quad = persistence.weeklyStimulusRepository.getAll().filter { it.muscle == MuscleGroup.Quad }
        assertEquals(2, quad.size, "two week buckets")
        val byWeek = quad.associateBy { it.weekStart }
        assertTrue(byWeek.keys.all { it == Instant.parse("2025-05-05T00:00:00Z") || it == Instant.parse("2025-05-12T00:00:00Z") })
    }

    @Test
    fun `replaceWeeklySeries leaves other response tables untouched`() = runTest {
        val liftId = LiftId("bench")
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(listOf(lift(liftId, MuscleGroup.Chest)))
        val weekStart = Instant.parse("2025-05-05T00:00:00Z")
        val repo = FakeTrainingLogRepository()
        val session = WorkoutLoggingFixtures.completedLiveSession(id = "s1").withStart(weekStart)
        repo.seedSession(session)
        repo.seedSet(set(liftId, "set-1", session.id, weekStart, rir = 2, weight = 200.0))
        // Pre-seed some epochs to confirm replaceWeeklySeries doesn't clobber them.
        val epochRepo = persistence.epochRepository
        val existingEpoch = com.stratum.domain.response.Epoch(
            id = com.stratum.domain.response.identity.EpochId("e1"),
            start = weekStart,
            end = weekStart.plus(7, ChronoUnit.DAYS),
            anchorLiftIds = emptySet(),
            splitReason = com.stratum.domain.response.EpochSplitReason.WindowEnd,
        )
        epochRepo.replaceAll(listOf(existingEpoch))
        val useCase = buildUseCase(repo, liftRepo)

        useCase()

        assertEquals(1, epochRepo.getAll().size, "epochs untouched by weekly-series refresh")
    }

    private val persistence = FakeResponseProfilePersistencePort()

    private fun buildUseCase(
        repo: FakeTrainingLogRepository,
        liftRepo: FakeLiftRepository = FakeLiftRepository(),
    ): RefreshWeeklySeriesUseCase = RefreshWeeklySeriesUseCase(
        trainingLogRepository = repo,
        liftRepository = liftRepo,
        persistence = persistence,
    )

    private fun lift(id: LiftId, muscle: MuscleGroup): Lift = Lift(
        id = id,
        name = id.value,
        movementPatterns = setOf(MovementPattern.SquatPattern),
        primaryMuscles = setOf(muscle),
        secondaryMuscles = emptySet(),
        incrementTiers = emptyList(),
        presetTags = emptySet(),
        enabled = true,
    )

    private fun set(
        liftId: LiftId,
        id: String,
        sessionId: TrainingSessionId,
        completedAt: Instant,
        rir: Int,
        weight: Double,
    ): LoggedSet = LoggedSet(
        id = LoggedSetId(id),
        sessionId = sessionId,
        exerciseNameRaw = "Lift",
        liftId = liftId,
        setIndex = 0,
        weight = Weight(weight, Weight.WeightUnit.Lb),
        reps = 5,
        rir = rir,
        completedAt = completedAt,
        liftContext = LoggedLiftContext(liftId.value),
    )
}
