package com.stratum.feature.workout.notification

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
class RestTimerControllerTest {

    private var nowMs = 1_000_000L
    private lateinit var controller: RestTimerController

    @BeforeEach
    fun setUp() {
        nowMs = 1_000_000L
        controller = RestTimerController(clock = EpochMilliClock { nowMs })
    }

    @Test
    @DisplayName("Given idle when start then state is Running with wall-clock end")
    fun startSetsRunningState() = runTest {
        controller.state.test {
            assertEquals(RestTimerState.Idle, awaitItem())

            controller.start(90, workoutId = "w1", workoutName = "Push")

            val running = awaitItem() as RestTimerState.Running
            assertEquals(90, running.totalSeconds)
            assertEquals(nowMs + 90_000L, running.endsAtEpochMs)
            assertEquals("w1", running.workoutId)
            assertEquals("Push", running.workoutName)
            assertEquals(90, controller.remainingSeconds())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given running when time advances then remaining derives from wall clock")
    fun remainingDerivesFromClock() {
        controller.start(60, "w1", "Push")
        nowMs += 15_000L
        assertEquals(45, controller.remainingSeconds())
    }

    @Test
    @DisplayName("Given running when add 10s then remaining and total expand")
    fun add10SecondsExpandsRemaining() {
        controller.start(30, "w1", "Push")
        controller.add10Seconds()
        val running = controller.state.value as RestTimerState.Running
        assertEquals(40, controller.remainingSeconds())
        assertEquals(40, running.totalSeconds)
    }

    @Test
    @DisplayName("Given running when subtract 10s below zero then becomes Idle")
    fun subtractToZeroIdles() {
        controller.start(5, "w1", "Push")
        controller.subtract10Seconds()
        assertEquals(RestTimerState.Idle, controller.state.value)
        assertFalse(controller.isRunning())
    }

    @Test
    @DisplayName("Given max rest when add 10s then remaining clamps at MAX")
    fun addClampsAtMax() {
        controller.start(RestTimerDefaults.MAX_REST_SECONDS, "w1", "Push")
        controller.add10Seconds()
        assertEquals(RestTimerDefaults.MAX_REST_SECONDS, controller.remainingSeconds())
    }

    @Test
    @DisplayName("Given running when skip then Idle")
    fun skipIdles() {
        controller.start(90, "w1", "Push")
        controller.skip()
        assertEquals(RestTimerState.Idle, controller.state.value)
    }

    @Test
    @DisplayName("Given due timer when completeIfDue then transitions to Idle once")
    fun completeIfDue() {
        controller.start(10, "w1", "Push")
        nowMs += 10_000L
        assertTrue(controller.completeIfDue())
        assertEquals(RestTimerState.Idle, controller.state.value)
        assertFalse(controller.completeIfDue())
    }

    @Test
    @DisplayName("Given not due when completeIfDue then stays Running")
    fun completeIfDueNotYet() {
        controller.start(10, "w1", "Push")
        nowMs += 5_000L
        assertFalse(controller.completeIfDue())
        assertTrue(controller.isRunning())
    }

    @Test
    @DisplayName("Given non-positive seconds when start then remains Idle")
    fun startIgnoresNonPositive() {
        controller.start(0, "w1", "Push")
        assertEquals(RestTimerState.Idle, controller.state.value)
    }
}
