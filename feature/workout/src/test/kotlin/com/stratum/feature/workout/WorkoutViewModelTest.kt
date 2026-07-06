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
import com.stratum.domain.workout.ProjectWorkoutStructureUseCase
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
        allowWeightChangeBetweenSets = true,
        incrementTiers = emptyList(),
        presetTags = emptySet(),
        enabled = true,
    )

    private fun buildViewModel(): WorkoutViewModel {
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
            recommendationEngine = RecommendationEngine(),
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
            fatigueModel = FakeFatigueModel(),
            logErrorUseCase = LogErrorUseCase(FakeLoggingPort()),
        )
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
        viewModel.uiState.test {
            val loaded = awaitLoaded()
            val firstSet = loaded.lifts.first().sets.first()
            assertEquals("135.0", firstSet.recommendedWeightLabel)
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
            assertEquals("135.0", set.loggedWeightLabel)
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
            assertEquals(120.0, ls2Set!!.recommendedWeightLabel.toDouble(), 0.05)
            assertNull(ls2Set.loggedWeightLabel)
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
            assertEquals("130.0", set.loggedWeightLabel)
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
            recommendationEngine = RecommendationEngine(),
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
            fatigueModel = FakeFatigueModel(),
            logErrorUseCase = LogErrorUseCase(FakeLoggingPort()),
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
            recommendationEngine = RecommendationEngine(),
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
            fatigueModel = FakeFatigueModel(),
            logErrorUseCase = LogErrorUseCase(FakeLoggingPort()),
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
            assertEquals("135.0", setsAfterLog1[1].recommendedWeightLabel)

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
            assertEquals("140.0", setsAfterLog2[1].recommendedWeightLabel)

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
