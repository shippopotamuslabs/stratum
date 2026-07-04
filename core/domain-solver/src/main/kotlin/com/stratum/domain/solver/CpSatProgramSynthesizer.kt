@file:Suppress("MagicNumber", "SwallowedException")

package com.stratum.domain.solver

import com.google.ortools.Loader
import com.google.ortools.sat.CpSolver
import com.google.ortools.sat.CpSolverStatus
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationLevel
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
        checkFeasibleConfiguration(weeklyTargets, context)
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

        val solution = modelBuilder.readUnifiedSolution(solver, plans)
        val objective = modelBuilder.readObjective(solver)
        return materializeSynthesisResult(universe, solution).copy(objective = objective)
    }

    /**
     * Cheap structural checks before building/solving the CP-SAT model, so genuinely infeasible
     * configurations surface an actionable error instead of burning the 120 s deadline (audit §7.4).
     * These are necessary-but-not-sufficient: the solver may still fail on subtler conflicts.
     */
    private fun checkFeasibleConfiguration(
        weeklyTargets: WeeklyVolumeTargets,
        context: ProgramGenerationContext,
    ) {
        val distinctWeekdays = context.weekdays.distinct()
        require(distinctWeekdays.size == context.weekdays.size) {
            "Training weekdays contain duplicates: ${context.weekdays}. Each day must be distinct."
        }
        val sessionCount = context.trainingDaysPerWeek
        require(distinctWeekdays.size >= sessionCount) {
            "Need $sessionCount distinct training days but only ${distinctWeekdays.size} provided " +
                "(${distinctWeekdays}). Add training days or reduce frequency."
        }
        // A LargeCompound muscle specialized at a high MEV needs enough non-adjacent slots to fit it.
        // With the adjacent-day hard forbid (addLargeCompoundAdjacentForbids), a muscle trained on
        // back-to-back available days can fire meaningfully at most ceil(availableNonAdjacentSlots/2)
        // times — flag configs where the required MEV clearly can't fit before solving.
        weeklyTargets.forEach { (muscle, target) ->
            val level = runCatching { MuscleGroup.valueOf(muscle) }
                .getOrNull()
                ?.let { context.specialization[it] }
                ?: SpecializationLevel.Maintenance
            if (level != SpecializationLevel.Specialize || target.min <= 0.0) return@forEach
            if (distinctWeekdays.size <= 1) {
                throw ProgramSynthesisException(
                    "Cannot specialize $muscle (MEV ${target.min}) with only one distinct training day " +
                        "— adjacent-day recovery forbids training it twice. Add training days.",
                )
            }
        }
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
