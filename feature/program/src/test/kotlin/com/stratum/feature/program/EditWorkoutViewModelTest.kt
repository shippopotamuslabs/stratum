package com.stratum.feature.program

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.ObserveLiftCatalogUseCase
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.ComputeWorkoutMuscleVolumeUseCase
import com.stratum.domain.program.EstimateWorkoutDurationMinutesUseCase
import com.stratum.domain.program.ObserveProgramUseCase
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.feature.program.ui.EditWorkoutUiState
import com.stratum.feature.program.ui.PLACEHOLDER_LIFT_NAME
import app.cash.turbine.ReceiveTurbine
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
class EditWorkoutViewModelTest {
    private lateinit var repository: FakeProgramRepository

    @BeforeEach
    fun setUp() {
        repository = FakeProgramRepository(PROGRAM)
    }

    @Test
    @DisplayName("Given a workout when initialized then per-set reps and RIR are mapped")
    fun mapsWorkoutSets() = runTest {
        val viewModel = buildViewModel()

        viewModel.initialize(WORKOUT_ID)
        viewModel.uiState.test {
            val loaded = awaitLoaded()
            assertEquals("Push Day", loaded.workoutName)
            val sets = loaded.lifts.first().sets
            assertEquals(2, sets.size)
            assertEquals(8, sets.first().repMin)
            assertEquals(12, sets.first().repMax)
            assertEquals(2, sets.first().rirGoal)
            assertEquals(2, sets[1].rirGoal)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given loaded workout when initialized then volume and duration are derived")
    fun derivedMetricsOnLoad() = runTest {
        val viewModel = buildViewModel()
        viewModel.initialize(WORKOUT_ID)

        viewModel.uiState.test {
            val loaded = awaitLoaded()
            assertTrue(loaded.upperVolumeByMuscle.isNotEmpty() || loaded.lowerVolumeByMuscle.isNotEmpty())
            assertTrue(
                loaded.programUpperVolumeByMuscle.isNotEmpty() ||
                    loaded.programLowerVolumeByMuscle.isNotEmpty(),
            )
            assertTrue(loaded.estimatedDurationMinutes > 0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given loaded workout when addSet then workout and program volume both update")
    fun addSetUpdatesWorkoutAndProgramVolume() = runTest {
        val viewModel = buildViewModel()
        viewModel.initialize(WORKOUT_ID)
        viewModel.uiState.test {
            val loaded = awaitLoaded()
            val workoutChestSets =
                loaded.upperVolumeByMuscle
                    .firstOrNull { card -> card.muscle == MuscleGroup.Chest }
                    ?.setCount
                    ?: 0.0
            val programChestSets =
                loaded.programUpperVolumeByMuscle
                    .firstOrNull { card -> card.muscle == MuscleGroup.Chest }
                    ?.setCount
                    ?: 0.0
            viewModel.addSet(WORKOUT_LIFT_ID)
            val updated = awaitItem() as EditWorkoutUiState.Loaded
            val updatedWorkoutChestSets =
                updated.upperVolumeByMuscle
                    .first { card -> card.muscle == MuscleGroup.Chest }
                    .setCount
            val updatedProgramChestSets =
                updated.programUpperVolumeByMuscle
                    .first { card -> card.muscle == MuscleGroup.Chest }
                    .setCount
            assertEquals(workoutChestSets + 1.0, updatedWorkoutChestSets)
            assertEquals(programChestSets + 1.0, updatedProgramChestSets)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given loaded workout when sets viewed then default rest seconds are applied")
    fun defaultRestSecondsApplied() = runTest {
        val viewModel = buildViewModel()
        viewModel.initialize(WORKOUT_ID)

        viewModel.uiState.test {
            val loaded = awaitLoaded()
            assertTrue(loaded.lifts.first().sets.all { set -> set.restSeconds == 120 })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given a lift when rest applied to all sets then every set shares the value")
    fun setRestSecondsForAllSetsOnLift() = runTest {
        val viewModel = buildViewModel()
        viewModel.initialize(WORKOUT_ID)
        viewModel.uiState.test {
            awaitLoaded()
            viewModel.setRestSecondsForAllSetsOnLift(WORKOUT_LIFT_ID, 90)
            val updated = awaitItem() as EditWorkoutUiState.Loaded
            assertTrue(updated.lifts.first().sets.all { set -> set.restSeconds == 90 })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given a lift when removeSet then set is removed and numbers renumber")
    fun removeSetRenumbers() = runTest {
        val viewModel = buildViewModel()
        viewModel.initialize(WORKOUT_ID)
        viewModel.uiState.test {
            val loaded = awaitLoaded()
            val setId = loaded.lifts.first().sets.last().setId
            viewModel.removeSet(setId)
            val updated = awaitItem() as EditWorkoutUiState.Loaded
            assertEquals(1, updated.lifts.first().sets.size)
            assertEquals(1, updated.lifts.first().sets.first().setNumber)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given a lift when addSet then set count increases and numbers renumber")
    fun addSetIncrementsCount() = runTest {
        val viewModel = buildViewModel()
        viewModel.initialize(WORKOUT_ID)
        viewModel.uiState.test {
            awaitLoaded()
            viewModel.addSet(WORKOUT_LIFT_ID)
            val loaded = awaitItem() as EditWorkoutUiState.Loaded
            assertEquals(3, loaded.lifts.first().sets.size)
            assertEquals(listOf(1, 2, 3), loaded.lifts.first().sets.map { set -> set.setNumber })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given lifts when reordered and confirmed then positions update")
    fun reorderUpdatesPositions() = runTest {
        val viewModel = buildViewModel()
        viewModel.initialize(WORKOUT_ID)
        viewModel.uiState.test {
            awaitLoaded()
            viewModel.toggleReorderMode()
            val reorderMode = awaitItem() as EditWorkoutUiState.Loaded
            assertTrue(reorderMode.isReorderMode)
            viewModel.reorderLifts(fromIndex = 0, toIndex = 1)
            awaitItem()
            viewModel.confirmReorder()
            val loaded = awaitItem() as EditWorkoutUiState.Loaded
            assertFalse(loaded.isReorderMode)
            assertEquals("Incline Press", loaded.lifts.first().name)
            assertEquals(0, loaded.lifts.first().position)
            assertEquals(1, loaded.lifts[1].position)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given a lift when removeLift then it is removed from the list")
    fun removeLift() = runTest {
        val viewModel = buildViewModel()
        viewModel.initialize(WORKOUT_ID)
        viewModel.uiState.test {
            awaitLoaded()
            viewModel.removeLift(WORKOUT_LIFT_ID)
            val loaded = awaitLoaded()
            assertEquals(1, loaded.lifts.size)
            assertEquals("Incline Press", loaded.lifts.first().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given loaded workout when addLift then placeholder exercise is appended")
    fun addLiftAppendsPlaceholder() = runTest {
        val viewModel = buildViewModel()
        viewModel.initialize(WORKOUT_ID)
        viewModel.uiState.test {
            awaitLoaded()
            viewModel.addLift()
            val loaded = awaitLoaded()
            assertEquals(3, loaded.lifts.size)
            assertEquals(PLACEHOLDER_LIFT_NAME, loaded.lifts.last().name)
            assertEquals(1, loaded.lifts.last().sets.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given a set when rep min changes then state updates in memory")
    fun setRepMinUpdatesState() = runTest {
        val viewModel = buildViewModel()
        viewModel.initialize(WORKOUT_ID)
        viewModel.uiState.test {
            val loaded = awaitLoaded()
            val setId = loaded.lifts.first().sets.first().setId
            viewModel.setRepMin(setId, 6)
            val updated = awaitItem() as EditWorkoutUiState.Loaded
            assertEquals(6, updated.lifts.first().sets.first().repMin)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given a set when RIR changes then state updates in memory")
    fun setRirGoalUpdatesState() = runTest {
        val viewModel = buildViewModel()
        viewModel.initialize(WORKOUT_ID)
        viewModel.uiState.test {
            awaitLoaded()
            val setId = WORKOUT_LIFT_ID.let { _ ->
                (viewModel.uiState.value as EditWorkoutUiState.Loaded).lifts.first().sets.first().setId
            }
            viewModel.setRirGoal(setId, 1)
            val updated = awaitItem() as EditWorkoutUiState.Loaded
            assertEquals(1, updated.lifts.first().sets.first().rirGoal)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given adjacent lifts when linked with previous then both lifts share a group id")
    fun linkSupersetWithPrevious() = runTest {
        val viewModel = buildViewModel()
        viewModel.initialize(WORKOUT_ID)
        viewModel.uiState.test {
            awaitLoaded()
            viewModel.linkSupersetWithPrevious("wl-2")
            val loaded = awaitItem() as EditWorkoutUiState.Loaded
            assertEquals(1, loaded.lifts[0].supersetGroupId)
            assertEquals(1, loaded.lifts[1].supersetGroupId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given a superset pair when one lift is unlinked then both lose their group id")
    fun removeFromSupersetClearsPair() = runTest {
        val viewModel = buildViewModel()
        viewModel.initialize(WORKOUT_ID)
        viewModel.uiState.test {
            awaitLoaded()
            viewModel.linkSupersetWithPrevious("wl-2")
            awaitItem()
            viewModel.removeFromSuperset("wl-2")
            val loaded = awaitItem() as EditWorkoutUiState.Loaded
            assertEquals(null, loaded.lifts[0].supersetGroupId)
            assertEquals(null, loaded.lifts[1].supersetGroupId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given a superset pair when one lift is removed then remaining lift loses its group id")
    fun removeLiftClearsOrphanSupersetGroup() = runTest {
        val viewModel = buildViewModel()
        viewModel.initialize(WORKOUT_ID)
        viewModel.uiState.test {
            awaitLoaded()
            viewModel.linkSupersetWithPrevious("wl-2")
            awaitItem()
            viewModel.removeLift("wl-2")
            val loaded = awaitLoaded()
            assertEquals(null, loaded.lifts.single().supersetGroupId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun ReceiveTurbine<EditWorkoutUiState>.awaitLoaded(): EditWorkoutUiState.Loaded {
        var item = awaitItem()
        while (item !is EditWorkoutUiState.Loaded) {
            item = awaitItem()
        }
        return item
    }

    private fun buildViewModel(): EditWorkoutViewModel {
        val liftRepository =
            FakeLiftRepository(
                initialLifts =
                    listOf(
                        catalogLift(LiftId("bench-press"), "Barbell Bench Press"),
                        catalogLift(LiftId("incline-press"), "Incline Press"),
                    ),
            )
        return EditWorkoutViewModel(
            observeProgram = ObserveProgramUseCase(repository),
            observeLiftCatalog = ObserveLiftCatalogUseCase(liftRepository),
            computeWorkoutMuscleVolume = ComputeWorkoutMuscleVolumeUseCase(),
            estimateWorkoutDurationMinutes = EstimateWorkoutDurationMinutesUseCase(),
        )
    }

    private fun catalogLift(id: LiftId, name: String): Lift =
        Lift(
            id = id,
            name = name,
            movementPatterns = setOf(MovementPattern.HorizontalPress),
            primaryMuscles = setOf(MuscleGroup.Chest),
            secondaryMuscles = emptySet(),
            allowWeightChangeBetweenSets = false,
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = true,
        )

    private companion object {
        const val WORKOUT_ID = "w-1"
        const val WORKOUT_LIFT_ID = "wl-1"

        val PROGRAM: Program =
            Program(
                id = ProgramId("p-1"),
                name = "Push/Pull/Legs",
                createdAt = Instant.parse("2026-06-26T12:00:00Z"),
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
                                        id = WorkoutLiftId(WORKOUT_LIFT_ID),
                                        workoutId = WorkoutId(WORKOUT_ID),
                                        liftId = LiftId("bench-press"),
                                        progressionScheme = ProgressionScheme.Linear,
                                        position = 0,
                                        sets =
                                            listOf(
                                                LiftSet(
                                                    LiftSetId("ls-1"),
                                                    WorkoutLiftId(WORKOUT_LIFT_ID),
                                                    0,
                                                    SetType.Standard,
                                                    8,
                                                    12,
                                                    2,
                                                    isTopSet = true,
                                                ),
                                                LiftSet(
                                                    LiftSetId("ls-2"),
                                                    WorkoutLiftId(WORKOUT_LIFT_ID),
                                                    1,
                                                    SetType.Standard,
                                                    8,
                                                    12,
                                                    2,
                                                    isTopSet = false,
                                                ),
                                            ),
                                    ),
                                    WorkoutLift(
                                        id = WorkoutLiftId("wl-2"),
                                        workoutId = WorkoutId(WORKOUT_ID),
                                        liftId = LiftId("incline-press"),
                                        progressionScheme = ProgressionScheme.Linear,
                                        position = 1,
                                        sets =
                                            listOf(
                                                LiftSet(
                                                    LiftSetId("ls-3"),
                                                    WorkoutLiftId("wl-2"),
                                                    0,
                                                    SetType.Standard,
                                                    10,
                                                    10,
                                                    2,
                                                    isTopSet = true,
                                                ),
                                            ),
                                    ),
                                ),
                        ),
                    ),
            )
    }
}
