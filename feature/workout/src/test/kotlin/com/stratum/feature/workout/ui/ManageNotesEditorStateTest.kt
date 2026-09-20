package com.stratum.feature.workout.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Manage notes editor state")
class ManageNotesEditorStateTest {

    @Test
    @DisplayName("clean drafts are not dirty")
    fun cleanNotDirty() {
        val loaded = listOf(NoteDraftUi(id = "a", body = "one"), NoteDraftUi(id = "b", body = "two"))

        assertFalse(noteDraftsDirty(loaded, editableFromLoaded(loaded)))
    }

    @Test
    @DisplayName("edited body is dirty")
    fun editedBodyDirty() {
        val loaded = listOf(NoteDraftUi(id = "a", body = "one"))

        assertTrue(noteDraftsDirty(loaded, listOf(EditableNote(key = "a", id = "a", body = "uno"))))
    }

    @Test
    @DisplayName("added and removed rows are dirty")
    fun addedRemovedDirty() {
        val loaded = listOf(NoteDraftUi(id = "a", body = "one"))

        assertTrue(noteDraftsDirty(loaded, emptyList()))
        assertTrue(
            noteDraftsDirty(
                loaded,
                listOf(
                    EditableNote(key = "a", id = "a", body = "one"),
                    EditableNote(key = "k", body = "new"),
                ),
            ),
        )
    }

    @Test
    @DisplayName("blank drafts do not dirty a clean tab")
    fun blanksIgnored() {
        val loaded = listOf(NoteDraftUi(id = "a", body = "one"))

        assertFalse(
            noteDraftsDirty(
                loaded,
                listOf(
                    EditableNote(key = "a", id = "a", body = "one"),
                    EditableNote(key = "k", body = "   "),
                ),
            ),
        )
    }

    @Test
    @DisplayName("reordered rows are dirty")
    fun reorderDirty() {
        val loaded =
            listOf(NoteDraftUi(id = "a", body = "one"), NoteDraftUi(id = "b", body = "two"))

        assertTrue(
            noteDraftsDirty(
                loaded,
                listOf(
                    EditableNote(key = "b", id = "b", body = "two"),
                    EditableNote(key = "a", id = "a", body = "one"),
                ),
            ),
        )
    }

    @Test
    @DisplayName("loaded drafts keep stable keys and ids")
    fun editableMapping() {
        val loaded = listOf(NoteDraftUi(id = "a", body = "one"))

        assertEquals(
            listOf(EditableNote(key = "a", id = "a", body = "one")),
            editableFromLoaded(loaded),
        )
    }

    @Test
    @DisplayName("save request is dirty when any tab is present")
    fun requestDirty() {
        assertFalse(LiftNoteListsSaveRequest(workoutLiftId = "wl", liftId = "l").isDirty)
        assertTrue(
            LiftNoteListsSaveRequest(
                workoutLiftId = "wl",
                liftId = "l",
                liftNotes = emptyList(),
            ).isDirty,
        )
    }
}
