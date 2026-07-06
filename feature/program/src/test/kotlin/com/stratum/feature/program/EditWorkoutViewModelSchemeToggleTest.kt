package com.stratum.feature.program

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeProgramChangeEventRepository
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.ObserveLiftCatalogUseCase
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.ComputeWorkoutMuscleVolumeUseCase
import com.stratum.domain.program.EstimateWorkoutDurationMinutesUseCase
import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.ObserveProgramUseCase
import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SaveWorkoutUseCase
import com.stratum.domain.program.SetType
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.ProgramChangeEventPayload
import com.stratum.domain.training.ProgramChangeEventType
import com.stratum.feature.program.ui.EditWorkoutUiState
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Slice 8 — EditWorkoutScreen per-lift scheme toggle (D11). 5 scenarios (S8-*).
 *
 * `setProgressionScheme` is gated on `isCompound` and, on save, a `SchemeChanged`
 * `ProgramChangeEvent` is appended only when the scheme actually changed. State-based
 * assertions only (UNIT_TESTING.md §1, Detekt `ForbiddenVerifyInViewModelTests`).
 */
@ExtendWith(MainDispatcherRule::class)
@DisplayName("Slice 8: EditWorkoutScreen per-lift scheme toggle")
class EditWorkoutViewModelSchemeToggleTest {
    private val programId = ProgramId("p-1")
    private val workoutId = WorkoutId("w-1")
    private lateinit var repository: FakeProgramRepository
    private lateinit var programChangeEventRepository: FakeProgramChangeEventRepository
    private lateinit var workoutEditSession: WorkoutEditSession

    @BeforeEach
    fun setUp() {
        repository = FakeProgramRepository(programWithCompoundAndIsolation())
        programChangeEventRepository = FakeProgramChangeEventRepository()
        workoutEditSession = WorkoutEditSession()
    }

    @Test
    @DisplayName("S8-toggle-compound-updates-state-marks-dirty")
    fun `S8-toggle-compound-updates-state-marks-dirty`() = runTest {
        val viewModel = buildViewModel()
        viewModel.initialize(WORKOUT_ID)
        viewModel.uiState.test {
            val loaded = awaitLoaded()
            val compoundLift = loaded.lifts.first { it.workoutLiftId == COMPOUND_WL_ID }
            assertTrue(compoundLift.isCompound, "wl-1 must be a compound lift")
            assertEquals(ProgressionScheme.Linear, compoundLift.progressionScheme)

            viewModel.setProgressionScheme(COMPOUND_WL_ID, ProgressionScheme.Double)

            val updated = awaitItem() as EditWorkoutUiState.Loaded
            val toggledLift = updated.lifts.first { it.workoutLiftId == COMPOUND_WL_ID }
            assertEquals(ProgressionScheme.Double, toggledLift.progressionScheme)
            assertTrue(updated.isDirty, "isDirty must be true after a scheme toggle")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("S8-toggle-isolation-is-noop")
    fun `S8-toggle-isolation-is-noop`() = runTest {
        val viewModel = buildViewModel()
        viewModel.initialize(WORKOUT_ID)
        viewModel.uiState.test {
            val loaded = awaitLoaded()
            val isolationLift = loaded.lifts.first { it.workoutLiftId == ISOLATION_WL_ID }
            assertFalse(isolationLift.isCompound, "wl-2 must be an isolation lift")
            assertEquals(ProgressionScheme.Double, isolationLift.progressionScheme)

            viewModel.setProgressionScheme(ISOLATION_WL_ID, ProgressionScheme.Linear)

            val updated = viewModel.uiState.value as EditWorkoutUiState.Loaded
            val stillIsolation = updated.lifts.first { it.workoutLiftId == ISOLATION_WL_ID }
            assertEquals(
                ProgressionScheme.Double,
                stillIsolation.progressionScheme,
                "isolation lift scheme must not change",
            )
            assertFalse(updated.isDirty, "isDirty must remain false for a no-op toggle")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("S8-toggle-appends-scheme-changed-event")
    fun `S8-toggle-appends-scheme-changed-event`() = runTest {
        val viewModel = buildViewModel()
        viewModel.initialize(WORKOUT_ID)
        viewModel.uiState.test {
            awaitLoaded()
            viewModel.setProgressionScheme(COMPOUND_WL_ID, ProgressionScheme.Double)
            awaitItem()
            viewModel.saveWorkout()
            awaitItem()

            val events = programChangeEventRepository.getEventsForProgram(programId)
            val schemeEvents = events.filter { it.eventType == ProgramChangeEventType.SCHEME_CHANGED }
            assertEquals(1, schemeEvents.size, "exactly one SCHEME_CHANGED event must be appended")
            val event = schemeEvents.single()
            val payload = event.payload as ProgramChangeEventPayload.SchemeChanged
            assertEquals(WorkoutLiftId(COMPOUND_WL_ID), payload.workoutLiftId)
            assertEquals(ProgressionScheme.Linear, payload.previous)
            assertEquals(ProgressionScheme.Double, payload.current)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("S8-mutation-defense-toggle-appends-no-event")
    fun `S8-mutation-defense-toggle-appends-no-event`() = runTest {
        val viewModel = buildViewModel()
        viewModel.initialize(WORKOUT_ID)
        viewModel.uiState.test {
            val loaded = awaitLoaded()
            // Edit only a set's repMin — no scheme change. isDirty becomes true but no
            // SchemeChanged event should be appended on save.
            val setId = loaded.lifts.first().sets.first().setId
            viewModel.setRepMin(setId, 6)
            awaitItem()
            viewModel.saveWorkout()
            awaitItem()

            val events = programChangeEventRepository.getEventsForProgram(programId)
            val schemeEvents = events.filter { it.eventType == ProgramChangeEventType.SCHEME_CHANGED }
            assertTrue(
                schemeEvents.isEmpty(),
                "no SCHEME_CHANGED event must be appended when the scheme was not toggled",
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("S8-mutation-defense-toggle-isolation-not-noop")
    fun `S8-mutation-defense-toggle-isolation-not-noop`() = runTest {
        val viewModel = buildViewModel()
        viewModel.initialize(WORKOUT_ID)
        viewModel.uiState.test {
            awaitLoaded()
            // Isolation → Linear: must be a no-op (wl-2 stays Double).
            viewModel.setProgressionScheme(ISOLATION_WL_ID, ProgressionScheme.Linear)
            val afterIsolation = viewModel.uiState.value as EditWorkoutUiState.Loaded
            val stillIsolation = afterIsolation.lifts.first { it.workoutLiftId == ISOLATION_WL_ID }
            assertEquals(ProgressionScheme.Double, stillIsolation.progressionScheme)

            // Compound → Double: must change (wl-1 was Linear).
            viewModel.setProgressionScheme(COMPOUND_WL_ID, ProgressionScheme.Double)
            val afterCompound = awaitItem() as EditWorkoutUiState.Loaded
            val nowCompound = afterCompound.lifts.first { it.workoutLiftId == COMPOUND_WL_ID }
            assertEquals(ProgressionScheme.Double, nowCompound.progressionScheme)
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
                        catalogLift(LiftId("back-squat"), "Back Squat", compound = true),
                        catalogLift(LiftId("leg-curl"), "Leg Curl", compound = false),
                    ),
            )
        return EditWorkoutViewModel(
            observeProgram = ObserveProgramUseCase(repository),
            observeLiftCatalog = ObserveLiftCatalogUseCase(liftRepository),
            saveWorkoutUseCase = SaveWorkoutUseCase(repository, programChangeEventRepository),
            computeWorkoutMuscleVolume = ComputeWorkoutMuscleVolumeUseCase(),
            estimateWorkoutDurationMinutes = EstimateWorkoutDurationMinutesUseCase(),
            programChangeEventRepository = programChangeEventRepository,
            workoutEditSession = workoutEditSession,
        )
    }

    private fun catalogLift(id: LiftId, name: String, compound: Boolean): Lift =
        Lift(
            id = id,
            name = name,
            movementPatterns =
                if (compound) {
                    setOf(MovementPattern.SquatPattern, MovementPattern.HipHinge)
                } else {
                    setOf(MovementPattern.HipHinge)
                },
            primaryMuscles = setOf(MuscleGroup.Quad),
            secondaryMuscles = setOf(MuscleGroup.Hamstring),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = true,
        )

    private fun programWithCompoundAndIsolation(): Program =
        Program(
            id = programId,
            name = "Lower",
            createdAt = Instant.parse("2026-06-26T12:00:00Z"),
            cycleLength = 1,
            trainingWeekdays = setOf(Weekday.Monday),
            workouts =
                listOf(
                    Workout(
                        id = workoutId,
                        programId = programId,
                        name = "Lower",
                        weekday = Weekday.Monday,
                        position = 0,
                        workoutLifts =
                            listOf(
                                WorkoutLift(
                                    id = WorkoutLiftId(COMPOUND_WL_ID),
                                    workoutId = workoutId,
                                    liftId = LiftId("back-squat"),
                                    progressionScheme = ProgressionScheme.Linear,
                                    position = 0,
                                    sets =
                                        listOf(
                                            LiftSet(
                                                id = com.stratum.domain.program.identity.LiftSetId("ls-1"),
                                                workoutLiftId = WorkoutLiftId(COMPOUND_WL_ID),
                                                position = 0,
                                                setType = SetType.Standard,
                                                repMin = 4,
                                                repMax = 6,
                                                rirGoal = 2,
                                                isTopSet = true,
                                                restSeconds = 180,
                                            ),
                                        ),
                                ),
                                WorkoutLift(
                                    id = WorkoutLiftId(ISOLATION_WL_ID),
                                    workoutId = workoutId,
                                    liftId = LiftId("leg-curl"),
                                    progressionScheme = ProgressionScheme.Double,
                                    position = 1,
                                    sets =
                                        listOf(
                                            LiftSet(
                                                id = com.stratum.domain.program.identity.LiftSetId("ls-2"),
                                                workoutLiftId = WorkoutLiftId(ISOLATION_WL_ID),
                                                position = 0,
                                                setType = SetType.Standard,
                                                repMin = 8,
                                                repMax = 12,
                                                rirGoal = 2,
                                                isTopSet = true,
                                                restSeconds = 90,
                                            ),
                                        ),
                                ),
                            ),
                    ),
                ),
        )

    private companion object {
        const val WORKOUT_ID = "w-1"
        const val COMPOUND_WL_ID = "wl-1"
        const val ISOLATION_WL_ID = "wl-2"
    }
}
