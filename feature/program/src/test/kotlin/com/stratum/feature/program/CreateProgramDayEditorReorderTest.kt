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
