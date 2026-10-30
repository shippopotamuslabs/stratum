package com.stratum.domain.telemetry

import com.stratum.domain.program.generation.synthesis.ProgramSynthesisException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("classifyGenerateOutcome")
class GenerateOutcomeClassificationTest {
    @Test
    @DisplayName("synthesis failure maps to the expected-failure outcome fact")
    fun synthesisFailureIsExpected() {
        val outcome = classifyGenerateOutcome(ProgramSynthesisException("quota exhausted"))

        assertEquals(
            OutcomeTelemetry(
                useCase = OutcomeTelemetry.USE_CASE_PROGRAM_GENERATION,
                result = OutcomeResult.ExpectedFailure(OutcomeTelemetry.REASON_SYNTHESIS_FAILED),
            ),
            outcome,
        )
    }

    @Test
    @DisplayName("anything else has no outcome home")
    fun otherFailuresAreExceptional() {
        assertNull(classifyGenerateOutcome(IllegalStateException("boom")))
    }
}
