package com.stratum.feature.settings

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.domain.export.ExportProgramsUseCase
import com.stratum.domain.export.PreviewExportProgramsUseCase
import com.stratum.domain.lift.identity.LiftId
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
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class ExportProgramsViewModelTest {

    private val clock: () -> Instant = { Instant.parse("2026-07-16T15:00:00Z") }

    private fun program(id: String, name: String, isActive: Boolean): Program {
        val programId = ProgramId(id)
        val workoutId = WorkoutId("$id-w")
        val wlId = WorkoutLiftId("$id-wl")
        return Program(
            id = programId,
            name = name,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            cycleLength = 4,
            workouts =
                listOf(
                    Workout(
                        id = workoutId,
                        programId = programId,
                        name = "Day A",
                        weekday = null,
                        position = 0,
                        workoutLifts =
                            listOf(
                                WorkoutLift(
                                    id = wlId,
                                    workoutId = workoutId,
                                    liftId = LiftId("lift-1"),
                                    progressionScheme = ProgressionScheme.Double,
                                    position = 0,
                                    sets =
                                        listOf(
                                            LiftSet(
                                                id = LiftSetId("$id-s0"),
                                                workoutLiftId = wlId,
                                                position = 0,
                                                setType = SetType.Standard,
                                                repMin = 5,
                                                repMax = 8,
                                                rirGoal = 2,
                                                isTopSet = true,
                                            ),
                                        ),
                                ),
                            ),
                    ),
                ),
            isActive = isActive,
        )
    }

    private suspend fun buildVm(vararg programs: Program): ExportProgramsViewModel {
        val repo = FakeProgramRepository()
        programs.forEach { p ->
            if (p.isActive) repo.saveProgram(p) else repo.saveToLibrary(p)
        }
        return ExportProgramsViewModel(
            previewPrograms = PreviewExportProgramsUseCase(repo),
            exportPrograms =
                ExportProgramsUseCase(
                    programRepository = repo,
                    liftRepository = FakeLiftRepository(),
                    clock = clock,
                ),
        )
    }

    @Test
    @DisplayName("IE-S6-005 Toggle program selects id")
    fun ieS6_005_toggleSelect() = runTest {
        val vm = buildVm(program("prog-1", "Alpha", isActive = true))
        advanceUntilIdle()
        vm.onToggleProgram("prog-1")
        advanceUntilIdle()
        assertEquals(setOf("prog-1"), vm.uiState.value.selectedIds)
    }

    @Test
    @DisplayName("IE-S6-006 Select all selects every program")
    fun ieS6_006_selectAll() = runTest {
        val vm =
            buildVm(
                program("prog-1", "A", isActive = true),
                program("prog-2", "B", isActive = false),
            )
        advanceUntilIdle()
        vm.onSelectAll()
        advanceUntilIdle()
        assertEquals(setOf("prog-1", "prog-2"), vm.uiState.value.selectedIds)
    }

    @Test
    @DisplayName("IE-S6-007 Export programs emits share event")
    fun ieS6_007_shareEvent() = runTest {
        val vm = buildVm(program("prog-active", "Active", isActive = true))
        advanceUntilIdle()
        vm.onToggleProgram("prog-active")
        advanceUntilIdle()
        vm.shareEvents.test {
            vm.onExportClick()
            advanceUntilIdle()
            val event = awaitItem()
            assertEquals("application/json", event.mimeType)
            assertTrue(event.fileName.startsWith("stratum-programs-"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("IE-S6-008 Export programs with no selection shows error")
    fun ieS6_008_emptySelectionError() = runTest {
        val vm = buildVm(program("prog-active", "Active", isActive = true))
        advanceUntilIdle()
        vm.shareEvents.test {
            vm.onExportClick()
            advanceUntilIdle()
            assertEquals("Select at least one program to export.", vm.uiState.value.errorMessage)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("IE-S6-M001 Mutation defense select-all must select all listed ids")
    fun ieS6_m001_selectAllThree() = runTest {
        val vm =
            buildVm(
                program("a", "A", isActive = true),
                program("b", "B", isActive = false),
                program("c", "C", isActive = false),
            )
        advanceUntilIdle()
        vm.onSelectAll()
        advanceUntilIdle()
        assertEquals(3, vm.uiState.value.selectedIds.size)
        assertTrue(vm.uiState.value.selectedIds.containsAll(listOf("a", "b", "c")))
    }
}
