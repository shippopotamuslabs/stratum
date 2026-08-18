package com.stratum.feature.workout

import com.stratum.domain.program.identity.WorkoutId
import com.stratum.feature.workout.ui.WorkoutUiState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LiveWorkoutSessionIsolationTest {
    @Test
    fun `start apply and complete commands are terminal`() {
        assertTrue(sessionCommandFailureIsTerminal(SessionCommand.StartSession(WorkoutId("w-1"))))
        assertTrue(
            sessionCommandFailureIsTerminal(
                SessionCommand.ApplyCycleSnapshot(
                    routeWorkoutId = WorkoutId("w-1"),
                    program = null,
                    session = null,
                    workout = null,
                    catalog = emptyList(),
                ),
            ),
        )
        assertTrue(sessionCommandFailureIsTerminal(SessionCommand.CompleteWorkout(saveTemplate = false)))
        assertFalse(sessionCommandFailureIsTerminal(SessionCommand.Undo))
        assertFalse(sessionCommandFailureIsTerminal(SessionCommand.RenderOnly))
        assertFalse(sessionCommandFailureIsTerminal(SessionCommand.BeginAbandon))
    }

    @Test
    fun `unrecoverable copy is presentation-safe`() {
        val state = WorkoutUiState.Error(MSG_SESSION_UNRECOVERABLE)
        assertEquals(MSG_SESSION_UNRECOVERABLE, state.message)
    }
}
