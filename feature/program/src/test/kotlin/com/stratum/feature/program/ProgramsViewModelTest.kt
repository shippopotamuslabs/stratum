package com.stratum.feature.program

import app.cash.turbine.test
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.domain.lift.identity.LiftId
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
import com.stratum.feature.program.ui.ProgramsUiState
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import com.stratum.core.testing.MainDispatcherRule

/**
 * [ProgramsViewModel] / [toProgramsUiState] mapping tests. Scenarios PROG-GEN-400 (empty → Empty),
 * 401 (populated → Loaded mapped presentation), 402 (WorkoutLift expansion).
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
        fun `a populated repository emits Loaded with the program name and a single workout`() =
            runTest {
                val viewModel = buildViewModel(initial = PROGRAM_PPL)

                viewModel.programsUiState.test {
                    val state = awaitItem()
                    assertTrue(state is ProgramsUiState.Loaded)
                    val loaded = state as ProgramsUiState.Loaded
                    assertEquals("Push/Pull/Legs", loaded.program.name)
                    assertEquals(1, loaded.program.workouts.size)
                    assertEquals("Push Day", loaded.program.workouts[0].name)
                    assertEquals(1, loaded.program.workouts[0].workoutLifts.size)
                    cancelAndIgnoreRemainingEvents()
                }
            }

        @Test
        fun `the presentation does not expose any domain type`() = runTest {
            val viewModel = buildViewModel(initial = PROGRAM_PPL)

            viewModel.programsUiState.test {
                val state = awaitItem() as ProgramsUiState.Loaded
                // ProgramsUiState and its view states must not surface Program/Workout/etc.
                val uiPackage = ProgramsUiState::class.java.`package`.name
                val viewStateTypes = listOf(
                    state.program::class,
                    state.program.workouts.first()::class,
                    state.program.workouts.first().workoutLifts.first()::class,
                )
                assertTrue(viewStateTypes.all { it.java.`package`.name == uiPackage })
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    @DisplayName("PROG-GEN-402 WorkoutLift expansion reveals its sets")
    inner class Expansion {
        @Test
        fun `the populated WorkoutLift view state carries two sets`() = runTest {
            val viewModel = buildViewModel(initial = PROGRAM_PPL)

            viewModel.programsUiState.test {
                val lift = (awaitItem() as ProgramsUiState.Loaded)
                    .program.workouts[0].workoutLifts[0]
                assertEquals(2, lift.sets.size)
                val first = lift.sets.first()
                assertTrue(first.repMin in 5..12)
                assertTrue(first.repMax in 5..15)
                assertTrue(first.rirGoal in 0..3)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    private fun buildViewModel(initial: Program?): ProgramsViewModel {
        repository = FakeProgramRepository(initial)
        return ProgramsViewModel(com.stratum.domain.program.ObserveProgramUseCase(repository))
    }

    private companion object {
        val PROGRAM_PPL: Program = Program(
            id = ProgramId("p-1"),
            name = "Push/Pull/Legs",
            createdAt = Instant.parse("2026-06-26T12:00:00Z"),
            workouts = listOf(
                Workout(
                    id = WorkoutId("w-1"),
                    programId = ProgramId("p-1"),
                    name = "Push Day",
                    weekday = Weekday.Monday,
                    position = 0,
                    workoutLifts = listOf(
                        WorkoutLift(
                            id = WorkoutLiftId("wl-1"),
                            workoutId = WorkoutId("w-1"),
                            liftId = LiftId("bench-press"),
                            progressionScheme = ProgressionScheme.Linear,
                            position = 0,
                            sets = listOf(
                                LiftSet(
                                    LiftSetId("ls-1"), WorkoutLiftId("wl-1"), 0,
                                    SetType.Standard, 8, 12, 2, isTopSet = true,
                                ),
                                LiftSet(
                                    LiftSetId("ls-2"), WorkoutLiftId("wl-1"), 1,
                                    SetType.Standard, 8, 12, 2, isTopSet = false,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }
}
