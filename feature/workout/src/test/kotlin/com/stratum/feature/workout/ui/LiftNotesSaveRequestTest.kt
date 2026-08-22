package com.stratum.feature.workout.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("LiftNotesSaveRequest dirty tabs")
class LiftNotesSaveRequestTest {

    @Test
    @DisplayName("unchanged drafts are not dirty")
    fun unchangedIsClean() {
        val request = request(sessionDraft = "s", workoutDraft = "w", liftDraft = "l")
        assertFalse(request.isDirty)
        assertEquals(listOf(false, false, false), request.tabModified)
    }

    @Test
    @DisplayName("session draft change marks only the session tab")
    fun sessionDraftMarksSessionTab() {
        val request = request(sessionDraft = "typed")
        assertTrue(request.sessionModified)
        assertFalse(request.workoutModified)
        assertFalse(request.liftModified)
        assertTrue(request.isDirty)
        assertEquals(listOf(true, false, false), request.tabModified)
    }

    @Test
    @DisplayName("workout and lift draft changes mark those tabs")
    fun pinDraftsMarkPinTabs() {
        val request = request(workoutDraft = "w2", liftDraft = "l2")
        assertFalse(request.sessionModified)
        assertTrue(request.workoutModified)
        assertTrue(request.liftModified)
        assertEquals(listOf(false, true, true), request.tabModified)
    }

    private fun request(
        sessionDraft: String = "s",
        workoutDraft: String = "w",
        liftDraft: String = "l",
    ) = LiftNotesSaveRequest(
        workoutLiftId = "wl-1",
        liftId = "lift-1",
        sessionDraft = sessionDraft,
        workoutDraft = workoutDraft,
        liftDraft = liftDraft,
        loadedSession = "s",
        loadedWorkout = "w",
        loadedLift = "l",
    )
}
