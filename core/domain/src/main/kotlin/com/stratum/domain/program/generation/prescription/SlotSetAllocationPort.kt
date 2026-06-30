package com.stratum.domain.program.generation.prescription

import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.volume.WeeklyVolumeTargets

/**
 * Allocates integer hard-set counts per [FilledSlot] across all sessions at once,
 * satisfying session/muscle/per-slot caps and minimizing deviation from per-muscle
 * weekly targets. Implementations may use a constraint optimizer (CP-SAT) or any
 * other strategy; the greedy allocator in [SlotSetAllocator] is the fallback when
 * an optimizer is unavailable or reports infeasibility.
 *
 * The port receives [SessionSlotPlan]s already built by the domain (optional
 * slots filtered, [FilledSlot]s instantiated at `setCount = 0`). The
 * implementation mutates [FilledSlot.setCount] in place and returns the same
 * plans on success. This keeps the domain's private slot-construction helpers
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
 * Outcome of [SlotSetAllocationPort.allocate].
 *
 * - [Success]: the optimizer assigned `setCount` on every included [FilledSlot]
 *   and the returned plans are ready for lift selection.
 * - [Fallback]: the optimizer did not find a feasible solution within its time
 *   budget (or hit an internal error). The caller must fall back to the greedy
 *   allocator.
 */
sealed interface SlotSetAllocationResult {
    data class Success(val plans: List<SessionSlotPlan>) : SlotSetAllocationResult
    data object Fallback : SlotSetAllocationResult
}
