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
import com.stratum.domain.logging.LogErrorUseCase
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.ObserveLiftCatalogUseCase
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.Weekday
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
import com.stratum.domain.training.GetLoggedSetsForSessionUseCase
import com.stratum.domain.training.LogSetUseCase
import com.stratum.domain.training.ObserveCycleProgressUseCase
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.domain.workout.EstimateAddedSetRepsUseCase
import com.stratum.domain.workout.GenerateProgressionSnapshotUseCase
import com.stratum.domain.workout.MergeRenderedWorkoutUseCase
import com.stratum.domain.workout.WorkoutEditSession
import com.stratum.domain.workout.ProjectWorkoutStructureUseCase
import com.stratum.domain.workout.RecomputeRecommendationsUseCase
import com.stratum.domain.workout.RecommendationEngine
import com.stratum.domain.workout.SetSlotId
import com.stratum.domain.workout.SlotHistoryEntry
import com.stratum.domain.workout.SlotHistoryRequest
import com.stratum.domain.workout.progression.DoubleProgressionEngine
import com.stratum.domain.workout.progression.LinearProgressionDefaults
import com.stratum.domain.workout.progression.LinearProgressionEngine
import com.stratum.feature.workout.ui.WorkoutUiState
import java.io.File
import java.time.Instant
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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
        seedHistoryForTopSet()
    }

    private fun lb(amount: Double) = Weight(amount, Weight.WeightUnit.Lb)

    private fun seedHistoryForTopSet() {
        val session =
            TrainingSession(
                id = TrainingSessionId("s-history"),
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
            SlotHistoryRequest(programId, workoutId, workoutLiftId, liftId, 0),
            SlotHistoryEntry(
                session,
                com.stratum.domain.training.LoggedSet(
                    id = com.stratum.domain.importing.identity.LoggedSetId("hist-set-1"),
                    sessionId = session.id,
                    exerciseNameRaw = "Squat",
                    liftId = liftId,
                    setIndex = 0,
                    weight = lb(135.0),
                    reps = 5,
                    rir = 2,
                    completedAt = Instant.parse("2026-06-28T10:30:00Z"),
                    liftContext = com.stratum.domain.training.LoggedLiftContext(
                        liftName = "Squat",
                        movementPatternsBitmask = 1,
                        primaryMusclesBitmask = 1,
                        secondaryMusclesBitmask = 0,
                        kinematicProfile = com.stratum.domain.lift.KinematicProfile.Isolation,
                        muscleLengthBias = com.stratum.domain.lift.MuscleLengthBias.MidRange,
                    ),
                ),
            ),
        )
        progressionHistory.seed(
            SlotHistoryRequest(programId, workoutId, workoutLiftId, liftId, 1),
            SlotHistoryEntry(
                session,
                com.stratum.domain.training.LoggedSet(
                    id = com.stratum.domain.importing.identity.LoggedSetId("hist-set-2"),
                    sessionId = session.id,
                    exerciseNameRaw = "Squat",
                    liftId = liftId,
                    setIndex = 1,
                    weight = lb(135.0),
                    reps = 8,
                    rir = 2,
                    completedAt = Instant.parse("2026-06-28T10:35:00Z"),
                    liftContext = com.stratum.domain.training.LoggedLiftContext(
                        liftName = "Squat",
                        movementPatternsBitmask = 1,
                        primaryMusclesBitmask = 1,
                        secondaryMusclesBitmask = 0,
                        kinematicProfile = com.stratum.domain.lift.KinematicProfile.Isolation,
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
                                repMin = 8,
                                repMax = 12,
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

    private fun buildViewModel(): WorkoutViewModel {
        // Uses the seeded [programRepository] / [progressionHistory] from [setUp].
        val liftRepository = FakeLiftRepository(initialLifts = listOf(catalogLift(), catalogLift2()))
        return WorkoutViewModel(
            observeCycleProgress = ObserveCycleProgressUseCase(programRepository, trainingLogRepository),
            generateSnapshot =
                GenerateProgressionSnapshotUseCase(
                    progressionHistoryPort = progressionHistory,
                    doubleProgressionEngine = DoubleProgressionEngine(),
                    linearProgressionEngine = LinearProgressionEngine(LinearProgressionDefaults.config),
                    programChangeEventRepository = NoOpProgramChangeEventRepository(),
                ),
            mergeRenderedWorkout = MergeRenderedWorkoutUseCase(),
            projectStructure = ProjectWorkoutStructureUseCase(),
            recomputeRecommendationsUseCase = RecomputeRecommendationsUseCase(RecommendationEngine(), FakeFatigueModel()),
            estimateAddedSetReps = EstimateAddedSetRepsUseCase(FakeFatigueModel()),
            logSetUseCase = LogSetUseCase(
                trainingLogRepository = trainingLogRepository,
                liftRepository = liftRepository,
                programRepository = programRepository,
                loggedLiftContextFactory = DefaultLoggedLiftContextFactory(),
            ),
            observeLiftCatalog = ObserveLiftCatalogUseCase(liftRepository),
            getLoggedSetsForSession = GetLoggedSetsForSessionUseCase(trainingLogRepository),
            deleteLoggedSet = DeleteLoggedSetUseCase(trainingLogRepository),
            abandonTrainingSession = com.stratum.domain.training.AbandonTrainingSessionUseCase(trainingLogRepository),
            observeLiftTrainingInsights = com.stratum.domain.importing.ObserveLiftTrainingInsightsUseCase(
                com.stratum.core.testing.importing.FakeLiftTrainingInsightsRepository(),
            ),
            logErrorUseCase = LogErrorUseCase(FakeLoggingPort()),
            workoutEditSession = WorkoutEditSession(),
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
                ),
            mergeRenderedWorkout = MergeRenderedWorkoutUseCase(),
            projectStructure = ProjectWorkoutStructureUseCase(),
            recomputeRecommendationsUseCase = RecomputeRecommendationsUseCase(RecommendationEngine(), FakeFatigueModel()),
            estimateAddedSetReps = EstimateAddedSetRepsUseCase(FakeFatigueModel()),
            logSetUseCase = LogSetUseCase(
                trainingLogRepository = trainingLogRepository,
                liftRepository = liftRepository,
                programRepository = programRepository,
                loggedLiftContextFactory = DefaultLoggedLiftContextFactory(),
            ),
            observeLiftCatalog = ObserveLiftCatalogUseCase(liftRepository),
            getLoggedSetsForSession = GetLoggedSetsForSessionUseCase(trainingLogRepository),
            deleteLoggedSet = DeleteLoggedSetUseCase(trainingLogRepository),
            abandonTrainingSession = com.stratum.domain.training.AbandonTrainingSessionUseCase(trainingLogRepository),
            observeLiftTrainingInsights = com.stratum.domain.importing.ObserveLiftTrainingInsightsUseCase(
                com.stratum.core.testing.importing.FakeLiftTrainingInsightsRepository(),
            ),
            logErrorUseCase = LogErrorUseCase(FakeLoggingPort()),
            workoutEditSession = WorkoutEditSession(),
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
                ),
            mergeRenderedWorkout = MergeRenderedWorkoutUseCase(),
            projectStructure = ProjectWorkoutStructureUseCase(),
            recomputeRecommendationsUseCase = RecomputeRecommendationsUseCase(RecommendationEngine(), FakeFatigueModel()),
            estimateAddedSetReps = EstimateAddedSetRepsUseCase(FakeFatigueModel()),
            logSetUseCase = LogSetUseCase(
                trainingLogRepository = trainingLogRepository,
                liftRepository = liftRepository,
                programRepository = programRepository,
                loggedLiftContextFactory = DefaultLoggedLiftContextFactory(),
            ),
            observeLiftCatalog = ObserveLiftCatalogUseCase(liftRepository),
            getLoggedSetsForSession = GetLoggedSetsForSessionUseCase(trainingLogRepository),
            deleteLoggedSet = DeleteLoggedSetUseCase(trainingLogRepository),
            abandonTrainingSession = com.stratum.domain.training.AbandonTrainingSessionUseCase(trainingLogRepository),
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
            logErrorUseCase = LogErrorUseCase(FakeLoggingPort()),
            workoutEditSession = WorkoutEditSession(),
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
        viewModel.uiState.test {
            val loaded = awaitLoaded()
            val firstSet = loaded.lifts.first().sets.first()
            assertEquals("135", firstSet.recommendedWeightLabel)
            assertEquals("4", firstSet.recommendedRepsLabel)
            assertNull(firstSet.loggedWeightLabel)
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
            assertEquals(112.5, ls2Set!!.recommendedWeightLabel.toDouble(), 0.05)
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
            // The projected reps must be a concrete value within the backoff slot's
            // prescribed 8..12 range (the fatigue model clamps to repMin..repMax).
            val projected = ls2Set.recommendedReps
            assertTrue(projected in 8..12, "projected reps must clamp to the slot's 8..12 range: $projected")
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
    @DisplayName("S7-rir-math-1: 75x4@2 RIR top set (4-12 range) -> next top set Hold 75, reps clamp to 4")
    fun `75x4 at 2 RIR holds weight and clamps reps to repMin`() = runTest {
        // e1RM from 75x6 = 89.53. At 75 lb, repsForWeight = 6 @ 0 RIR → 4 @ 2 RIR.
        // targetReps (top, repMin=4) = 4. projectedReps (4) >= 4 → Hold 75.
        // Fatigue projection: raw set2 = 6 - 2 = 4 @ 0 RIR → 4 - 2 = 2 @ 2 RIR.
        // Clamped to repMin=4 → 4 reps. Weight label "75" (no trailing .0).
        val viewModel = buildViewModelWithProgram(programWithTopSetRange(repMin = 4, repMax = 12, rirGoal = 2))
        viewModel.startSession(workoutId)
        viewModel.uiState.test {
            awaitLoaded()
            viewModel.logSet(slotKey = liftSetId.value, weight = lb(75.0), reps = 4, rir = 2)
            advanceUntilIdle()
            val updated = viewModel.uiState.value as WorkoutUiState.Loaded
            val nextSet = updated.lifts.first().sets.firstOrNull { it.slotKey.contains("ls-2") }
            assertNotNull(nextSet, "an upcoming ls-2 set must be rendered")
            assertEquals("75", nextSet!!.recommendedWeightLabel)
            assertTrue(nextSet.hasLiveRepsRecommendation)
            assertEquals(4, nextSet.recommendedReps)
            assertEquals("4", nextSet.recommendedRepsLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("S7-rir-math-2: 75x8@2 RIR top set (4-12 range) -> next top set Hold 75, 7 reps")
    fun `75x8 at 2 RIR holds weight and projects 7 reps`() = runTest {
        // e1RM from 75x10 = 102.81. At 75 lb, repsForWeight = 11 @ 0 RIR → 9 @ 2 RIR.
        // targetReps (top, repMin=4) = 4. 9 >= 4 → Hold 75.
        // Fatigue projection: raw set2 = 11 - 2 = 9 @ 0 RIR → 9 - 2 = 7 @ 2 RIR.
        // Clamped to 4..12 → 7 reps. Weight label "75".
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
            assertEquals(7, nextSet.recommendedReps)
            assertEquals("7", nextSet.recommendedRepsLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("S7-fatigue-slope: steeper per-lift slope projects fewer reps end-to-end (6 vs 7)")
    fun `steeper per lift slope projects fewer reps end to end`() = runTest {
        // Same scenario as `75x8 at 2 RIR holds weight and projects 7 reps` (population slope
        // -2.0 → 7 reps). With a steeper per-lift slope (-3.0) wired via the insights cache,
        // the VM should thread it into RecomputeRecommendationsUseCase and project 6 reps:
        // raw set2 = 11 - 3 = 8 @ 0 RIR → 8 - 2 = 6 @ 2 RIR.
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
            assertEquals(6, nextSet.recommendedReps, "Steeper slope (-3.0) should project 6 reps, not the population 7")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("S7-weight-label-formatting: fractional weights round to 1 decimal, drop .0")
    fun `weight label formatting strips trailing zero and rounds to one decimal`() = runTest {
        // 100x3@1 RIR top set (5-8 range, rirGoal=1).
        // e1RM from 100x4 = 112.13. At 100 lb, repsForWeight = 4 @ 0 RIR → 3 @ 1 RIR.
        // targetReps (top, repMin=5) = 5. 3 < 5 → Reduce.
        // weightForReps(112.13, 5+1=6) = 93.93 → label "93.9".
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
            assertEquals(93.9, label.toDouble(), 0.1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("S7-increment-tier-rounding: reduce weight rounds to lift's increment tier")
    fun `reduce weight rounds to increment tier`() = runTest {
        // Same scenario as S7-weight-label-formatting but with default population tiers.
        // Raw reduce = 93.93. In the 40-100 band (5 lb), 93.93/5 = 18.79 → 19 → 95.
        // The label should be "95" (no fractional — tier-aligned).
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
            assertEquals("95", nextSet!!.recommendedWeightLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("S7-too-hard-reduce: missed floor by 2 reduces weight for last set")
    fun `too hard reduces weight targeting last remaining set`() = runTest {
        // 75x2@2 on a 4-12 top set. 2 sets total; after logging set 1, 1 remains.
        // achieved=2, floor=4, 4-2=2 → too hard. e1RM from 75x4 = 84.10.
        // remainingCount=1 → fatigueForLastSet=0.
        // weightForReps(84.10, 4+2+0=6) = 70.5. No tiers → label "70.5".
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
            assertEquals(70.5, nextSet.recommendedWeightLabel.toDouble(), 0.1)
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
                ),
            mergeRenderedWorkout = MergeRenderedWorkoutUseCase(),
            projectStructure = ProjectWorkoutStructureUseCase(),
            recomputeRecommendationsUseCase = RecomputeRecommendationsUseCase(RecommendationEngine(), FakeFatigueModel()),
            estimateAddedSetReps = EstimateAddedSetRepsUseCase(FakeFatigueModel()),
            logSetUseCase = LogSetUseCase(
                trainingLogRepository = trainingLogRepository,
                liftRepository = liftRepository,
                programRepository = programRepository,
                loggedLiftContextFactory = DefaultLoggedLiftContextFactory(),
            ),
            observeLiftCatalog = ObserveLiftCatalogUseCase(liftRepository),
            getLoggedSetsForSession = GetLoggedSetsForSessionUseCase(trainingLogRepository),
            deleteLoggedSet = DeleteLoggedSetUseCase(trainingLogRepository),
            abandonTrainingSession = com.stratum.domain.training.AbandonTrainingSessionUseCase(trainingLogRepository),
            observeLiftTrainingInsights = com.stratum.domain.importing.ObserveLiftTrainingInsightsUseCase(
                com.stratum.core.testing.importing.FakeLiftTrainingInsightsRepository(),
            ),
            logErrorUseCase = LogErrorUseCase(FakeLoggingPort()),
            workoutEditSession = WorkoutEditSession(),
        )

        localViewModel.startSession(workoutId)
        localViewModel.uiState.test {
            val loaded = awaitLoaded()
            val sets = loaded.lifts.first().sets

            // The first set repMin=4, repMax=6. With no history, placeholder is "4-6"
            assertEquals("4-6", sets[0].recommendedRepsLabel)

            // The second set repMin=8, repMax=12. With no history, placeholder is "8-12"
            assertEquals("8-12", sets[1].recommendedRepsLabel)

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
                ),
            mergeRenderedWorkout = MergeRenderedWorkoutUseCase(),
            projectStructure = ProjectWorkoutStructureUseCase(),
            recomputeRecommendationsUseCase = RecomputeRecommendationsUseCase(RecommendationEngine(), FakeFatigueModel()),
            estimateAddedSetReps = EstimateAddedSetRepsUseCase(FakeFatigueModel()),
            logSetUseCase = LogSetUseCase(
                trainingLogRepository = trainingLogRepository,
                liftRepository = liftRepository,
                programRepository = programRepository,
                loggedLiftContextFactory = DefaultLoggedLiftContextFactory(),
            ),
            observeLiftCatalog = ObserveLiftCatalogUseCase(liftRepository),
            getLoggedSetsForSession = GetLoggedSetsForSessionUseCase(trainingLogRepository),
            deleteLoggedSet = DeleteLoggedSetUseCase(trainingLogRepository),
            abandonTrainingSession = com.stratum.domain.training.AbandonTrainingSessionUseCase(trainingLogRepository),
            observeLiftTrainingInsights = com.stratum.domain.importing.ObserveLiftTrainingInsightsUseCase(
                com.stratum.core.testing.importing.FakeLiftTrainingInsightsRepository(),
            ),
            logErrorUseCase = LogErrorUseCase(FakeLoggingPort()),
            workoutEditSession = WorkoutEditSession(),
        )

        localViewModel.startSession(workoutId)
        localViewModel.uiState.test {
            val loaded = awaitLoaded()
            val initialSets = loaded.lifts.first().sets

            // Log the first set with target repMax=6 at 2 RIR.
            // Case A (blank fallback): Log it with 135 lb x 5 reps at 2 RIR.
            // The second set (initially blank) should fall back to get the completed set's weight (135.0).
            localViewModel.logSet(
                slotKey = initialSets[0].slotKey,
                weight = lb(135.0),
                reps = 5,
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

            // Abandon the session
            viewModel.abandonSession()

            // Should transition to NoActiveSession and reset states
            val noSession = awaitItem()
            assertTrue(noSession is WorkoutUiState.NoActiveSession)

            cancelAndIgnoreRemainingEvents()
        }
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
            val updated = awaitItem() as WorkoutUiState.Loaded
            assertEquals("Bench Press", updated.lifts.first().liftName)
            assertEquals("L-2", updated.lifts.first().liftId)

            cancelAndIgnoreRemainingEvents()
        }
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

    private suspend fun app.cash.turbine.ReceiveTurbine<WorkoutUiState>.awaitLoaded(): WorkoutUiState.Loaded {
        var item = awaitItem()
        while (item is WorkoutUiState.Loading) {
            item = awaitItem()
        }
        return item as WorkoutUiState.Loaded
    }
}
