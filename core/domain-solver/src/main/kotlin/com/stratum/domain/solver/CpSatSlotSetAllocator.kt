@file:Suppress("MagicNumber", "ReturnCount", "MaxLineLength")

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
 * of each included slot.
 *
 * The model uses only hard upper-bound constraints (per-slot cap, per-session
 * muscle cap, session time cap, MRV ceiling) plus soft objective penalties
 * (aim deviation, MEV floor shortfall, role-minimum shortfall, co-specialization
 * balance). This guarantees feasibility — setting all `x[s] = 0` satisfies every
 * hard constraint. The solver always returns [SlotSetAllocationResult.Success].
 *
 * **Native libs:** [Loader.loadNativeLibraries] is called once on first use.
 * On JVM hosts this resolves the platform `.so`/`.dylib`/`.dll` from the
 * `ortools-java` jar. On Android the CMake-built `libortools.so` is bundled.
 * If native libs cannot load, the call throws — there is no silent fallback.
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
        plans.forEach { plan -> plan.filledSlots.forEach { it.setCount = 0 } }

        val modelBuilder = VolumeAllocationModel(weeklyTargets, context)
        val model = modelBuilder.buildModel(plans)
        val solver = CpSolver().apply {
            getParameters().setMaxTimeInSeconds(deadlineMillis / MILLISECONDS_PER_SECOND)
        }
        val status = solver.solve(model)
        if (status != CpSolverStatus.OPTIMAL && status != CpSolverStatus.FEASIBLE) {
            // Should not happen — the hard constraints are always feasible
            // (x[s] = 0 satisfies all upper bounds). If we reach here, the
            // deadline was too short to find even a trivial feasible solution.
            throw SlotSetAllocationException(
                "CP-SAT solver returned $status — the model should always be feasible " +
                    "(hard constraints are upper bounds only). Try increasing the deadline.",
            )
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

        @Synchronized
        private fun ensureNativeLibrariesLoaded() {
            if (!nativeLoaded) {
                Loader.loadNativeLibraries()
                nativeLoaded = true
            }
        }
    }
}

/**
 * Thrown when the CP-SAT solver cannot find a feasible solution. Should not
 * happen under normal operation — the hard constraints are always satisfiable.
 * Indicates the deadline was too short or an internal solver error.
 */
class SlotSetAllocationException(message: String) : RuntimeException(message)
