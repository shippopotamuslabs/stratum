package com.stratum.feature.program

import com.stratum.core.testing.logging.FakeLoggingPort
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.program.FakeProgramsUiPreferencesRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.workout.FakeWorkoutOverlayStore
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.application.lift.ObserveLiftCatalogUseCase
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.Weekday
import com.stratum.application.program.ComputeProgramMuscleVolumeUseCase
import com.stratum.domain.program.LiftSet
import com.stratum.application.program.ObserveProgramsUiPreferencesUseCase
import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgramsUiPreferences
import com.stratum.domain.program.ProgressionScheme
import com.stratum.application.program.SetExpandedWorkoutIdUseCase
import com.stratum.domain.program.SetType
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.application.training.ObserveCycleProgressUseCase
import com.stratum.application.training.ObserveMinimizedWorkoutBarUseCase
import com.stratum.application.training.StartTrainingSessionUseCase
import com.stratum.application.workout.ManageWorkoutOverlayUseCase
import com.stratum.feature.program.ui.ProgramsUiEvent
import com.stratum.feature.program.ui.ProgramsUiState
import com.stratum.feature.program.ui.ScheduleItemState
import java.time.Instant
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * [ProgramsViewModel] / [toProgramsUiState] mapping tests. Scenarios PROG-GEN-400 (empty → Empty),
 * 401 (populated → Loaded mapped presentation), 402 (lift summary lines).
 */
@ExtendWith(MainDispatcherRule::class)
class ProgramsViewModelTest {
    private lateinit var repository: FakeProgramRepository
    private lateinit var lastTrainingLog: FakeTrainingLogRepository
    private lateinit var lastLogging: FakeLoggingPort

    @Nested
    @DisplayName("PROG-GEN-400 empty repo maps to Empty state")
    inner class EmptyState {
        @Test
        fun `an empty repository emits Empty with a null program`() = runTest {
            val viewModel = buildViewModel(initial = null)

            viewModel.programsUiState.test {
                assertEquals(ProgramsUiState.Empty(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    @DisplayName("PROG-GEN-401 populated repo maps to a Loaded presentation")
    inner class LoadedState {
        @Test
        fun `a populated repository emits Loaded with schedule and volume`() = runTest {
            val viewModel = buildViewModel(initial = PROGRAM_PPL)

            viewModel.programsUiState.test {
                val state = awaitItem()
                assertTrue(state is ProgramsUiState.Loaded)
                val loaded = state as ProgramsUiState.Loaded
                assertEquals("p-1", loaded.program.programId)
                assertEquals("Push/Pull/Legs", loaded.program.name)
                assertTrue(loaded.scheduleItems.any { item -> item is ScheduleItemState.Workout })
                assertTrue(
                    loaded.upperVolumeByMuscle.isNotEmpty() ||
                        loaded.lowerVolumeByMuscle.isNotEmpty(),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `the presentation does not expose any domain type`() = runTest {
            val viewModel = buildViewModel(initial = PROGRAM_PPL)

            viewModel.programsUiState.test {
                val state = awaitItem() as ProgramsUiState.Loaded
                val uiPackage = ProgramsUiState::class.java.`package`.name
                val workoutItem =
                    state.scheduleItems.filterIsInstance<ScheduleItemState.Workout>().first()
                val viewStateTypes =
                    listOf(
                        state.program::class,
                        workoutItem.workout::class,
                        workoutItem.workout.lifts.first()::class,
                    )
                assertTrue(viewStateTypes.all { it.java.`package`.name == uiPackage })
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    @DisplayName("PROG-GEN-402 WorkoutLift view state carries name set count reps and RPE")
    inner class LiftSummary {
        @Test
        fun `the populated WorkoutLift view state carries name set count reps and RPE`() =
            runTest {
                val viewModel = buildViewModel(initial = PROGRAM_PPL)

                viewModel.programsUiState.test {
                    val lift =
                        (awaitItem() as ProgramsUiState.Loaded)
                            .scheduleItems
                            .filterIsInstance<ScheduleItemState.Workout>()
                            .first()
                            .workout
                            .lifts
                            .first()
                    assertEquals("Barbell Bench Press", lift.name)
                    assertEquals(2, lift.setCount)
                    assertEquals("8\u201312", lift.repRange)
                    assertEquals("@8", lift.topSetRpe)
                    cancelAndIgnoreRemainingEvents()
                }
            }
    }

    @Nested
    @DisplayName("Start workout navigation")
    inner class StartWorkout {
        @Test
        fun `startProgramWorkout emits NavigateToWorkout after session is created`() = runTest {
            val viewModel = buildViewModel(initial = PROGRAM_PPL)
            viewModel.programsUiState.test {
                assertTrue(awaitItem() is ProgramsUiState.Loaded)
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.events.test {
                viewModel.startProgramWorkout("w-1")
                assertEquals(ProgramsUiEvent.NavigateToWorkout("w-1"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `startNextWorkout emits NavigateToWorkout for the first incomplete workout`() = runTest {
            val viewModel = buildViewModel(initial = PROGRAM_PPL)
            viewModel.programsUiState.test {
                assertTrue(awaitItem() is ProgramsUiState.Loaded)
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.events.test {
                viewModel.startNextWorkout()
                assertEquals(ProgramsUiEvent.NavigateToWorkout("w-1"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `startEmptyWorkout emits NavigateToWorkout with session route id`() = runTest {
            val viewModel = buildViewModel(initial = PROGRAM_PPL)
            viewModel.programsUiState.test {
                assertTrue(awaitItem() is ProgramsUiState.Loaded)
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.events.test {
                viewModel.startEmptyWorkout()
                val event = awaitItem() as ProgramsUiEvent.NavigateToWorkout
                // Ad-hoc has no template workoutId — route key is the live session id (non-empty).
                assertTrue(event.workoutId.isNotBlank())
                assertTrue(event.workoutId != "w-1")
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `when empty session is active then startProgramWorkout navigates to ad-hoc route`() = runTest {
            val viewModel = buildViewModel(initial = PROGRAM_PPL)
            viewModel.programsUiState.test {
                assertTrue(awaitItem() is ProgramsUiState.Loaded)
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.events.test {
                viewModel.startEmptyWorkout()
                val emptyRoute = (awaitItem() as ProgramsUiEvent.NavigateToWorkout).workoutId

                // Conflict: should resume the live ad-hoc session, not snackbar-deadlock.
                viewModel.startProgramWorkout("w-1")
                assertEquals(ProgramsUiEvent.NavigateToWorkout(emptyRoute), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    @DisplayName("Start and abandon failure drains (STR-237 review round 1)")
    inner class StartAbandonFailureDrains {
        @Test
        fun `startProgramWorkout persistence failure drains exactly one start record`() = runTest {
            val viewModel = buildViewModel(initial = PROGRAM_PPL)
            val boom = IllegalStateException("insert boom")
            lastTrainingLog.insertSessionError = boom

            viewModel.startProgramWorkout("w-1")
            advanceUntilIdle()

            val error = lastLogging.errors.single()
            assertEquals("StartTrainingSession", error.tag)
            assertEquals("Failed to start training session: insert boom", error.message)
            assertSame(boom, error.throwable)
            assertEquals("Couldn't start that workout. Try again.", viewModel.userMessage.value)
        }

        @Test
        fun `startEmptyWorkout persistence failure drains exactly one start record`() = runTest {
            val viewModel = buildViewModel(initial = PROGRAM_PPL)
            val boom = IllegalStateException("insert boom")
            lastTrainingLog.insertSessionError = boom

            viewModel.startEmptyWorkout()
            advanceUntilIdle()

            val error = lastLogging.errors.single()
            assertEquals("StartTrainingSession", error.tag)
            assertEquals("Failed to start training session: insert boom", error.message)
            assertSame(boom, error.throwable)
            assertEquals("Couldn't start that workout. Try again.", viewModel.userMessage.value)
        }

        @Test
        fun `abandonActiveSession persistence failure drains exactly one abandon record`() = runTest {
            val viewModel = buildViewModel(initial = PROGRAM_PPL)
            viewModel.startProgramWorkout("w-1")
            advanceUntilIdle()
            assertTrue(lastLogging.errors.isEmpty())
            val boom = IllegalStateException("delete boom")
            lastTrainingLog.deleteSessionError = boom

            viewModel.abandonActiveSession()
            advanceUntilIdle()

            val error = lastLogging.errors.single()
            assertEquals("AbandonTrainingSession", error.tag)
            assertEquals("Failed to abandon training session: delete boom", error.message)
            assertSame(boom, error.throwable)
            assertEquals("Couldn't cancel the workout. Try again.", viewModel.userMessage.value)
            assertNotNull(lastTrainingLog.getActiveSession())
        }
    }

    @Nested
    @DisplayName("Active workout bar state")
    inner class ActiveWorkoutBar {
        @Test
        fun `when active session exists then programsUiState carries the minimized bar`() = runTest {
            val viewModel = buildViewModel(initial = PROGRAM_PPL)

            viewModel.programsUiState.test {
                val initial = awaitItem()
                assertTrue(initial is ProgramsUiState.Loaded)
                assertNull(initial.activeBar)

                viewModel.startProgramWorkout("w-1")

                val withBar = awaitLoadedWithBar()
                assertEquals("w-1", withBar.activeBar?.workoutId)
                assertEquals("Push Day", withBar.activeBar?.workoutName)
                assertTrue(withBar.activeBar?.startedAtEpochMilli != null)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `when empty session is active then bar uses session id and Empty Workout name`() = runTest {
            val viewModel = buildViewModel(initial = PROGRAM_PPL)

            viewModel.programsUiState.test {
                val initial = awaitItem()
                assertTrue(initial is ProgramsUiState.Loaded)
                assertNull(initial.activeBar)

                viewModel.startEmptyWorkout()

                val withBar = awaitLoadedWithBar()
                assertTrue(withBar.activeBar!!.workoutId.isNotBlank())
                assertEquals("Empty Workout", withBar.activeBar?.workoutName)
                assertEquals("Add lifts", withBar.activeBar?.nextLiftName)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `orphan active session is abandoned and bar stays empty`() = runTest {
            repository = FakeProgramRepository(PROGRAM_PPL)
            val trainingLog = FakeTrainingLogRepository(programRepository = repository)
            trainingLog.seedSession(
                com.stratum.domain.training.TrainingSession(
                    id = com.stratum.domain.training.identity.TrainingSessionId("ts-orphan"),
                    programId = ProgramId("p-1"),
                    workoutId = WorkoutId("deleted-w"),
                    status = com.stratum.domain.training.TrainingSessionStatus.IN_PROGRESS,
                    kind = com.stratum.domain.training.TrainingSessionKind.PROGRAM_WORKOUT,
                    origin = com.stratum.domain.training.TrainingSessionOrigin.LIVE,
                    cycleNumberSnapshot = 1,
                    weekdaySnapshot = Weekday.Monday,
                    startedAt = Instant.parse("2026-06-26T12:00:00Z"),
                    endedAt = null,
                    sourceApp = null,
                    programNameSnapshot = "Push/Pull/Legs",
                    workoutNameSnapshot = "Deleted Day",
                ),
            )
            val liftRepository =
                FakeLiftRepository(
                    initialLifts =
                        listOf(
                            catalogLift(LiftId("bench-press"), "Barbell Bench Press"),
                        ),
                )
            val logging = FakeLoggingPort()
            val viewModel =
                ProgramsViewModel(
                    observeProgram = com.stratum.application.program.ObserveProgramUseCase(repository),
                    observeLiftCatalog = ObserveLiftCatalogUseCase(liftRepository),
                    computeProgramMuscleVolume = ComputeProgramMuscleVolumeUseCase(),
                    startTrainingSession = StartTrainingSessionUseCase(trainingLog, repository),
                    observeCycleProgress = ObserveCycleProgressUseCase(repository, trainingLog),
                    abandonTrainingSession =
                        com.stratum.application.training.AbandonTrainingSessionUseCase(trainingLog),
                    observeMinimizedBar =
                        ObserveMinimizedWorkoutBarUseCase(
                            ObserveCycleProgressUseCase(repository, trainingLog),
                            ObserveLiftCatalogUseCase(liftRepository),
                            ManageWorkoutOverlayUseCase(FakeWorkoutOverlayStore()),
                            com.stratum.application.training.GetLoggedSetsForSessionUseCase(trainingLog),
                        ),
                    recordWorkoutSkip =
                        com.stratum.application.training.RecordWorkoutSkipUseCase(trainingLog, repository),
                    recordRestDaySkip =
                        com.stratum.application.training.RecordRestDaySkipUseCase(trainingLog, repository),
                    unskipWorkout = com.stratum.application.training.UnskipWorkoutUseCase(trainingLog, repository),
                    manageWorkoutOverlay =
                        ManageWorkoutOverlayUseCase(FakeWorkoutOverlayStore()),
                    observeProgramsUiPreferences =
                        ObserveProgramsUiPreferencesUseCase(FakeProgramsUiPreferencesRepository()),
                    setExpandedWorkoutId =
                        SetExpandedWorkoutIdUseCase(FakeProgramsUiPreferencesRepository()),
                    diagnosticDrain = DiagnosticDrain(logging),
                )

            // Collect so WhileSubscribed starts; orphan cleanup runs from init.
            viewModel.programsUiState.test {
                val first = awaitItem()
                assertNull(first.activeBar)
                advanceUntilIdle()
                cancelAndIgnoreRemainingEvents()
            }
            assertNull(trainingLog.getActiveSession())
            // Keep VM referenced so init collector is not GC'd mid-test.
            assertNull((viewModel.programsUiState.value as? ProgramsUiState.Loaded)?.activeBar)
        }

        @Test
        fun `orphan abandon failure surfaces snackbar and keeps session`() = runTest {
            repository = FakeProgramRepository(PROGRAM_PPL)
            val trainingLog = FakeTrainingLogRepository(programRepository = repository)
            trainingLog.deleteSessionError = IllegalStateException("delete boom")
            trainingLog.seedSession(
                com.stratum.domain.training.TrainingSession(
                    id = com.stratum.domain.training.identity.TrainingSessionId("ts-orphan"),
                    programId = ProgramId("p-1"),
                    workoutId = WorkoutId("deleted-w"),
                    status = com.stratum.domain.training.TrainingSessionStatus.IN_PROGRESS,
                    kind = com.stratum.domain.training.TrainingSessionKind.PROGRAM_WORKOUT,
                    origin = com.stratum.domain.training.TrainingSessionOrigin.LIVE,
                    cycleNumberSnapshot = 1,
                    weekdaySnapshot = Weekday.Monday,
                    startedAt = Instant.parse("2026-06-26T12:00:00Z"),
                    endedAt = null,
                    sourceApp = null,
                    programNameSnapshot = "Push/Pull/Legs",
                    workoutNameSnapshot = "Deleted Day",
                ),
            )
            val liftRepository =
                FakeLiftRepository(
                    initialLifts =
                        listOf(
                            catalogLift(LiftId("bench-press"), "Barbell Bench Press"),
                        ),
                )
            val logging = FakeLoggingPort()
            val viewModel =
                ProgramsViewModel(
                    observeProgram = com.stratum.application.program.ObserveProgramUseCase(repository),
                    observeLiftCatalog = ObserveLiftCatalogUseCase(liftRepository),
                    computeProgramMuscleVolume = ComputeProgramMuscleVolumeUseCase(),
                    startTrainingSession = StartTrainingSessionUseCase(trainingLog, repository),
                    observeCycleProgress = ObserveCycleProgressUseCase(repository, trainingLog),
                    abandonTrainingSession =
                        com.stratum.application.training.AbandonTrainingSessionUseCase(trainingLog),
                    observeMinimizedBar =
                        ObserveMinimizedWorkoutBarUseCase(
                            ObserveCycleProgressUseCase(repository, trainingLog),
                            ObserveLiftCatalogUseCase(liftRepository),
                            ManageWorkoutOverlayUseCase(FakeWorkoutOverlayStore()),
                            com.stratum.application.training.GetLoggedSetsForSessionUseCase(trainingLog),
                        ),
                    recordWorkoutSkip =
                        com.stratum.application.training.RecordWorkoutSkipUseCase(trainingLog, repository),
                    recordRestDaySkip =
                        com.stratum.application.training.RecordRestDaySkipUseCase(trainingLog, repository),
                    unskipWorkout = com.stratum.application.training.UnskipWorkoutUseCase(trainingLog, repository),
                    manageWorkoutOverlay =
                        ManageWorkoutOverlayUseCase(FakeWorkoutOverlayStore()),
                    observeProgramsUiPreferences =
                        ObserveProgramsUiPreferencesUseCase(FakeProgramsUiPreferencesRepository()),
                    setExpandedWorkoutId =
                        SetExpandedWorkoutIdUseCase(FakeProgramsUiPreferencesRepository()),
                    diagnosticDrain = DiagnosticDrain(logging),
                )

            viewModel.programsUiState.test {
                val first = awaitItem()
                assertNull(first.activeBar)
                advanceUntilIdle()
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals("Couldn't cancel the workout. Try again.", viewModel.userMessage.value)
            assertNotNull(trainingLog.getActiveSession())
        }
    }

    @Nested
    @DisplayName("Expanded workout card preference")
    inner class ExpandedWorkout {
        @Test
        fun `seeded expanded workout id is exposed on Loaded`() = runTest {
            val viewModel =
                buildViewModel(
                    initial = PROGRAM_PPL,
                    uiPrefs = FakeProgramsUiPreferencesRepository(
                        ProgramsUiPreferences(expandedWorkoutId = "w-1"),
                    ),
                )

            viewModel.programsUiState.test {
                val state = awaitItem() as ProgramsUiState.Loaded
                assertEquals("w-1", state.expandedWorkoutId)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `setExpandedWorkout persists id and re-emits`() = runTest {
            val uiPrefs = FakeProgramsUiPreferencesRepository()
            val viewModel = buildViewModel(initial = PROGRAM_PPL, uiPrefs = uiPrefs)

            viewModel.programsUiState.test {
                val initial = awaitItem() as ProgramsUiState.Loaded
                assertNull(initial.expandedWorkoutId)

                viewModel.setExpandedWorkout("w-1")
                advanceUntilIdle()

                val updated = awaitItem() as ProgramsUiState.Loaded
                assertEquals("w-1", updated.expandedWorkoutId)
                assertEquals("w-1", uiPrefs.current.expandedWorkoutId)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `setExpandedWorkout null clears the expanded card`() = runTest {
            val uiPrefs =
                FakeProgramsUiPreferencesRepository(
                    ProgramsUiPreferences(expandedWorkoutId = "w-1"),
                )
            val viewModel = buildViewModel(initial = PROGRAM_PPL, uiPrefs = uiPrefs)

            viewModel.programsUiState.test {
                val initial = awaitItem() as ProgramsUiState.Loaded
                assertEquals("w-1", initial.expandedWorkoutId)

                viewModel.setExpandedWorkout(null)
                advanceUntilIdle()

                val cleared = awaitItem() as ProgramsUiState.Loaded
                assertNull(cleared.expandedWorkoutId)
                assertNull(uiPrefs.current.expandedWorkoutId)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    /**
     * Reads [ProgramsUiState] emissions until the minimized bar is present. Starting a
     * session re-emits the combined pipeline once with the stale (null) bar before the
     * bar recomputes, so a single [awaitItem] is not enough.
     */
    private suspend fun ReceiveTurbine<ProgramsUiState>.awaitLoadedWithBar(): ProgramsUiState.Loaded {
        var state = awaitItem()
        while ((state as? ProgramsUiState.Loaded)?.activeBar == null) {
            state = awaitItem()
        }
        return state as ProgramsUiState.Loaded
    }

    private fun buildViewModel(
        initial: Program?,
        uiPrefs: FakeProgramsUiPreferencesRepository = FakeProgramsUiPreferencesRepository(),
    ): ProgramsViewModel {
        repository = FakeProgramRepository(initial)
        val trainingLog = FakeTrainingLogRepository(programRepository = repository)
        lastTrainingLog = trainingLog
        val liftRepository =
            FakeLiftRepository(
                initialLifts =
                    listOf(
                        catalogLift(LiftId("bench-press"), "Barbell Bench Press"),
                    ),
            )
        val logging = FakeLoggingPort()
        lastLogging = logging
        val overlayStore = FakeWorkoutOverlayStore()
        return ProgramsViewModel(
            observeProgram = com.stratum.application.program.ObserveProgramUseCase(repository),
            observeLiftCatalog = ObserveLiftCatalogUseCase(liftRepository),
            computeProgramMuscleVolume = ComputeProgramMuscleVolumeUseCase(),
            startTrainingSession = StartTrainingSessionUseCase(trainingLog, repository),
            observeCycleProgress = ObserveCycleProgressUseCase(repository, trainingLog),
            abandonTrainingSession = com.stratum.application.training.AbandonTrainingSessionUseCase(trainingLog),
            observeMinimizedBar =
                ObserveMinimizedWorkoutBarUseCase(
                    ObserveCycleProgressUseCase(repository, trainingLog),
                    ObserveLiftCatalogUseCase(liftRepository),
                    ManageWorkoutOverlayUseCase(overlayStore),
                    com.stratum.application.training.GetLoggedSetsForSessionUseCase(trainingLog),
                ),
            recordWorkoutSkip = com.stratum.application.training.RecordWorkoutSkipUseCase(trainingLog, repository),
            recordRestDaySkip = com.stratum.application.training.RecordRestDaySkipUseCase(trainingLog, repository),
            unskipWorkout = com.stratum.application.training.UnskipWorkoutUseCase(trainingLog, repository),
            manageWorkoutOverlay = ManageWorkoutOverlayUseCase(overlayStore),
            observeProgramsUiPreferences = ObserveProgramsUiPreferencesUseCase(uiPrefs),
            setExpandedWorkoutId = SetExpandedWorkoutIdUseCase(uiPrefs),
            diagnosticDrain = DiagnosticDrain(logging),
        )
    }

    private fun catalogLift(id: LiftId, name: String): Lift =
        Lift(
            id = id,
            name = name,
            movementPatterns = setOf(MovementPattern.HorizontalPress),
            primaryMuscles = setOf(MuscleGroup.Chest),
            secondaryMuscles = emptySet(),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = true,
        )

    private companion object {
        val PROGRAM_PPL: Program =
            Program(
                id = ProgramId("p-1"),
                name = "Push/Pull/Legs",
                createdAt = Instant.parse("2026-06-26T12:00:00Z"),
                cycleLength = 1,
                trainingWeekdays = setOf(Weekday.Monday),
                workouts =
                    listOf(
                        Workout(
                            id = WorkoutId("w-1"),
                            programId = ProgramId("p-1"),
                            name = "Push Day",
                            weekday = Weekday.Monday,
                            position = 0,
                            workoutLifts =
                                listOf(
                                    WorkoutLift(
                                        id = WorkoutLiftId("wl-1"),
                                        workoutId = WorkoutId("w-1"),
                                        liftId = LiftId("bench-press"),
                                        progressionScheme = ProgressionScheme.Linear,
                                        position = 0,
                                        sets =
                                            listOf(
                                                LiftSet(
                                                    LiftSetId("ls-1"),
                                                    WorkoutLiftId("wl-1"),
                                                    0,
                                                    SetType.Standard,
                                                    8,
                                                    12,
                                                    2,
                                                    isTopSet = true,
                                                ),
                                                LiftSet(
                                                    LiftSetId("ls-2"),
                                                    WorkoutLiftId("wl-1"),
                                                    1,
                                                    SetType.Standard,
                                                    8,
                                                    12,
                                                    2,
                                                    isTopSet = false,
                                                ),
                                            ),
                                    ),
                                ),
                        ),
                    ),
            )
    }
}
