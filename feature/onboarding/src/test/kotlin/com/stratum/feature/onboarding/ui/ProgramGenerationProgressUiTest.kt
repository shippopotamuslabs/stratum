package com.stratum.feature.onboarding.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ProgramGenerationProgressUiTest {
    @Test
    @DisplayName("Given technical generation detail when sanitized then returns null")
    fun `technical detail is hidden`() {
        assertNull(userFacingGenerationDetail("validate_program_metrics failed"))
    }

    @Test
    @DisplayName("Given plain generation detail when sanitized then keeps text")
    fun `plain detail is kept`() {
        assertEquals(
            "Balancing leg volume across your four training days",
            userFacingGenerationDetail("Balancing leg volume across your four training days"),
        )
    }

    @Test
    @DisplayName("Given detail matches phase label when deduped then subtitle is null")
    fun `duplicate detail is hidden`() {
        assertNull(
            distinctGenerationDetail(
                "Adjusting volume and exercise order…",
                "Adjusting volume and exercise order…",
            ),
        )
    }

    @Test
    @DisplayName("Given model detail differs from phase label when deduped then subtitle is kept")
    fun `distinct detail is kept`() {
        assertEquals(
            "Balancing leg volume across your four training days",
            distinctGenerationDetail(
                "Balancing leg volume across your four training days",
                "Checking your draft program…",
            ),
        )
    }
}
