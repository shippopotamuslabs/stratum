@file:Suppress("MagicNumber", "ReturnCount", "MaxLineLength", "SwallowedException")

package com.stratum.domain.solver

import com.google.ortools.Loader
import com.google.ortools.sat.CpSolver
import com.google.ortools.sat.CpSolverStatus
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.prescription.SessionSlotPlan
import com.stratum.domain.program.generation.prescription.SlotSetAllocationPort
import com.stratum.domain.program.generation.prescription.SlotSetAllocationResult
import com.stratum.domain.program.generation.volume.WeeklyVolumeTargets

/**
 * CP-SAT implementation of [SlotSetAllocationPort]. Builds a
 * [VolumeAllocationModel], solves it within [deadlineMillis], and maps the
 * solution onto the [com.stratum.domain.program.generation.prescription.FilledSlot.setCount]
 * of each included slot. Returns [SlotSetAllocationResult.Fallback] when the
 * model is infeasible or the solver does not find a feasible solution in time.
 *
 * The native OR-Tools libraries are loaded once on first use via
 * [Loader.loadNativeLibraries]. On JVM hosts this resolves the platform
 * `libortools.so`/`libjniortools.so` from the `ortools-java` jar; on Android
 * (Phase 4) they are bundled as jniLibs by `:core:domain-solver`.
 */
class CpSatSlotSetAllocator(
    private val deadlineMillis: Long = DEFAULT_DEADLINE_MILLIS,
) : SlotSetAllocationPort {

    override fun allocate(
        plans: List<SessionSlotPlan>,
        weeklyTargets: WeeklyVolumeTargets,
        context: ProgramGenerationContext,
    ): SlotSetAllocationResult {
        ensureNativeLibrariesLoaded()
        if (nativeLoadFailed) return SlotSetAllocationResult.Fallback
        val modelBuilder = VolumeAllocationModel(weeklyTargets, context)
        val model = modelBuilder.buildModel(plans)
        val solver = CpSolver().apply {
            getParameters().setMaxTimeInSeconds(deadlineMillis / MILLISECONDS_PER_SECOND)
        }
        val status = solver.solve(model)
        if (status != CpSolverStatus.OPTIMAL && status != CpSolverStatus.FEASIBLE) {
            return SlotSetAllocationResult.Fallback
        }
        modelBuilder.slots.forEach { sv ->
            sv.slot.setCount = solver.value(sv.x).toInt()
        }
        return SlotSetAllocationResult.Success(plans)
    }

    private companion object {
        const val DEFAULT_DEADLINE_MILLIS = 500L
        const val MILLISECONDS_PER_SECOND = 1000.0

        @Volatile
        private var nativeLoaded = false
        @Volatile
        private var nativeLoadFailed = false

        @Synchronized
        private fun ensureNativeLibrariesLoaded() {
            if (!nativeLoaded) {
                try {
                    Loader.loadNativeLibraries()
                    nativeLoaded = true
                } catch (t: Throwable) {
                    // Native libs unavailable (e.g. on Android before the
                    // CMake-built libortools.so is bundled, or wrong ABI).
                    // The caller falls back to the greedy allocator.
                    // Intentionally not rethrown — Fallback is the safe path.
                    nativeLoadFailed = true
                }
            }
        }
    }
}
