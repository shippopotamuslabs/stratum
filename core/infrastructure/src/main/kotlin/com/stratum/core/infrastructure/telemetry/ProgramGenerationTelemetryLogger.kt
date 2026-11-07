package com.stratum.core.infrastructure.telemetry

import com.stratum.core.infrastructure.logging.LoggingPort
import com.stratum.domain.program.generation.GenerationSynthesisSummary
import com.stratum.domain.program.generation.prescription.SynthesisObjectiveBreakdown
import com.stratum.domain.program.generation.slots.SlotGenerationDiagnostic
import javax.inject.Inject

/**
 * Structured info-level telemetry for on-device program synthesis. Uses [LoggingPort]
 * so adapters can fan out to Logcat, Crashlytics, or OpenTelemetry.
 *
 * Infrastructure-resident (STR-237 chunk 3): domain and application have no
 * logging capability, so the generation pipeline no longer records through
 * this logger mid-run. The synthesis facts ride the generation outcome as a
 * [GenerationSynthesisSummary] and the outcome boundary (program-generation
 * controller / onboarding) invokes this logger exactly once per generation.
 */
class ProgramGenerationTelemetryLogger @Inject constructor(
    private val loggingPort: LoggingPort,
) {
    fun logSynthesisComplete(summary: GenerationSynthesisSummary) {
        loggingPort.info(
            TAG,
            buildString {
                append("programSynthesis ")
                append("days=${summary.trainingDaysPerWeek} ")
                append("cycleLength=${summary.cycleLength} ")
                append("unified=true ")
                appendObjective("objective", summary.objective)
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
     * STR-254: reports each slot-build diagnostic exactly once at warning level.
     * Diagnostics are log-only — never surfaced in the UI.
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
            is SlotGenerationDiagnostic.SlotDiagnosticUndecodable ->
                "Slot diagnostic undecodable: entry=$entry could not be decoded — " +
                    "that diagnostic entry was dropped from the decoded diagnostics."
        }

    private companion object {
        const val TAG = "StratumProgramGeneration"
    }
}
