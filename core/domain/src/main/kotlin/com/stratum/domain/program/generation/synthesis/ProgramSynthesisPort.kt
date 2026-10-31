package com.stratum.domain.program.generation.synthesis

import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.prescription.SessionSlotPlan
import com.stratum.domain.program.generation.prescription.SynthesisObjectiveBreakdown
import com.stratum.domain.program.generation.split.ResolvedSessionPlan
import com.stratum.domain.program.generation.slots.SessionTypeId
import com.stratum.domain.program.generation.slots.SlotGenerationDiagnostic
import com.stratum.domain.program.generation.structure.SessionRegionFilter
import com.stratum.domain.program.generation.volume.WeeklyVolumeTargets

data class ProgramSynthesisResult(
    val sessions: List<ResolvedSessionPlan>,
    val plans: List<SessionSlotPlan>,
    val cycleSlotIndexByPosition: List<Int>,
    val objective: SynthesisObjectiveBreakdown,
    val activeSlotIdsBySession: List<Set<String>>,
    /**
     * Solver-chosen [SessionRegionFilter] per session position — **source of truth** for region
     * structure. Post-hoc [SessionTypeId] on [sessions]/[plans] is a legacy display label
     * mapped from this list at materialize time (not structure).
     */
    val regionFilterBySession: List<SessionRegionFilter>,
    /**
     * Slot-build diagnostics recorded while constructing the universe (STR-254). Empty on
     * the cloud path — the solver service owns synthesis there and emits no slot
     * diagnostics; populated by on-device universe construction (tests via
     * `FakeProgramSynthesisPort`). Drained once by the generation pipeline.
     */
    val diagnostics: List<SlotGenerationDiagnostic> = emptyList(),
)

interface ProgramSynthesisPort {
    suspend fun synthesize(
        weeklyTargets: WeeklyVolumeTargets,
        context: ProgramGenerationContext,
    ): ProgramSynthesisResult
}

class ProgramSynthesisException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
