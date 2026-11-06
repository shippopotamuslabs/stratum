package com.stratum.core.infrastructure.telemetry

import com.stratum.domain.program.generation.GenerateProgramFailureKind
import com.stratum.domain.program.generation.GenerateProgramResult
import com.stratum.domain.program.generation.synthesis.ProgramSynthesisRefusal
import com.stratum.domain.program.generation.validation.ValidationReport
import com.stratum.domain.program.generation.validation.ValidationSummary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("classifyGenerateOutcome")
class GenerateOutcomeClassificationTest {
    private val expectedOutcome =
        OutcomeTelemetry(
            useCase = OutcomeTelemetry.USE_CASE_PROGRAM_GENERATION,
            result = OutcomeResult.ExpectedFailure(OutcomeTelemetry.REASON_SYNTHESIS_FAILED),
        )

    @Test
    @DisplayName("synthesis failed branch maps to the expected-failure outcome fact")
    fun synthesisFailedBranchIsExpected() {
        val outcome = classifyGenerateOutcome(GenerateProgramResult.SynthesisFailed("quota exhausted"))

        assertEquals(expectedOutcome, outcome)
    }

    @Test
    @DisplayName("synthesis refused branch maps to the expected-failure outcome fact")
    fun synthesisRefusedBranchIsExpected() {
        val outcome =
            classifyGenerateOutcome(
                GenerateProgramResult.SynthesisRefused(ProgramSynthesisRefusal.FeatureDisabled),
            )

        assertEquals(expectedOutcome, outcome)
    }

    @Test
    @DisplayName("validation rejected branch has no outcome home")
    fun validationRejectedIsExceptional() {
        val report =
            ValidationReport(
                pass = false,
                issues = emptyList(),
                summary =
                    ValidationSummary(
                        weeklyMuscleVolume = emptyMap(),
                        workoutHardSets = emptyList(),
                        estimatedWorkoutMinutes = emptyList(),
                        muscleWeeklyFrequency = emptyMap(),
                        weeklyPushSets = 0.0,
                        weeklyPullSets = 0.0,
                    ),
            )

        assertNull(classifyGenerateOutcome(GenerateProgramResult.ValidationRejected(report)))
    }

    @Test
    @DisplayName("unexpected branch has no outcome home")
    fun unexpectedIsExceptional() {
        assertNull(
            classifyGenerateOutcome(
                GenerateProgramResult.Unexpected(
                    GenerateProgramFailureKind.OperationalFailure,
                    diagnostics = emptyList(),
                ),
            ),
        )
    }
}
