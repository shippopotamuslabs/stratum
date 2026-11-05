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

/**
 * Outcome of a synthesis call (STR-241): an expected refusal (kill-switch off,
 * provider paused) is a [Refused] branch carrying a typed [ProgramSynthesisRefusal],
 * never a thrown exception. Operational failures still throw
 * [ProgramSynthesisException] from the adapter boundary.
 */
sealed interface ProgramSynthesisOutcome {
    data class Completed(val result: ProgramSynthesisResult) : ProgramSynthesisOutcome
    data class Refused(val refusal: ProgramSynthesisRefusal) : ProgramSynthesisOutcome
}

/** Why a synthesis call was refused before any solver work ran. */
sealed interface ProgramSynthesisRefusal {
    /** The feature gate for cloud synthesis is disabled. */
    data object FeatureDisabled : ProgramSynthesisRefusal
}

interface ProgramSynthesisPort {
    suspend fun synthesize(
        weeklyTargets: WeeklyVolumeTargets,
        context: ProgramGenerationContext,
    ): ProgramSynthesisOutcome
}

class ProgramSynthesisException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    companion object {
        /** User-facing copy for a synthesis refusal while the feature gate is paused. */
        const val MESSAGE_SYNTHESIS_PAUSED =
            "Program generation is temporarily unavailable. Please try again later."
    }
}
