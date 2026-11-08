package com.stratum.application.workout

import app.cash.turbine.test
import com.stratum.domain.workout.EditSessionEvent
import com.stratum.domain.workout.ReorderLiftItem
import com.stratum.domain.workout.WorkoutEditSession
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ConfirmReorderWorkoutLiftsUseCaseTest {

    @Test
    @DisplayName("load returns the staged lifts in order")
    fun load_returnsStagedLifts() {
        val session = WorkoutEditSession()
        session.setReorderLifts(
            listOf(ReorderLiftItem("wl-1", "Squat"), ReorderLiftItem("wl-2", "Bench")),
        )

        val useCase = ConfirmReorderWorkoutLiftsUseCase(session)

        assertEquals(
            listOf(ReorderLiftItem("wl-1", "Squat"), ReorderLiftItem("wl-2", "Bench")),
            useCase.loadReorderLifts(),
        )
    }

    @Test
    @DisplayName("load with nothing staged returns empty")
    fun load_emptyWhenNothingStaged() {
        val useCase = ConfirmReorderWorkoutLiftsUseCase(WorkoutEditSession())

        assertTrue(useCase.loadReorderLifts().isEmpty())
    }

    @Test
    @DisplayName("invoke emits LiftsReordered with the confirmed order")
    fun invoke_emitsLiftsReordered() = runTest {
        val session = WorkoutEditSession()
        val useCase = ConfirmReorderWorkoutLiftsUseCase(session)

        session.events.test {
            useCase("w-1", listOf("wl-2", "wl-1"))

            assertEquals(
                EditSessionEvent.LiftsReordered("w-1", listOf("wl-2", "wl-1")),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }
}
