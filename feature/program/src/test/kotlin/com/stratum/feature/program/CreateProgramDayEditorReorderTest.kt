package com.stratum.feature.program

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class CreateProgramDayEditorReorderTest {

    @Test
    @DisplayName("reorderDaysByIds reorders by stable id and marks dirty")
    fun reorderDaysByIds_appliesOrder() {
        val state =
            CreateProgramUiState(
                programId = "p",
                days =
                    listOf(
                        day("a", "A", 0),
                        day("r", "Rest", 1, isRest = true),
                        day("b", "B", 2),
                    ),
                selectedDayId = "a",
            )
        val result =
            CreateProgramDayEditor.reorderDaysByIds(
                state,
                orderedDayIds = listOf("b", "r", "a"),
            )
        assertEquals(listOf("b", "r", "a"), result.days.map { it.id })
        assertEquals(listOf(0, 1, 2), result.days.map { it.position })
        assertTrue(result.isDirty)
    }

    @Test
    @DisplayName("reorderDaysByIds no-ops when id set mismatches")
    fun reorderDaysByIds_mismatchNoOp() {
        val state =
            CreateProgramUiState(
                programId = "p",
                days = listOf(day("a", "A", 0), day("b", "B", 1)),
            )
        val result = CreateProgramDayEditor.reorderDaysByIds(state, listOf("a"))
        assertEquals(state, result)
    }

    @Test
    @DisplayName("reorderDaysByIds no-ops without dirty when order unchanged")
    fun reorderDaysByIds_sameOrderNoDirty() {
        val state =
            CreateProgramUiState(
                programId = "p",
                days = listOf(day("a", "A", 0), day("b", "B", 1)),
                isDirty = false,
            )
        val result = CreateProgramDayEditor.reorderDaysByIds(state, listOf("a", "b"))
        assertEquals(state, result)
        assertFalse(result.isDirty)
    }

    @Test
    @DisplayName("removeSelectedDay drops the selected day and selects the first remaining")
    fun removeSelectedDay_selectsFirstRemaining() {
        val state =
            CreateProgramUiState(
                days = listOf(day("a", "A", 0), day("b", "B", 1), day("c", "C", 2)),
                selectedDayId = "b",
                isDirty = false,
            )

        val result = CreateProgramDayEditor.removeSelectedDay(state)

        assertEquals(listOf("a", "c"), result.days.map { it.id })
        assertEquals(listOf(0, 1), result.days.map { it.position })
        assertEquals("a", result.selectedDayId)
        assertTrue(result.isDirty)
        assertEquals(result, CreateProgramDayEditor.removeDay(state, state.selectedDayId))
    }

    @Test
    @DisplayName("removeDay removes the captured id when selection moved during the wait")
    fun removeDay_keepsSelectionThatMovedOffTheCapturedId() {
        val state =
            CreateProgramUiState(
                days = listOf(day("a", "A", 0), day("b", "B", 1), day("c", "C", 2)),
                selectedDayId = "c",
                isDirty = false,
            )

        val result = CreateProgramDayEditor.removeDay(state, "a")

        assertEquals(listOf("b", "c"), result.days.map { it.id })
        assertEquals(listOf(0, 1), result.days.map { it.position })
        assertEquals("c", result.selectedDayId)
        assertTrue(result.isDirty)
    }

    @Test
    @DisplayName("removeSelectedDay keeps the only remaining day")
    fun removeSelectedDay_singleDayNoOp() {
        val state =
            CreateProgramUiState(
                days = listOf(day("a", "A", 0)),
                selectedDayId = "a",
                isDirty = false,
            )

        assertEquals(state, CreateProgramDayEditor.removeSelectedDay(state))
        assertEquals(state, CreateProgramDayEditor.removeDay(state, "a"))
    }

    private fun day(
        id: String,
        name: String,
        position: Int,
        isRest: Boolean = false,
    ): CreateProgramDayUi =
        CreateProgramDayUi(
            id = id,
            name = name,
            position = position,
            isRest = isRest,
            editState = null,
        )
}
