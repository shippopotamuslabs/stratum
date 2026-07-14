package com.stratum.domain.program

import com.stratum.core.testing.program.scenarioContext
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.program.generation.build.BuildProgramResult
import com.stratum.domain.program.generation.build.BuiltProgram
import com.stratum.domain.program.generation.prescription.SynthesisObjectiveBreakdown
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ProgramGenerationTelemetryLoggerTest {
    @Test
    @DisplayName("Given synthesis result when logged then info includes unified flag and objective")
    fun `logs unified synthesis objective breakdown`() {
        val loggingPort = RecordingLoggingPort()
        val logger = ProgramGenerationTelemetryLogger(loggingPort)
        val context = scenarioContext(days = 4, specialization = mapOf("Chest" to "Specialize"))
        val objective =
            SynthesisObjectiveBreakdown(
                total = 42L,
                recoveryPenalty = 1L,
                specializedMevShortfall = 2L,
                coSpecializationImbalance = 0L,
                specializeAimDeviation = 3L,
                maintenanceAimDeviation = 0L,
                minimumVolumeAimDeviation = 0L,
                roleMinimumShortfall = 0L,
                pushPullImbalance = 0L,
                sessionFillShortfall = 0L,
            )
        logger.logSynthesisComplete(
            context = context,
            result =
                BuildProgramResult(
                    program = BuiltProgram(name = "Test", cycleLength = 4, workouts = emptyList()),
                    selectionsBySession = emptyList(),
                    cycleSlotIndexByPosition = listOf(0, 1, 3, 4),
                    synthesisObjective = objective,
                ),
        )

        assertTrue(
            loggingPort.infoMessages.any { message ->
                message.contains("programSynthesis") &&
                    message.contains("unified=true") &&
                    message.contains("objectiveTotal=42")
            },
        )
    }

    private class RecordingLoggingPort : LoggingPort {
        val infoMessages = mutableListOf<String>()

        override fun debug(tag: String, message: String) = Unit

        override fun info(tag: String, message: String) {
            infoMessages += message
        }

        override fun warning(tag: String, message: String) = Unit

        override fun error(tag: String, message: String, throwable: Throwable?) = Unit
    }
}
