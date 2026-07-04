package com.stratum.domain.program.generation.synthesis

import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.prescription.SessionSlotPlan
import com.stratum.domain.program.generation.prescription.SynthesisObjectiveBreakdown
import com.stratum.domain.program.generation.split.ResolvedSessionPlan
import com.stratum.domain.program.generation.split.SessionTypeId
import com.stratum.domain.program.generation.volume.WeeklyVolumeTargets

data class ProgramSynthesisResult(
    val sessions: List<ResolvedSessionPlan>,
    val plans: List<SessionSlotPlan>,
    val weekdayIndexByPosition: List<Int>,
    val objective: SynthesisObjectiveBreakdown,
    val activeSlotIdsBySession: List<Set<String>>,
)

interface ProgramSynthesisPort {
    fun synthesize(
        weeklyTargets: WeeklyVolumeTargets,
        context: ProgramGenerationContext,
    ): ProgramSynthesisResult
}

class ProgramSynthesisException(message: String) : RuntimeException(message)
