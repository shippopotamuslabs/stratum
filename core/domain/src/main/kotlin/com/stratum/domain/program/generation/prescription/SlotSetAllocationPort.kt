package com.stratum.domain.program.generation.prescription

import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.volume.WeeklyVolumeTargets

/**
 * Allocates integer hard-set counts per [FilledSlot] across all sessions at once,
 * satisfying session/muscle/per-slot caps and minimizing deviation from per-muscle
 * weekly targets. The CP-SAT solver in `:core:domain-solver` is the sole
 * implementation; there is no greedy fallback.
 *
 * The port receives [SessionSlotPlan]s already built by the domain (optional
 * slots filtered, [FilledSlot]s instantiated at `setCount = 0`). The
 * implementation mutates [FilledSlot.setCount] in place and returns the same
 * plans. This keeps the domain's private slot-construction helpers
 * (`shouldIncludeOptional`, `toFilledSlot`) inside `:core:domain`.
 *
 * The port lives in `:core:domain` so the generation pipeline can depend on it
 * without depending on a solver module. The CP-SAT implementation lives in
 * `:core:domain-solver` and is bound at the `:app` DI boundary.
 */
interface SlotSetAllocationPort {
    fun allocate(
        plans: List<SessionSlotPlan>,
        weeklyTargets: WeeklyVolumeTargets,
        context: ProgramGenerationContext,
    ): SlotSetAllocationResult
}

/**
 * Outcome of [SlotSetAllocationPort.allocate]. The solver always returns
 * [Success] — it uses progressive MEV-floor relaxation to guarantee
 * feasibility. If no feasible solution exists even without MEV floors (a
 * bug or an impossible split), [allocate] throws.
 */
sealed interface SlotSetAllocationResult {
    data class Success(val plans: List<SessionSlotPlan>) : SlotSetAllocationResult
}
