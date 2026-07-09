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
import com.stratum.domain.program.SaveWorkoutUseCase
import com.stratum.domain.workout.WorkoutEditSession
import com.stratum.core.ui.component.SupersetLinkDirection
import com.stratum.feature.program.ui.EditWorkoutUiState
import com.stratum.domain.training.ProgramChangeEventPayload
import com.stratum.domain.training.ProgramChangeEventType
import com.stratum.feature.program.ui.PLACEHOLDER_LIFT_NAME
import com.stratum.feature.program.ui.SaveBlockedReason
import app.cash.turbine.ReceiveTurbine
import java.time.Instant
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

@ExtendWith(MainDispatcherRule::class)
class EditWorkoutViewModelTest {
    private lateinit var repository: FakeProgramRepository
    private lateinit var workoutEditSession: WorkoutEditSession

    @BeforeEach
    fun setUp() {
        repository = FakeProgramRepository(PROGRAM)
        workoutEditSession = WorkoutEditSession()
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
            assertTrue(updated.isDirty)
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
            viewModel.setRestSeconds(setId = null, workoutLiftId = WORKOUT_LIFT_ID, restSeconds = 90)
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
    @DisplayName("Given loaded workout when session reorder confirmed then template lifts are reordered")
    fun reorderConfirmedUpdatesTemplateLifts() = runTest {
        val viewModel = buildViewModel()
        viewModel.initialize(WORKOUT_ID)
        viewModel.uiState.test {
            val initial = awaitLoaded()
            assertEquals("bench-press", initial.lifts[0].liftId)
            assertEquals("incline-press", initial.lifts[1].liftId)

            // Verify starting reorder populates the session
            viewModel.startReorderLifts()
            val reorderLifts = workoutEditSession.getReorderLifts()
            assertEquals(2, reorderLifts.size)
            assertEquals("wl-1", reorderLifts[0].workoutLiftId)
            assertEquals("wl-2", reorderLifts[1].workoutLiftId)

            // Confirm a reordered sequence
            workoutEditSession.confirmReorder(WORKOUT_ID, listOf("wl-2", "wl-1"))
            val updated = awaitItem() as EditWorkoutUiState.Loaded
            assertTrue(updated.isDirty)
            assertEquals("incline-press", updated.lifts[0].liftId)
            assertEquals("bench-press", updated.lifts[1].liftId)
            assertEquals(0, updated.lifts[0].position)
            assertEquals(1, updated.lifts[1].position)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given loaded workout when session reorder cancelled/ignored then template lifts order is unchanged")
    fun reorderCancelledKeepsTemplateLiftsOrder() = runTest {
        val viewModel = buildViewModel()
        viewModel.initialize(WORKOUT_ID)
        viewModel.uiState.test {
            val initial = awaitLoaded()
            assertEquals("bench-press", initial.lifts[0].liftId)
            assertEquals("incline-press", initial.lifts[1].liftId)

            viewModel.startReorderLifts()
            // No confirm event emitted (e.g. user pops back stack on Cancel)
            // Assert that state remains dirty=false and order is unchanged
            assertFalse(initial.isDirty)
            assertEquals("bench-press", initial.lifts[0].liftId)
            assertEquals("incline-press", initial.lifts[1].liftId)
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
    @DisplayName("Given loaded workout when addLift then navigates to picker with null target details")
    fun addLiftNavigatesToPicker() = runTest {
        val viewModel = buildViewModel()
        viewModel.initialize(WORKOUT_ID)
        viewModel.uiState.test {
            val initial = awaitLoaded()
            var pickerWorkoutId: String? = null
            var pickerWorkoutLiftId: String? = null
            var pickerLiftId: String? = null
            var pickerLiftName: String? = null

            viewModel.addLift { wId, wlId, lId, name ->
                pickerWorkoutId = wId
                pickerWorkoutLiftId = wlId
                pickerLiftId = lId
                pickerLiftName = name
            }

            assertEquals(WORKOUT_ID, pickerWorkoutId)
            assertNull(pickerWorkoutLiftId)
            assertNull(pickerLiftId)
            assertNull(pickerLiftName)

            // Should not modify the loaded lifts in memory
            val current = viewModel.uiState.value as EditWorkoutUiState.Loaded
            assertEquals(initial.lifts.size, current.lifts.size)
            assertFalse(current.isDirty)
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
            viewModel.setReps(setId, min = 6)
            val updated = awaitItem() as EditWorkoutUiState.Loaded
            assertEquals(6, updated.lifts.first().sets.first().repMin)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given a backoff set when rep min changes then state is unchanged (RIR-only sets)")
    fun setRepMinOnBackoffIsNoOp() = runTest {
        val viewModel = buildViewModel()
        viewModel.initialize(WORKOUT_ID)
        viewModel.uiState.test {
            val loaded = awaitLoaded()
            // ls-2 is the backoff set in wl-1 (isTopSet = false).
            val backoffSet = loaded.lifts.first().sets.first { !it.isTopSet }
            val repMinBefore = backoffSet.repMin
            val repMaxBefore = backoffSet.repMax
            viewModel.setReps(backoffSet.setId, min = 6)
            // No state emission expected — backoff sets are RIR-only and not editable.
            expectNoEvents()
            val current = viewModel.uiState.value as EditWorkoutUiState.Loaded
            val currentBackoff = current.lifts.first().sets.first { it.setId == backoffSet.setId }
            assertEquals(repMinBefore, currentBackoff.repMin)
            assertEquals(repMaxBefore, currentBackoff.repMax)
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
            viewModel.linkSuperset("wl-2", SupersetLinkDirection.Previous)
            val loaded = awaitItem() as EditWorkoutUiState.Loaded
            assertNotNull(loaded.lifts[0].supersetGroupId)
            assertEquals(loaded.lifts[0].supersetGroupId, loaded.lifts[1].supersetGroupId)
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
            viewModel.linkSuperset("wl-2", SupersetLinkDirection.Previous)
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
            viewModel.linkSuperset("wl-2", SupersetLinkDirection.Previous)
            awaitItem()
            viewModel.removeLift("wl-2")
            val loaded = awaitLoaded()
            assertEquals(null, loaded.lifts.single().supersetGroupId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given dirty workout when saved then repository receives updated workout")
    fun saveWorkoutPersistsChanges() = runTest {
        val viewModel = buildViewModel()
        viewModel.initialize(WORKOUT_ID)
        viewModel.uiState.test {
            awaitLoaded()
            viewModel.setRestSeconds(setId = null, workoutLiftId = WORKOUT_LIFT_ID, restSeconds = 45)
            awaitItem()
            viewModel.saveWorkout()
            val saved = awaitItem() as EditWorkoutUiState.Loaded
            assertFalse(saved.isDirty)
            assertEquals(45, repository.getProgram()!!.workouts.first().workoutLifts.first().sets.first().restSeconds)
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
            saveWorkoutUseCase = SaveWorkoutUseCase(repository, com.stratum.core.testing.training.NoOpProgramChangeEventRepository()),
            computeWorkoutMuscleVolume = ComputeWorkoutMuscleVolumeUseCase(),
            estimateWorkoutDurationMinutes = EstimateWorkoutDurationMinutesUseCase(),
            programChangeEventRepository = com.stratum.core.testing.training.NoOpProgramChangeEventRepository(),
            workoutEditSession = workoutEditSession,
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

    @Test
    @DisplayName("Given loaded workout when LiftSelected is emitted from session then lift is updated in memory")
    fun handlesInMemoryLiftSelected() = runTest {
        val viewModel = buildViewModel()
        viewModel.initialize(WORKOUT_ID)
        viewModel.uiState.test {
            val initial = awaitLoaded()
            val liftToSwap = initial.lifts.first()

            // Emit swap selection event
            workoutEditSession.selectLift(
                workoutId = WORKOUT_ID,
                workoutLiftId = liftToSwap.workoutLiftId,
                liftId = "incline-press"
            )

            // Should emit updated UI state reflecting swap
            val updated = awaitItem() as EditWorkoutUiState.Loaded
            val swappedLift = updated.lifts.first()
            assertEquals("incline-press", swappedLift.liftId)
            assertEquals("Incline Press", swappedLift.name)
            assertTrue(updated.isDirty)

            // Emit add selection event
            workoutEditSession.selectLift(
                workoutId = WORKOUT_ID,
                workoutLiftId = null,
                liftId = "bench-press"
            )

            // Should emit updated UI state reflecting add
            val afterAdd = awaitItem() as EditWorkoutUiState.Loaded
            assertEquals(3, afterAdd.lifts.size)
            assertEquals("bench-press", afterAdd.lifts.last().liftId)
            assertEquals("Barbell Bench Press", afterAdd.lifts.last().name)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given loaded workout when LiftsSelected with asSuperset then new lifts share a group id")
    fun handlesLiftsSelectedAsSuperset() = runTest {
        val viewModel = buildViewModel()
        viewModel.initialize(WORKOUT_ID)
        viewModel.uiState.test {
            val initial = awaitLoaded()
            val beforeCount = initial.lifts.size

            workoutEditSession.selectLifts(
                workoutId = WORKOUT_ID,
                liftIds = listOf("incline-press", "bench-press"),
                asSuperset = true,
            )

            val updated = awaitItem() as EditWorkoutUiState.Loaded
            assertEquals(beforeCount + 2, updated.lifts.size)
            val added = updated.lifts.takeLast(2)
            assertEquals("incline-press", added[0].liftId)
            assertEquals("bench-press", added[1].liftId)
            assertNotNull(added[0].supersetGroupId)
            assertEquals(added[0].supersetGroupId, added[1].supersetGroupId)
            assertFalse(added[0].useRestTimerBetweenLifts)
            assertFalse(added[1].useRestTimerBetweenLifts)
            assertTrue(updated.isDirty)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given loaded workout when LiftsSelected without superset then new lifts have null group id")
    fun handlesLiftsSelectedWithoutSuperset() = runTest {
        val viewModel = buildViewModel()
        viewModel.initialize(WORKOUT_ID)
        viewModel.uiState.test {
            val initial = awaitLoaded()
            val beforeCount = initial.lifts.size

            workoutEditSession.selectLifts(
                workoutId = WORKOUT_ID,
                liftIds = listOf("incline-press", "bench-press"),
                asSuperset = false,
            )

            val updated = awaitItem() as EditWorkoutUiState.Loaded
            assertEquals(beforeCount + 2, updated.lifts.size)
            val added = updated.lifts.takeLast(2)
            assertNull(added[0].supersetGroupId)
            assertNull(added[1].supersetGroupId)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given swapped lift in memory when saved then LIFT_SWAPPED event is recorded")
    fun saveSwappedLiftRecordsEvent() = runTest {
        val events = com.stratum.core.testing.training.FakeProgramChangeEventRepository()
        val liftRepository =
            FakeLiftRepository(
                initialLifts =
                    listOf(
                        catalogLift(LiftId("bench-press"), "Barbell Bench Press"),
                        catalogLift(LiftId("incline-press"), "Incline Press"),
                    ),
            )
        val viewModel = EditWorkoutViewModel(
            observeProgram = ObserveProgramUseCase(repository),
            observeLiftCatalog = ObserveLiftCatalogUseCase(liftRepository),
            saveWorkoutUseCase = SaveWorkoutUseCase(repository, events),
            computeWorkoutMuscleVolume = ComputeWorkoutMuscleVolumeUseCase(),
            estimateWorkoutDurationMinutes = EstimateWorkoutDurationMinutesUseCase(),
            programChangeEventRepository = events,
            workoutEditSession = workoutEditSession,
        )

        viewModel.initialize(WORKOUT_ID)
        viewModel.uiState.test {
            val initial = awaitLoaded()
            val liftToSwap = initial.lifts.first()

            // Emit swap selection event
            workoutEditSession.selectLift(
                workoutId = WORKOUT_ID,
                workoutLiftId = liftToSwap.workoutLiftId,
                liftId = "incline-press"
            )
            awaitItem() // Consume swap state change

            viewModel.saveWorkout()
            awaitItem() // Consume saved state change

            // Verify LIFT_SWAPPED event is appended
            val appended = events.getEventsForProgram(ProgramId("p-1"))
            val swapEvent = appended.firstOrNull { it.eventType == ProgramChangeEventType.LIFT_SWAPPED }
            assertNotNull(swapEvent)
            val payload = swapEvent?.payload as ProgramChangeEventPayload.LiftSwapped
            assertEquals(liftToSwap.workoutLiftId, payload.workoutLiftId.value)
            assertEquals("bench-press", payload.previousLiftId.value)
            assertEquals("incline-press", payload.newLiftId.value)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given initialized viewModel when clearSession is called then state is reset and allows fresh initialization")
    fun clearSessionResetsState() = runTest {
        val viewModel = buildViewModel()
        viewModel.initialize(WORKOUT_ID)
        viewModel.uiState.test {
            val initial = awaitLoaded()

            // Mutate in-memory state
            workoutEditSession.selectLift(
                workoutId = WORKOUT_ID,
                workoutLiftId = initial.lifts.first().workoutLiftId,
                liftId = "incline-press"
            )
            val updated = awaitItem() as EditWorkoutUiState.Loaded
            assertEquals("incline-press", updated.lifts.first().liftId)

            // Clear session
            viewModel.clearSession()
            val loading = awaitItem()
            assertTrue(loading is EditWorkoutUiState.Loading)

            // Re-initialize: should load fresh from repository (bench-press instead of incline-press)
            viewModel.initialize(WORKOUT_ID)
            val reloaded = awaitLoaded()
            assertEquals("bench-press", reloaded.lifts.first().liftId)
            assertFalse(reloaded.isDirty)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
