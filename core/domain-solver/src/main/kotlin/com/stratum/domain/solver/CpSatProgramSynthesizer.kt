@file:Suppress("MagicNumber", "SwallowedException")

package com.stratum.domain.solver

import com.google.ortools.Loader
import com.google.ortools.sat.CpSolver
import com.google.ortools.sat.CpSolverSolutionCallback
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
import android.util.Log
import java.util.Locale
import java.util.function.Consumer
import kotlin.math.abs
import kotlin.math.max

/**
 * Unified CP-SAT program synthesis: structure, set counts, and calendar in one solve.
 *
 * Diagnostics (always on): each improving incumbent is logged with wall time + objective, then a
 * final summary with objective, dual bound, relative gap, and response stats.
 *
 * Warm-start (default **off**): device A/B (cold / U/L dense / U/L sparse / Full sparse) did not
 * beat cold search on first-obj or final@120s. Opt in with [useWarmStart] = true or system property
 * `stratum.solver.warm_start=true`.
 *
 * Full CP-SAT search progress (very verbose): set system property
 * `stratum.solver.log_search=true`, or pass [logSearchProgress] = true.
 *
 * Note: do **not** wire Kotlin lambdas into [CpSolver.setBestBoundCallback] /
 * [CpSolver.setLogCallback] without a concrete [Consumer] class. The OR-Tools SWIG JNI layer
 * looks up `accept` via GetMethodID and aborts (`jarg2_method_id != nullptr`) when the lambda
 * shape is not what it expects — observed as SIGABRT on Android during solve setup.
 */
class CpSatProgramSynthesizer(
    private val deadlineMillis: Long = DEFAULT_DEADLINE_MILLIS,
    private val logSearchProgress: Boolean = defaultLogSearchProgress(),
    private val useWarmStart: Boolean = defaultUseWarmStart(),
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

        if (useWarmStart) {
            val seed =
                ConstructiveSynthesisSeed.build(
                    weeklyTargets = weeklyTargets,
                    context = context,
                    universe = universe,
                    plans = plans,
                )
            modelBuilder.applyWarmStartHints(model, seed)
            logInfo(
                "warmStart domains=${seed.domainBySession.toSortedMap().values.joinToString(",") { it.name }} " +
                    "actives=${seed.activeSlotCount} totalSets=${seed.totalSets} " +
                    "days=${seed.weekdayBySession.toSortedMap().values.joinToString(",")}",
            )
        }

        logInfo(
            "start days=$sessionCount universe=${universe.size} " +
                "slotVars=${plans.sumOf { it.filledSlots.size }} " +
                "deadlineMs=$deadlineMillis logSearch=$logSearchProgress warmStart=$useWarmStart",
        )
        // Multi-line model size dump from OR-Tools (vars / constraints / domains).
        runCatching { model.modelStats() }.getOrNull()?.lineSequence()?.forEach { line ->
            if (line.isNotBlank()) logInfo("model $line")
        }

        val solver = CpSolver().apply {
            getParameters().setMaxTimeInSeconds(deadlineMillis / MILLISECONDS_PER_SECOND)
            if (logSearchProgress) {
                getParameters().setLogSearchProgress(true)
                // Route through our logger so device logcat / test stdout both see progress.
                getParameters().setLogToStdout(false)
                // Named Consumer class — not a Kotlin lambda (see class KDoc).
                setLogCallback(OrToolsLogConsumer())
            }
            // Intentionally no setBestBoundCallback: final bound is logged after solve via
            // bestObjectiveBound(). Mid-search bound streaming is not required for gap decisions.
        }

        val incumbents = IncumbentLog()
        val status = solver.solve(model, incumbents)
        logSolveSummary(status = status, solver = solver, incumbents = incumbents)

        if (status != CpSolverStatus.OPTIMAL && status != CpSolverStatus.FEASIBLE) {
            throw ProgramSynthesisException(
                "CP-SAT synthesis returned $status — try relaxing specialization or adding a training day.",
            )
        }

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

    private fun logSolveSummary(
        status: CpSolverStatus,
        solver: CpSolver,
        incumbents: IncumbentLog,
    ) {
        val objective = solver.objectiveValue()
        val bound = solver.bestObjectiveBound()
        val gap = relativeGap(objective = objective, bound = bound)
        val first = incumbents.first
        val last = incumbents.last
        logInfo(
            "done status=$status wallTime=${formatNum(solver.wallTime())}s " +
                "objective=${formatNum(objective)} bound=${formatNum(bound)} " +
                "relativeGap=${formatPct(gap)} " +
                "incumbents=${incumbents.count} " +
                "firstIncumbentAt=${first?.let { formatNum(it.wallTimeSeconds) } ?: "n/a"}s " +
                "lastIncumbentAt=${last?.let { formatNum(it.wallTimeSeconds) } ?: "n/a"}s " +
                "firstObj=${first?.let { formatNum(it.objective) } ?: "n/a"} " +
                "lastObj=${last?.let { formatNum(it.objective) } ?: "n/a"} " +
                "branches=${solver.numBranches()} conflicts=${solver.numConflicts()}",
        )
        // How much of the deadline was spent after the last improving solution (prove vs improve).
        if (last != null) {
            val afterLast = (solver.wallTime() - last.wallTimeSeconds).coerceAtLeast(0.0)
            val objImprove =
                if (first != null) first.objective - last.objective else 0.0
            logInfo(
                "gapDecisionHint timeAfterLastIncumbent=${formatNum(afterLast)}s " +
                    "objImprovedFromFirst=${formatNum(objImprove)} " +
                    "if_relative_gap_1pct_would_stop=${gap <= 0.01} " +
                    "if_relative_gap_5pct_would_stop=${gap <= 0.05}",
            )
        }
        runCatching { solver.responseStats() }.getOrNull()?.lineSequence()?.forEach { line ->
            if (line.isNotBlank()) logInfo("response $line")
        }
    }

    /**
     * Logs every improving incumbent. The wall-time series answers: "did we already have a
     * good solution early and then only prove optimality?"
     *
     * Uses a concrete [CpSolverSolutionCallback] subclass (not a Consumer lambda) so JNI/SWIG
     * can call into it safely from worker threads.
     */
    private class IncumbentLog : CpSolverSolutionCallback() {
        data class Point(val wallTimeSeconds: Double, val objective: Double)

        private val points = mutableListOf<Point>()
        val count: Int
            get() = synchronized(points) { points.size }
        val first: Point?
            get() = synchronized(points) { points.firstOrNull() }
        val last: Point?
            get() = synchronized(points) { points.lastOrNull() }

        override fun onSolutionCallback() {
            val point = Point(wallTimeSeconds = wallTime(), objective = objectiveValue())
            val n = synchronized(points) {
                points.add(point)
                points.size
            }
            logInfo(
                "incumbent n=$n wall=${formatNum(point.wallTimeSeconds)}s " +
                    "obj=${formatNum(point.objective)}",
            )
        }
    }

    /**
     * Explicit [Consumer] for OR-Tools log lines. Must remain a named class with a real
     * `accept(String)` method for SWIG JNI GetMethodID.
     */
    private class OrToolsLogConsumer : Consumer<String> {
        override fun accept(line: String) {
            logInfo("progress $line")
        }
    }

    private companion object {
        const val DEFAULT_DEADLINE_MILLIS = 120_000L
        const val MILLISECONDS_PER_SECOND = 1000.0
        const val LOG_TAG = "CpSatProgramSynthesizer"
        const val LOG_SEARCH_PROPERTY = "stratum.solver.log_search"
        const val WARM_START_PROPERTY = "stratum.solver.warm_start"

        @Volatile
        private var nativeLoaded = false

        fun defaultLogSearchProgress(): Boolean =
            System.getProperty(LOG_SEARCH_PROPERTY)?.equals("true", ignoreCase = true) == true

        /** Warm-start off by default; set `stratum.solver.warm_start=true` to experiment. */
        fun defaultUseWarmStart(): Boolean =
            System.getProperty(WARM_START_PROPERTY)?.equals("true", ignoreCase = true) == true

        /**
         * CP-SAT relative gap for a minimization problem: |obj − bound| / max(1, |obj|).
         * Matches the usual OR-Tools gap semantics used by relative_gap_limit.
         */
        fun relativeGap(objective: Double, bound: Double): Double {
            if (!objective.isFinite() || !bound.isFinite()) return Double.POSITIVE_INFINITY
            return abs(objective - bound) / max(1.0, abs(objective))
        }

        fun formatNum(value: Double): String =
            if (value.isFinite()) String.format(Locale.US, "%.3f", value) else value.toString()

        fun formatPct(fraction: Double): String =
            if (fraction.isFinite()) {
                String.format(Locale.US, "%.2f%%", fraction * 100.0)
            } else {
                "n/a"
            }

        fun logInfo(message: String) {
            // Logcat tag for device runs; println keeps host JVM solver tests readable.
            Log.i(LOG_TAG, message)
            println("$LOG_TAG: $message")
        }

        @Synchronized
        fun ensureNativeLibrariesLoaded() {
            if (!nativeLoaded) {
                try {
                    Loader.loadNativeLibraries()
                } catch (t: Throwable) {
                    try {
                        System.loadLibrary("ortools")
                    } catch (e: UnsatisfiedLinkError) {
                        // libortools may be statically linked into libjniortools.
                    }
                    try {
                        System.loadLibrary("jniortools")
                    } catch (e: UnsatisfiedLinkError) {
                        val abi = runCatching { android.os.Build.SUPPORTED_ABIS.joinToString() }
                            .getOrElse { "unknown" }
                        throw UnsatisfiedLinkError(
                            "OR-Tools natives failed to load (supported ABIs: $abi). " +
                                "Stratum packages arm64-v8a only — use an arm64 device/emulator, " +
                                "not a pure x86_64 image. Cause: ${e.message}",
                        ).apply { initCause(e) }
                    }
                }
                nativeLoaded = true
            }
        }
    }
}
