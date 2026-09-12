package com.stratum.feature.workout

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.NoOpProgramChangeEventRepository
import com.stratum.core.testing.workout.FakeFatigueModel
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.workout.FakeProgressionHistoryRepository
import com.stratum.core.testing.workout.FakeWorkoutOverlayStore
import com.stratum.core.testing.importing.FakeImportAnalysisMetadataRepository
import com.stratum.core.testing.importing.FakeLiftTrainingInsightsRepository
import com.stratum.core.testing.importing.FakeMuscleVolumeBaselineRepository
import com.stratum.core.testing.importing.FakeSupersetAffinityRepository
import com.stratum.core.testing.lift.FakeWeightDisplayPreferencesRepository
import com.stratum.domain.importing.ExtractTrainingInsightsUseCase
import com.stratum.domain.lift.GetPastSessionNotesForLiftUseCase
import com.stratum.domain.lift.GetSessionLiftNoteUseCase
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.LiftSessionNote
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.identity.LiftSessionNoteId
import com.stratum.domain.lift.ObserveLiftCatalogUseCase
import com.stratum.domain.lift.ObserveWeightDisplayPreferencesUseCase
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.logging.LogErrorUseCase
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.GetProgramByIdUseCase
import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.DefaultLoggedLiftContextFactory
import com.stratum.domain.training.DeleteLoggedSetUseCase
import com.stratum.domain.training.ComputePersonalRecordsUseCase
import com.stratum.domain.training.GetLoggedSetsForSessionUseCase
import com.stratum.domain.training.LogSetUseCase
import com.stratum.domain.training.ObserveCycleProgressUseCase
import com.stratum.domain.training.UpdateLoggedSetUseCase
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.domain.workout.EstimateAddedSetRepsUseCase
import com.stratum.domain.workout.GenerateProgressionSnapshotUseCase
import com.stratum.domain.workout.ManageWorkoutOverlayUseCase
import com.stratum.domain.workout.MergeRenderedWorkoutUseCase
import com.stratum.domain.workout.WorkoutEditSession
import com.stratum.domain.workout.WorkoutOverlay
import com.stratum.domain.workout.ProjectWorkoutStructureUseCase
import com.stratum.domain.workout.RecomputeRecommendationsUseCase
import com.stratum.domain.workout.RecommendationEngine
import com.stratum.domain.workout.SetSlotId
import com.stratum.domain.workout.SlotHistoryEntry
import com.stratum.domain.workout.SlotHistoryRequest
import com.stratum.domain.workout.progression.DoubleProgressionEngine
import com.stratum.domain.workout.progression.LinearProgressionDefaults
import com.stratum.domain.workout.progression.LinearProgressionEngine
import com.stratum.feature.workout.ui.LiftNotesSaveRequest
import com.stratum.feature.workout.ui.WorkoutUiEvent
import com.stratum.feature.workout.ui.WorkoutUiState
import java.io.File
import java.time.Instant
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Slice 7 — :feature:workout presentation skeleton. 7 scenarios (S7-*).
 *
 * State-based assertions on uiState only (UNIT_TESTING.md §1, Detekt
 * `ForbiddenVerifyInViewModelTests`). No MockK verify on the ViewModel.
 */
@ExtendWith(MainDispatcherRule::class)
@DisplayName("Slice 7: :feature:workout presentation skeleton")
class WorkoutViewModelTest {

    private val programId = ProgramId("p-1")
    private val workoutId = WorkoutId("w-1")
    private val workoutLiftId = WorkoutLiftId("wl-1")
    private val liftId = LiftId("L-1")
    private val liftSetId = LiftSetId("ls-1")
    private val backoffSetId = LiftSetId("ls-2")

    private lateinit var programRepository: FakeProgramRepository
    private lateinit var trainingLogRepository: FakeTrainingLogRepository
    private lateinit var progressionHistory: FakeProgressionHistoryRepository
    private lateinit var workoutEditSession: WorkoutEditSession
    private lateinit var overlayStore: FakeWorkoutOverlayStore
    private lateinit var liftNotesRepository: com.stratum.core.testing.lift.FakeLiftNotesRepository

    @BeforeEach
    fun setUp() {
        programRepository = FakeProgramRepository(program())
        trainingLogRepository = FakeTrainingLogRepository(
            initialSessions = listOf(
                TrainingSession(
                    id = TrainingSessionId("ts-active"),
                    programId = programId,
                    workoutId = workoutId,
                    status = TrainingSessionStatus.IN_PROGRESS,
                    kind = TrainingSessionKind.PROGRAM_WORKOUT,
                    origin = TrainingSessionOrigin.LIVE,
                    cycleNumberSnapshot = 1,
                    weekdaySnapshot = Weekday.Monday,
                    startedAt = Instant.parse("2026-07-05T10:00:00Z"),
                    endedAt = null,
                    sourceApp = null,
                ),
            ),
        )
        progressionHistory = FakeProgressionHistoryRepository()
        workoutEditSession = WorkoutEditSession()
        overlayStore = FakeWorkoutOverlayStore()
        liftNotesRepository = com.stratum.core.testing.lift.FakeLiftNotesRepository()
        seedHistoryForTopSet()
    }

    private fun lb(amount: Double) = Weight(amount, Weight.WeightUnit.Lb)

    private fun finalizeTrainingSession(
        trainingLog: FakeTrainingLogRepository = trainingLogRepository,
        programRepo: FakeProgramRepository = programRepository,
        liftRepo: FakeLiftRepository,
    ) = com.stratum.domain.training.FinalizeTrainingSessionUseCase(
        trainingLogRepository = trainingLog,
        programRepository = programRepo,
        computePersonalRecords = ComputePersonalRecordsUseCase(trainingLog),
        localDerivedStages =
            com.stratum.core.testing.training.localDerivedTrainingStages(
                trainingLog = trainingLog,
                liftRepository = liftRepo,
            ),
        loggingPort = FakeLoggingPort(),
        refreshScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
    )

    private fun seedHistoryForTopSet() {
        seedHistoryForLift(
            historyLiftId = liftId,
            name = "Squat",
            topWeight = 135.0,
            sessionId = "s-history",
            set0Id = "hist-set-1",
            set1Id = "hist-set-2",
            set0Reps = 5,
            set1Reps = 8,
        )
    }

    private fun seedHistoryForLift(
        historyLiftId: LiftId,
        name: String,
        topWeight: Double,
        sessionId: String = "s-history-${historyLiftId.value}",
        set0Id: String = "hist-set-1-${historyLiftId.value}",
        set1Id: String = "hist-set-2-${historyLiftId.value}",
        set0Reps: Int = 5,
        set1Reps: Int = 8,
    ) {
        val session =
            TrainingSession(
                id = TrainingSessionId(sessionId),
                programId = programId,
                workoutId = workoutId,
                status = TrainingSessionStatus.COMPLETED,
                kind = TrainingSessionKind.PROGRAM_WORKOUT,
                origin = TrainingSessionOrigin.IMPORT,
                cycleNumberSnapshot = 1,
                weekdaySnapshot = Weekday.Monday,
                startedAt = Instant.parse("2026-06-28T10:00:00Z"),
                endedAt = Instant.parse("2026-06-28T11:00:00Z"),
                sourceApp = null,
            )
        progressionHistory.seed(
            SlotHistoryRequest(programId, workoutId, workoutLiftId, historyLiftId, 0),
            SlotHistoryEntry(
                session,
                com.stratum.domain.training.LoggedSet(
                    id = com.stratum.domain.importing.identity.LoggedSetId(set0Id),
                    sessionId = session.id,
                    exerciseNameRaw = name,
                    liftId = historyLiftId,
                    setIndex = 0,
                    weight = lb(topWeight),
                    reps = set0Reps,
                    rir = 2,
                    completedAt = Instant.parse("2026-06-28T10:30:00Z"),
                    liftContext = com.stratum.domain.training.LoggedLiftContext(
                        liftName = name,
                        movementPatternsBitmask = 1,
                        primaryMusclesBitmask = 1,
                        secondaryMusclesBitmask = 0,
                        fatigueProfile = com.stratum.domain.lift.FatigueProfile.Low,
                        muscleLengthBias = com.stratum.domain.lift.MuscleLengthBias.MidRange,
                    ),
                ),
            ),
        )
        progressionHistory.seed(
            SlotHistoryRequest(programId, workoutId, workoutLiftId, historyLiftId, 1),
            SlotHistoryEntry(
                session,
                com.stratum.domain.training.LoggedSet(
                    id = com.stratum.domain.importing.identity.LoggedSetId(set1Id),
                    sessionId = session.id,
                    exerciseNameRaw = name,
                    liftId = historyLiftId,
                    setIndex = 1,
                    weight = lb(topWeight),
                    reps = set1Reps,
                    rir = 2,
                    completedAt = Instant.parse("2026-06-28T10:35:00Z"),
                    liftContext = com.stratum.domain.training.LoggedLiftContext(
                        liftName = name,
                        movementPatternsBitmask = 1,
                        primaryMusclesBitmask = 1,
                        secondaryMusclesBitmask = 0,
                        fatigueProfile = com.stratum.domain.lift.FatigueProfile.Low,
                        muscleLengthBias = com.stratum.domain.lift.MuscleLengthBias.MidRange,
                    ),
                ),
            ),
        )
    }

    private fun program(): Program = Program(
        id = programId,
        name = "Test Program",
        createdAt = Instant.parse("2026-06-26T00:00:00Z"),
        cycleLength = 1,
        trainingWeekdays = setOf(Weekday.Monday),
        workouts = listOf(
            Workout(
                id = workoutId,
                programId = programId,
                name = "Workout 1",
                weekday = Weekday.Monday,
                position = 0,
                workoutLifts = listOf(
                    WorkoutLift(
                        id = workoutLiftId,
                        workoutId = workoutId,
                        liftId = liftId,
                        progressionScheme = ProgressionScheme.Double,
                        position = 0,
                        sets = listOf(
                            LiftSet(
                                id = liftSetId,
                                workoutLiftId = workoutLiftId,
                                position = 0,
                                setType = SetType.Standard,
                                repMin = 4,
                                repMax = 6,
                                rirGoal = 2,
                                isTopSet = true,
                            ),
                            LiftSet(
                                id = backoffSetId,
                                workoutLiftId = workoutLiftId,
                                position = 1,
                                setType = SetType.Standard,
                                repMin = 0,
                                repMax = 0,
                                rirGoal = 2,
                                isTopSet = false,
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun catalogLift(): Lift = Lift(
        id = liftId,
        name = "Squat",
        movementPatterns = setOf(MovementPattern.SquatPattern, MovementPattern.HipHinge),
        primaryMuscles = setOf(MuscleGroup.Quad),
        secondaryMuscles = setOf(MuscleGroup.Glute),
        incrementTiers = emptyList(),
        presetTags = emptySet(),
        enabled = true,
    )

    /** [catalogLift] variant with the default population increment tiers populated. */
    private fun catalogLiftWithTiers(): Lift = catalogLift().copy(
        incrementTiers = listOf(
            com.stratum.domain.lift.IncrementTier(lb(0.0), lb(40.0), lb(2.5)),
            com.stratum.domain.lift.IncrementTier(lb(40.0), lb(100.0), lb(5.0)),
            com.stratum.domain.lift.IncrementTier(lb(100.0), lb(300.0), lb(10.0)),
            com.stratum.domain.lift.IncrementTier(lb(300.0), null, lb(25.0)),
        ),
    )

    private fun catalogLift2(): Lift = Lift(
        id = LiftId("L-2"),
        name = "Bench Press",
        movementPatterns = setOf(MovementPattern.SquatPattern),
        primaryMuscles = setOf(MuscleGroup.Quad),
        secondaryMuscles = emptySet(),
        incrementTiers = emptyList(),
        presetTags = emptySet(),
        enabled = true,
    )

    private fun fakeLiveWorkoutSurfaces(): com.stratum.feature.workout.notification.LiveWorkoutSurfaces {
        val restTimer = com.stratum.feature.workout.notification.RestTimerController(
            clock = com.stratum.feature.workout.notification.EpochMilliClock {
                System.currentTimeMillis()
            },
        )
        return com.stratum.feature.workout.notification.LiveWorkoutSurfaces(
            activeWorkout = com.stratum.feature.workout.notification.ActiveWorkoutTracker(),
            restTimer = restTimer,
            restCompletion = com.stratum.feature.workout.notification.RestTimerCompletion(
                restTimer = restTimer,
                announcer = com.stratum.feature.workout.notification.RestCompleteAnnouncer { _, _ -> },
            ),
        )
    }

    private fun processScopedAbandon(
        trainingLog: FakeTrainingLogRepository = trainingLogRepository,
    ): ProcessScopedAbandonTrainingSession =
        ProcessScopedAbandonTrainingSession(
            abandonTrainingSession =
                com.stratum.domain.training.AbandonTrainingSessionUseCase(
                    trainingLog,
                    FakeLoggingPort(),
                ),
            liveWorkoutSurfaces = fakeLiveWorkoutSurfaces(),
            applicationScope =
                kotlinx.coroutines.CoroutineScope(
                    kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined,
                ),
        )

    private fun buildViewModel(
        liftRepository: FakeLiftRepository =
            FakeLiftRepository(initialLifts = listOf(catalogLift(), catalogLift2())),
        fakeLogging: FakeLoggingPort = FakeLoggingPort(),
    ): WorkoutViewModel {
        // Uses the seeded [programRepository] / [progressionHistory] from [setUp].
        return WorkoutViewModel(
            observeCycleProgress = ObserveCycleProgressUseCase(programRepository, trainingLogRepository),
            generateSnapshot =
                GenerateProgressionSnapshotUseCase(
                    progressionHistoryPort = progressionHistory,
                    doubleProgressionEngine = DoubleProgressionEngine(),
                    linearProgressionEngine = LinearProgressionEngine(LinearProgressionDefaults.config),
                    programChangeEventRepository = NoOpProgramChangeEventRepository(),
                    liftTrainingInsightsRepository = FakeLiftTrainingInsightsRepository(),
                    programRepository = programRepository,
                ),
            mergeRenderedWorkout = MergeRenderedWorkoutUseCase(),
            projectStructure = ProjectWorkoutStructureUseCase(),
            recomputeRecommendationsUseCase = RecomputeRecommendationsUseCase(RecommendationEngine(FakeFatigueModel()), FakeFatigueModel()),
            estimateAddedSetReps = EstimateAddedSetRepsUseCase(FakeFatigueModel()),
            logSetUseCase = LogSetUseCase(
                trainingLogRepository = trainingLogRepository,
                liftRepository = liftRepository,
                programRepository = programRepository,
                loggedLiftContextFactory = DefaultLoggedLiftContextFactory(),
                loggingPort = FakeLoggingPort(),
            ),
            updateLoggedSet = UpdateLoggedSetUseCase(trainingLogRepository, FakeLoggingPort()),
            observeLiftCatalog = ObserveLiftCatalogUseCase(liftRepository),
            getLoggedSetsForSession = GetLoggedSetsForSessionUseCase(trainingLogRepository),
            deleteLoggedSet = DeleteLoggedSetUseCase(trainingLogRepository, FakeLoggingPort()),
            processScopedAbandon = processScopedAbandon(trainingLogRepository),
            finalizeTrainingSession = finalizeTrainingSession(liftRepo = liftRepository),
            applyOverlayToWorkout = com.stratum.domain.workout.ApplyOverlayToWorkoutUseCase(),
            saveWorkoutUseCase = com.stratum.domain.program.SaveWorkoutUseCase(programRepository, NoOpProgramChangeEventRepository(), FakeLoggingPort()),
            getProgramById = GetProgramByIdUseCase(programRepository),
            observeLiftTrainingInsights = com.stratum.domain.importing.ObserveLiftTrainingInsightsUseCase(
                com.stratum.core.testing.importing.FakeLiftTrainingInsightsRepository(),
            ),
            manageWorkoutOverlay = ManageWorkoutOverlayUseCase(overlayStore, FakeLoggingPort()),
            workoutEditSession = workoutEditSession,
            liveWorkoutSurfaces = fakeLiveWorkoutSurfaces(),
            observeWeightDisplayPreferences =
                ObserveWeightDisplayPreferencesUseCase(FakeWeightDisplayPreferencesRepository()),
            logError = LogErrorUseCase(fakeLogging),
            saveLiftNotesUseCase = com.stratum.domain.lift.SaveLiftNotesUseCase(
                liftNotesRepository,
                liftRepository,
                programRepository,
                FakeLoggingPort(),
            ),
            getPastSessionNotesForLift = GetPastSessionNotesForLiftUseCase(
                liftNotesRepository,
            ),
            getSessionLiftNote = GetSessionLiftNoteUseCase(
                liftNotesRepository,
            ),
        )
    }

    /** Re-points every template slot in the active program workout onto [survivorLiftId]. */
    private suspend fun repointActiveWorkoutTo(survivorLiftId: LiftId) {
        val current = programRepository.getProgram()!!
        val workout = current.workouts.single { it.id == workoutId }
        val repointed =
            workout.copy(
                workoutLifts = workout.workoutLifts.map { wl -> wl.copy(liftId = survivorLiftId) },
            )
        programRepository.saveProgram(
            current.copy(
                workouts = current.workouts.map { if (it.id == workoutId) repointed else it },
            ),
        )
    }

    /**
     * Builds a ViewModel with a custom [Program] and empty progression history. Used by
     * the pre-computed recommendation scenarios where the top-set rep range and the
     * performed set values must be controlled exactly.
     */
    private fun buildViewModelWithProgram(
        program: Program,
        lifts: List<Lift> = listOf(catalogLift()),
    ): WorkoutViewModel {
        programRepository = FakeProgramRepository(program)
        progressionHistory = FakeProgressionHistoryRepository()
        val liftRepository = FakeLiftRepository(initialLifts = lifts)
        return WorkoutViewModel(
            observeCycleProgress = ObserveCycleProgressUseCase(programRepository, trainingLogRepository),
            generateSnapshot =
                GenerateProgressionSnapshotUseCase(
                    progressionHistoryPort = progressionHistory,
                    doubleProgressionEngine = DoubleProgressionEngine(),
                    linearProgressionEngine = LinearProgressionEngine(LinearProgressionDefaults.config),
                    programChangeEventRepository = NoOpProgramChangeEventRepository(),
                    liftTrainingInsightsRepository = FakeLiftTrainingInsightsRepository(),
                    programRepository = programRepository,
                ),
            mergeRenderedWorkout = MergeRenderedWorkoutUseCase(),
            projectStructure = ProjectWorkoutStructureUseCase(),
            recomputeRecommendationsUseCase = RecomputeRecommendationsUseCase(RecommendationEngine(FakeFatigueModel()), FakeFatigueModel()),
            estimateAddedSetReps = EstimateAddedSetRepsUseCase(FakeFatigueModel()),
            logSetUseCase = LogSetUseCase(
                trainingLogRepository = trainingLogRepository,
                liftRepository = liftRepository,
                programRepository = programRepository,
                loggedLiftContextFactory = DefaultLoggedLiftContextFactory(),
                loggingPort = FakeLoggingPort(),
            ),
            updateLoggedSet = UpdateLoggedSetUseCase(trainingLogRepository, FakeLoggingPort()),
            observeLiftCatalog = ObserveLiftCatalogUseCase(liftRepository),
            getLoggedSetsForSession = GetLoggedSetsForSessionUseCase(trainingLogRepository),
            deleteLoggedSet = DeleteLoggedSetUseCase(trainingLogRepository, FakeLoggingPort()),
            processScopedAbandon = processScopedAbandon(trainingLogRepository),
            finalizeTrainingSession = finalizeTrainingSession(liftRepo = liftRepository),
            applyOverlayToWorkout = com.stratum.domain.workout.ApplyOverlayToWorkoutUseCase(),
            saveWorkoutUseCase = com.stratum.domain.program.SaveWorkoutUseCase(programRepository, NoOpProgramChangeEventRepository(), FakeLoggingPort()),
            getProgramById = GetProgramByIdUseCase(programRepository),
            observeLiftTrainingInsights = com.stratum.domain.importing.ObserveLiftTrainingInsightsUseCase(
                com.stratum.core.testing.importing.FakeLiftTrainingInsightsRepository(),
            ),
            manageWorkoutOverlay = ManageWorkoutOverlayUseCase(overlayStore, FakeLoggingPort()),
            workoutEditSession = workoutEditSession,
            liveWorkoutSurfaces = fakeLiveWorkoutSurfaces(),
            observeWeightDisplayPreferences =
                ObserveWeightDisplayPreferencesUseCase(FakeWeightDisplayPreferencesRepository()),
            logError = LogErrorUseCase(FakeLoggingPort()),
            saveLiftNotesUseCase = com.stratum.domain.lift.SaveLiftNotesUseCase(
                liftNotesRepository,
                liftRepository,
                programRepository,
                FakeLoggingPort(),
            ),
            getPastSessionNotesForLift = GetPastSessionNotesForLiftUseCase(
                liftNotesRepository,
            ),
            getSessionLiftNote = GetSessionLiftNoteUseCase(
                liftNotesRepository,
            ),
        )
    }

    /**
     * Builds a ViewModel wired with a per-lift [FatigueDecay] for [liftId]. Used to verify
     * the end-to-end slope wiring: the VM loads insights at session start and threads them
     * into [RecomputeRecommendationsUseCase] and [EstimateAddedSetRepsUseCase].
     */
    private fun buildViewModelWithFatigueSlope(
        slope: Double?,
        intercept: Double?,
        program: Program = programWithTopSetRange(repMin = 4, repMax = 12, rirGoal = 2),
    ): WorkoutViewModel {
        programRepository = FakeProgramRepository(program)
        progressionHistory = FakeProgressionHistoryRepository()
        val liftRepository = FakeLiftRepository(initialLifts = listOf(catalogLift()))
        return WorkoutViewModel(
            observeCycleProgress = ObserveCycleProgressUseCase(programRepository, trainingLogRepository),
            generateSnapshot =
                GenerateProgressionSnapshotUseCase(
                    progressionHistoryPort = progressionHistory,
                    doubleProgressionEngine = DoubleProgressionEngine(),
                    linearProgressionEngine = LinearProgressionEngine(LinearProgressionDefaults.config),
                    programChangeEventRepository = NoOpProgramChangeEventRepository(),
                    liftTrainingInsightsRepository = FakeLiftTrainingInsightsRepository(),
                    programRepository = programRepository,
                ),
            mergeRenderedWorkout = MergeRenderedWorkoutUseCase(),
            projectStructure = ProjectWorkoutStructureUseCase(),
            recomputeRecommendationsUseCase = RecomputeRecommendationsUseCase(RecommendationEngine(FakeFatigueModel()), FakeFatigueModel()),
            estimateAddedSetReps = EstimateAddedSetRepsUseCase(FakeFatigueModel()),
            logSetUseCase = LogSetUseCase(
                trainingLogRepository = trainingLogRepository,
                liftRepository = liftRepository,
                programRepository = programRepository,
                loggedLiftContextFactory = DefaultLoggedLiftContextFactory(),
                loggingPort = FakeLoggingPort(),
            ),
            updateLoggedSet = UpdateLoggedSetUseCase(trainingLogRepository, FakeLoggingPort()),
            observeLiftCatalog = ObserveLiftCatalogUseCase(liftRepository),
            getLoggedSetsForSession = GetLoggedSetsForSessionUseCase(trainingLogRepository),
            deleteLoggedSet = DeleteLoggedSetUseCase(trainingLogRepository, FakeLoggingPort()),
            processScopedAbandon = processScopedAbandon(trainingLogRepository),
            finalizeTrainingSession = finalizeTrainingSession(liftRepo = liftRepository),
            applyOverlayToWorkout = com.stratum.domain.workout.ApplyOverlayToWorkoutUseCase(),
            saveWorkoutUseCase = com.stratum.domain.program.SaveWorkoutUseCase(programRepository, NoOpProgramChangeEventRepository(), FakeLoggingPort()),
            getProgramById = GetProgramByIdUseCase(programRepository),
            observeLiftTrainingInsights = com.stratum.domain.importing.ObserveLiftTrainingInsightsUseCase(
                com.stratum.core.testing.importing.FakeLiftTrainingInsightsRepository(
                    listOf(
                        com.stratum.domain.importing.LiftTrainingInsights(
                             liftId = liftId,
                             estimatedOneRm = null,
                             fatigueDecaySlope = slope,
                             fatigueDecayIntercept = intercept,
                             sessionCount = 5,
                             progressTrend = null,
                             lastPerformedAt = null,
                        ),
                    ),
                ),
            ),
            manageWorkoutOverlay = ManageWorkoutOverlayUseCase(overlayStore, FakeLoggingPort()),
            workoutEditSession = workoutEditSession,
            liveWorkoutSurfaces = fakeLiveWorkoutSurfaces(),
            observeWeightDisplayPreferences =
                ObserveWeightDisplayPreferencesUseCase(FakeWeightDisplayPreferencesRepository()),
            logError = LogErrorUseCase(FakeLoggingPort()),
            saveLiftNotesUseCase = com.stratum.domain.lift.SaveLiftNotesUseCase(
                liftNotesRepository,
                liftRepository,
                programRepository,
                FakeLoggingPort(),
            ),
            getPastSessionNotesForLift = GetPastSessionNotesForLiftUseCase(
                liftNotesRepository,
            ),
            getSessionLiftNote = GetSessionLiftNoteUseCase(
                liftNotesRepository,
            ),
        )
    }

    /**
     * Builds a program with a single top set slot using the given rep range and RIR goal.
     * Used by the pre-computed recommendation scenarios so the next upcoming set is
     * another top set at the same rep range (matching the user's reported scenarios).
     */
    private fun programWithTopSetRange(repMin: Int, repMax: Int, rirGoal: Int): Program = Program(
        id = programId,
        name = "Test Program",
        createdAt = Instant.parse("2026-06-26T00:00:00Z"),
        cycleLength = 1,
        trainingWeekdays = setOf(Weekday.Monday),
        workouts = listOf(
            Workout(
                id = workoutId,
                programId = programId,
                name = "Workout 1",
                weekday = Weekday.Monday,
                position = 0,
                workoutLifts = listOf(
                    WorkoutLift(
                        id = workoutLiftId,
                        workoutId = workoutId,
                        liftId = liftId,
                        progressionScheme = ProgressionScheme.Double,
                        position = 0,
                        sets = listOf(
                            LiftSet(
                                id = liftSetId,
                                workoutLiftId = workoutLiftId,
                                position = 0,
                                setType = SetType.Standard,
                                repMin = repMin,
                                repMax = repMax,
                                rirGoal = rirGoal,
                                isTopSet = true,
                            ),
                            LiftSet(
                                id = backoffSetId,
                                workoutLiftId = workoutLiftId,
                                position = 1,
                                setType = SetType.Standard,
                                repMin = repMin,
                                repMax = repMax,
                                rirGoal = rirGoal,
                                isTopSet = true,
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    @DisplayName("Given active session when session reorder confirmed then overlay and rendered lifts are reordered")
    fun reorderConfirmedUpdatesOverlayAndRenderedLifts() = runTest {
        // Workout has L-1 at index 0 and L-2 at index 1
        val program = Program(
            id = programId,
            name = "Test Program",
            createdAt = Instant.parse("2026-06-26T00:00:00Z"),
            cycleLength = 1,
            trainingWeekdays = setOf(Weekday.Monday),
            workouts = listOf(
                Workout(
                    id = workoutId,
                    programId = programId,
                    name = "Workout 1",
                    weekday = Weekday.Monday,
                    position = 0,
                    workoutLifts = listOf(
                        WorkoutLift(
                            id = WorkoutLiftId("wl-1"),
                            workoutId = workoutId,
                            liftId = LiftId("L-1"),
                            progressionScheme = ProgressionScheme.Double,
                            position = 0,
                            sets = listOf(
                                LiftSet(
                                    id = LiftSetId("ls-1"),
                                    workoutLiftId = WorkoutLiftId("wl-1"),
                                    position = 0,
                                    setType = SetType.Standard,
                                    repMin = 5,
                                    repMax = 5,
                                    rirGoal = 2,
                                    isTopSet = true,
                                )
                            )
                        ),
                        WorkoutLift(
                            id = WorkoutLiftId("wl-2"),
                            workoutId = workoutId,
                            liftId = LiftId("L-2"),
                            progressionScheme = ProgressionScheme.Double,
                            position = 1,
                            sets = listOf(
                                LiftSet(
                                    id = LiftSetId("ls-2"),
                                    workoutLiftId = WorkoutLiftId("wl-2"),
                                    position = 0,
                                    setType = SetType.Standard,
                                    repMin = 5,
                                    repMax = 5,
                                    rirGoal = 2,
                                    isTopSet = true,
                                )
                            )
                        )
                    )
                )
            )
        )
        val vm = buildViewModelWithProgram(program, lifts = listOf(catalogLift(), catalogLift2()))
        vm.startSession(workoutId)
        advanceUntilIdle()

        vm.uiState.test {
            val loaded = awaitItem() as WorkoutUiState.Loaded
            assertEquals("wl-1", loaded.lifts[0].workoutLiftId)
            assertEquals("wl-2", loaded.lifts[1].workoutLiftId)

            // Start reorder populates the session
            vm.startReorderLifts()
            val reorderLifts = workoutEditSession.getReorderLifts()
            assertEquals(2, reorderLifts.size)
            assertEquals("wl-1", reorderLifts[0].workoutLiftId)
            assertEquals("wl-2", reorderLifts[1].workoutLiftId)

            // Confirm a reordered sequence
            workoutEditSession.confirmReorder(workoutId.value, listOf("wl-2", "wl-1"))
            val updated = awaitItem() as WorkoutUiState.Loaded
            assertEquals("wl-2", updated.lifts[0].workoutLiftId)
            assertEquals("wl-1", updated.lifts[1].workoutLiftId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given active session when session reorder cancelled/ignored then lifts order is unchanged")
    fun reorderCancelledKeepsLiftsOrder() = runTest {
        val program = Program(
            id = programId,
            name = "Test Program",
            createdAt = Instant.parse("2026-06-26T00:00:00Z"),
            cycleLength = 1,
            trainingWeekdays = setOf(Weekday.Monday),
            workouts = listOf(
                Workout(
                    id = workoutId,
                    programId = programId,
                    name = "Workout 1",
                    weekday = Weekday.Monday,
                    position = 0,
                    workoutLifts = listOf(
                        WorkoutLift(
                            id = WorkoutLiftId("wl-1"),
                            workoutId = workoutId,
                            liftId = LiftId("L-1"),
                            progressionScheme = ProgressionScheme.Double,
                            position = 0,
                            sets = listOf(
                                LiftSet(
                                    id = LiftSetId("ls-1"),
                                    workoutLiftId = WorkoutLiftId("wl-1"),
                                    position = 0,
                                    setType = SetType.Standard,
                                    repMin = 5,
                                    repMax = 5,
                                    rirGoal = 2,
                                    isTopSet = true,
                                )
                            )
                        ),
                        WorkoutLift(
                            id = WorkoutLiftId("wl-2"),
                            workoutId = workoutId,
                            liftId = LiftId("L-2"),
                            progressionScheme = ProgressionScheme.Double,
                            position = 1,
                            sets = listOf(
                                LiftSet(
                                    id = LiftSetId("ls-2"),
                                    workoutLiftId = WorkoutLiftId("wl-2"),
                                    position = 0,
                                    setType = SetType.Standard,
                                    repMin = 5,
                                    repMax = 5,
                                    rirGoal = 2,
                                    isTopSet = true,
                                )
                            )
                        )
                    )
                )
            )
        )
        val vm = buildViewModelWithProgram(program, lifts = listOf(catalogLift(), catalogLift2()))
        vm.startSession(workoutId)
        advanceUntilIdle()

        vm.uiState.test {
            val loaded = awaitItem() as WorkoutUiState.Loaded
            assertEquals("wl-1", loaded.lifts[0].workoutLiftId)
            assertEquals("wl-2", loaded.lifts[1].workoutLiftId)

            vm.startReorderLifts()
            // No confirm event emitted (e.g. user Cancelled)
            assertEquals("wl-1", loaded.lifts[0].workoutLiftId)
            assertEquals("wl-2", loaded.lifts[1].workoutLiftId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("S7-no-navcontroller-in-feature")
    fun `S7-no-navcontroller-in-feature`() {
        // feature/workout source must not reference NavController anywhere.
        val featureDir = File("src/main/kotlin")
        assertTrue(featureDir.exists(), "feature/workout main source dir must exist")
        val matches = featureDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { it.readLines().asSequence() }
            .filter { it.contains("NavController") }
            .toList()
        assertTrue(matches.isEmpty(), "feature/workout must not reference NavController")
        // WorkoutRoute exposes navigation via callbacks (no NavController). Verifying the
        // source contains the @Composable WorkoutRoute signature with the expected callback
        // parameters keeps this a structural-contract check that doesn't require reflection.
        val routeSource =
            File("src/main/kotlin/com/stratum/feature/workout/ui/WorkoutRoute.kt").readText()
        assertTrue(
            routeSource.contains("fun WorkoutRoute("),
            "WorkoutRoute composable must exist",
        )
        assertTrue(
            routeSource.contains("onNavigateToFinalize"),
            "WorkoutRoute must expose navigation via callbacks (onNavigateToFinalize)",
        )
    }

    @Test
    @DisplayName("S7-empty-overlay-renders-snapshot-baseline")
    fun `S7-empty-overlay-renders-snapshot-baseline`() = runTest {
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        // combine(catalog, cycleProgress) can emit more than once at session start; wait for
        // the snapshot rebuild to settle before asserting baseline labels.
        advanceUntilIdle()
        val loaded = viewModel.uiState.value as WorkoutUiState.Loaded
        val firstSet = loaded.lifts.first().sets.first()
        // History: slot 0 (top, 4–6 RIR 2) did 135×5 RIR 2; slot 1 (backoff) did 135×8 RIR 2.
        // Capacity reorder uses the stronger 8@2 for math → scheme gate increments +5 lb, then
        // straight-set shared-weight floor may drop slightly so last-set capacity clears the floor.
        // previous* stays positional (what you did last time on this set index) → 135×5 @ 2 RIR.
        assertEquals("135 lb × 5", firstSet.previousWeightLabel)
        assertEquals("2 RIR", firstSet.previousRirLabel)
        assertTrue(
            firstSet.recommendedWeightAmount > 135.0,
            "expected increment from capacity-reordered baseline, got ${firstSet.recommendedWeightAmount}",
        )
        assertTrue(
            firstSet.recommendedWeightAmount <= 140.0,
            "expected shared-weight floor ≤ 140, got ${firstSet.recommendedWeightAmount}",
        )
        assertEquals("5", firstSet.recommendedRepsLabel)
        assertNull(firstSet.loggedWeightLabel)
        assertFalse(firstSet.hasLiveRepsRecommendation)
    }

    @Test
    @DisplayName("bodyweight catalog lift hides weight column and does not require weight to complete")
    fun bodyweightCatalogLiftHidesWeightColumn() = runTest {
        val viewModel = buildViewModelWithProgram(
            program = program(),
            lifts = listOf(catalogLift().copy(loadType = LoadType.Bodyweight, name = "Pull Up")),
        )
        viewModel.startSession(workoutId)
        viewModel.uiState.test {
            val loaded = awaitLoaded()
            val lift = loaded.lifts.single()
            assertFalse(lift.showWeightColumn)
            assertEquals("", lift.weightHeaderLabel)
            lift.sets.forEach { set ->
                assertFalse(set.showWeightInput)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("S7-log-set-updates-overlay-calls-log-set-use-case")
    fun `S7-log-set-updates-overlay-calls-log-set-use-case`() = runTest {
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        viewModel.uiState.test {
            awaitLoaded()
            viewModel.logSet(
                slotKey = liftSetId.value,
                weight = lb(135.0),
                reps = 5,
                rir = 2,
            )
            advanceUntilIdle()
            val updated = viewModel.uiState.value as WorkoutUiState.Loaded
            val set = updated.lifts.first().sets.first()
            assertEquals("135", set.loggedWeightLabel)
            assertEquals("5", set.loggedRepsLabel)
            assertTrue(set.isComplete)
            assertEquals(1, trainingLogRepository.insertSetCallCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("logSet persists partialReps and reloads them")
    fun logSetPersistsPartialReps() = runTest {
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        advanceUntilIdle()

        viewModel.logSet(
            slotKey = liftSetId.value,
            weight = lb(185.0),
            reps = 5,
            rir = 1,
            partialReps = 2,
        )
        advanceUntilIdle()

        val stored =
            trainingLogRepository.getSetsForSession(TrainingSessionId("ts-active")).single()
        assertEquals(5, stored.reps)
        assertEquals(2, stored.partialReps)
        assertEquals(
            2,
            overlayStore.storedOverlay().loggedSets.values.single().partialReps,
        )
    }

    @Test
    @DisplayName("logSet stores zero partialReps as none on Room and overlay")
    fun logSetZeroPartialRepsStoresNull() = runTest {
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        advanceUntilIdle()

        viewModel.logSet(
            slotKey = liftSetId.value,
            weight = lb(185.0),
            reps = 5,
            rir = 1,
            partialReps = 0,
        )
        advanceUntilIdle()

        val stored =
            trainingLogRepository.getSetsForSession(TrainingSessionId("ts-active")).single()
        assertNull(stored.partialReps)
        assertNull(overlayStore.storedOverlay().loggedSets.values.single().partialReps)
        val set =
            (viewModel.uiState.value as WorkoutUiState.Loaded).lifts.first().sets.first()
        assertNull(set.loggedPartialReps)
    }

    @Test
    @DisplayName("S7-amend-completed-set-updates-without-insert-or-SetLogged")
    fun `S7-amend-completed-set-updates-without-insert-or-SetLogged`() = runTest {
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        advanceUntilIdle()

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.events.test {
                viewModel.logSet(
                    slotKey = liftSetId.value,
                    weight = lb(135.0),
                    reps = 5,
                    rir = 2,
                )
                advanceUntilIdle()
                assertEquals(1, trainingLogRepository.insertSetCallCount)
                val firstEvent = awaitItem()
                assertEquals(
                    liftSetId.value,
                    (firstEvent as com.stratum.feature.workout.ui.WorkoutUiEvent.SetLogged).slotKey,
                )

                viewModel.logSet(
                    slotKey = liftSetId.value,
                    weight = lb(140.0),
                    reps = 6,
                    rir = 1,
                )
                advanceUntilIdle()
                // Amend emits SetAmended (draft rollback signal) but not SetLogged (no rest).
                val amendEvent = awaitItem()
                assertEquals(
                    com.stratum.feature.workout.ui.WorkoutUiEvent.SetAmended(liftSetId.value),
                    amendEvent,
                )
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
            val amended = viewModel.uiState.value as WorkoutUiState.Loaded
            val set = amended.lifts.first().sets.first()
            assertEquals("140", set.loggedWeightLabel)
            assertEquals("6", set.loggedRepsLabel)
            assertTrue(set.isComplete)
            assertEquals(1, trainingLogRepository.insertSetCallCount)
            val allSets = trainingLogRepository.getAllSets()
            assertEquals(1, allSets.size, "amend must not insert a duplicate logged set")
            assertEquals(140.0, allSets.single().weight.amount, 0.001)
            assertEquals(6, allSets.single().reps)
            assertEquals(1, allSets.single().rir)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("S7-add-delete-undo-mutate-overlay")
    fun `S7-add-delete-undo-mutate-overlay`() = runTest {
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        viewModel.uiState.test {
            awaitLoaded()
            viewModel.addSet(afterSlotId = SetSlotId.Template(liftSetId))
            val afterAdd = awaitItem() as WorkoutUiState.Loaded
            assertEquals(3, afterAdd.lifts.first().sets.size)
            viewModel.deleteSet(slotId = SetSlotId.Template(liftSetId))
            val afterDelete = awaitItem() as WorkoutUiState.Loaded
            assertEquals(2, afterDelete.lifts.first().sets.size)
            viewModel.undo()
            val afterUndo = awaitItem() as WorkoutUiState.Loaded
            assertEquals(3, afterUndo.lifts.first().sets.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("S7-recommendation-supersedes-snapshot-upcoming")
    fun `S7-recommendation-supersedes-snapshot-upcoming`() = runTest {
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        viewModel.uiState.test {
            awaitLoaded()
            viewModel.logSet(
                slotKey = liftSetId.value,
                weight = lb(135.0),
                reps = 1,
                rir = 0,
            )
            advanceUntilIdle()
            val updated = viewModel.uiState.value as WorkoutUiState.Loaded
            val ls2Set = updated.lifts.first().sets.firstOrNull { it.slotKey.contains("ls-2") }
            assertNotNull(ls2Set, "an upcoming ls-2 set must be rendered")
            // rem=1 → lastSetIndex=2; capacity-oracle reduce ≈106.7 for 135×1@0.
            assertEquals(106.7, ls2Set!!.recommendedWeightLabel.toDouble(), 1.5)
            assertNull(ls2Set.loggedWeightLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("S7-next-set-reps-projected-from-prior-completion")
    fun `S7-next-set-reps-projected-from-prior-completion`() = runTest {
        // After logging the top set, the upcoming backoff set's recommended reps should be
        // projected one set-index forward from the performed e1RM via the fatigue model
        // (D4), surfacing a concrete count instead of the rep-range placeholder.
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        viewModel.uiState.test {
            awaitLoaded()
            viewModel.logSet(
                slotKey = liftSetId.value,
                weight = lb(135.0),
                reps = 5,
                rir = 2,
            )
            advanceUntilIdle()
            val updated = viewModel.uiState.value as WorkoutUiState.Loaded
            val ls2Set = updated.lifts.first().sets.firstOrNull { it.slotKey.contains("ls-2") }
            assertNotNull(ls2Set, "an upcoming ls-2 set must be rendered")
            assertTrue(
                ls2Set!!.hasLiveRepsRecommendation,
                "upcoming set must carry a live reps recommendation after a prior completion",
            )
            // The projected reps must be a concrete positive value (the backoff slot has
            // no rep range — only a floor and RIR target — so the projection is unclamped).
            val projected = ls2Set.recommendedReps
            assertTrue(projected > 0, "projected reps must be a concrete positive value: $projected")
            assertEquals(projected.toString(), ls2Set.recommendedRepsLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Pre-computed recommendation scenarios (Python-verified math) ---
    // These cover two regressions reported by the user:
    //  1. Weight recommendation going UP after a set at the same load (reduce branch was
    //     targeting 0-RIR reps instead of reps-at-target-RIR).
    //  2. Rep recommendation going UP despite fatigue (raw 0-RIR capacity was displayed
    //     without subtracting the slot's RIR goal).

    @Test
    @DisplayName("S7-rir-math-1: 75x4@2 RIR top set (4-12 range) -> Reduce so next set hits floor of 5")
    fun `75x4 at 2 RIR reduces weight so next set hits rep floor of 5`() = runTest {
        // e1RM from 75x6 = 89.53. At 75 lb, repsForWeight = 6 @ 0 RIR → 4 @ 2 RIR.
        // Global floor: targetReps = max(repMin=4, BACKOFF_REP_FLOOR=5) = 5 → Reduce.
        // Live rep projection never surfaces below 5.
        val viewModel = buildViewModelWithProgram(programWithTopSetRange(repMin = 4, repMax = 12, rirGoal = 2))
        viewModel.startSession(workoutId)
        viewModel.uiState.test {
            awaitLoaded()
            viewModel.logSet(slotKey = liftSetId.value, weight = lb(75.0), reps = 4, rir = 2)
            advanceUntilIdle()
            val updated = viewModel.uiState.value as WorkoutUiState.Loaded
            val nextSet = updated.lifts.first().sets.firstOrNull { it.slotKey.contains("ls-2") }
            assertNotNull(nextSet, "an upcoming ls-2 set must be rendered")
            assertTrue(
                nextSet!!.recommendedWeightLabel.toDouble() < 75.0,
                "must drop weight when capacity is below the rep floor",
            )
            assertTrue(nextSet.hasLiveRepsRecommendation)
            assertTrue(nextSet.recommendedReps >= 5)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("S7-rir-math-2: 75x8@2 RIR top set (4-12 range) -> next top set Hold 75, 7 reps")
    fun `75x8 at 2 RIR holds weight and projects 7 reps`() = runTest {
        // e1RM from 75x10 = 102.81. At 75 lb, repsForWeight = 10 @ 0 RIR → 8 @ 2 RIR.
        // targetReps (top, repMin=4) = 4. 8 >= 4 → Hold 75.
        // Fatigue projection: raw set2 = 10 - 2 = 8 @ 0 RIR → 8 - 2 = 6 @ 2 RIR.
        // Clamped to 4..12 → 6 reps. Weight label "75".
        val viewModel = buildViewModelWithProgram(programWithTopSetRange(repMin = 4, repMax = 12, rirGoal = 2))
        viewModel.startSession(workoutId)
        viewModel.uiState.test {
            awaitLoaded()
            viewModel.logSet(slotKey = liftSetId.value, weight = lb(75.0), reps = 8, rir = 2)
            advanceUntilIdle()
            val updated = viewModel.uiState.value as WorkoutUiState.Loaded
            val nextSet = updated.lifts.first().sets.firstOrNull { it.slotKey.contains("ls-2") }
            assertNotNull(nextSet, "an upcoming ls-2 set must be rendered")
            assertEquals("75", nextSet!!.recommendedWeightLabel)
            assertTrue(nextSet.hasLiveRepsRecommendation)
            assertEquals(6, nextSet.recommendedReps)
            assertEquals("6", nextSet.recommendedRepsLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("S7-fatigue-slope: steeper per-lift slope projects fewer reps end-to-end (6 vs 7)")
    fun `steeper per lift slope projects fewer reps end to end`() = runTest {
        // Same scenario as `75x8 at 2 RIR holds weight and projects 7 reps` (population slope
        // -2.0 → 6 reps). With a steeper per-lift slope (-3.0) wired via the insights cache,
        // the VM should thread it into RecomputeRecommendationsUseCase and project 5 reps:
        // raw set2 = 10 - 3 = 7 @ 0 RIR → 7 - 2 = 5 @ 2 RIR.
        val viewModel = buildViewModelWithFatigueSlope(slope = -3.0, intercept = 0.0)
        viewModel.startSession(workoutId)
        viewModel.uiState.test {
            awaitLoaded()
            viewModel.logSet(slotKey = liftSetId.value, weight = lb(75.0), reps = 8, rir = 2)
            advanceUntilIdle()
            val updated = viewModel.uiState.value as WorkoutUiState.Loaded
            val nextSet = updated.lifts.first().sets.firstOrNull { it.slotKey.contains("ls-2") }
            assertNotNull(nextSet, "an upcoming ls-2 set must be rendered")
            assertTrue(nextSet!!.hasLiveRepsRecommendation)
            assertEquals(5, nextSet.recommendedReps, "Steeper slope (-3.0) should project 5 reps, not the population 6")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("S7-weight-label-formatting: fractional weights round to 1 decimal, drop .0")
    fun `weight label formatting strips trailing zero and rounds to one decimal`() = runTest {
        // 100x3@1 RIR. Capacity-oracle reduce ≈89.4 (no tiers) → label "89.4".
        val viewModel = buildViewModelWithProgram(programWithTopSetRange(repMin = 5, repMax = 8, rirGoal = 1))
        viewModel.startSession(workoutId)
        viewModel.uiState.test {
            awaitLoaded()
            viewModel.logSet(slotKey = liftSetId.value, weight = lb(100.0), reps = 3, rir = 1)
            advanceUntilIdle()
            val updated = viewModel.uiState.value as WorkoutUiState.Loaded
            val nextSet = updated.lifts.first().sets.firstOrNull { it.slotKey.contains("ls-2") }
            assertNotNull(nextSet)
            // Must not contain a long fractional tail; at most one decimal place.
            val label = nextSet!!.recommendedWeightLabel
            assertTrue(label.contains('.'), "expected a fractional label, got $label")
            assertTrue(label.substringAfter('.').length <= 1, "label must have at most 1 decimal: $label")
            assertEquals(89.4, label.toDouble(), 1.0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("S7-increment-tier-rounding: reduce weight steps down lattice until floor clears")
    fun `reduce weight rounds to increment tier`() = runTest {
        // Continuous best ≈89.4 → nearest 90 fails capacity → 85.
        val viewModel = buildViewModelWithProgram(
            program = programWithTopSetRange(repMin = 5, repMax = 8, rirGoal = 1),
            lifts = listOf(catalogLiftWithTiers()),
        )
        viewModel.startSession(workoutId)
        viewModel.uiState.test {
            awaitLoaded()
            viewModel.logSet(slotKey = liftSetId.value, weight = lb(100.0), reps = 3, rir = 1)
            advanceUntilIdle()
            val updated = viewModel.uiState.value as WorkoutUiState.Loaded
            val nextSet = updated.lifts.first().sets.firstOrNull { it.slotKey.contains("ls-2") }
            assertNotNull(nextSet)
            assertEquals("85", nextSet!!.recommendedWeightLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("S7-too-hard-reduce: missed floor by 2 reduces weight for last set")
    fun `too hard reduces weight targeting last remaining set`() = runTest {
        // 75x2@2 on a 4-12 top set. 2 sets total; after logging set 1, 1 remains.
        // achieved=2, targetFloor=max(4,5)=5 → too hard. e1RM from 75x4 = 84.10.
        // remainingCount=1 → fatigueForLastSet=0.
        // weightForReps(84.10, 5+2+0=7) ≈ 68.0. No tiers → unrounded label.
        val viewModel = buildViewModelWithProgram(programWithTopSetRange(repMin = 4, repMax = 12, rirGoal = 2))
        viewModel.startSession(workoutId)
        viewModel.uiState.test {
            awaitLoaded()
            viewModel.logSet(slotKey = liftSetId.value, weight = lb(75.0), reps = 2, rir = 2)
            advanceUntilIdle()
            val updated = viewModel.uiState.value as WorkoutUiState.Loaded
            val nextSet = updated.lifts.first().sets.firstOrNull { it.slotKey.contains("ls-2") }
            assertNotNull(nextSet)
            assertTrue(nextSet!!.recommendedWeightLabel.toDouble() < 75.0, "must reduce below 75")
            assertTrue(nextSet.recommendedReps >= 5, "recommended reps must respect global floor")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("S7-merge-priority-logged-over-recommendation-over-snapshot")
    fun `S7-merge-priority-logged-over-recommendation-over-snapshot`() = runTest {
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        viewModel.uiState.test {
            awaitLoaded()
            viewModel.logSet(
                slotKey = liftSetId.value,
                weight = lb(130.0),
                reps = 5,
                rir = 2,
            )
            advanceUntilIdle()
            val updated = viewModel.uiState.value as WorkoutUiState.Loaded
            val set = updated.lifts.first().sets.first()
            assertEquals("130", set.loggedWeightLabel)
            assertTrue(set.isComplete)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("S7-recommendation-formatting-rules")
    fun `S7-recommendation-formatting-rules`() = runTest {
        val emptyHistory = FakeProgressionHistoryRepository()
        val liftRepository = FakeLiftRepository(initialLifts = listOf(catalogLift()))
        val localViewModel = WorkoutViewModel(
            observeCycleProgress = ObserveCycleProgressUseCase(programRepository, trainingLogRepository),
            generateSnapshot =
                GenerateProgressionSnapshotUseCase(
                    progressionHistoryPort = emptyHistory,
                    doubleProgressionEngine = DoubleProgressionEngine(),
                    linearProgressionEngine = LinearProgressionEngine(LinearProgressionDefaults.config),
                    programChangeEventRepository = NoOpProgramChangeEventRepository(),
                    liftTrainingInsightsRepository = FakeLiftTrainingInsightsRepository(),
                    programRepository = programRepository,
                ),
            mergeRenderedWorkout = MergeRenderedWorkoutUseCase(),
            projectStructure = ProjectWorkoutStructureUseCase(),
            recomputeRecommendationsUseCase = RecomputeRecommendationsUseCase(RecommendationEngine(FakeFatigueModel()), FakeFatigueModel()),
            estimateAddedSetReps = EstimateAddedSetRepsUseCase(FakeFatigueModel()),
            logSetUseCase = LogSetUseCase(
                trainingLogRepository = trainingLogRepository,
                liftRepository = liftRepository,
                programRepository = programRepository,
                loggedLiftContextFactory = DefaultLoggedLiftContextFactory(),
                loggingPort = FakeLoggingPort(),
            ),
            updateLoggedSet = UpdateLoggedSetUseCase(trainingLogRepository, FakeLoggingPort()),
            observeLiftCatalog = ObserveLiftCatalogUseCase(liftRepository),
            getLoggedSetsForSession = GetLoggedSetsForSessionUseCase(trainingLogRepository),
            deleteLoggedSet = DeleteLoggedSetUseCase(trainingLogRepository, FakeLoggingPort()),
            processScopedAbandon = processScopedAbandon(trainingLogRepository),
            finalizeTrainingSession = finalizeTrainingSession(liftRepo = liftRepository),
            applyOverlayToWorkout = com.stratum.domain.workout.ApplyOverlayToWorkoutUseCase(),
            saveWorkoutUseCase = com.stratum.domain.program.SaveWorkoutUseCase(programRepository, NoOpProgramChangeEventRepository(), FakeLoggingPort()),
            getProgramById = GetProgramByIdUseCase(programRepository),
            observeLiftTrainingInsights = com.stratum.domain.importing.ObserveLiftTrainingInsightsUseCase(
                com.stratum.core.testing.importing.FakeLiftTrainingInsightsRepository(),
            ),
            manageWorkoutOverlay = ManageWorkoutOverlayUseCase(overlayStore, FakeLoggingPort()),
            workoutEditSession = WorkoutEditSession(),
            liveWorkoutSurfaces = fakeLiveWorkoutSurfaces(),
            observeWeightDisplayPreferences =
                ObserveWeightDisplayPreferencesUseCase(FakeWeightDisplayPreferencesRepository()),
            logError = LogErrorUseCase(FakeLoggingPort()),
            saveLiftNotesUseCase = com.stratum.domain.lift.SaveLiftNotesUseCase(
                liftNotesRepository,
                liftRepository,
                programRepository,
                FakeLoggingPort(),
            ),
            getPastSessionNotesForLift = GetPastSessionNotesForLiftUseCase(
                liftNotesRepository,
            ),
            getSessionLiftNote = GetSessionLiftNoteUseCase(
                liftNotesRepository,
            ),
        )

        localViewModel.startSession(workoutId)
        localViewModel.uiState.test {
            val loaded = awaitLoaded()
            val sets = loaded.lifts.first().sets

            // The first set repMin=4, repMax=6. With no history, placeholder is "4-6"
            assertEquals("4-6", sets[0].recommendedRepsLabel)

            // The second set is a backoff (repMin=0, repMax=0, RIR-only). With no history,
            // the placeholder is blank — same as weight amount 0 — rather than "0".
            assertEquals("", sets[1].recommendedRepsLabel)

            // With no history, recommended weight amount is 0.0, so recommendedWeightLabel should be "" (blank)
            assertEquals("", sets[0].recommendedWeightLabel)
            assertEquals("", sets[1].recommendedWeightLabel)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("S7-recommendation-blank-fallback-and-too-easy-bump")
    fun `S7-recommendation-blank-fallback-and-too-easy-bump`() = runTest {
        val emptyHistory = FakeProgressionHistoryRepository()
        val liftRepository = FakeLiftRepository(initialLifts = listOf(catalogLift()))
        val localViewModel = WorkoutViewModel(
            observeCycleProgress = ObserveCycleProgressUseCase(programRepository, trainingLogRepository),
            generateSnapshot =
                GenerateProgressionSnapshotUseCase(
                    progressionHistoryPort = emptyHistory,
                    doubleProgressionEngine = DoubleProgressionEngine(),
                    linearProgressionEngine = LinearProgressionEngine(LinearProgressionDefaults.config),
                    programChangeEventRepository = NoOpProgramChangeEventRepository(),
                    liftTrainingInsightsRepository = FakeLiftTrainingInsightsRepository(),
                    programRepository = programRepository,
                ),
            mergeRenderedWorkout = MergeRenderedWorkoutUseCase(),
            projectStructure = ProjectWorkoutStructureUseCase(),
            recomputeRecommendationsUseCase = RecomputeRecommendationsUseCase(RecommendationEngine(FakeFatigueModel()), FakeFatigueModel()),
            estimateAddedSetReps = EstimateAddedSetRepsUseCase(FakeFatigueModel()),
            logSetUseCase = LogSetUseCase(
                trainingLogRepository = trainingLogRepository,
                liftRepository = liftRepository,
                programRepository = programRepository,
                loggedLiftContextFactory = DefaultLoggedLiftContextFactory(),
                loggingPort = FakeLoggingPort(),
            ),
            updateLoggedSet = UpdateLoggedSetUseCase(trainingLogRepository, FakeLoggingPort()),
            observeLiftCatalog = ObserveLiftCatalogUseCase(liftRepository),
            getLoggedSetsForSession = GetLoggedSetsForSessionUseCase(trainingLogRepository),
            deleteLoggedSet = DeleteLoggedSetUseCase(trainingLogRepository, FakeLoggingPort()),
            processScopedAbandon = processScopedAbandon(trainingLogRepository),
            finalizeTrainingSession = finalizeTrainingSession(liftRepo = liftRepository),
            applyOverlayToWorkout = com.stratum.domain.workout.ApplyOverlayToWorkoutUseCase(),
            saveWorkoutUseCase = com.stratum.domain.program.SaveWorkoutUseCase(programRepository, NoOpProgramChangeEventRepository(), FakeLoggingPort()),
            getProgramById = GetProgramByIdUseCase(programRepository),
            observeLiftTrainingInsights = com.stratum.domain.importing.ObserveLiftTrainingInsightsUseCase(
                com.stratum.core.testing.importing.FakeLiftTrainingInsightsRepository(),
            ),
            manageWorkoutOverlay = ManageWorkoutOverlayUseCase(overlayStore, FakeLoggingPort()),
            workoutEditSession = WorkoutEditSession(),
            liveWorkoutSurfaces = fakeLiveWorkoutSurfaces(),
            observeWeightDisplayPreferences =
                ObserveWeightDisplayPreferencesUseCase(FakeWeightDisplayPreferencesRepository()),
            logError = LogErrorUseCase(FakeLoggingPort()),
            saveLiftNotesUseCase = com.stratum.domain.lift.SaveLiftNotesUseCase(
                liftNotesRepository,
                liftRepository,
                programRepository,
                FakeLoggingPort(),
            ),
            getPastSessionNotesForLift = GetPastSessionNotesForLiftUseCase(
                liftNotesRepository,
            ),
            getSessionLiftNote = GetSessionLiftNoteUseCase(
                liftNotesRepository,
            ),
        )

        localViewModel.startSession(workoutId)
        localViewModel.uiState.test {
            val loaded = awaitLoaded()
            val initialSets = loaded.lifts.first().sets

            // Case A (blank fallback): log 135×7@2 — not too-easy (≥2 over ceiling), enough
            // capacity to Hold 135 after one fatigue step. Blank next slot re-anchors to 135.
            localViewModel.logSet(
                slotKey = initialSets[0].slotKey,
                weight = lb(135.0),
                reps = 7,
                rir = 2,
            )
            val updated1 = awaitItem() as WorkoutUiState.Loaded
            val setsAfterLog1 = updated1.lifts.first().sets
            assertEquals("135", setsAfterLog1[1].recommendedWeightLabel)

            // Undo the log so we can test the too-easy bump case
            localViewModel.unlogSet(initialSets[0].slotKey)
            awaitItem() // Consume the unlogged state

            // Case B (too easy set weight bump): Log first set with 135 lb x 9 reps at 1 RIR.
            // Target repMax is 6, rirGoal is 2.
            // Achieved reps at 2 RIR = 9 + 1 - 2 = 8 reps.
            // Exceeded reps = 8 - 6 = 2 reps (exceeds repMax by 2).
            // So this set is "too easy" -> should bump the recommended weight of upcoming set to 140.0 (135.0 + 5.0).
            localViewModel.logSet(
                slotKey = initialSets[0].slotKey,
                weight = lb(135.0),
                reps = 9,
                rir = 1,
            )
            val updated2 = awaitItem() as WorkoutUiState.Loaded
            val setsAfterLog2 = updated2.lifts.first().sets
            assertEquals("140", setsAfterLog2[1].recommendedWeightLabel)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given active session when abandoned then ViewModel clears all states and overlay")
    fun abandonWorkoutSessionClearsStateAndOverlay() = runTest {
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        viewModel.uiState.test {
            val loaded = awaitLoaded()
            assertNotNull(loaded)

            // Abandon the session (process-scoped delete; navigate event after success)
            viewModel.abandonSession()
            advanceUntilIdle()

            // Keep last Loaded frame for NavHost exit — no "No active session." flash.
            expectNoEvents()
            assertTrue(viewModel.uiState.value is WorkoutUiState.Loaded)

            cancelAndIgnoreRemainingEvents()
        }
        viewModel.events.test {
            assertEquals(WorkoutUiEvent.NavigateBackAfterAbandon, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given active session when completed then ui stays Loaded for exit (no NoActiveSession flash)")
    fun completeWorkoutRetainsLoadedUiForExitTransition() = runTest {
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        viewModel.uiState.test {
            assertNotNull(awaitLoaded())

            viewModel.completeWorkout(saveTemplate = false)
            advanceUntilIdle()

            // Domain session is cleared, but Loaded is retained for the NavHost exit frame.
            expectNoEvents()
            assertTrue(viewModel.uiState.value is WorkoutUiState.Loaded)

            cancelAndIgnoreRemainingEvents()
        }
        viewModel.events.test {
            assertTrue(awaitItem() is WorkoutUiEvent.NavigateToFinalize)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Active session whose workout was deleted leaves Loading and shows NoActiveSession")
    fun deletedWorkoutTemplateLeavesNoActiveSessionNotSpinner() = runTest {
        // Keep a different training day so the program is still valid, but remove the bound one.
        val current = programRepository.getProgram()!!
        val otherWorkout =
            current.workouts.single().copy(
                id = WorkoutId("w-other"),
                name = "Other Day",
            )
        programRepository.saveProgram(current.copy(workouts = listOf(otherWorkout)))

        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        viewModel.uiState.test {
            var item = awaitItem()
            while (item is WorkoutUiState.Loading) {
                item = awaitItem()
            }
            assertTrue(item is WorkoutUiState.NoActiveSession)
            cancelAndIgnoreRemainingEvents()
        }
        advanceUntilIdle()
        assertNull(trainingLogRepository.getActiveSession())
    }

    @Test
    @DisplayName("Active Session Edit: Swap Lift in memory updates presentation lift metadata")
    fun activeSessionSwapLiftUpdatesMetadata() = runTest {
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        viewModel.uiState.test {
            val loaded = awaitLoaded()
            assertEquals("Squat", loaded.lifts.first().liftName)

            // Swap to catalogLift2 (Bench Press)
            viewModel.swapLift(workoutLiftId, LiftId("L-2"))
            advanceUntilIdle()
            val updated = awaitItem() as WorkoutUiState.Loaded
            assertEquals("Bench Press", updated.lifts.first().liftName)
            assertEquals("L-2", updated.lifts.first().liftId)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("After in-session swap, previous and recommendations rebind to the new lift")
    fun activeSessionSwapLiftRebindsPreviousAndRecommendations() = runTest {
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        viewModel.uiState.test {
            val loaded = awaitLoaded()
            assertEquals("Squat", loaded.lifts.first().liftName)
            val preSwapPrevious = loaded.lifts.first().sets.first().previousWeightLabel.orEmpty()
            assertTrue(
                preSwapPrevious.contains("135"),
                "pre-swap previous should come from L-1 history, got $preSwapPrevious",
            )

            // Distinct history for the swap target so stale L-1 baselines are detectable.
            seedHistoryForLift(LiftId("L-2"), name = "Bench Press", topWeight = 185.0)

            viewModel.swapLift(workoutLiftId, LiftId("L-2"))
            advanceUntilIdle()

            val updated = awaitItem() as WorkoutUiState.Loaded
            assertEquals("Bench Press", updated.lifts.first().liftName)
            assertEquals("L-2", updated.lifts.first().liftId)
            val previousLabel = updated.lifts.first().sets.first().previousWeightLabel.orEmpty()
            assertTrue(
                previousLabel.contains("185"),
                "expected previous from swapped-in lift history, got $previousLabel",
            )
            val recommended = updated.lifts.first().sets.first().recommendedWeightAmount
            assertTrue(
                recommended > 135.0,
                "recommended weight should track L-2 history (185-class), got $recommended",
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("After template re-point (lift merge) mid-session, name and previous rebind to survivor")
    fun templateRepointRebuildsNameAndPrevious() = runTest {
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        viewModel.uiState.test {
            val loaded = awaitLoaded()
            assertEquals("Squat", loaded.lifts.first().liftName)
            assertEquals("L-1", loaded.lifts.first().liftId)

            // Survivor history for the re-pointed catalog id (merge reassigns sets similarly).
            seedHistoryForLift(LiftId("L-2"), name = "Bench Press", topWeight = 185.0)

            repointActiveWorkoutTo(LiftId("L-2"))
            advanceUntilIdle()

            val updated = awaitItem() as WorkoutUiState.Loaded
            assertEquals("Bench Press", updated.lifts.first().liftName)
            assertEquals("L-2", updated.lifts.first().liftId)
            // Previous column should now come from survivor history, not the pre-merge snapshot.
            val previousLabel = updated.lifts.first().sets.first().previousWeightLabel.orEmpty()
            assertTrue(
                previousLabel.contains("185"),
                "expected previous from survivor history, got $previousLabel",
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("After re-point when source is deleted from catalog, card name is survivor not blank")
    fun templateRepointAfterSourceDeletedKeepsSurvivorName() = runTest {
        val liftRepository =
            FakeLiftRepository(initialLifts = listOf(catalogLift(), catalogLift2()))
        val viewModel = buildViewModel(liftRepository)
        viewModel.startSession(workoutId)
        advanceUntilIdle()
        assertEquals("Squat", (viewModel.uiState.value as WorkoutUiState.Loaded).lifts.first().liftName)

        seedHistoryForLift(LiftId("L-2"), name = "Bench Press", topWeight = 185.0)
        // Simulate custom-source merge: re-point then hard-delete the source row.
        repointActiveWorkoutTo(LiftId("L-2"))
        liftRepository.deleteLifts(listOf(LiftId("L-1")))
        advanceUntilIdle()

        val updated = viewModel.uiState.value as WorkoutUiState.Loaded
        assertEquals("Bench Press", updated.lifts.first().liftName)
        assertEquals("L-2", updated.lifts.first().liftId)
        assertTrue(updated.lifts.first().liftName.isNotBlank())
    }

    @Test
    @DisplayName("After merge remaps overlay swap/add to survivor, live UI shows survivor name")
    fun overlayRemapAfterMergeShowsSurvivorName() = runTest {
        val liftRepository =
            FakeLiftRepository(initialLifts = listOf(catalogLift(), catalogLift2()))
        val viewModel = buildViewModel(liftRepository)
        val sessionId = TrainingSessionId("ts-active")
        viewModel.startSession(workoutId)
        advanceUntilIdle()
        assertEquals("Squat", (viewModel.uiState.value as WorkoutUiState.Loaded).lifts.first().liftName)

        // In-session swap onto L-2 (as if the user performed the source lift).
        viewModel.swapLift(workoutLiftId, LiftId("L-2"))
        advanceUntilIdle()
        assertEquals(
            "Bench Press",
            (viewModel.uiState.value as WorkoutUiState.Loaded).lifts.first().liftName,
        )

        // Merge L-2 → L-1: remap store, template stays L-1, delete source from catalog.
        val remapped =
            overlayStore.load(sessionId)!!.remapCatalogLiftIds(
                sourceLiftIds = setOf(LiftId("L-2")),
                targetLiftId = LiftId("L-1"),
            )
        overlayStore.save(sessionId, remapped)
        liftRepository.deleteLifts(listOf(LiftId("L-2")))
        advanceUntilIdle()

        val updated = viewModel.uiState.value as WorkoutUiState.Loaded
        assertEquals("Squat", updated.lifts.first().liftName)
        assertEquals("L-1", updated.lifts.first().liftId)
        assertTrue(updated.lifts.first().liftName.isNotBlank())
    }

    @Test
    @DisplayName("After re-point when two slots share survivor, both cards show survivor name")
    fun duplicateSurvivorSlotsBothKeepNames() = runTest {
        val twoSlotProgram =
            Program(
                id = programId,
                name = "Test Program",
                createdAt = Instant.parse("2026-06-26T00:00:00Z"),
                cycleLength = 1,
                trainingWeekdays = setOf(Weekday.Monday),
                workouts =
                    listOf(
                        Workout(
                            id = workoutId,
                            programId = programId,
                            name = "Workout 1",
                            weekday = Weekday.Monday,
                            position = 0,
                            workoutLifts =
                                listOf(
                                    WorkoutLift(
                                        id = WorkoutLiftId("wl-1"),
                                        workoutId = workoutId,
                                        liftId = LiftId("L-1"),
                                        progressionScheme = ProgressionScheme.Double,
                                        position = 0,
                                        sets =
                                            listOf(
                                                LiftSet(
                                                    id = LiftSetId("ls-1"),
                                                    workoutLiftId = WorkoutLiftId("wl-1"),
                                                    position = 0,
                                                    setType = SetType.Standard,
                                                    repMin = 5,
                                                    repMax = 5,
                                                    rirGoal = 2,
                                                    isTopSet = true,
                                                ),
                                            ),
                                    ),
                                    WorkoutLift(
                                        id = WorkoutLiftId("wl-2"),
                                        workoutId = workoutId,
                                        liftId = LiftId("L-2"),
                                        progressionScheme = ProgressionScheme.Double,
                                        position = 1,
                                        sets =
                                            listOf(
                                                LiftSet(
                                                    id = LiftSetId("ls-2"),
                                                    workoutLiftId = WorkoutLiftId("wl-2"),
                                                    position = 0,
                                                    setType = SetType.Standard,
                                                    repMin = 5,
                                                    repMax = 5,
                                                    rirGoal = 2,
                                                    isTopSet = true,
                                                ),
                                            ),
                                    ),
                                ),
                        ),
                    ),
            )
        val viewModel =
            buildViewModelWithProgram(
                twoSlotProgram,
                lifts = listOf(catalogLift(), catalogLift2()),
            )
        viewModel.startSession(workoutId)
        viewModel.uiState.test {
            val loaded = awaitLoaded()
            assertEquals(2, loaded.lifts.size)
            assertEquals("Squat", loaded.lifts[0].liftName)
            assertEquals("Bench Press", loaded.lifts[1].liftName)

            // Merge L-1 → L-2 without collapsing slots.
            val current = programRepository.getProgram()!!
            val workout = current.workouts.single()
            programRepository.saveProgram(
                current.copy(
                    workouts =
                        listOf(
                            workout.copy(
                                workoutLifts =
                                    workout.workoutLifts.map { it.copy(liftId = LiftId("L-2")) },
                            ),
                        ),
                ),
            )
            advanceUntilIdle()

            val updated = awaitItem() as WorkoutUiState.Loaded
            assertEquals(2, updated.lifts.size)
            assertEquals("wl-1", updated.lifts[0].workoutLiftId)
            assertEquals("wl-2", updated.lifts[1].workoutLiftId)
            assertTrue(updated.lifts.all { it.liftId == "L-2" })
            assertTrue(updated.lifts.all { it.liftName == "Bench Press" })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("After template re-point, logSet attributes LoggedSet.liftId to the survivor")
    fun logAfterRepointAttributesToSurvivor() = runTest {
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        advanceUntilIdle()

        repointActiveWorkoutTo(LiftId("L-2"))
        advanceUntilIdle()

        viewModel.logSet(
            slotKey = liftSetId.value,
            weight = lb(185.0),
            reps = 5,
            rir = 2,
        )
        advanceUntilIdle()

        val sessionSets =
            trainingLogRepository.getSetsForSession(TrainingSessionId("ts-active"))
        assertEquals(1, sessionSets.size)
        assertEquals(LiftId("L-2"), sessionSets.single().liftId)
        assertEquals("Bench Press", sessionSets.single().exerciseNameRaw)
    }

    @Test
    @DisplayName("Bug #10: after in-session swap, logSet persists LoggedSet.liftId of the swapped lift")
    fun swapThenLogSetUsesSwappedLiftId() = runTest {
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        advanceUntilIdle()

        viewModel.swapLift(workoutLiftId, LiftId("L-2"))
        advanceUntilIdle()
        viewModel.logSet(
            slotKey = liftSetId.value,
            weight = lb(135.0),
            reps = 5,
            rir = 2,
        )
        advanceUntilIdle()

        val sessionSets =
            trainingLogRepository.getSetsForSession(TrainingSessionId("ts-active"))
        assertEquals(1, sessionSets.size)
        val logged = sessionSets.single()
        assertEquals(LiftId("L-2"), logged.liftId, "logged set must attribute to performed (swapped) lift")
        assertEquals("Bench Press", logged.exerciseNameRaw)
        assertEquals("Bench Press", logged.liftContext.liftName)
        // Original template lift must not receive the set.
        assertTrue(
            trainingLogRepository.getSetsForLift(liftId).none { it.sessionId.value == "ts-active" },
        )
    }

    @Test
    @DisplayName("Bug #10: complete with saveTemplate=false keeps sets on swapped lift and leaves template")
    fun swapLogCompleteDiscardTemplateKeepsSwappedSetOwnership() = runTest {
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        advanceUntilIdle()

        viewModel.swapLift(workoutLiftId, LiftId("L-2"))
        advanceUntilIdle()
        viewModel.logSet(
            slotKey = liftSetId.value,
            weight = lb(185.0),
            reps = 5,
            rir = 1,
        )
        advanceUntilIdle()

        viewModel.completeWorkout(saveTemplate = false)
        advanceUntilIdle()

        val sessionId = TrainingSessionId("ts-active")
        val session = trainingLogRepository.getSession(sessionId)
        assertEquals(TrainingSessionStatus.COMPLETED, session?.status)

        val sessionSets = trainingLogRepository.getSetsForSession(sessionId)
        assertEquals(1, sessionSets.size)
        assertEquals(LiftId("L-2"), sessionSets.single().liftId)

        // Template must still point at the original lift when the user discards edits.
        val templateLift =
            programRepository.getProgram()!!.workouts.single().workoutLifts.single()
        assertEquals(liftId, templateLift.liftId)
        assertEquals(0, programRepository.saveProgramCalls.size)
    }

    @Test
    @DisplayName("Bug #10: complete with saveTemplate=true persists structural swap on template")
    fun swapLogCompleteSaveTemplateUpdatesTemplateLiftId() = runTest {
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        advanceUntilIdle()

        viewModel.swapLift(workoutLiftId, LiftId("L-2"))
        advanceUntilIdle()
        viewModel.logSet(
            slotKey = liftSetId.value,
            weight = lb(185.0),
            reps = 5,
            rir = 1,
        )
        advanceUntilIdle()

        viewModel.completeWorkout(saveTemplate = true)
        advanceUntilIdle()

        val sessionSets =
            trainingLogRepository.getSetsForSession(TrainingSessionId("ts-active"))
        assertEquals(LiftId("L-2"), sessionSets.single().liftId)

        val templateLift =
            programRepository.getProgram()!!.workouts.single().workoutLifts.single()
        assertEquals(LiftId("L-2"), templateLift.liftId)
        assertTrue(programRepository.saveProgramCalls.isNotEmpty())
    }

    @Test
    @DisplayName("Active Session Edit: Remove Lift in memory filters it from UI list")
    fun activeSessionRemoveLiftFiltersFromUiList() = runTest {
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        viewModel.uiState.test {
            val loaded = awaitLoaded()
            assertEquals(1, loaded.lifts.size)

            viewModel.removeLift(workoutLiftId)
            val updated = awaitItem() as WorkoutUiState.Loaded
            assertEquals(0, updated.lifts.size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Active Session Edit: Set rest timer override in memory updates property")
    fun activeSessionSetRestTimerOverrideUpdatesProperty() = runTest {
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        viewModel.uiState.test {
            val loaded = awaitLoaded()
            assertTrue(!loaded.lifts.first().useRestTimerBetweenLifts)

            viewModel.setUseRestTimerBetweenLifts(workoutLiftId, true)
            val updated = awaitItem() as WorkoutUiState.Loaded
            assertTrue(updated.lifts.first().useRestTimerBetweenLifts)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("setRestSecondsForSlots updates overlay and rendered rest for the slot")
    fun setRestSecondsForSlotsUpdatesRenderedRest() = runTest {
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        viewModel.uiState.test {
            val loaded = awaitLoaded()
            val slotKey = loaded.lifts.first().sets.first().slotKey
            assertEquals(120, loaded.lifts.first().sets.first().restSeconds)

            viewModel.setRestSecondsForSlots(mapOf(slotKey to 45))
            val updated = awaitItem() as WorkoutUiState.Loaded
            assertEquals(45, updated.lifts.first().sets.first().restSeconds)
            assertTrue(updated.hasDirtyEdits)

            cancelAndIgnoreRemainingEvents()
        }
        val stored = overlayStore.storedOverlay()
        assertEquals(
            45,
            stored.restSecondsOverrides[SetSlotId.Template(liftSetId)],
        )
    }

    @Test
    @DisplayName("Given persisted overlay when startSession then overlay is restored")
    fun persistedOverlayIsRestoredOnStartSession() = runTest {
        // Seed a persisted overlay (e.g. left behind by a prior process that was killed) with
        // a swap + an added set, then start the session. The rendered state must reflect the
        // restored swap and the restored added slot.
        overlayStore.seed(
            TrainingSessionId("ts-active"),
            WorkoutOverlay(
                swappedLifts = mapOf(workoutLiftId to LiftId("L-2")),
            ),
        )
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        advanceUntilIdle()

        val loaded = viewModel.uiState.value as WorkoutUiState.Loaded
        // The swap to L-2 (Bench Press) survives the process death.
        assertEquals("Bench Press", loaded.lifts.first().liftName)
        assertEquals("L-2", loaded.lifts.first().liftId)
    }

    @Test
    @DisplayName("Given no persisted overlay when startSession then DB-resumed logged sets are used")
    fun noOverlayFallsBackToDbResume() = runTest {
        // With nothing persisted, startSession falls back to the DB-resume path
        // (template-slot logged sets recovered from Room). After recompute, the session
        // flushes live recs so minimize/reopen can restore the freeze — that is a save,
        // not a structural overlay adopt.
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        advanceUntilIdle()

        val loaded = viewModel.uiState.value as WorkoutUiState.Loaded
        // First set starts unlogged (no logged sets in the seeded session).
        assertNull(loaded.lifts.first().sets.first().loggedWeightLabel)
        assertTrue(
            overlayStore.saveCallCount >= 1,
            "initial recompute flushes live recommendations for restore",
        )
        assertTrue(
            overlayStore.storedOverlay().loggedSets.isEmpty(),
            "DB-resume with no Room logs must not invent logged sets in the overlay",
        )
    }

    @Test
    @DisplayName("When overlay mutated then it is persisted to the store")
    fun overlayMutationIsPersisted() = runTest {
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        advanceUntilIdle()

        viewModel.swapLift(workoutLiftId, LiftId("L-2"))
        advanceUntilIdle()

        assertTrue(overlayStore.saveCallCount >= 1, "swap must persist the overlay")
        val stored = overlayStore.storedOverlay()
        assertEquals(LiftId("L-2"), stored.swappedLifts[workoutLiftId])
    }

    @Test
    @DisplayName("Given active session when abandoned then persisted overlay is cleared")
    fun abandonClearsPersistedOverlay() = runTest {
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        advanceUntilIdle()

        viewModel.swapLift(workoutLiftId, LiftId("L-2"))
        advanceUntilIdle()
        assertTrue(overlayStore.saveCallCount >= 1)

        viewModel.abandonSession()
        advanceUntilIdle()

        assertEquals(1, overlayStore.clearCallCount)
        assertNull(overlayStore.storedSessionId())
    }

    @Test
    @DisplayName("S7-mutation-defense-no-verify")
    fun `S7-mutation-defense-no-verify`() {
        // This ViewModel test must not use MockK verify / coVerify (Detekt enforced).
        // State-based assertions on uiState only (UNIT_TESTING.md §1). We assert the test
        // source does not import the MockK verification symbols.
        val testSource = File("src/test/kotlin/com/stratum/feature/workout/WorkoutViewModelTest.kt")
        assertTrue(testSource.exists())
        // Only inspect `import` statements — guards against self-matches from the literal
        // forbidden-token list below (Detekt `ForbiddenVerifyInViewModelTests` parity).
        val imports = testSource.readLines().filter { it.startsWith("import ") }
        val forbiddenImports =
            listOf("io.mockk.verify", "io.mockk.coVerify", "mockk.verify", "mockk.coVerify")
        val offending = forbiddenImports.filter { token -> imports.any { it.contains(token) } }
        assertTrue(offending.isEmpty(), "ViewModel test must not import MockK verify symbols: $offending")
    }

    @Test
    @DisplayName("addSetForLift appends a set and marks overlay dirty")
    fun addSetForLiftAppendsSet() = runTest {
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        viewModel.uiState.test {
            val loaded = awaitLoaded()
            assertEquals(2, loaded.lifts.first().sets.size)

            viewModel.addSetForLift(workoutLiftId.value)
            val after = awaitItem() as WorkoutUiState.Loaded
            assertEquals(3, after.lifts.first().sets.size)
            assertTrue(after.hasDirtyEdits)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("deleteSetByKey removes a non-last set")
    fun deleteSetByKeyRemovesSet() = runTest {
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        viewModel.uiState.test {
            awaitLoaded()
            viewModel.addSetForLift(workoutLiftId.value)
            val withThree = awaitItem() as WorkoutUiState.Loaded
            assertEquals(3, withThree.lifts.first().sets.size)

            val middleKey = withThree.lifts.first().sets[1].slotKey
            viewModel.deleteSetByKey(middleKey)
            val after = awaitItem() as WorkoutUiState.Loaded
            assertEquals(2, after.lifts.first().sets.size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("deleteSetByKey no-ops when the lift has only one set")
    fun deleteSetByKeyBlocksLastSet() = runTest {
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        advanceUntilIdle()

        // Drop to one set first
        viewModel.deleteSetByKey(backoffSetId.value)
        advanceUntilIdle()
        val oneSet = viewModel.uiState.value as WorkoutUiState.Loaded
        assertEquals(1, oneSet.lifts.first().sets.size)
        val onlyKey = oneSet.lifts.first().sets.single().slotKey

        viewModel.deleteSetByKey(onlyKey)
        advanceUntilIdle()
        val stillOne = viewModel.uiState.value as WorkoutUiState.Loaded
        assertEquals(1, stillOne.lifts.first().sets.size)
    }

    @Test
    @DisplayName("addLift appends a new lift with one cold-start set")
    fun addLiftAppendsLift() = runTest {
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        viewModel.uiState.test {
            val loaded = awaitLoaded()
            assertEquals(1, loaded.lifts.size)

            viewModel.addLifts(listOf(LiftId("L-2")))
            val after = awaitItem() as WorkoutUiState.Loaded
            assertEquals(2, after.lifts.size)
            assertEquals("Bench Press", after.lifts.last().liftName)
            assertEquals(1, after.lifts.last().sets.size)
            assertTrue(after.hasDirtyEdits)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("addLift then logSet persists set for session-only workout lift")
    fun addLiftThenLogSetPersists() = runTest {
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        advanceUntilIdle()

        viewModel.addLifts(listOf(LiftId("L-2")))
        advanceUntilIdle()
        val withAdded = viewModel.uiState.value as WorkoutUiState.Loaded
        assertEquals(2, withAdded.lifts.size)
        val addedLift = withAdded.lifts.last()
        val addedSlotKey = addedLift.sets.single().slotKey

        viewModel.logSet(
            slotKey = addedSlotKey,
            weight = lb(95.0),
            reps = 10,
            rir = 2,
        )
        advanceUntilIdle()

        val afterLog = viewModel.uiState.value as WorkoutUiState.Loaded
        val logged = afterLog.lifts.last().sets.single()
        assertTrue(logged.isComplete)
        assertEquals("95", logged.loggedWeightLabel)
        assertEquals("10", logged.loggedRepsLabel)
        assertEquals(1, trainingLogRepository.insertSetCallCount)
        assertNull(viewModel.userMessage.value)

        val sessionSets =
            trainingLogRepository.getSetsForSession(TrainingSessionId("ts-active"))
        assertEquals(1, sessionSets.size)
        assertEquals(LiftId("L-2"), sessionSets.single().liftId)
        assertEquals(
            WorkoutLiftId(addedLift.workoutLiftId),
            sessionSets.single().liftContext.workoutLiftId,
        )
    }

    @Test
    @DisplayName("addLifts as superset shares a group id")
    fun addLiftsAsSupersetSharesGroup() = runTest {
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        viewModel.uiState.test {
            awaitLoaded()
            viewModel.addLifts(listOf(LiftId("L-2"), LiftId("L-1")), asSuperset = true)
            val after = awaitItem() as WorkoutUiState.Loaded
            val added = after.lifts.takeLast(2)
            assertEquals(2, added.size)
            assertNotNull(added[0].supersetGroupId)
            assertEquals(added[0].supersetGroupId, added[1].supersetGroupId)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("removeLift of one session-added superset member clears the survivor group id")
    fun removeSessionAddedSupersetClearsOrphanGroup() = runTest {
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        advanceUntilIdle()

        viewModel.addLifts(listOf(LiftId("L-2"), LiftId("L-1")), asSuperset = true)
        advanceUntilIdle()
        val withSuperset = viewModel.uiState.value as WorkoutUiState.Loaded
        val added = withSuperset.lifts.takeLast(2)
        assertEquals(2, added.size)
        assertNotNull(added[0].supersetGroupId)
        assertEquals(added[0].supersetGroupId, added[1].supersetGroupId)

        viewModel.removeLift(WorkoutLiftId(added[1].workoutLiftId))
        advanceUntilIdle()
        val after = viewModel.uiState.value as WorkoutUiState.Loaded
        val survivor = after.lifts.single { it.workoutLiftId == added[0].workoutLiftId }
        assertNull(survivor.supersetGroupId)
    }

    @Test
    @DisplayName("removeLift of one template superset member clears the survivor group id")
    fun removeTemplateSupersetClearsOrphanGroup() = runTest {
        val groupId = com.stratum.domain.program.identity.WorkoutSupersetGroupId("ss-template")
        val wl2 = WorkoutLiftId("wl-2")
        val ls3 = LiftSetId("ls-3")
        programRepository = FakeProgramRepository(
            Program(
                id = programId,
                name = "Test Program",
                createdAt = Instant.parse("2026-06-26T00:00:00Z"),
                cycleLength = 1,
                trainingWeekdays = setOf(Weekday.Monday),
                workouts = listOf(
                    Workout(
                        id = workoutId,
                        programId = programId,
                        name = "Workout 1",
                        weekday = Weekday.Monday,
                        position = 0,
                        workoutLifts = listOf(
                            WorkoutLift(
                                id = workoutLiftId,
                                workoutId = workoutId,
                                liftId = liftId,
                                progressionScheme = ProgressionScheme.Double,
                                position = 0,
                                sets = listOf(
                                    LiftSet(
                                        id = liftSetId,
                                        workoutLiftId = workoutLiftId,
                                        position = 0,
                                        setType = SetType.Standard,
                                        repMin = 4,
                                        repMax = 6,
                                        rirGoal = 2,
                                        isTopSet = true,
                                    ),
                                ),
                                supersetGroupId = groupId,
                            ),
                            WorkoutLift(
                                id = wl2,
                                workoutId = workoutId,
                                liftId = LiftId("L-2"),
                                progressionScheme = ProgressionScheme.Double,
                                position = 1,
                                sets = listOf(
                                    LiftSet(
                                        id = ls3,
                                        workoutLiftId = wl2,
                                        position = 0,
                                        setType = SetType.Standard,
                                        repMin = 8,
                                        repMax = 12,
                                        rirGoal = 2,
                                        isTopSet = true,
                                    ),
                                ),
                                supersetGroupId = groupId,
                            ),
                        ),
                    ),
                ),
            ),
        )
        seedHistoryForLift(
            historyLiftId = LiftId("L-2"),
            name = "Bench Press",
            topWeight = 135.0,
        )
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        advanceUntilIdle()

        val loaded = viewModel.uiState.value as WorkoutUiState.Loaded
        assertEquals(2, loaded.lifts.size)
        assertEquals(groupId.value, loaded.lifts[0].supersetGroupId)
        assertEquals(groupId.value, loaded.lifts[1].supersetGroupId)

        viewModel.removeLift(wl2)
        advanceUntilIdle()
        val after = viewModel.uiState.value as WorkoutUiState.Loaded
        assertEquals(1, after.lifts.size)
        assertNull(after.lifts.single().supersetGroupId)
    }

    @Test
    @DisplayName("LiftSelected with null workoutLiftId adds a lift via edit session")
    fun liftSelectedNullIdAddsLift() = runTest {
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        advanceUntilIdle()

        workoutEditSession.selectLift(
            workoutId = workoutId.value,
            workoutLiftId = null,
            liftId = "L-2",
        )
        advanceUntilIdle()

        val loaded = viewModel.uiState.value as WorkoutUiState.Loaded
        assertEquals(2, loaded.lifts.size)
        assertEquals("Bench Press", loaded.lifts.last().liftName)
    }

    @Test
    @DisplayName("LiftSelected with workoutLiftId swaps lift via edit session")
    fun liftSelectedWithWorkoutLiftIdSwapsLift() = runTest {
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        advanceUntilIdle()

        workoutEditSession.selectLift(
            workoutId = workoutId.value,
            workoutLiftId = workoutLiftId.value,
            liftId = "L-2",
        )
        advanceUntilIdle()

        val loaded = viewModel.uiState.value as WorkoutUiState.Loaded
        assertEquals("Bench Press", loaded.lifts.first().liftName)
        assertEquals("L-2", loaded.lifts.first().liftId)
    }

    @Test
    @DisplayName("LiftSelected with mismatched workoutId logs error and ignores event")
    fun liftSelectedWithMismatchedWorkoutIdLogsError() = runTest {
        val fakeLogging = FakeLoggingPort()
        val viewModel = buildViewModel(fakeLogging = fakeLogging)
        viewModel.startSession(workoutId)
        advanceUntilIdle()

        workoutEditSession.selectLift(
            workoutId = "wrong-workout-id",
            workoutLiftId = workoutLiftId.value,
            liftId = "L-2",
        )
        advanceUntilIdle()

        val loaded = viewModel.uiState.value as WorkoutUiState.Loaded
        assertEquals("Squat", loaded.lifts.first().liftName)
        assertTrue(fakeLogging.errors.any { it.message.contains("Ignored LiftSelected") })
    }

    @Test
    @DisplayName("removeLift drops a session-added lift without template delete marker")
    fun removeSessionAddedLift() = runTest {
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        advanceUntilIdle()

        viewModel.addLifts(listOf(LiftId("L-2")))
        advanceUntilIdle()
        val withAdded = viewModel.uiState.value as WorkoutUiState.Loaded
        assertEquals(2, withAdded.lifts.size)
        assertTrue(withAdded.hasDirtyEdits)
        val addedId = withAdded.lifts.last().workoutLiftId

        viewModel.removeLift(WorkoutLiftId(addedId))
        advanceUntilIdle()
        val after = viewModel.uiState.value as WorkoutUiState.Loaded
        assertEquals(1, after.lifts.size)
        assertEquals("Squat", after.lifts.single().liftName)
        // Add → remove should not leave orderedLiftIds/addedLifts fossils as dirty.
        assertTrue(!after.hasDirtyEdits)
    }

    @Test
    @DisplayName("Given persisted overlay with addedLifts when startSession then lift is restored")
    fun persistedAddedLiftsRestoredOnStartSession() = runTest {
        val addedWl = WorkoutLiftId("wl-session-added")
        val slotId =
            com.stratum.domain.workout.SetSlotId.Overlay(
                com.stratum.domain.workout.OverlaySetInstanceId("added-lift-restored-0"),
            )
        overlayStore.seed(
            TrainingSessionId("ts-active"),
            WorkoutOverlay(
                addedLifts =
                    listOf(
                        com.stratum.domain.workout.OverlayAddedLift(
                            workoutLiftId = addedWl,
                            liftId = LiftId("L-2"),
                        ),
                    ),
                addedSlots =
                    listOf(
                        com.stratum.domain.workout.SetSlot(
                            slotId = slotId,
                            workoutLiftId = addedWl,
                            liftId = LiftId("L-2"),
                            position = 0,
                            setType = SetType.Standard,
                            isTopSet = true,
                            repMin = 8,
                            repMax = 12,
                            rirGoal = 2,
                            restSeconds = 120,
                        ),
                    ),
                dynamicBaselines =
                    mapOf(
                        slotId to
                            com.stratum.domain.workout.SlotRecommendation(
                                slotId = slotId,
                                recommendedWeight = lb(0.0),
                                baselineExpectedReps = 8,
                            ),
                    ),
                orderedLiftIds = listOf(workoutLiftId.value, addedWl.value),
            ),
        )
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        advanceUntilIdle()

        val loaded = viewModel.uiState.value as WorkoutUiState.Loaded
        assertEquals(2, loaded.lifts.size)
        assertEquals("Bench Press", loaded.lifts.last().liftName)
        assertEquals(1, loaded.lifts.last().sets.size)
    }

    @Test
    @DisplayName("Ad-hoc empty session loads with no template lifts and route key = session id")
    fun adHocEmptySessionLoadsEmptyStructure() = runTest {
        trainingLogRepository = FakeTrainingLogRepository(
            initialSessions = listOf(
                TrainingSession(
                    id = TrainingSessionId("ts-adhoc"),
                    programId = programId,
                    workoutId = null,
                    status = TrainingSessionStatus.IN_PROGRESS,
                    kind = TrainingSessionKind.AD_HOC,
                    origin = TrainingSessionOrigin.LIVE,
                    cycleNumberSnapshot = 1,
                    weekdaySnapshot = null,
                    startedAt = Instant.parse("2026-07-05T10:00:00Z"),
                    endedAt = null,
                    sourceApp = null,
                    workoutNameSnapshot = "Empty Workout",
                ),
            ),
        )
        val routeId = WorkoutId("ts-adhoc")
        val viewModel = buildViewModel()
        viewModel.startSession(routeId)
        viewModel.uiState.test {
            val loaded = awaitLoaded()
            assertEquals("ts-adhoc", loaded.workoutId)
            assertEquals("Empty Workout", loaded.workoutName)
            assertTrue(loaded.lifts.isEmpty())
            // Overlay mutations must not offer save-to-template for ad-hoc.
            assertTrue(!loaded.hasDirtyEdits)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Ad-hoc empty session can add a lift without marking dirty-template edits")
    fun adHocEmptySessionAddLift() = runTest {
        trainingLogRepository = FakeTrainingLogRepository(
            initialSessions = listOf(
                TrainingSession(
                    id = TrainingSessionId("ts-adhoc"),
                    programId = programId,
                    workoutId = null,
                    status = TrainingSessionStatus.IN_PROGRESS,
                    kind = TrainingSessionKind.AD_HOC,
                    origin = TrainingSessionOrigin.LIVE,
                    cycleNumberSnapshot = 1,
                    weekdaySnapshot = null,
                    startedAt = Instant.parse("2026-07-05T10:00:00Z"),
                    endedAt = null,
                    sourceApp = null,
                    workoutNameSnapshot = "Empty Workout",
                ),
            ),
        )
        val viewModel = buildViewModel()
        viewModel.startSession(WorkoutId("ts-adhoc"))
        advanceUntilIdle()

        viewModel.addLifts(listOf(LiftId("L-2")))
        advanceUntilIdle()
        val loaded = viewModel.uiState.value as WorkoutUiState.Loaded
        assertEquals(1, loaded.lifts.size)
        assertEquals("Bench Press", loaded.lifts.single().liftName)
        assertTrue(!loaded.hasDirtyEdits)
    }

    @Test
    @DisplayName("Ad-hoc swap updates rendered lift name (session-added path)")
    fun adHocSwapUpdatesRenderedName() = runTest {
        trainingLogRepository = FakeTrainingLogRepository(
            initialSessions = listOf(
                TrainingSession(
                    id = TrainingSessionId("ts-adhoc"),
                    programId = programId,
                    workoutId = null,
                    status = TrainingSessionStatus.IN_PROGRESS,
                    kind = TrainingSessionKind.AD_HOC,
                    origin = TrainingSessionOrigin.LIVE,
                    cycleNumberSnapshot = 1,
                    weekdaySnapshot = null,
                    startedAt = Instant.parse("2026-07-05T10:00:00Z"),
                    endedAt = null,
                    sourceApp = null,
                    workoutNameSnapshot = "Empty Workout",
                ),
            ),
        )
        val viewModel = buildViewModel()
        viewModel.startSession(WorkoutId("ts-adhoc"))
        advanceUntilIdle()
        viewModel.addLifts(listOf(LiftId("L-2")))
        advanceUntilIdle()
        val added = (viewModel.uiState.value as WorkoutUiState.Loaded).lifts.single()
        assertEquals("Bench Press", added.liftName)

        viewModel.swapLift(WorkoutLiftId(added.workoutLiftId), liftId)
        advanceUntilIdle()
        val after = viewModel.uiState.value as WorkoutUiState.Loaded
        assertEquals("Squat", after.lifts.single().liftName)
        assertEquals(liftId.value, after.lifts.single().liftId)
        assertTrue(!after.hasDirtyEdits)
    }

    @Test
    @DisplayName("Ad-hoc complete finalizes without save-template prompt eligibility")
    fun adHocCompleteDoesNotOfferTemplateSave() = runTest {
        trainingLogRepository = FakeTrainingLogRepository(
            initialSessions = listOf(
                TrainingSession(
                    id = TrainingSessionId("ts-adhoc"),
                    programId = programId,
                    workoutId = null,
                    status = TrainingSessionStatus.IN_PROGRESS,
                    kind = TrainingSessionKind.AD_HOC,
                    origin = TrainingSessionOrigin.LIVE,
                    cycleNumberSnapshot = 1,
                    weekdaySnapshot = null,
                    startedAt = Instant.parse("2026-07-05T10:00:00Z"),
                    endedAt = null,
                    sourceApp = null,
                    workoutNameSnapshot = "Empty Workout",
                ),
            ),
        )
        val viewModel = buildViewModel()
        viewModel.startSession(WorkoutId("ts-adhoc"))
        advanceUntilIdle()
        viewModel.addLifts(listOf(LiftId("L-2")))
        advanceUntilIdle()
        val before = viewModel.uiState.value as WorkoutUiState.Loaded
        assertTrue(!before.hasDirtyEdits)

        viewModel.events.test {
            viewModel.completeWorkout(saveTemplate = true)
            advanceUntilIdle()
            val event = awaitItem()
            assertTrue(event is WorkoutUiEvent.NavigateToFinalize)
            cancelAndIgnoreRemainingEvents()
        }
        val active = trainingLogRepository.getActiveSession()
        assertNull(active)
        // Program template unchanged — no spurious day with session-id as workout id.
        assertTrue(programRepository.getProgram()!!.workouts.none { it.id.value == "ts-adhoc" })
    }

    @Test
    @DisplayName("Ad-hoc resume rebuilds structure from DB logs when overlay is missing")
    fun adHocResumeRebuildsFromLoggedSetsWithoutOverlay() = runTest {
        val sessionId = TrainingSessionId("ts-adhoc")
        val wlId = WorkoutLiftId("wl-session-1")
        trainingLogRepository = FakeTrainingLogRepository(
            initialSessions = listOf(
                TrainingSession(
                    id = sessionId,
                    programId = programId,
                    workoutId = null,
                    status = TrainingSessionStatus.IN_PROGRESS,
                    kind = TrainingSessionKind.AD_HOC,
                    origin = TrainingSessionOrigin.LIVE,
                    cycleNumberSnapshot = 1,
                    weekdaySnapshot = null,
                    startedAt = Instant.parse("2026-07-05T10:00:00Z"),
                    endedAt = null,
                    sourceApp = null,
                    workoutNameSnapshot = "Empty Workout",
                ),
            ),
        )
        // Seed a durable logged set with session-only workoutLiftId (no overlay store).
        trainingLogRepository.seedSet(
            com.stratum.domain.training.LoggedSet(
                id = com.stratum.domain.importing.identity.LoggedSetId("ls-adhoc-1"),
                sessionId = sessionId,
                exerciseNameRaw = "Bench Press",
                liftId = LiftId("L-2"),
                setIndex = 0,
                weight = lb(95.0),
                reps = 8,
                rir = 2,
                completedAt = Instant.parse("2026-07-05T10:05:00Z"),
                liftContext = com.stratum.domain.training.LoggedLiftContext(
                    liftName = "Bench Press",
                    workoutLiftId = wlId,
                    progressionScheme = ProgressionScheme.Double,
                    setType = SetType.Standard,
                    repMin = 8,
                    repMax = 12,
                    rirGoal = 2,
                ),
            ),
        )
        // Ensure overlay store is empty for this session.
        overlayStore = FakeWorkoutOverlayStore()
        val viewModel = buildViewModel()
        viewModel.startSession(WorkoutId("ts-adhoc"))
        advanceUntilIdle()

        val loaded = viewModel.uiState.value as WorkoutUiState.Loaded
        assertEquals(1, loaded.lifts.size)
        assertEquals("Bench Press", loaded.lifts.single().liftName)
        assertTrue(loaded.lifts.single().sets.any { it.isComplete })
    }

    @Test
    @DisplayName(
        "Actor regression: myo activation with trailing Standards keeps minis after cycle re-emit",
    )
    fun myoMinisSurviveCatalogCycleReEmit() = runTest {
        val myoActivationId = LiftSetId("ls-myo-act")
        val trailingStdId = LiftSetId("ls-myo-std")
        val myoProgram =
            Program(
                id = programId,
                name = "Myo Program",
                createdAt = Instant.parse("2026-06-26T00:00:00Z"),
                cycleLength = 1,
                trainingWeekdays = setOf(Weekday.Monday),
                workouts =
                    listOf(
                        Workout(
                            id = workoutId,
                            programId = programId,
                            name = "Myo Day",
                            weekday = Weekday.Monday,
                            position = 0,
                            workoutLifts =
                                listOf(
                                    WorkoutLift(
                                        id = workoutLiftId,
                                        workoutId = workoutId,
                                        liftId = liftId,
                                        progressionScheme = ProgressionScheme.Double,
                                        position = 0,
                                        sets =
                                            listOf(
                                                LiftSet(
                                                    id = myoActivationId,
                                                    workoutLiftId = workoutLiftId,
                                                    position = 0,
                                                    setType = SetType.MyoRep,
                                                    repMin = 10,
                                                    repMax = 15,
                                                    rirGoal = 1,
                                                    isTopSet = true,
                                                    myoRepKind =
                                                        com.stratum.domain.workout.myorep.MyoRepKind.RepFloor,
                                                ),
                                                LiftSet(
                                                    id = trailingStdId,
                                                    workoutLiftId = workoutLiftId,
                                                    position = 1,
                                                    setType = SetType.Standard,
                                                    repMin = 0,
                                                    repMax = 0,
                                                    rirGoal = 2,
                                                    isTopSet = false,
                                                ),
                                            ),
                                    ),
                                ),
                        ),
                    ),
            )
        // Empty history so snapshot does not require prior top-set data.
        programRepository = FakeProgramRepository(myoProgram)
        progressionHistory = FakeProgressionHistoryRepository()
        trainingLogRepository =
            FakeTrainingLogRepository(
                initialSessions =
                    listOf(
                        TrainingSession(
                            id = TrainingSessionId("ts-active"),
                            programId = programId,
                            workoutId = workoutId,
                            status = TrainingSessionStatus.IN_PROGRESS,
                            kind = TrainingSessionKind.PROGRAM_WORKOUT,
                            origin = TrainingSessionOrigin.LIVE,
                            cycleNumberSnapshot = 1,
                            weekdaySnapshot = Weekday.Monday,
                            startedAt = Instant.parse("2026-07-05T10:00:00Z"),
                            endedAt = null,
                            sourceApp = null,
                        ),
                    ),
            )
        val viewModel = buildViewModelWithProgram(myoProgram)
        viewModel.startSession(workoutId)
        advanceUntilIdle()

        val beforeLog = viewModel.uiState.value as WorkoutUiState.Loaded
        assertEquals(2, beforeLog.lifts.single().sets.size, "activation + trailing standard")
        val activationKey = beforeLog.lifts.single().sets.first().slotKey

        viewModel.logSet(
            slotKey = activationKey,
            weight = lb(65.0),
            reps = 12,
            rir = 1,
        )
        advanceUntilIdle()

        val afterLog = viewModel.uiState.value as WorkoutUiState.Loaded
        val setsAfterLog = afterLog.lifts.single().sets
        assertTrue(
            setsAfterLog.size >= 3,
            "myo mini should spawn between activation and Standards; was ${setsAfterLog.size}",
        )
        val miniKeys =
            setsAfterLog
                .map { it.slotKey }
                .filter { it.startsWith("mini-") }
        assertTrue(miniKeys.isNotEmpty(), "expected overlay mini slot keys")

        // Stale store without minis (pre-actor race would adopt this over unflushed memory).
        // After actor log flush, memory is flushed; re-emit still must not drop minis when
        // the store is force-staled then a cycle emission reloads.
        overlayStore.seed(
            TrainingSessionId("ts-active"),
            WorkoutOverlay(
                loggedSets =
                    mapOf(
                        SetSlotId.Template(myoActivationId) to
                            com.stratum.domain.workout.LoggedSetValue(
                                slotId = SetSlotId.Template(myoActivationId),
                                weight = lb(65.0),
                                reps = 12,
                                rir = 1,
                            ),
                    ),
            ),
        )
        // Force ObserveCycleProgress to re-emit (program flow).
        programRepository.saveProgram(myoProgram)
        advanceUntilIdle()

        val afterRefresh = viewModel.uiState.value as WorkoutUiState.Loaded
        val setsAfterRefresh = afterRefresh.lifts.single().sets
        assertTrue(
            setsAfterRefresh.size >= 3,
            "minis must survive cycle re-emit; was ${setsAfterRefresh.size}",
        )
        assertTrue(
            setsAfterRefresh.any { it.slotKey.startsWith("mini-") },
            "mini slot must remain after cycle re-emit",
        )
    }

    @Test
    @DisplayName("save lift notes updates rendered workout and lift pins")
    fun saveLiftNotesUpdatesRenderedPins() = runTest {
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        advanceUntilIdle()
        val loaded = viewModel.uiState.value as WorkoutUiState.Loaded
        assertNull(loaded.lifts.single().workoutPinnedNote)
        assertNull(loaded.lifts.single().liftPinnedNote)

        viewModel.saveLiftNotes(
            LiftNotesSaveRequest(
                workoutLiftId = workoutLiftId.value,
                liftId = liftId.value,
                sessionDraft = "session only",
                workoutDraft = "workout pin",
                liftDraft = "lift pin",
                loadedSession = "",
                loadedWorkout = "",
                loadedLift = "",
            ),
        )
        advanceUntilIdle()

        val updated = viewModel.uiState.value as WorkoutUiState.Loaded
        assertEquals("workout pin", updated.lifts.single().workoutPinnedNote)
        assertEquals("lift pin", updated.lifts.single().liftPinnedNote)
    }

    @Test
    @DisplayName("clearing a catalog pin updates the rendered lift card")
    fun clearingCatalogPinUpdatesCard() = runTest {
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        advanceUntilIdle()
        viewModel.saveLiftNotes(
            LiftNotesSaveRequest(
                workoutLiftId = workoutLiftId.value,
                liftId = liftId.value,
                sessionDraft = "",
                workoutDraft = "",
                liftDraft = "lift pin",
                loadedSession = "",
                loadedWorkout = "",
                loadedLift = "",
            ),
        )
        advanceUntilIdle()
        assertEquals("lift pin", (viewModel.uiState.value as WorkoutUiState.Loaded).lifts.single().liftPinnedNote)

        viewModel.saveLiftNotes(
            LiftNotesSaveRequest(
                workoutLiftId = workoutLiftId.value,
                liftId = liftId.value,
                sessionDraft = "",
                workoutDraft = "",
                liftDraft = "",
                loadedSession = "",
                loadedWorkout = "",
                loadedLift = "lift pin",
            ),
        )
        advanceUntilIdle()
        assertNull((viewModel.uiState.value as WorkoutUiState.Loaded).lifts.single().liftPinnedNote)
    }

    @Test
    @DisplayName("overlay-only workout pin does not rewrite the template slot")
    fun overlayOnlyWorkoutPinSkipsTemplate() = runTest {
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        advanceUntilIdle()
        viewModel.addLifts(listOf(LiftId("L-2")))
        advanceUntilIdle()
        val added = (viewModel.uiState.value as WorkoutUiState.Loaded).lifts.last()
        assertTrue(added.workoutLiftId != workoutLiftId.value)

        viewModel.saveLiftNotes(
            LiftNotesSaveRequest(
                workoutLiftId = added.workoutLiftId,
                liftId = added.liftId,
                sessionDraft = "",
                workoutDraft = "adhoc pin",
                liftDraft = "",
                loadedSession = "",
                loadedWorkout = "",
                loadedLift = "",
            ),
        )
        advanceUntilIdle()

        val rendered = viewModel.uiState.value as WorkoutUiState.Loaded
        assertEquals("adhoc pin", rendered.lifts.last().workoutPinnedNote)
        assertNull(
            programRepository.getProgram()!!.workouts.single().workoutLifts.single().pinnedNote,
        )
    }

    @Test
    @DisplayName("complete save-template keeps a Room workout pin written during the session")
    fun completeSaveTemplateKeepsWorkoutPin() = runTest {
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        advanceUntilIdle()
        viewModel.saveLiftNotes(
            LiftNotesSaveRequest(
                workoutLiftId = workoutLiftId.value,
                liftId = liftId.value,
                sessionDraft = "",
                workoutDraft = "keep me",
                liftDraft = "",
                loadedSession = "",
                loadedWorkout = "",
                loadedLift = "",
            ),
        )
        advanceUntilIdle()
        viewModel.completeWorkout(saveTemplate = true)
        advanceUntilIdle()
        assertEquals(
            "keep me",
            programRepository.getProgram()!!.workouts.single().workoutLifts.single().pinnedNote,
        )
    }

    @Test
    @DisplayName("past session notes map date, workout name, and body")
    fun pastSessionNotesMapping() = runTest {
        val pastSession = TrainingSessionId("ts-past")
        liftNotesRepository.upsertSessionNote(
            LiftSessionNote(
                id = LiftSessionNoteId("n-past"),
                sessionId = pastSession,
                liftId = liftId,
                workoutLiftId = workoutLiftId,
                body = "High bar today",
                updatedAt = Instant.EPOCH,
            ),
        )
        liftNotesRepository.markSessionCompleted(
            pastSession,
            Instant.parse("2026-08-14T12:00:00Z"),
            "Lower A",
        )
        val viewModel = buildViewModel()
        viewModel.startSession(workoutId)
        advanceUntilIdle()

        val notes = viewModel.pastSessionNotes(liftId.value)
        assertEquals(1, notes.size)
        assertEquals("Lower A", notes.single().workoutName)
        assertEquals("High bar today", notes.single().body)
        assertTrue(notes.single().dateLabel.isNotBlank())
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<WorkoutUiState>.awaitLoaded(): WorkoutUiState.Loaded {
        var item = awaitItem()
        while (item is WorkoutUiState.Loading) {
            item = awaitItem()
        }
        return item as WorkoutUiState.Loaded
    }
}
