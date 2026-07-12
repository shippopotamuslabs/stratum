package com.stratum.feature.program

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class CreateProgramDayCapTest {
    @Test
    @DisplayName("canAddDay is true below the week cap and false at 7 days")
    fun canAddDayRespectsWeekCap() {
        val underCap =
            CreateProgramUiState(
                days =
                    List(6) { index ->
                        CreateProgramDayUi(
                            id = "d-$index",
                            name = "Day $index",
                            position = index,
                            isRest = true,
                            editState = null,
                        )
                    },
            )
        val atCap =
            underCap.copy(
                days =
                    underCap.days +
                        CreateProgramDayUi(
                            id = "d-6",
                            name = "Day 6",
                            position = 6,
                            isRest = true,
                            editState = null,
                        ),
            )

        assertTrue(underCap.canAddDay)
        assertFalse(atCap.canAddDay)
        assertEquals(MAX_CREATE_PROGRAM_DAYS, atCap.days.size)
    }
}
