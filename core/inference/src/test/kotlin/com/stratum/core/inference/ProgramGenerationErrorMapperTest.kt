package com.stratum.core.inference

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ProgramGenerationErrorMapperTest {
    @Test
    @DisplayName("wrapped App attestation text maps to App Check guidance")
    fun appAttestationMessageMapsToAppCheck() {
        val message = ProgramGenerationErrorMapper.userMessage(
            IllegalStateException("App attestation failed."),
        )
        assertTrue(message.contains("App Check", ignoreCase = true))
    }
}
