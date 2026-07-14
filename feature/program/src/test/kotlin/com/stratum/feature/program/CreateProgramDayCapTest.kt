package com.stratum.feature.program

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class CreateProgramDayCapTest {
    @Test
    @DisplayName("canAddDay is always true — cycle slots are uncapped")
    fun canAddDayUncapped() {
        val manyDays =
            CreateProgramUiState(
                days =
                    List(12) { index ->
                        CreateProgramDayUi(
                            id = "d-$index",
                            name = "Day $index",
                            position = index,
                            isRest = true,
                            editState = null,
                        )
                    },
            )
        assertTrue(manyDays.canAddDay)
    }
}
