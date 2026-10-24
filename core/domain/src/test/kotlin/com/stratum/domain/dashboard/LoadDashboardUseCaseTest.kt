package com.stratum.domain.dashboard

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.response.FakeLiftWeeklyPerformanceRepository
import com.stratum.core.testing.response.FakeMuscleWeeklyOutcomeIndexRepository
import com.stratum.core.testing.response.FakeMuscleWeeklyStimulusRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.modeling.LiftWeeklyPerformance
import com.stratum.domain.response.modeling.MuscleWeeklyOutcomeIndex
import com.stratum.domain.response.modeling.MuscleWeeklyStimulus
import com.stratum.domain.response.TrainingSignalQualityFlag
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.training.LoggedLiftContext
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class LoadDashboardUseCaseTest {

    private val utc: ZoneId = ZoneOffset.UTC
    private val newYork: ZoneId = ZoneId.of("America/New_York")
    @Test
    fun `returns null when no weekly data exists in window`() = runTest {
        val useCase = buildUseCase()

        assertNull(useCase(now = NOW, zone = utc).getOrThrow())
    }

    @Test
    fun `window cutoff excludes data older than 13 weeks`() = runTest {
        val benchId = LiftId("bench")
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(listOf(lift(benchId, "Bench Press", MuscleGroup.Chest)))
        val stimulusRepo = FakeMuscleWeeklyStimulusRepository()
        val performanceRepo = FakeLiftWeeklyPerformanceRepository()
        val outcomeRepo = FakeMuscleWeeklyOutcomeIndexRepository()
        // Old data (20 weeks ago) — outside the window.
        val oldWeek = NOW.minus(140, ChronoUnit.DAYS)
        stimulusRepo.seed(listOf(stimulus(MuscleGroup.Chest, oldWeek, 10.0)))
        performanceRepo.seed(listOf(performance(benchId, oldWeek, 100.0)))
        outcomeRepo.seed(listOf(outcomeIndex(MuscleGroup.Chest, oldWeek, 0.01)))
        val useCase = buildUseCase(
            stimulusRepo = stimulusRepo,
            performanceRepo = performanceRepo,
            outcomeIndexRepo = outcomeRepo,
            liftRepo = liftRepo,
        )

        assertNull(useCase(now = NOW, zone = utc).getOrThrow(), "data older than the window should yield null")
    }

    @Test
    fun `window includes data within 13 weeks`() = runTest {
        val benchId = LiftId("bench")
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(listOf(lift(benchId, "Bench Press", MuscleGroup.Chest)))
        val stimulusRepo = FakeMuscleWeeklyStimulusRepository()
        val performanceRepo = FakeLiftWeeklyPerformanceRepository()
        val recentWeek = NOW.minus(28, ChronoUnit.DAYS)
        stimulusRepo.seed(listOf(stimulus(MuscleGroup.Chest, recentWeek, 10.0)))
        performanceRepo.seed(listOf(performance(benchId, recentWeek, 100.0)))
        val useCase = buildUseCase(
            stimulusRepo = stimulusRepo,
            performanceRepo = performanceRepo,
            liftRepo = liftRepo,
            programLiftIds = setOf(benchId),
        )

        val summary = useCase(now = NOW, zone = utc).getOrThrow()
        assertNotNull(summary)
        assertEquals(1, summary!!.muscles.size)
        assertEquals(MuscleGroup.Chest, summary.muscles.first().muscle)
    }

    @Test
    fun `Loaded when only recent hard sets exist without weekly tables`() = runTest {
        val benchId = LiftId("bench")
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(listOf(lift(benchId, "Bench Press", MuscleGroup.Chest)))
        val log = FakeTrainingLogRepository()
        val session = WorkoutLoggingFixtures.completedLiveSession(id = "s1")
        log.seedSession(session)
        log.seedSet(hardSet(benchId, "a", session.id, NOW, rir = 2))
        val useCase = buildUseCase(liftRepo = liftRepo, trainingLog = log, programLiftIds = setOf(benchId))

        val summary = useCase(now = NOW, zone = utc).getOrThrow()
        assertNotNull(summary, "7-Day data alone should yield Loaded, not Empty")
        assertTrue(summary!!.muscles.isEmpty())
        assertTrue(summary.runningDailyVolume.isNotEmpty())
    }

    @Test
    fun `running daily volume zero-fills 7 days and sums hard sets`() = runTest {
        val benchId = LiftId("bench")
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(listOf(lift(benchId, "Bench Press", MuscleGroup.Chest)))
        val stimulusRepo = FakeMuscleWeeklyStimulusRepository()
        val performanceRepo = FakeLiftWeeklyPerformanceRepository()
        val recentWeek = NOW.minus(7, ChronoUnit.DAYS)
        stimulusRepo.seed(listOf(stimulus(MuscleGroup.Chest, recentWeek, 10.0)))
        performanceRepo.seed(listOf(performance(benchId, recentWeek, 100.0)))

        val log = FakeTrainingLogRepository()
        val session = WorkoutLoggingFixtures.completedLiveSession(id = "s1")
        log.seedSession(session)
        // Two hard sets today, one two days ago; warmup excluded.
        log.seedSet(hardSet(benchId, "w", session.id, NOW, rir = 5))
        log.seedSet(hardSet(benchId, "a", session.id, NOW, rir = 2))
        log.seedSet(hardSet(benchId, "b", session.id, NOW, rir = 1))
        log.seedSet(hardSet(benchId, "c", session.id, NOW.minus(2, ChronoUnit.DAYS), rir = 2))

        val useCase = buildUseCase(
            stimulusRepo = stimulusRepo,
            performanceRepo = performanceRepo,
            liftRepo = liftRepo,
            trainingLog = log,
            programLiftIds = setOf(benchId),
        )

        val summary = useCase(now = NOW, zone = utc).getOrThrow()!!
        val daily = summary.runningDailyVolume.single { it.muscle == MuscleGroup.Chest }
        assertEquals(7, daily.dailyVolume.size, "always 7 zero-filled days")
        assertEquals(3.0, daily.totalHardSets, 0.01, "warmup excluded")
        assertEquals(1, daily.contributingLiftCount)
        // Two calendar days with volume (today + two days ago); rest days zero-filled.
        assertEquals(2, daily.dailyVolume.count { it.value > 0.0 })
        assertTrue(daily.dailyVolume.any { it.value == 0.0 })
    }

    @Test
    @DisplayName("7-Day window ends on local day after UTC midnight when still evening in America/New_York")
    fun runningDailyVolumeUsesDeviceLocalDay() = runTest {
        val benchId = LiftId("bench")
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(listOf(lift(benchId, "Bench Press", MuscleGroup.Chest)))
        // Wed 2026-07-22 20:30 EDT = Thu 00:30 UTC (the reported early-flip case).
        val asOf = Instant.parse("2026-07-23T00:30:00Z")
        val log = FakeTrainingLogRepository()
        val session = WorkoutLoggingFixtures.completedLiveSession(id = "s1")
        log.seedSession(session)
        log.seedSet(hardSet(benchId, "a", session.id, asOf, rir = 2))

        val useCase = buildUseCase(
            liftRepo = liftRepo,
            trainingLog = log,
            programLiftIds = setOf(benchId),
        )
        val summary = useCase(now = asOf, zone = newYork).getOrThrow()!!
        val daily = summary.runningDailyVolume.single { it.muscle == MuscleGroup.Chest }
        assertEquals(7, daily.dailyVolume.size)
        val lastDay = daily.dailyVolume.last().weekStart.atZone(newYork)
        assertEquals(
            java.time.LocalDate.of(2026, 7, 22),
            lastDay.toLocalDate(),
            "rightmost day must be Wednesday locally, not Thursday UTC",
        )
        assertEquals(DayOfWeek.WEDNESDAY, lastDay.dayOfWeek)
        assertEquals(1.0, daily.dailyVolume.last().value, 0.01)
    }

    @Test
    fun `lifts tab filters to active program lifts`() = runTest {
        val benchId = LiftId("bench")
        val curlId = LiftId("curl")
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(
            listOf(
                lift(benchId, "Bench Press", MuscleGroup.Chest),
                lift(curlId, "DB Curl", MuscleGroup.Bicep),
            ),
        )
        val performanceRepo = FakeLiftWeeklyPerformanceRepository()
        val recentWeek = NOW.minus(7, ChronoUnit.DAYS)
        performanceRepo.seed(
            listOf(
                performance(benchId, recentWeek, 100.0),
                performance(curlId, recentWeek, 50.0),
            ),
        )
        // Active program has bench but not curl.
        val useCase = buildUseCase(
            performanceRepo = performanceRepo,
            liftRepo = liftRepo,
            programLiftIds = setOf(benchId),
        )

        val summary = useCase(now = NOW, zone = utc).getOrThrow()!!
        val liftIds = summary.lifts.map { it.liftId }
        assertTrue(benchId in liftIds, "program lift should appear")
        assertTrue(curlId !in liftIds, "non-program lift should be excluded from lifts tab")
    }

    @Test
    fun `muscles tab rolls up across all lifts, not just program lifts`() = runTest {
        val benchId = LiftId("bench")
        val curlId = LiftId("curl")
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(
            listOf(
                lift(benchId, "Bench Press", MuscleGroup.Chest),
                lift(curlId, "DB Curl", MuscleGroup.Bicep),
            ),
        )
        val stimulusRepo = FakeMuscleWeeklyStimulusRepository()
        val performanceRepo = FakeLiftWeeklyPerformanceRepository()
        val recentWeek = NOW.minus(7, ChronoUnit.DAYS)
        stimulusRepo.seed(
            listOf(
                stimulus(MuscleGroup.Chest, recentWeek, 10.0),
                stimulus(MuscleGroup.Bicep, recentWeek, 6.0),
            ),
        )
        performanceRepo.seed(
            listOf(
                performance(benchId, recentWeek, 100.0),
                performance(curlId, recentWeek, 50.0),
            ),
        )
        // Active program has only bench — but Bicep still appears in the muscles tab.
        val useCase = buildUseCase(
            stimulusRepo = stimulusRepo,
            performanceRepo = performanceRepo,
            liftRepo = liftRepo,
            programLiftIds = setOf(benchId),
        )

        val summary = useCase(now = NOW, zone = utc).getOrThrow()!!
        val muscles = summary.muscles.map { it.muscle }.toSet()
        assertTrue(MuscleGroup.Chest in muscles, "program muscle should appear")
        assertTrue(MuscleGroup.Bicep in muscles, "non-program muscle with stimulus should still appear")
    }

    @Test
    fun `progress series is null when outcome index has fewer than 3 points`() = runTest {
        val benchId = LiftId("bench")
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(listOf(lift(benchId, "Bench Press", MuscleGroup.Chest)))
        val stimulusRepo = FakeMuscleWeeklyStimulusRepository()
        val outcomeRepo = FakeMuscleWeeklyOutcomeIndexRepository()
        val w0 = NOW.minus(28, ChronoUnit.DAYS)
        val w1 = NOW.minus(21, ChronoUnit.DAYS)
        stimulusRepo.seed(
            listOf(
                stimulus(MuscleGroup.Chest, w0, 10.0),
                stimulus(MuscleGroup.Chest, w1, 10.0),
            ),
        )
        outcomeRepo.seed(
            listOf(
                outcomeIndex(MuscleGroup.Chest, w0, 0.0, contributing = 2),
                outcomeIndex(MuscleGroup.Chest, w1, 0.01, contributing = 2),
            ),
        )
        val useCase = buildUseCase(
            stimulusRepo = stimulusRepo,
            outcomeIndexRepo = outcomeRepo,
            liftRepo = liftRepo,
        )

        val chest = useCase(now = NOW, zone = utc).getOrThrow()!!.muscles.first()
        assertNull(chest.progressSeries, "fewer than 3 outcome points → no progress series")
    }

    @Test
    fun `progress series is sourced from muscle weekly outcome index`() = runTest {
        val benchId = LiftId("bench")
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(listOf(lift(benchId, "Bench Press", MuscleGroup.Chest)))
        val stimulusRepo = FakeMuscleWeeklyStimulusRepository()
        val outcomeRepo = FakeMuscleWeeklyOutcomeIndexRepository()
        val w0 = NOW.minus(28, ChronoUnit.DAYS)
        val w1 = NOW.minus(21, ChronoUnit.DAYS)
        val w2 = NOW.minus(14, ChronoUnit.DAYS)
        stimulusRepo.seed(
            listOf(
                stimulus(MuscleGroup.Chest, w0, 10.0),
                stimulus(MuscleGroup.Chest, w1, 11.0),
                stimulus(MuscleGroup.Chest, w2, 12.0),
            ),
        )
        outcomeRepo.seed(
            listOf(
                outcomeIndex(MuscleGroup.Chest, w0, 0.0, contributing = 3),
                outcomeIndex(MuscleGroup.Chest, w1, 0.005, contributing = 3),
                outcomeIndex(MuscleGroup.Chest, w2, 0.01, contributing = 2),
            ),
        )
        val useCase = buildUseCase(
            stimulusRepo = stimulusRepo,
            outcomeIndexRepo = outcomeRepo,
            liftRepo = liftRepo,
        )

        val chest = useCase(now = NOW, zone = utc).getOrThrow()!!.muscles.first()
        assertNotNull(chest.progressSeries)
        assertEquals(3, chest.progressSeries!!.size)
        assertEquals(0.0, chest.progressSeries!![0].value, 1e-9)
        assertEquals(0.01, chest.progressSeries!![2].value, 1e-9)
        assertEquals(3, chest.contributingLiftCount, "falls back to max outcome count when composition empty")
        assertNotNull(chest.progressSlope)
        assertTrue(chest.progressSlope!! > 0.0, "rising log-index → positive slope")
    }

    @Test
    fun `contributing lifts unions primary volume contributors across weeks`() = runTest {
        // Week 0: only bench; week 1: only fly — outcome index max would be 1, footer must be 2.
        val benchId = LiftId("bench")
        val flyId = LiftId("fly")
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(
            listOf(
                lift(benchId, "Bench Press", MuscleGroup.Chest),
                lift(flyId, "Cable Fly", MuscleGroup.Chest),
            ),
        )
        val stimulusRepo = FakeMuscleWeeklyStimulusRepository()
        val outcomeRepo = FakeMuscleWeeklyOutcomeIndexRepository()
        val w0 = NOW.minus(21, ChronoUnit.DAYS)
        val w1 = NOW.minus(14, ChronoUnit.DAYS)
        val w2 = NOW.minus(7, ChronoUnit.DAYS)
        stimulusRepo.seed(
            listOf(
                stimulus(MuscleGroup.Chest, w0, 8.0, compositionHash = "bench:80"),
                stimulus(MuscleGroup.Chest, w1, 6.0, compositionHash = "fly:60"),
                stimulus(MuscleGroup.Chest, w2, 10.0, compositionHash = "bench:50|fly:50"),
            ),
        )
        outcomeRepo.seed(
            listOf(
                // Per-week outcome counts stay at 1 when only one lift has e1RM that week.
                outcomeIndex(MuscleGroup.Chest, w0, 0.0, contributing = 1),
                outcomeIndex(MuscleGroup.Chest, w1, 0.005, contributing = 1),
                outcomeIndex(MuscleGroup.Chest, w2, 0.01, contributing = 1),
            ),
        )
        val useCase = buildUseCase(
            stimulusRepo = stimulusRepo,
            outcomeIndexRepo = outcomeRepo,
            liftRepo = liftRepo,
        )

        val chest = useCase(now = NOW, zone = utc).getOrThrow()!!.muscles.single { it.muscle == MuscleGroup.Chest }
        assertEquals(2, chest.contributingLiftCount, "union of primary lifts over the window, not max week")
    }

    @Test
    fun `contributing lifts excludes secondary-only volume contributors`() = runTest {
        val benchId = LiftId("bench")
        val pushdownId = LiftId("pushdown")
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(
            listOf(
                // Bench is primary Chest; composition also lists it under Tricep as secondary credit.
                lift(benchId, "Bench Press", MuscleGroup.Chest, secondary = setOf(MuscleGroup.Tricep)),
                lift(pushdownId, "Pushdown", MuscleGroup.Tricep),
            ),
        )
        val stimulusRepo = FakeMuscleWeeklyStimulusRepository()
        val week = NOW.minus(7, ChronoUnit.DAYS)
        stimulusRepo.seed(
            listOf(
                stimulus(MuscleGroup.Chest, week, 8.0, compositionHash = "bench:80"),
                // Tricep volume includes secondary bench + primary pushdown.
                stimulus(MuscleGroup.Tricep, week, 6.0, compositionHash = "bench:40|pushdown:40"),
            ),
        )
        val useCase = buildUseCase(stimulusRepo = stimulusRepo, liftRepo = liftRepo)

        val summary = useCase(now = NOW, zone = utc).getOrThrow()!!
        assertEquals(1, summary.muscles.single { it.muscle == MuscleGroup.Chest }.contributingLiftCount)
        assertEquals(
            1,
            summary.muscles.single { it.muscle == MuscleGroup.Tricep }.contributingLiftCount,
            "bench secondary must not count as a Tricep primary lift",
        )
    }

    @Test
    fun `progress series ignores outcome rows outside the window`() = runTest {
        val stimulusRepo = FakeMuscleWeeklyStimulusRepository()
        val outcomeRepo = FakeMuscleWeeklyOutcomeIndexRepository()
        val oldWeek = NOW.minus(140, ChronoUnit.DAYS)
        val recentWeek = NOW.minus(7, ChronoUnit.DAYS)
        stimulusRepo.seed(listOf(stimulus(MuscleGroup.Chest, recentWeek, 10.0)))
        outcomeRepo.seed(
            listOf(
                outcomeIndex(MuscleGroup.Chest, oldWeek, -0.5),
                outcomeIndex(MuscleGroup.Chest, recentWeek, 0.01),
            ),
        )
        val useCase = buildUseCase(stimulusRepo = stimulusRepo, outcomeIndexRepo = outcomeRepo)

        val chest = useCase(now = NOW, zone = utc).getOrThrow()!!.muscles.first()
        // Only one in-window point → null series (needs MIN_PROGRESS_POINTS).
        assertNull(chest.progressSeries)
    }

    @Test
    fun `lift tenure uses first-set date from training log`() = runTest {
        val benchId = LiftId("bench")
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(listOf(lift(benchId, "Bench Press", MuscleGroup.Chest)))
        val firstSetAt = NOW.minus(70, ChronoUnit.DAYS) // 10 weeks
        val recentWeek = NOW.minus(7, ChronoUnit.DAYS)
        val performanceRepo = FakeLiftWeeklyPerformanceRepository()
        performanceRepo.seed(listOf(performance(benchId, recentWeek, 100.0)))
        val trainingLog = FakeTrainingLogRepository()
        val session = WorkoutLoggingFixtures.completedLiveSession(id = "s1").copy(
            startedAt = firstSetAt,
            endedAt = firstSetAt.plusSeconds(3600),
        )
        val set = WorkoutLoggingFixtures.loggedSet(id = "set-1", sessionId = session.id).copy(
            liftId = benchId,
            completedAt = firstSetAt,
        )
        trainingLog.replaceAll(listOf(session), listOf(set))
        val useCase = buildUseCase(
            performanceRepo = performanceRepo,
            liftRepo = liftRepo,
            trainingLog = trainingLog,
            programLiftIds = setOf(benchId),
        )

        val bench = useCase(now = NOW, zone = utc).getOrThrow()!!.lifts.first { it.liftId == benchId }
        assertEquals(10, bench.tenureWeeks)
    }

    @Test
    fun `muscles sort by mean weekly hard sets descending`() = runTest {
        val stimulusRepo = FakeMuscleWeeklyStimulusRepository()
        val week = NOW.minus(7, ChronoUnit.DAYS)
        stimulusRepo.seed(
            listOf(
                stimulus(MuscleGroup.Chest, week, 8.0),
                stimulus(MuscleGroup.Quad, week, 14.0),
                stimulus(MuscleGroup.Bicep, week, 6.0),
            ),
        )
        val useCase = buildUseCase(stimulusRepo = stimulusRepo)

        val summary = useCase(now = NOW, zone = utc).getOrThrow()!!
        assertEquals(
            listOf(MuscleGroup.Quad, MuscleGroup.Chest, MuscleGroup.Bicep),
            summary.muscles.map { it.muscle },
            "highest sets/wk first",
        )
    }

    @Test
    fun `lifts sort Upper then Lower and Axial before NonAxial before Isolation`() = runTest {
        val benchId = LiftId("bench")
        val curlId = LiftId("curl")
        val squatId = LiftId("squat")
        val legPressId = LiftId("leg-press")
        val legCurlId = LiftId("leg-curl")
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(
            listOf(
                lift(legCurlId, "Machine Seated Leg Curl", MuscleGroup.Hamstring, FatigueProfile.Low),
                lift(curlId, "DB Curl", MuscleGroup.Bicep, FatigueProfile.Low),
                lift(squatId, "Back Squat", MuscleGroup.Quad, FatigueProfile.High),
                lift(legPressId, "Leg Press", MuscleGroup.Quad, FatigueProfile.Medium),
                lift(benchId, "Bench Press", MuscleGroup.Chest, FatigueProfile.High),
            ),
        )
        val performanceRepo = FakeLiftWeeklyPerformanceRepository()
        val week = NOW.minus(7, ChronoUnit.DAYS)
        performanceRepo.seed(
            listOf(
                performance(legCurlId, week, 50.0),
                performance(curlId, week, 30.0),
                performance(squatId, week, 140.0),
                performance(legPressId, week, 200.0),
                performance(benchId, week, 100.0),
            ),
        )
        val useCase = buildUseCase(
            performanceRepo = performanceRepo,
            liftRepo = liftRepo,
            programLiftIds = setOf(benchId, curlId, squatId, legPressId, legCurlId),
        )

        val summary = useCase(now = NOW, zone = utc).getOrThrow()!!
        assertEquals(
            listOf(benchId, curlId, squatId, legPressId, legCurlId),
            summary.lifts.map { it.liftId },
            "Upper (axial, isolation) then Lower (axial, non-axial, isolation)",
        )
        assertTrue(summary.lifts.take(2).all { !it.isLower })
        assertTrue(summary.lifts.drop(2).all { it.isLower })
    }

    @Test
    fun `returns failure when weekly tables throw`() = runTest {
        val stimulusRepo = FakeMuscleWeeklyStimulusRepository()
        stimulusRepo.getSinceError = IllegalStateException("stimulus boom")
        val useCase = buildUseCase(stimulusRepo = stimulusRepo)

        val result = useCase(now = NOW, zone = utc)

        assertTrue(result.isFailure)
        assertEquals("stimulus boom", result.exceptionOrNull()?.message)
    }

    private fun buildUseCase(
        stimulusRepo: FakeMuscleWeeklyStimulusRepository = FakeMuscleWeeklyStimulusRepository(),
        performanceRepo: FakeLiftWeeklyPerformanceRepository = FakeLiftWeeklyPerformanceRepository(),
        outcomeIndexRepo: FakeMuscleWeeklyOutcomeIndexRepository = FakeMuscleWeeklyOutcomeIndexRepository(),
        liftRepo: FakeLiftRepository = FakeLiftRepository(),
        trainingLog: FakeTrainingLogRepository = FakeTrainingLogRepository(),
        programLiftIds: Set<LiftId> = emptySet(),
    ): LoadDashboardUseCase {
        val baseProgram = WorkoutLoggingFixtures.twoWorkoutProgram()
        val program = if (programLiftIds.isEmpty()) {
            baseProgram
        } else {
            val templateLift = WorkoutLoggingFixtures.workoutLiftWithDoubleProgression()
            val firstWorkout = baseProgram.workouts.first()
            baseProgram.copy(
                workouts = baseProgram.workouts.mapIndexed { index, w ->
                    if (index == 0) {
                        w.copy(
                            workoutLifts = programLiftIds.mapIndexed { i, lid ->
                                templateLift.copy(liftId = lid, workoutId = firstWorkout.id, position = i)
                            },
                        )
                    } else {
                        w
                    }
                },
            )
        }
        return LoadDashboardUseCase(
            muscleWeeklyStimulusRepository = stimulusRepo,
            liftWeeklyPerformanceRepository = performanceRepo,
            muscleWeeklyOutcomeIndexRepository = outcomeIndexRepo,
            liftRepository = liftRepo,
            trainingLogRepository = trainingLog,
            programRepository = FakeProgramRepository(program),
            loggingPort = FakeLoggingPort(),
        )
    }

    private fun hardSet(
        liftId: LiftId,
        id: String,
        sessionId: TrainingSessionId,
        completedAt: Instant,
        rir: Int,
    ): LoggedSet = LoggedSet(
        id = LoggedSetId(id),
        sessionId = sessionId,
        exerciseNameRaw = "Bench",
        liftId = liftId,
        setIndex = 0,
        weight = Weight(100.0, Weight.WeightUnit.Kg),
        reps = 8,
        rir = rir,
        completedAt = completedAt,
        liftContext = LoggedLiftContext(liftId.value),
    )

    private fun lift(
        id: LiftId,
        name: String,
        muscle: MuscleGroup,
        fatigueProfile: FatigueProfile = FatigueProfile.Low,
        secondary: Set<MuscleGroup> = emptySet(),
    ): Lift = Lift(
        id = id,
        name = name,
        movementPatterns = setOf(MovementPattern.HorizontalPress),
        primaryMuscles = setOf(muscle),
        secondaryMuscles = secondary,
        incrementTiers = emptyList(),
        presetTags = emptySet(),
        enabled = true,
        fatigueProfile = fatigueProfile,
    )

    private fun stimulus(
        muscle: MuscleGroup,
        weekStart: Instant,
        hardSets: Double,
        compositionHash: String = "",
    ): MuscleWeeklyStimulus =
        MuscleWeeklyStimulus(
            muscle = muscle,
            weekStart = weekStart,
            hardSets = hardSets,
            sessions = 1,
            meanRir = 2.0,
            intensityProxy = null,
            compositionHash = compositionHash,
            deloadFlag = false,
            qualityFlags = emptySet<TrainingSignalQualityFlag>(),
        )

    private fun performance(liftId: LiftId, weekStart: Instant, peakE1rm: Double): LiftWeeklyPerformance =
        LiftWeeklyPerformance(
            liftId = liftId,
            weekStart = weekStart,
            peakE1rm = peakE1rm,
            smoothedE1rm = peakE1rm,
            logE1rm = kotlin.math.ln(peakE1rm.coerceAtLeast(1.0)),
            hardSets = 4.0,
            sessions = 1,
            meanRir = 2.0,
            reliability = 0.9,
            qualityFlags = emptySet<TrainingSignalQualityFlag>(),
        )

    private fun outcomeIndex(
        muscle: MuscleGroup,
        weekStart: Instant,
        indexValue: Double,
        contributing: Int = 2,
    ): MuscleWeeklyOutcomeIndex =
        MuscleWeeklyOutcomeIndex(
            muscle = muscle,
            weekStart = weekStart,
            indexValue = indexValue,
            indexStdError = 0.01,
            contributingLiftCount = contributing,
            effectiveLiftCount = contributing,
            primaryCoverage = 1.0,
            compositionHash = "h",
            compositionDrift = 0.0,
            qualityFlags = emptySet(),
        )

    private companion object {
        val NOW = Instant.parse("2025-06-01T00:00:00Z")
    }
}
