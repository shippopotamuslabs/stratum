package com.stratum.application.workout

import com.stratum.core.testing.importing.FakeImportAnalysisMetadataRepository
import com.stratum.core.testing.importing.FakeLiftTrainingInsightsRepository
import com.stratum.core.testing.importing.FakeMuscleVolumeBaselineRepository
import com.stratum.core.testing.importing.FakeSupersetAffinityRepository
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.core.testing.workout.FakeRepRangeShiftProposalPreferences
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.Weight
import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.LoggedLiftContext
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.onboarding.Weekday
import com.stratum.application.importing.ExtractTrainingInsightsUseCase
import com.stratum.domain.program.Program
import com.stratum.domain.workout.repRangeShiftProposalFingerprint

@DisplayName("AnalyzeSessionRepRangeShiftProposalsUseCase")
class AnalyzeSessionRepRangeShiftProposalsUseCaseTest {

    private val sessionId = TrainingSessionId("ts-rrs-current")
    private val wlId = WorkoutLiftId("wl-bench")
    private val lb = Weight.WeightUnit.Lb
    private val t0 = Instant.parse("2025-01-01T10:00:00Z")

    private lateinit var logRepo: FakeTrainingLogRepository
    private lateinit var programRepo: FakeProgramRepository
    private lateinit var insightsRepo: FakeLiftTrainingInsightsRepository
    private lateinit var liftRepo: FakeLiftRepository
    private lateinit var prefs: FakeRepRangeShiftProposalPreferences
    private lateinit var subject: AnalyzeSessionRepRangeShiftProposalsUseCase

    @BeforeEach
    fun setup() {
        logRepo = FakeTrainingLogRepository()
        programRepo = FakeProgramRepository(programWithFourSetDouble())
        insightsRepo = FakeLiftTrainingInsightsRepository()
        liftRepo = FakeLiftRepository(listOf(WorkoutLoggingFixtures.benchLift()))
        prefs = FakeRepRangeShiftProposalPreferences()
        subject =
            AnalyzeSessionRepRangeShiftProposalsUseCase(
                trainingLogRepository = logRepo,
                programRepository = programRepo,
                liftTrainingInsightsRepository = insightsRepo,
                liftRepository = liftRepo,
                preferences = prefs,
                extractTrainingInsights =
                    ExtractTrainingInsightsUseCase(
                        trainingLogRepository = logRepo,
                        liftTrainingInsightsRepository = insightsRepo,
                        supersetAffinityRepository = FakeSupersetAffinityRepository(),
                        muscleVolumeBaselineRepository = FakeMuscleVolumeBaselineRepository(),
                        importAnalysisMetadataRepository = FakeImportAnalysisMetadataRepository(),
                        liftRepository = liftRepo,
                    ),
                loggingPort = FakeLoggingPort(),
            )
    }

    @Test
    @DisplayName("steep fatigue history + last set under floor → raises band, preserves width")
    fun steepSlopeProposesShift() = runTest {
        seedFatigueHistoryAndCurrentSession(lastSetReps = 3)

        val proposals = subject(sessionId)
        assertEquals(1, proposals.size)
        val p = proposals.single()
        assertEquals(wlId, p.workoutLiftId)
        assertEquals(8, p.currentRepMin)
        assertEquals(10, p.currentRepMax)
        // Extract averages history (~−3) with this session; proposed min must rise and keep width 2.
        assertTrue(p.proposedRepMin > 8, "proposed min ${p.proposedRepMin}")
        assertEquals(p.proposedRepMin + 2, p.proposedRepMax)
        assertEquals("Barbell Bench Press", p.liftName)
        assertEquals(
            repRangeShiftProposalFingerprint(
                workoutLiftId = wlId,
                currentRepMin = 8,
                currentRepMax = 10,
                proposedRepMin = p.proposedRepMin,
                proposedRepMax = p.proposedRepMax,
            ),
            p.id,
        )
    }

    @Test
    @DisplayName("strong last set (≥ floor) → silence even with steep slope")
    fun strongLastSetSilent() = runTest {
        seedFatigueHistoryAndCurrentSession(lastSetReps = 8)
        assertTrue(subject(sessionId).isEmpty())
    }

    @Test
    @DisplayName("low-rep myo mini after strong main block does not trigger rep-range proposal")
    fun myoMiniAfterStrongMainsSilent() = runTest {
        seedFatigueHistorySessionsOnly()
        logRepo.seedSession(currentSession())
        // Main block stays above floor; trailing mini at 3 reps must not open the gate.
        logRepo.seedSet(loggedSet(sessionId, 0, reps = 10, id = "cur-0"))
        logRepo.seedSet(loggedSet(sessionId, 1, reps = 8, id = "cur-1"))
        logRepo.seedSet(loggedSet(sessionId, 2, reps = 7, id = "cur-2"))
        logRepo.seedSet(
            loggedSet(
                sessionId = sessionId,
                setIndex = 3,
                reps = 3,
                id = "cur-mini",
                rir = 0,
                setType = SetType.MyoRep,
                repMin = 0,
                repMax = 0,
                rirGoal = 0,
            ),
        )
        assertTrue(
            subject(sessionId).isEmpty(),
            "mini cluster reps must not drive rep-range shift",
        )
    }

    @Test
    @DisplayName("dismissed fingerprint is filtered")
    fun dismissedFiltered() = runTest {
        seedFatigueHistoryAndCurrentSession(lastSetReps = 3)
        val before = subject(sessionId)
        assertEquals(1, before.size)
        prefs.dismiss(before.single().id)
        assertTrue(subject(sessionId).isEmpty())
    }

    @Test
    @DisplayName("import session → no proposal")
    fun importSessionNoProposal() = runTest {
        seedFatigueHistoryAndCurrentSession(lastSetReps = 3)
        logRepo.seedSession(
            currentSession().copy(origin = TrainingSessionOrigin.IMPORT),
        )
        assertTrue(subject(sessionId).isEmpty())
    }

    @Test
    @DisplayName("null programId (soft-unlink) → no proposal")
    fun softUnlinkNoProposal() = runTest {
        seedFatigueHistoryAndCurrentSession(lastSetReps = 3)
        logRepo.seedSession(currentSession().copy(programId = null))
        assertTrue(subject(sessionId).isEmpty())
    }

    @Test
    @DisplayName("AD_HOC kind → no proposal")
    fun adHocNoProposal() = runTest {
        seedFatigueHistoryAndCurrentSession(lastSetReps = 3)
        logRepo.seedSession(currentSession().copy(kind = TrainingSessionKind.AD_HOC))
        assertTrue(subject(sessionId).isEmpty())
    }

    @Test
    @DisplayName("feature disabled in preferences → no proposal")
    fun featureDisabledNoProposal() = runTest {
        seedFatigueHistoryAndCurrentSession(lastSetReps = 3)
        prefs.seedEnabled(false)
        assertTrue(subject(sessionId).isEmpty())
    }

    @Test
    @DisplayName("sets without workoutLiftId → no proposal")
    fun noWorkoutLiftIdSilent() = runTest {
        seedFatigueHistorySessionsOnly()
        logRepo.seedSession(currentSession())
        logRepo.seedSet(
            loggedSet(
                sessionId = sessionId,
                setIndex = 0,
                reps = 8,
                id = "cur-0",
                workoutLiftId = null,
            ),
        )
        logRepo.seedSet(
            loggedSet(
                sessionId = sessionId,
                setIndex = 1,
                reps = 3,
                id = "cur-1",
                workoutLiftId = null,
            ),
        )
        assertTrue(subject(sessionId).isEmpty())
    }

    private suspend fun seedFatigueHistoryAndCurrentSession(lastSetReps: Int) {
        seedFatigueHistorySessionsOnly()
        logRepo.seedSession(currentSession())
        // 4 template sets logged; last under floor triggers session gate.
        logRepo.seedSet(loggedSet(sessionId, 0, reps = 8, id = "cur-0"))
        logRepo.seedSet(loggedSet(sessionId, 1, reps = 6, id = "cur-1"))
        logRepo.seedSet(loggedSet(sessionId, 2, reps = 5, id = "cur-2"))
        logRepo.seedSet(loggedSet(sessionId, 3, reps = lastSetReps, id = "cur-3"))
    }

    /**
     * Three prior live sessions × 3 sets with failure-reps declining ~3 per set
     * so extract yields a steep negative slope (~−3).
     */
    private suspend fun seedFatigueHistorySessionsOnly() {
        for (sessionIndex in 1..3) {
            val id = TrainingSessionId("ts-hist-$sessionIndex")
            logRepo.seedSession(
                TrainingSession(
                    id = id,
                    programId = WorkoutLoggingFixtures.programId,
                    workoutId = WorkoutLoggingFixtures.workoutAId,
                    status = TrainingSessionStatus.COMPLETED,
                    kind = TrainingSessionKind.PROGRAM_WORKOUT,
                    origin = TrainingSessionOrigin.LIVE,
                    cycleNumberSnapshot = 1,
                    weekdaySnapshot = com.stratum.domain.onboarding.Weekday.Monday,
                    startedAt = t0.plusSeconds(sessionIndex * 86_400L),
                    endedAt = t0.plusSeconds(sessionIndex * 86_400L + 3600),
                    sourceApp = null,
                    programNameSnapshot = "Test Program",
                    workoutNameSnapshot = "Workout A",
                ),
            )
            // Failure-reps at RIR 0: 14, 11, 8 → slope −3
            val failureReps = listOf(14, 11, 8)
            failureReps.forEachIndexed { setIndex, reps ->
                logRepo.seedSet(
                    loggedSet(
                        sessionId = id,
                        setIndex = setIndex,
                        reps = reps,
                        rir = 0,
                        id = "h$sessionIndex-$setIndex",
                    ),
                )
            }
        }
    }

    private fun currentSession(): TrainingSession =
        TrainingSession(
            id = sessionId,
            programId = WorkoutLoggingFixtures.programId,
            workoutId = WorkoutLoggingFixtures.workoutAId,
            status = TrainingSessionStatus.COMPLETED,
            kind = TrainingSessionKind.PROGRAM_WORKOUT,
            origin = TrainingSessionOrigin.LIVE,
            cycleNumberSnapshot = 1,
            weekdaySnapshot = com.stratum.domain.onboarding.Weekday.Monday,
            startedAt = t0.plusSeconds(10 * 86_400L),
            endedAt = t0.plusSeconds(10 * 86_400L + 3600),
            sourceApp = null,
            programNameSnapshot = "Test Program",
            workoutNameSnapshot = "Workout A",
        )

    private fun loggedSet(
        sessionId: TrainingSessionId,
        setIndex: Int,
        reps: Int,
        id: String,
        rir: Int? = 2,
        workoutLiftId: WorkoutLiftId? = wlId,
        setType: SetType? = SetType.Standard,
        repMin: Int? = 8,
        repMax: Int? = 10,
        rirGoal: Int? = 2,
    ): LoggedSet =
        LoggedSet(
            id = LoggedSetId(id),
            sessionId = sessionId,
            exerciseNameRaw = "Bench Press",
            liftId = WorkoutLoggingFixtures.liftBenchId,
            setIndex = setIndex,
            weight = Weight(135.0, lb),
            reps = reps,
            rir = rir,
            completedAt = t0.plusSeconds(setIndex * 120L),
            liftContext =
                LoggedLiftContext(
                    liftName = "Barbell Bench Press",
                    workoutLiftId = workoutLiftId,
                    progressionScheme = ProgressionScheme.Double,
                    setType = setType,
                    repMin = repMin,
                    repMax = repMax,
                    rirGoal = rirGoal,
                ),
        )

    private fun programWithFourSetDouble() =
        WorkoutLoggingFixtures.twoWorkoutProgram(
            workouts =
                listOf(
                    Workout(
                        id = WorkoutLoggingFixtures.workoutAId,
                        programId = WorkoutLoggingFixtures.programId,
                        name = "Workout A",
                        weekday = com.stratum.domain.onboarding.Weekday.Monday,
                        position = 0,
                        workoutLifts =
                            listOf(
                                WorkoutLift(
                                    id = wlId,
                                    workoutId = WorkoutLoggingFixtures.workoutAId,
                                    liftId = WorkoutLoggingFixtures.liftBenchId,
                                    progressionScheme = ProgressionScheme.Double,
                                    position = 0,
                                    sets =
                                        listOf(
                                            LiftSet(
                                                id = LiftSetId("ls-0"),
                                                workoutLiftId = wlId,
                                                position = 0,
                                                setType = SetType.Standard,
                                                repMin = 8,
                                                repMax = 10,
                                                rirGoal = 2,
                                                isTopSet = true,
                                            ),
                                            LiftSet(
                                                id = LiftSetId("ls-1"),
                                                workoutLiftId = wlId,
                                                position = 1,
                                                setType = SetType.Standard,
                                                repMin = 0,
                                                repMax = 0,
                                                rirGoal = 1,
                                                isTopSet = false,
                                            ),
                                            LiftSet(
                                                id = LiftSetId("ls-2"),
                                                workoutLiftId = wlId,
                                                position = 2,
                                                setType = SetType.Standard,
                                                repMin = 0,
                                                repMax = 0,
                                                rirGoal = 1,
                                                isTopSet = false,
                                            ),
                                            LiftSet(
                                                id = LiftSetId("ls-3"),
                                                workoutLiftId = wlId,
                                                position = 3,
                                                setType = SetType.Standard,
                                                repMin = 0,
                                                repMax = 0,
                                                rirGoal = 1,
                                                isTopSet = false,
                                            ),
                                        ),
                                ),
                            ),
                    ),
                ),
        )
}
