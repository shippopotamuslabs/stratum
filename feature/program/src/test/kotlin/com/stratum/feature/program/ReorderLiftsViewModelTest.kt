package com.stratum.feature.program

import app.cash.turbine.test
import com.stratum.application.workout.ConfirmReorderWorkoutLiftsUseCase
import com.stratum.domain.workout.EditSessionEvent
import com.stratum.domain.workout.ReorderLiftItem
import com.stratum.domain.workout.WorkoutEditSession
import com.stratum.feature.program.ui.ReorderLiftsViewModel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ReorderLiftsViewModelTest {

    private lateinit var session: WorkoutEditSession
    private lateinit var viewModel: ReorderLiftsViewModel

    @BeforeEach
    fun setUp() {
        session = WorkoutEditSession()
        session.setReorderLifts(
            listOf(ReorderLiftItem("wl-1", "Squat"), ReorderLiftItem("wl-2", "Bench")),
        )
        viewModel = ReorderLiftsViewModel(ConfirmReorderWorkoutLiftsUseCase(session))
    }

    @Test
    @DisplayName("Given staged lifts when initialize then uiState holds lifts and isLoaded")
    fun initialize_loadsLifts() {
        viewModel.initialize()

        val state = viewModel.uiState.value
        assertTrue(state.isLoaded)
        assertEquals(listOf("wl-1", "wl-2"), state.lifts.map { it.workoutLiftId })
        assertEquals(listOf("Squat", "Bench"), state.lifts.map { it.name })
    }

    @Test
    @DisplayName("Given loaded lifts when reorder then order changes")
    fun reorder_movesItem() {
        viewModel.initialize()

        viewModel.reorderLifts(fromIndex = 0, toIndex = 1)

        assertEquals(listOf("wl-2", "wl-1"), viewModel.uiState.value.lifts.map { it.workoutLiftId })
    }

    @Test
    @DisplayName("Given out-of-bounds indices when reorder then order unchanged")
    fun reorder_outOfBoundsNoOp() {
        viewModel.initialize()

        viewModel.reorderLifts(fromIndex = -1, toIndex = 5)

        assertEquals(listOf("wl-1", "wl-2"), viewModel.uiState.value.lifts.map { it.workoutLiftId })
    }

    @Test
    @DisplayName("Given reordered lifts when confirm then session emits the new order")
    fun confirm_emitsReorderedOrder() = runTest {
        session.events.test {
            viewModel.initialize()
            viewModel.reorderLifts(fromIndex = 0, toIndex = 1)

            viewModel.confirmReorder("w-1")

            assertEquals(
                EditSessionEvent.LiftsReordered("w-1", listOf("wl-2", "wl-1")),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given not initialized when confirm then nothing is emitted")
    fun confirm_beforeInitializeNoOp() = runTest {
        session.events.test {
            viewModel.confirmReorder("w-1")

            expectNoEvents()
        }
        assertFalse(viewModel.uiState.value.isLoaded)
    }
}
