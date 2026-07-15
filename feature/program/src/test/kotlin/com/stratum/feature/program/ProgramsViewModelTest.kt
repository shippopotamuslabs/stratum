package com.stratum.feature.program

import com.stratum.core.testing.logging.FakeLoggingPort
import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.program.FakeProgramsUiPreferencesRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.ObserveLiftCatalogUseCase
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.ComputeProgramMuscleVolumeUseCase
import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.ObserveProgramsUiPreferencesUseCase
import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgramsUiPreferences
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetExpandedWorkoutIdUseCase
import com.stratum.domain.program.SetType
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.ObserveCycleProgressUseCase
import com.stratum.domain.training.StartTrainingSessionUseCase
import com.stratum.feature.program.ui.ProgramsUiEvent
import com.stratum.feature.program.ui.ProgramsUiState
import com.stratum.feature.program.ui.ScheduleItemState
import java.time.Instant
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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

    @Nested
    @DisplayName("PROG-GEN-400 empty repo maps to Empty state")
    inner class EmptyState {
        @Test
        fun `an empty repository emits Empty with a null program`() = runTest {
            val viewModel = buildViewModel(initial = null)

            viewModel.programsUiState.test {
                assertEquals(ProgramsUiState.Empty, awaitItem())
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
    }

    @Nested
    @DisplayName("Active workout bar state")
    inner class ActiveWorkoutBar {
        @Test
        fun `when active session exists then activeWorkoutBarState emits MinimizedWorkoutState`() = runTest {
            val viewModel = buildViewModel(initial = PROGRAM_PPL)

            viewModel.activeWorkoutBarState.test {
                assertEquals(null, awaitItem())

                viewModel.startProgramWorkout("w-1")

                val state = awaitItem()
                assertTrue(state != null)
                assertEquals("w-1", state?.workoutId)
                assertEquals("Push Day", state?.workoutName)
                assertTrue(state?.startedAtEpochMilli != null)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    @DisplayName("Expanded workout card preference")
    inner class ExpandedWorkout {
        @Test
        fun `seeded expanded workout id is exposed on expandedWorkoutId`() = runTest {
            val viewModel =
                buildViewModel(
                    initial = PROGRAM_PPL,
                    uiPrefs = FakeProgramsUiPreferencesRepository(
                        ProgramsUiPreferences(expandedWorkoutId = "w-1"),
                    ),
                )

            viewModel.expandedWorkoutId.test {
                // stateIn starts at null until the preference flow delivers.
                var id = awaitItem()
                if (id == null) {
                    id = awaitItem()
                }
                assertEquals("w-1", id)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `setExpandedWorkout persists id and re-emits`() = runTest {
            val uiPrefs = FakeProgramsUiPreferencesRepository()
            val viewModel = buildViewModel(initial = PROGRAM_PPL, uiPrefs = uiPrefs)

            viewModel.expandedWorkoutId.test {
                assertNull(awaitItem())

                viewModel.setExpandedWorkout("w-1")
                advanceUntilIdle()

                assertEquals("w-1", awaitItem())
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

            viewModel.expandedWorkoutId.test {
                var id = awaitItem()
                if (id == null) {
                    id = awaitItem()
                }
                assertEquals("w-1", id)

                viewModel.setExpandedWorkout(null)
                advanceUntilIdle()

                assertNull(awaitItem())
                assertNull(uiPrefs.current.expandedWorkoutId)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    private fun buildViewModel(
        initial: Program?,
        uiPrefs: FakeProgramsUiPreferencesRepository = FakeProgramsUiPreferencesRepository(),
    ): ProgramsViewModel {
        repository = FakeProgramRepository(initial)
        val trainingLog = FakeTrainingLogRepository(programRepository = repository)
        val liftRepository =
            FakeLiftRepository(
                initialLifts =
                    listOf(
                        catalogLift(LiftId("bench-press"), "Barbell Bench Press"),
                    ),
            )
        val logging = FakeLoggingPort()
        return ProgramsViewModel(
            observeProgram = com.stratum.domain.program.ObserveProgramUseCase(repository),
            observeLiftCatalog = ObserveLiftCatalogUseCase(liftRepository),
            computeProgramMuscleVolume = ComputeProgramMuscleVolumeUseCase(),
            startTrainingSession = StartTrainingSessionUseCase(trainingLog, repository, logging),
            observeCycleProgress = ObserveCycleProgressUseCase(repository, trainingLog),
            abandonTrainingSession = com.stratum.domain.training.AbandonTrainingSessionUseCase(trainingLog, logging),
            getLoggedSetsForSession = com.stratum.domain.training.GetLoggedSetsForSessionUseCase(trainingLog),
            recordWorkoutSkip = com.stratum.domain.training.RecordWorkoutSkipUseCase(trainingLog, repository),
            recordRestDaySkip = com.stratum.domain.training.RecordRestDaySkipUseCase(trainingLog, repository),
            unskipWorkout = com.stratum.domain.training.UnskipWorkoutUseCase(trainingLog, repository),
            observeProgramsUiPreferences = ObserveProgramsUiPreferencesUseCase(uiPrefs),
            setExpandedWorkoutId = SetExpandedWorkoutIdUseCase(uiPrefs, logging),
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
