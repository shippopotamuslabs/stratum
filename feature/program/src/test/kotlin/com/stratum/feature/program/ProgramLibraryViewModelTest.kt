package com.stratum.feature.program

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.ObserveLiftCatalogUseCase
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.ActivateStoredProgramUseCase
import com.stratum.domain.program.DeleteProgramUseCase
import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.ObserveLibraryProgramsUseCase
import com.stratum.domain.program.ObserveProgramUseCase
import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.ObserveActiveSessionUseCase
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
class ProgramLibraryViewModelTest {
    @Test
    @DisplayName("Lists active program first then library inventory")
    fun listsActiveAndLibrary() = runTest {
        val active = program(id = "active", name = "Active PPL", isActive = true)
        val library = program(id = "library", name = "Library UL", isActive = false)
        val programRepo = FakeProgramRepository(active)
        programRepo.saveToLibrary(library)
        val viewModel = buildViewModel(programRepo)

        viewModel.uiState.test {
            val state = awaitItemUntil { it.programs.size == 2 }
            assertEquals(listOf("active", "library"), state.programs.map { it.programId })
            assertTrue(state.programs.first().isActive)
            assertFalse(state.programs[1].isActive)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Expanded program schedule includes rest days from weekly schedule")
    fun scheduleIncludesRestDays() = runTest {
        val active =
            program(
                id = "active",
                name = "Active",
                isActive = true,
                weekdays = setOf(Weekday.Monday, Weekday.Wednesday),
            )
        val viewModel = buildViewModel(FakeProgramRepository(active))

        viewModel.uiState.test {
            val state = awaitItemUntil { it.programs.isNotEmpty() }
            val card = state.programs.single()
            assertTrue(card.days.any { day -> day.isRest })
            assertTrue(card.days.any { day -> !day.isRest && day.workoutId != null })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Activate promotes library program")
    fun activateLibraryProgram() = runTest {
        val active = program(id = "active", name = "Active", isActive = true)
        val library = program(id = "library", name = "Library", isActive = false)
        val programRepo = FakeProgramRepository(active)
        programRepo.saveToLibrary(library)
        val viewModel = buildViewModel(programRepo)

        viewModel.uiState.test {
            awaitItemUntil { it.programs.size == 2 }
            viewModel.activateProgram("library")
            val updated = awaitItemUntil { it.programs.first().programId == "library" }
            assertTrue(updated.programs.first().isActive)
            // No duplicate ids after activate (LazyColumn keys programId).
            assertEquals(
                updated.programs.map { it.programId }.distinct().size,
                updated.programs.size,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("mergeActiveAndLibraryPrograms drops library row that still matches active id")
    fun mergeDedupesActiveStillPresentInLibrary() {
        val active = program(id = "shared", name = "Shared", isActive = true)
        // Stale library emission still includes the program that is now (or still) active.
        val library =
            listOf(
                program(id = "shared", name = "Shared stale", isActive = false),
                program(id = "other", name = "Other", isActive = false),
            )

        val merged = mergeActiveAndLibraryPrograms(active, library)

        assertEquals(listOf("shared", "other"), merged.map { it.id.value })
        assertTrue(merged.first().isActive)
        assertEquals(1, merged.count { it.id.value == "shared" })
    }

    @Test
    @DisplayName("Delete confirm removes program and clears pending dialog")
    fun deleteConfirmRemovesProgram() = runTest {
        val active = program(id = "active", name = "Active", isActive = true)
        val library = program(id = "library", name = "Library", isActive = false)
        val programRepo = FakeProgramRepository(active)
        programRepo.saveToLibrary(library)
        val viewModel = buildViewModel(programRepo)

        viewModel.uiState.test {
            awaitItemUntil { it.programs.size == 2 }
            viewModel.requestDelete("library")
            val pending = awaitItemUntil { it.pendingDeleteProgramId == "library" }
            assertEquals("Library", pending.pendingDeleteProgramName)
            assertFalse(pending.pendingDeleteHasActiveSession)
            viewModel.confirmDelete()
            val after = awaitItemUntil { it.programs.none { program -> program.programId == "library" } }
            assertEquals(null, after.pendingDeleteProgramId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Pending delete with active session sets block flag")
    fun deleteWithActiveSessionFlagsWarning() = runTest {
        val active = program(id = WorkoutLoggingFixtures.PROGRAM_ID, name = "Active", isActive = true)
        val programRepo = FakeProgramRepository(active)
        val trainingLog = FakeTrainingLogRepository()
        trainingLog.seedSession(WorkoutLoggingFixtures.inProgressSession())
        val viewModel = buildViewModel(programRepo, trainingLog)

        viewModel.uiState.test {
            awaitItemUntil { it.programs.isNotEmpty() }
            viewModel.requestDelete(WorkoutLoggingFixtures.PROGRAM_ID)
            val pending = awaitItemUntil { it.pendingDeleteProgramId != null }
            assertTrue(pending.pendingDeleteHasActiveSession)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Confirm delete with active session does not remove program")
    fun confirmDeleteBlockedByActiveSession() = runTest {
        val active = program(id = WorkoutLoggingFixtures.PROGRAM_ID, name = "Active", isActive = true)
        val programRepo = FakeProgramRepository(active)
        val trainingLog = FakeTrainingLogRepository()
        trainingLog.seedSession(WorkoutLoggingFixtures.inProgressSession())
        val viewModel = buildViewModel(programRepo, trainingLog)

        viewModel.uiState.test {
            awaitItemUntil { it.programs.isNotEmpty() }
            viewModel.requestDelete(WorkoutLoggingFixtures.PROGRAM_ID)
            awaitItemUntil { it.pendingDeleteHasActiveSession }
            // UI blocks confirm; if confirm is invoked, domain refuses.
            viewModel.confirmDelete()
            val after = awaitItemUntil { it.userMessage != null || it.pendingDeleteProgramId == null }
            assertTrue(programRepo.getProgramById(active.id) != null)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<com.stratum.feature.program.ui.ProgramLibraryUiState>.awaitItemUntil(
        predicate: (com.stratum.feature.program.ui.ProgramLibraryUiState) -> Boolean,
    ): com.stratum.feature.program.ui.ProgramLibraryUiState {
        var item = awaitItem()
        while (!predicate(item)) {
            item = awaitItem()
        }
        return item
    }

    private fun buildViewModel(
        programRepo: FakeProgramRepository,
        trainingLog: FakeTrainingLogRepository = FakeTrainingLogRepository(),
    ): ProgramLibraryViewModel {
        val liftRepo =
            FakeLiftRepository(
                initialLifts =
                    listOf(
                        Lift(
                            id = LiftId("bench-press"),
                            name = "Barbell Bench Press",
                            movementPatterns = setOf(MovementPattern.HorizontalPress),
                            primaryMuscles = setOf(MuscleGroup.Chest),
                            secondaryMuscles = setOf(MuscleGroup.Tricep),
                            incrementTiers = emptyList(),
                            presetTags = emptySet(),
                            enabled = true,
                        ),
                    ),
            )
        return ProgramLibraryViewModel(
            observeProgram = ObserveProgramUseCase(programRepo),
            observeLibraryPrograms = ObserveLibraryProgramsUseCase(programRepo),
            observeLiftCatalog = ObserveLiftCatalogUseCase(liftRepo),
            observeActiveSession = ObserveActiveSessionUseCase(trainingLog),
            activateStoredProgram = ActivateStoredProgramUseCase(programRepo, FakeLoggingPort()),
            deleteProgram =
                DeleteProgramUseCase(
                    programRepository = programRepo,
                    trainingLogRepository = trainingLog,
                    loggingPort = FakeLoggingPort(),
                ),
        )
    }

    private fun program(
        id: String,
        name: String,
        isActive: Boolean,
        weekdays: Set<Weekday> = setOf(Weekday.Monday, Weekday.Wednesday),
    ): Program {
        val programId = ProgramId(id)
        val workouts =
            weekdays.sortedBy { weekday -> Weekday.entries.indexOf(weekday) }.mapIndexed { index, weekday ->
                Workout(
                    id = WorkoutId("$id-w-$index"),
                    programId = programId,
                    name = "Workout ${'A' + index}",
                    weekday = weekday,
                    position = index,
                    workoutLifts =
                        listOf(
                            WorkoutLift(
                                id = WorkoutLiftId("$id-wl-$index"),
                                workoutId = WorkoutId("$id-w-$index"),
                                liftId = LiftId("bench-press"),
                                progressionScheme = ProgressionScheme.Linear,
                                position = 0,
                                sets =
                                    listOf(
                                        LiftSet(
                                            id = LiftSetId("$id-ls-$index"),
                                            workoutLiftId = WorkoutLiftId("$id-wl-$index"),
                                            position = 0,
                                            setType = SetType.Standard,
                                            repMin = 8,
                                            repMax = 12,
                                            rirGoal = 2,
                                            isTopSet = true,
                                        ),
                                    ),
                            ),
                        ),
                )
            }
        val trainingOrdinals = weekdays.map { weekday -> weekday.ordinal }.toSet()
        val restSlotIndices = (0 until 7).filterNot { index -> index in trainingOrdinals }.toSet()
        return Program(
            id = programId,
            name = name,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            cycleLength = 7,
            trainingWeekdays = weekdays,
            workouts = workouts,
            isActive = isActive,
            restSlotIndices = restSlotIndices,
        )
    }
}
