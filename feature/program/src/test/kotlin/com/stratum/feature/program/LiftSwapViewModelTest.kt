package com.stratum.feature.program

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.NoOpProgramChangeEventRepository
import com.stratum.core.ui.component.LiftSwapSheetState
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.RankSwapCandidatesUseCase
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.logging.LogErrorUseCase
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SwapWorkoutLiftUseCase
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.workout.EditSessionEvent
import com.stratum.domain.workout.WorkoutEditSession
import com.stratum.feature.program.navigation.LiftSwapCommitMode
import com.stratum.feature.program.ui.PLACEHOLDER_LIFT_NAME
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class LiftSwapViewModelTest {
    private lateinit var fakeLoggingPort: FakeLoggingPort
    private lateinit var liftRepository: FakeLiftRepository
    private lateinit var workoutEditSession: WorkoutEditSession
    private lateinit var programRepository: FakeProgramRepository
    private lateinit var viewModel: LiftSwapViewModel

    @BeforeEach
    fun setUp() {
        fakeLoggingPort = FakeLoggingPort()
        liftRepository =
            FakeLiftRepository(
                initialLifts =
                    listOf(
                        catalogLift(LiftId("bench-press"), "Barbell Bench Press"),
                        catalogLift(LiftId("incline-press"), "Incline Press"),
                        catalogLift(LiftId("fly"), "Cable Fly"),
                    ),
            )
        workoutEditSession = WorkoutEditSession()
        programRepository = FakeProgramRepository(sampleProgram())
        viewModel =
            LiftSwapViewModel(
                rankSwapCandidates = RankSwapCandidatesUseCase(liftRepository),
                workoutEditSession = workoutEditSession,
                swapWorkoutLift = SwapWorkoutLiftUseCase(
                    programRepository,
                    NoOpProgramChangeEventRepository(),
                    fakeLoggingPort,
                ),
                logError = LogErrorUseCase(fakeLoggingPort),
            )
    }

    @Test
    @DisplayName("Given add mode when toggling lifts then selection order is preserved")
    fun multiSelectPreservesOrder() = runTest {
        viewModel.initialize(WORKOUT_ID, workoutLiftId = null, liftId = null, liftName = null)

        viewModel.liftSwapState.test {
            val state = awaitNonNullState()
            val incline = state.options.first { it.id == "incline-press" }
            val fly = state.options.first { it.id == "fly" }

            viewModel.toggleLiftSelection(incline)
            val afterFirst = awaitItem()!!
            assertEquals(listOf("incline-press"), afterFirst.selectedOptions.map { it.id })

            viewModel.toggleLiftSelection(fly)
            val afterSecond = awaitItem()!!
            assertEquals(listOf("incline-press", "fly"), afterSecond.selectedOptions.map { it.id })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given swap mode when lift is tapped then it commits immediately without multi-select")
    fun swapModeOneTapCommits() = runTest {
        viewModel.initialize(
            workoutId = WORKOUT_ID,
            workoutLiftId = "wl-1",
            liftId = "bench-press",
            liftName = "Barbell Bench Press",
        )

        val events = mutableListOf<EditSessionEvent>()
        val job =
            launch(UnconfinedTestDispatcher(testScheduler)) {
                workoutEditSession.events.collect { events.add(it) }
            }

        viewModel.liftSwapState.test {
            val state = awaitNonNullState()
            assertFalse(state.isAddMode)
            val incline = state.options.first { it.id == "incline-press" }

            var completed = false
            viewModel.onLiftClicked(incline) { completed = true }
            assertTrue(completed)
            assertFalse(state.hasSelection)

            val event = events.filterIsInstance<EditSessionEvent.LiftSelected>().single()
            assertEquals(WORKOUT_ID, event.workoutId)
            assertEquals("wl-1", event.workoutLiftId)
            assertEquals("incline-press", event.liftId)

            cancelAndIgnoreRemainingEvents()
        }
        job.cancel()
    }

    @Test
    @DisplayName("Given selected lift when toggled again then it is deselected")
    fun reTapDeselects() = runTest {
        viewModel.initialize(WORKOUT_ID, workoutLiftId = null, liftId = null, liftName = null)

        viewModel.liftSwapState.test {
            val state = awaitNonNullState()
            val incline = state.options.first { it.id == "incline-press" }

            viewModel.toggleLiftSelection(incline)
            assertTrue(awaitItem()!!.hasSelection)

            viewModel.toggleLiftSelection(incline)
            assertFalse(awaitItem()!!.hasSelection)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given multi selection with superset when confirmed then LiftsSelected is emitted")
    fun confirmMultiAddWithSuperset() = runTest {
        viewModel.initialize(WORKOUT_ID, workoutLiftId = null, liftId = null, liftName = null)

        val events = mutableListOf<EditSessionEvent>()
        val job =
            launch(UnconfinedTestDispatcher(testScheduler)) {
                workoutEditSession.events.collect { events.add(it) }
            }

        viewModel.liftSwapState.test {
            val state = awaitNonNullState()
            val incline = state.options.first { it.id == "incline-press" }
            val fly = state.options.first { it.id == "fly" }

            viewModel.toggleLiftSelection(incline)
            awaitItem()
            viewModel.toggleLiftSelection(fly)
            awaitItem()
            viewModel.setAddAsSuperset(true)
            awaitItem()

            var completed = false
            viewModel.confirmSelections { completed = true }
            assertTrue(completed)

            val event = events.filterIsInstance<EditSessionEvent.LiftsSelected>().single()
            assertEquals(WORKOUT_ID, event.workoutId)
            assertEquals(listOf("incline-press", "fly"), event.liftIds)
            assertTrue(event.asSuperset)

            cancelAndIgnoreRemainingEvents()
        }
        job.cancel()
    }

    @Test
    @DisplayName("Given empty selection when confirmSelections then no event and no complete")
    fun emptyConfirmIsNoOp() = runTest {
        viewModel.initialize(WORKOUT_ID, workoutLiftId = null, liftId = null, liftName = null)

        val events = mutableListOf<EditSessionEvent>()
        val job =
            launch(UnconfinedTestDispatcher(testScheduler)) {
                workoutEditSession.events.collect { events.add(it) }
            }

        viewModel.liftSwapState.test {
            awaitNonNullState()
            var completed = false
            viewModel.confirmSelections { completed = true }
            assertFalse(completed)
            assertTrue(events.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
        job.cancel()
    }

    @Test
    @DisplayName("Given fewer than two selected when setAddAsSuperset then remains false")
    fun supersetRequiresTwoSelections() = runTest {
        viewModel.initialize(WORKOUT_ID, workoutLiftId = null, liftId = null, liftName = null)

        viewModel.liftSwapState.test {
            val state = awaitNonNullState()
            val incline = state.options.first { it.id == "incline-press" }
            viewModel.toggleLiftSelection(incline)
            awaitItem()

            viewModel.setAddAsSuperset(true)
            // No new emission if value stays false — read current after call via force toggle deselect/select
            // setAddAsSuperset with size < 2 leaves false without state change; assert via confirm path
            val events = mutableListOf<EditSessionEvent>()
            val job =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    workoutEditSession.events.collect { events.add(it) }
                }
            viewModel.confirmSelections { }
            val event = events.filterIsInstance<EditSessionEvent.LiftsSelected>().single()
            assertFalse(event.asSuperset)
            job.cancel()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given add mode placeholder name when initialized then isAddMode is true")
    fun addModeFromPlaceholder() = runTest {
        viewModel.initialize(WORKOUT_ID, null, null, null)
        viewModel.liftSwapState.test {
            val state = awaitNonNullState()
            assertTrue(state.isAddMode)
            assertEquals(PLACEHOLDER_LIFT_NAME, state.targetLiftName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given prior selection when re-initialized with same target then selection is cleared")
    fun reInitializeClearsSelection() = runTest {
        viewModel.initialize(WORKOUT_ID, workoutLiftId = null, liftId = null, liftName = null)

        viewModel.liftSwapState.test {
            val state = awaitNonNullState()
            val incline = state.options.first { it.id == "incline-press" }
            viewModel.toggleLiftSelection(incline)
            assertTrue(awaitItem()!!.hasSelection)

            // Same nav args as a second visit (retained ViewModel) must not keep selection.
            viewModel.initialize(WORKOUT_ID, workoutLiftId = null, liftId = null, liftName = null)
            assertFalse(awaitItem()!!.hasSelection)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given PersistToProgram when swap tapped then program is updated and no edit session event")
    fun persistToProgramSwapsWithoutEditSession() = runTest {
        viewModel.initialize(
            workoutId = WORKOUT_ID,
            workoutLiftId = "wl-1",
            liftId = "bench-press",
            liftName = "Barbell Bench Press",
            commitMode = LiftSwapCommitMode.PersistToProgram,
        )

        val events = mutableListOf<EditSessionEvent>()
        val job =
            launch(UnconfinedTestDispatcher(testScheduler)) {
                workoutEditSession.events.collect { events.add(it) }
            }

        viewModel.liftSwapState.test {
            val state = awaitNonNullState()
            val incline = state.options.first { it.id == "incline-press" }
            var completed = false
            viewModel.onLiftClicked(incline) { completed = true }
            advanceUntilIdle()
            assertTrue(completed)
            assertTrue(events.isEmpty())
            val updated =
                programRepository.getProgram()
                    ?.workouts
                    ?.first()
                    ?.workoutLifts
                    ?.first()
            assertEquals(LiftId("incline-press"), updated?.liftId)
            cancelAndIgnoreRemainingEvents()
        }
        job.cancel()
    }

    @Test
    @DisplayName("Given PersistToProgram when swap fails then user message is shown and error is logged")
    fun persistToProgramLogsErrorWhenSwapFails() = runTest {
        viewModel.initialize(
            workoutId = WORKOUT_ID,
            workoutLiftId = "wl-nonexistent",
            liftId = "bench-press",
            liftName = "Barbell Bench Press",
            commitMode = LiftSwapCommitMode.PersistToProgram,
        )

        viewModel.liftSwapState.test {
            val state = awaitNonNullState()
            val incline = state.options.first { it.id == "incline-press" }
            var completed = false
            viewModel.onLiftClicked(incline) { completed = true }
            advanceUntilIdle()
            assertFalse(completed)
            assertEquals("Couldn't swap that lift. Try again.", viewModel.userMessage.value)
            assertTrue(fakeLoggingPort.errors.isNotEmpty())
            assertTrue(fakeLoggingPort.errors.any { it.message.contains("Failed to persist lift swap") })
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<LiftSwapSheetState?>.awaitNonNullState(): LiftSwapSheetState {
        var item = awaitItem()
        while (item == null) {
            item = awaitItem()
        }
        return item
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

    private fun sampleProgram(): Program =
        Program(
            id = ProgramId("p-1"),
            name = "Push Program",
            createdAt = Instant.now(),
            cycleLength = 1,
            trainingWeekdays = setOf(Weekday.Monday),
            workouts =
                listOf(
                    Workout(
                        id = WorkoutId(WORKOUT_ID),
                        programId = ProgramId("p-1"),
                        name = "Push Day",
                        weekday = Weekday.Monday,
                        position = 0,
                        workoutLifts =
                            listOf(
                                WorkoutLift(
                                    id = WorkoutLiftId("wl-1"),
                                    workoutId = WorkoutId(WORKOUT_ID),
                                    liftId = LiftId("bench-press"),
                                    progressionScheme = ProgressionScheme.Linear,
                                    position = 0,
                                    sets = emptyList(),
                                ),
                            ),
                    ),
                ),
        )

    private companion object {
        const val WORKOUT_ID = "w-1"
    }
}
