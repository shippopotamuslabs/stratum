package com.stratum.domain.program.generation

import com.stratum.domain.program.generation.prescription.SynthesisObjectiveBreakdown
import com.stratum.domain.program.generation.slots.SlotGenerationDiagnostic

/**
 * Facts of a completed synthesis build, carried on the generation outcomes
 * (STR-237 chunk 3) so the outcome boundary can record them exactly once.
 *
 * Domain and application have no logging capability: the pipeline used to
 * record the synthesis-complete info record and the slot-build diagnostics
 * itself, mid-run. The facts now ride the outcome — [diagnostics] are the
 * §10 slot-build diagnostic values the build already produces — and the
 * consuming boundary (program-generation controller / onboarding) records
 * them through the infrastructure-resident generation logger, preserving
 * the legacy tag, messages, and exactly-once count, including on the
 * validation-rejected branch (the build completed before validation ran).
 */
data class GenerationSynthesisSummary(
    val trainingDaysPerWeek: Int,
    val cycleLength: Int,
    val objective: SynthesisObjectiveBreakdown,
    val diagnostics: List<SlotGenerationDiagnostic> = emptyList(),
)
