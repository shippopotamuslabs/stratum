package com.stratum.domain.program.generation.synthesis

import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.prescription.SessionSlotPlan
import com.stratum.domain.program.generation.prescription.SynthesisObjectiveBreakdown
import com.stratum.domain.program.generation.split.ResolvedSessionPlan
import com.stratum.domain.program.generation.slots.SessionTypeId
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
     * CP-SAT solve statistics for trace logging (STR-13). Null when the result was not
     * produced by a solver run (e.g. decoded from wire JSON). Not serialized to the
     * client-facing result artifact — lives only on the Firestore job doc.
     */
    val solveStats: SolveStats? = null,
)

/**
 * Compact CP-SAT solve statistics for the per-job trace waterfall (STR-13).
 * Answers "did we find a good solution early and then only prove optimality?"
 * All wall times are seconds from solve start.
 */
data class SolveStats(
    val status: String,
    val wallTimeSeconds: Double,
    val objective: Double,
    val bestBound: Double,
    val relativeGap: Double,
    val incumbentCount: Int,
    val firstIncumbentAtSeconds: Double?,
    val lastIncumbentAtSeconds: Double?,
    val firstObjective: Double?,
    val lastObjective: Double?,
    val numBranches: Long,
    val numConflicts: Long,
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
