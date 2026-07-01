@file:Suppress("MagicNumber", "SwallowedException")

package com.stratum.domain.solver

import com.google.ortools.Loader
import com.google.ortools.sat.CpSolver
import com.google.ortools.sat.CpSolverStatus
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.synthesis.ProgramSynthesisException
import com.stratum.domain.program.generation.synthesis.ProgramSynthesisPort
import com.stratum.domain.program.generation.synthesis.ProgramSynthesisResult
import com.stratum.domain.program.generation.synthesis.buildSlotUniverse
import com.stratum.domain.program.generation.synthesis.buildUnifiedSessionPlans
import com.stratum.domain.program.generation.synthesis.materializeSynthesisResult
import com.stratum.domain.program.generation.volume.WeeklyVolumeTargets

/**
 * Unified CP-SAT program synthesis: structure, set counts, and calendar in one solve.
 */
class CpSatProgramSynthesizer(
    private val deadlineMillis: Long = DEFAULT_DEADLINE_MILLIS,
) : ProgramSynthesisPort {

    override fun synthesize(
        weeklyTargets: WeeklyVolumeTargets,
        context: ProgramGenerationContext,
    ): ProgramSynthesisResult {
        ensureNativeLibrariesLoaded()
        val universe = buildSlotUniverse(context, weeklyTargets)
        require(universe.isNotEmpty()) { "Slot universe is empty for program synthesis." }
        val sessionCount = context.trainingDaysPerWeek
        val plans = buildUnifiedSessionPlans(universe, sessionCount)
        plans.forEach { plan -> plan.filledSlots.forEach { it.setCount = 0 } }

        val modelBuilder = ProgramSynthesisModel(weeklyTargets, context, unifiedUniverse = universe)
        val model = modelBuilder.buildModel(plans)
        val solver = CpSolver().apply {
            getParameters().setMaxTimeInSeconds(deadlineMillis / MILLISECONDS_PER_SECOND)
        }
        val status = solver.solve(model)
        if (status != CpSolverStatus.OPTIMAL && status != CpSolverStatus.FEASIBLE) {
            throw ProgramSynthesisException(
                "CP-SAT synthesis returned $status — try relaxing specialization or adding a training day.",
            )
        }
        // Instrumentation: log model size and solve time for capacity planning.
        println(
            "CpSatProgramSynthesizer: status=$status wallTime=${solver.wallTime()}s",
        )

        val priorityMuscles = recoveryPriorityMuscles(context)
        val modelViolations = modelBuilder.findPriorityAdjacentExposureViolations(solver, plans, priorityMuscles)
        if (modelViolations.isNotEmpty()) {
            throw ProgramSynthesisException(
                "CP-SAT synthesis violated adjacent recovery constraints: $modelViolations",
            )
        }

        val solution = modelBuilder.readUnifiedSolution(solver, plans)
        val objective = modelBuilder.readObjective(solver)
        return materializeSynthesisResult(universe, solution).copy(objective = objective)
    }

    private companion object {
        const val DEFAULT_DEADLINE_MILLIS = 120_000L
        const val MILLISECONDS_PER_SECOND = 1000.0

        @Volatile
        private var nativeLoaded = false

        @Synchronized
        private fun ensureNativeLibrariesLoaded() {
            if (!nativeLoaded) {
                try {
                    Loader.loadNativeLibraries()
                } catch (t: Throwable) {
                    try {
                        System.loadLibrary("ortools")
                    } catch (e: UnsatisfiedLinkError) {
                        // libortools may be statically linked into libjniortools.
                    }
                    System.loadLibrary("jniortools")
                }
                nativeLoaded = true
            }
        }
    }
}
