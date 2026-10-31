package com.stratum.domain.program

import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.program.generation.build.BuildProgramResult
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.prescription.SynthesisObjectiveBreakdown
import com.stratum.domain.program.generation.slots.SlotGenerationDiagnostic
import javax.inject.Inject

/**
 * Structured info-level telemetry for on-device program synthesis. Uses [LoggingPort]
 * so adapters can fan out to Logcat, Crashlytics, or OpenTelemetry without pulling
 * Android into `:core:domain`.
 */
class ProgramGenerationTelemetryLogger @Inject constructor(
    private val loggingPort: LoggingPort,
) {
    fun logSynthesisComplete(
        context: ProgramGenerationContext,
        result: BuildProgramResult,
    ) {
        loggingPort.info(
            TAG,
            buildString {
                append("programSynthesis ")
                append("days=${context.trainingDaysPerWeek} ")
                append("cycleLength=${result.program.cycleLength} ")
                append("unified=true ")
                appendObjective("objective", result.synthesisObjective)
            },
        )
    }

    private fun StringBuilder.appendObjective(
        label: String,
        objective: SynthesisObjectiveBreakdown,
    ) {
        append("${label}Total=${objective.total} ")
        append("${label}Recovery=${objective.recoveryPenalty} ")
        append("${label}Mev=${objective.specializedMevShortfall} ")
        append("${label}CoSpec=${objective.coSpecializationImbalance} ")
        append("${label}SpecAim=${objective.specializeAimDeviation} ")
        append("${label}MaintAim=${objective.maintenanceAimDeviation} ")
        append("${label}MinVolAim=${objective.minimumVolumeAimDeviation} ")
        append("${label}Role=${objective.roleMinimumShortfall} ")
        append("${label}PushPull=${objective.pushPullImbalance} ")
        append("${label}SessionFill=${objective.sessionFillShortfall}")
    }

    /**
     * STR-254: reports each slot-build diagnostic exactly once at warning level through
     * the pipeline's existing logging wiring. Diagnostics are log-only — never surfaced
     * in the UI.
     */
    fun logGenerationDiagnostics(diagnostics: List<SlotGenerationDiagnostic>) {
        for (diagnostic in diagnostics) {
            loggingPort.warning(TAG, diagnostic.describe())
        }
    }

    private fun SlotGenerationDiagnostic.describe(): String =
        when (this) {
            is SlotGenerationDiagnostic.CrossDomainTouchSkipped ->
                "Cross-domain touch skipped: pattern=\"$patternName\" is not a known " +
                    "MovementPattern — touch slot for muscle=$muscle omitted."
            is SlotGenerationDiagnostic.SpecFocusSlotSkipped ->
                "Spec-focus slot skipped: pattern=\"$patternName\" is not a known " +
                    "MovementPattern — isolation slot for muscle=$muscle dropped."
            is SlotGenerationDiagnostic.SpecFocusMuscleUnrecognized ->
                "Spec-focus lift excluded: muscle=\"$muscleName\" is not a known " +
                    "MuscleGroup — every lift rejected for pattern=\"$patternName\"."
        }

    private companion object {
        const val TAG = "StratumProgramGeneration"
    }
}
