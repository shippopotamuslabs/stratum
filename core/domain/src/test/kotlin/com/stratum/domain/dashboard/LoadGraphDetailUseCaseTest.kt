package com.stratum.domain.dashboard

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeLiftRepository
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
import com.stratum.domain.response.TrainingSignalQualityFlag
import com.stratum.domain.response.modeling.LiftWeeklyPerformance
import com.stratum.domain.response.modeling.MuscleWeeklyOutcomeIndex
import com.stratum.domain.response.modeling.MuscleWeeklyStimulus
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.training.LoggedLiftContext
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class LoadGraphDetailUseCaseTest {

    @Test
    @DisplayName("returns null when lift has no performance rows")
    fun liftEmpty() = runTest {
        val useCase = buildUseCase()
        assertNull(useCase(GraphDetailSubject.Lift(LiftId("missing"))))
    }

    @Test
    @DisplayName("loads full lift history beyond the 13-week board window")
    fun liftFullHistory() = runTest {
        val benchId = LiftId("bench")
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(listOf(lift(benchId, "Bench Press", MuscleGroup.Chest)))
        val performanceRepo = FakeLiftWeeklyPerformanceRepository()
        val oldWeek = NOW.minus(140, ChronoUnit.DAYS)
        val recentWeek = NOW.minus(7, ChronoUnit.DAYS)
        performanceRepo.seed(
            listOf(
                performance(benchId, oldWeek, peakE1rm = 90.0, hardSets = 3.0),
                performance(benchId, recentWeek, peakE1rm = 110.0, hardSets = 5.0),
                performance(LiftId("other"), recentWeek, peakE1rm = 200.0, hardSets = 9.0),
            ),
        )
        val useCase = buildUseCase(performanceRepo = performanceRepo, liftRepo = liftRepo)

        val detail = useCase(GraphDetailSubject.Lift(benchId))
        assertNotNull(detail)
        assertEquals("Bench Press", detail!!.title)
        assertEquals(GraphDetailKind.Lift, detail.kind)
        assertEquals(2, detail.primary.size, "includes weeks older than the board window")
        assertEquals(90.0, detail.primary.first().value)
        assertEquals(110.0, detail.primary.last().value)
        assertEquals(3.0, detail.secondary.first().value)
        assertEquals(5.0, detail.secondary.last().value)
    }

    @Test
    @DisplayName("loads muscle progress + volume history sorted by week")
    fun muscleHistory() = runTest {
        val stimulusRepo = FakeMuscleWeeklyStimulusRepository()
        val outcomeRepo = FakeMuscleWeeklyOutcomeIndexRepository()
        val w0 = NOW.minus(21, ChronoUnit.DAYS)
        val w1 = NOW.minus(14, ChronoUnit.DAYS)
        val w2 = NOW.minus(7, ChronoUnit.DAYS)
        stimulusRepo.seed(
            listOf(
                stimulus(MuscleGroup.Chest, w2, 15.0),
                stimulus(MuscleGroup.Chest, w0, 10.0),
                stimulus(MuscleGroup.Back, w0, 99.0),
            ),
        )
        outcomeRepo.seed(
            listOf(
                outcome(MuscleGroup.Chest, w1, 0.01),
                outcome(MuscleGroup.Chest, w0, 0.0),
            ),
        )
        val useCase = buildUseCase(stimulusRepo = stimulusRepo, outcomeRepo = outcomeRepo)

        val detail = useCase(GraphDetailSubject.Muscle(MuscleGroup.Chest))
        assertNotNull(detail)
        assertEquals("Chest", detail!!.title) // displayLabel
        assertEquals(GraphDetailKind.Muscle, detail.kind)
        assertEquals(listOf(0.0, 0.01), detail.primary.map { it.value })
        assertEquals(listOf(10.0, 15.0), detail.secondary.map { it.value })
        assertTrue(detail.primary.zipWithNext().all { (a, b) -> !a.weekStart.isAfter(b.weekStart) })
    }

    @Test
    @DisplayName("returns null when muscle has neither stimulus nor index rows")
    fun muscleEmpty() = runTest {
        val useCase = buildUseCase()
        assertNull(useCase(GraphDetailSubject.Muscle(MuscleGroup.Chest)))
    }

    @Test
    @DisplayName("loads daily hard-set history for MuscleDailyVolume subject")
    fun muscleDailyVolumeHistory() = runTest {
        val benchId = LiftId("bench")
        val bench = lift(benchId, "Bench Press", MuscleGroup.Chest)
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(listOf(bench))
        val log = FakeTrainingLogRepository()
        log.seedLiftForMuscleFilter(bench)
        val day0 = NOW.minus(3, ChronoUnit.DAYS)
        val day1 = NOW.minus(1, ChronoUnit.DAYS)
        val session = WorkoutLoggingFixtures.completedLiveSession(id = "s1")
        log.seedSession(session)
        log.seedSet(hardSet(benchId, "a", session.id, day0))
        log.seedSet(hardSet(benchId, "b", session.id, day1))
        log.seedSet(hardSet(benchId, "c", session.id, day1))
        val useCase = buildUseCase(liftRepo = liftRepo, trainingLog = log)

        val detail = useCase(GraphDetailSubject.MuscleDailyVolume(MuscleGroup.Chest))
        assertNotNull(detail)
        assertEquals(GraphDetailKind.MuscleDailyVolume, detail!!.kind)
        assertEquals("Chest", detail.title)
        assertEquals(2, detail.primary.size, "two distinct UTC days")
        assertEquals(1.0, detail.primary.first().value, 0.01)
        assertEquals(2.0, detail.primary.last().value, 0.01)
        assertTrue(detail.secondary.isEmpty())
    }

    @Test
    @DisplayName("MuscleDailyVolume includes press sets for Tricep via policy pattern SQL branch")
    fun muscleDailyVolumePolicyTricep() = runTest {
        val benchId = LiftId("bench")
        val bench = Lift(
            id = benchId,
            name = "Bench Press",
            movementPatterns = setOf(MovementPattern.HorizontalPress),
            primaryMuscles = setOf(MuscleGroup.Chest),
            secondaryMuscles = emptySet(),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = true,
            fatigueProfile = FatigueProfile.High,
        )
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(listOf(bench))
        val log = FakeTrainingLogRepository()
        log.seedLiftForMuscleFilter(bench)
        val session = WorkoutLoggingFixtures.completedLiveSession(id = "s1")
        log.seedSession(session)
        log.seedSet(hardSet(benchId, "a", session.id, NOW.minus(1, ChronoUnit.DAYS)))
        val useCase = buildUseCase(liftRepo = liftRepo, trainingLog = log)

        val detail = useCase(GraphDetailSubject.MuscleDailyVolume(MuscleGroup.Tricep))
        assertNotNull(detail, "press pattern should load sets for Tricep even without stored secondary")
        assertEquals(0.5, detail!!.primary.single().value, 0.01)
    }

    @Test
    @DisplayName("returns null for MuscleDailyVolume with no hard sets")
    fun muscleDailyVolumeEmpty() = runTest {
        val useCase = buildUseCase()
        assertNull(useCase(GraphDetailSubject.MuscleDailyVolume(MuscleGroup.Chest)))
    }

    @Test
    @DisplayName("OneWeek windowStart is about 7 days before now")
    fun oneWeekWindow() {
        val start = GraphDetailTimeRange.OneWeek.windowStart(NOW)
        assertNotNull(start)
        val days = ChronoUnit.DAYS.between(start, NOW)
        assertEquals(7, days)
    }

    @Test
    @DisplayName("zeroFillDailyTrendPoints pads rest days")
    fun zeroFillDaily() {
        val day = utcDayStart(NOW.minus(1, ChronoUnit.DAYS))
        val filled = zeroFillDailyTrendPoints(
            points = listOf(TrendPoint(day, 4.0)),
            now = NOW,
            dayCount = 7,
        )
        assertEquals(7, filled.size)
        assertEquals(4.0, filled.single { it.weekStart == day }.value)
        assertEquals(6, filled.count { it.value == 0.0 })
    }

    @Test
    @DisplayName("filterTrendPointsByRange keeps points on or after the lower bound")
    fun filterRange() {
        val w0 = NOW.minus(40, ChronoUnit.DAYS)
        val w1 = NOW.minus(10, ChronoUnit.DAYS)
        val points = listOf(
            TrendPoint(w0, 1.0),
            TrendPoint(w1, 2.0),
        )
        val since = NOW.minus(30, ChronoUnit.DAYS)
        val filtered = filterTrendPointsByRange(points, since)
        assertEquals(listOf(2.0), filtered.map { it.value })
    }

    @Test
    @DisplayName("graphDetailStats computes avg, best, and span")
    fun stats() {
        val w0 = NOW.minus(14, ChronoUnit.DAYS)
        val w1 = NOW.minus(7, ChronoUnit.DAYS)
        val stats = graphDetailStats(
            listOf(
                TrendPoint(w0, 100.0),
                TrendPoint(w1, 120.0),
            ),
        )
        assertEquals(110.0, stats.average)
        assertEquals(120.0, stats.best)
        assertEquals(2, stats.pointCount)
        assertEquals(w0, stats.from)
        assertEquals(w1, stats.to)
    }

    @Test
    @DisplayName("graphDetailStats is empty-safe")
    fun statsEmpty() {
        val stats = graphDetailStats(emptyList())
        assertNull(stats.average)
        assertNull(stats.best)
        assertEquals(0, stats.pointCount)
    }

    private fun buildUseCase(
        performanceRepo: FakeLiftWeeklyPerformanceRepository = FakeLiftWeeklyPerformanceRepository(),
        stimulusRepo: FakeMuscleWeeklyStimulusRepository = FakeMuscleWeeklyStimulusRepository(),
        outcomeRepo: FakeMuscleWeeklyOutcomeIndexRepository = FakeMuscleWeeklyOutcomeIndexRepository(),
        liftRepo: FakeLiftRepository = FakeLiftRepository(),
        trainingLog: FakeTrainingLogRepository = FakeTrainingLogRepository(),
    ): LoadGraphDetailUseCase = LoadGraphDetailUseCase(
        liftWeeklyPerformanceRepository = performanceRepo,
        muscleWeeklyStimulusRepository = stimulusRepo,
        muscleWeeklyOutcomeIndexRepository = outcomeRepo,
        liftRepository = liftRepo,
        trainingLogRepository = trainingLog,
        loggingPort = FakeLoggingPort(),
    )

    private fun hardSet(
        liftId: LiftId,
        id: String,
        sessionId: TrainingSessionId,
        completedAt: Instant,
    ): LoggedSet = LoggedSet(
        id = LoggedSetId(id),
        sessionId = sessionId,
        exerciseNameRaw = "Bench",
        liftId = liftId,
        setIndex = 0,
        weight = Weight(100.0, Weight.WeightUnit.Kg),
        reps = 8,
        rir = 2,
        completedAt = completedAt,
        liftContext = LoggedLiftContext(liftId.value),
    )

    private fun lift(id: LiftId, name: String, muscle: MuscleGroup): Lift = Lift(
        id = id,
        name = name,
        movementPatterns = setOf(MovementPattern.HorizontalPress),
        primaryMuscles = setOf(muscle),
        secondaryMuscles = emptySet(),
        incrementTiers = emptyList(),
        presetTags = emptySet(),
        enabled = true,
        fatigueProfile = FatigueProfile.High,
    )

    private fun performance(
        liftId: LiftId,
        weekStart: Instant,
        peakE1rm: Double,
        hardSets: Double = 4.0,
    ): LiftWeeklyPerformance = LiftWeeklyPerformance(
        liftId = liftId,
        weekStart = weekStart,
        peakE1rm = peakE1rm,
        smoothedE1rm = peakE1rm,
        logE1rm = kotlin.math.ln(peakE1rm.coerceAtLeast(1.0)),
        hardSets = hardSets,
        sessions = 1,
        meanRir = 2.0,
        reliability = 0.9,
        qualityFlags = emptySet(),
    )

    private fun stimulus(muscle: MuscleGroup, weekStart: Instant, hardSets: Double): MuscleWeeklyStimulus =
        MuscleWeeklyStimulus(
            muscle = muscle,
            weekStart = weekStart,
            hardSets = hardSets,
            sessions = 1,
            meanRir = 2.0,
            intensityProxy = null,
            compositionHash = "",
            deloadFlag = false,
            qualityFlags = emptySet<TrainingSignalQualityFlag>(),
        )

    private fun outcome(
        muscle: MuscleGroup,
        weekStart: Instant,
        indexValue: Double,
    ): MuscleWeeklyOutcomeIndex = MuscleWeeklyOutcomeIndex(
        muscle = muscle,
        weekStart = weekStart,
        indexValue = indexValue,
        indexStdError = null,
        contributingLiftCount = 1,
        effectiveLiftCount = 1,
        primaryCoverage = 1.0,
        compositionHash = "",
        compositionDrift = 0.0,
        qualityFlags = emptySet(),
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-07-01T12:00:00Z")
    }
}
