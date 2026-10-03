@file:Suppress("MagicNumber", "SwallowedException")

package com.stratum.domain.solver

import com.google.ortools.Loader
import com.google.ortools.sat.CpSolver
import com.google.ortools.sat.CpSolverSolutionCallback
import com.google.ortools.sat.CpSolverStatus
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.context.resolveTrainingTimeline
import com.stratum.domain.program.generation.context.withResolvedCycleSpace
import com.stratum.domain.program.generation.synthesis.ProgramSynthesisException
import com.stratum.domain.program.generation.synthesis.ProgramSynthesisPort
import com.stratum.domain.program.generation.synthesis.ProgramSynthesisResult
import com.stratum.domain.program.generation.universe.buildSlotUniverse
import com.stratum.domain.program.generation.synthesis.buildUnifiedSessionPlans
import com.stratum.domain.program.generation.synthesis.materializeSynthesisResult
import com.stratum.domain.program.generation.structure.regionFiltersBySessionPosition
import com.stratum.domain.program.generation.volume.WeeklyVolumeTargets
import java.util.Locale
import java.util.function.Consumer
import kotlin.math.abs
import kotlin.math.max

/**
 * Unified CP-SAT program synthesis: structure, set counts, and calendar in one solve.
 *
 * Pure JVM host: loads desktop OR-Tools natives via [Loader.loadNativeLibraries] (linux/darwin/
 * windows platform jars from `ortools-java`). Intended for Cloud Run / host unit tests — not the
 * Android APK path.
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
 * shape is not what it expects.
 */

/**
 * Immutable snapshot of the solver settings [CpSatProgramSynthesizer] applies. Null
 * [numSearchWorkers] means the OR-Tools worker default was left untouched.
 */
data class SolverParameters(
    val maxTimeSeconds: Double,
    val numSearchWorkers: Int?,
)
class CpSatProgramSynthesizer(
    private val deadlineMillis: Long = DEFAULT_DEADLINE_MILLIS,
    private val logSearchProgress: Boolean = defaultLogSearchProgress(),
    private val useWarmStart: Boolean = defaultUseWarmStart(),
    // Null leaves the OR-Tools worker default untouched. Callers that run on known hardware
    // (e.g. the Cloud Run worker) pass an explicit count; the domain default stays generic.
    private val numSearchWorkers: Int? = null,
) : ProgramSynthesisPort {

    /**
     * Stats from the most recent [synthesize] call. Null before the first solve.
     * Read by the worker for the STR-13 trace waterfall — not part of the domain result.
     */
    var lastSolveStats: SolveStats? = null
        private set

    override suspend fun synthesize(
        weeklyTargets: WeeklyVolumeTargets,
        context: ProgramGenerationContext,
    ): ProgramSynthesisResult {
        ensureNativeLibrariesLoaded()
        // Resolve cycle-space before any recovery geometry (defaults apply here, not only post-build).
        val context = context.withResolvedCycleSpace()
        checkFeasibleConfiguration(weeklyTargets, context)
        val universe = buildSlotUniverse(context, weeklyTargets)
        require(universe.isNotEmpty()) { "Slot universe is empty for program synthesis." }
        val sessionCount = context.trainingDaysPerWeek
        val plans = buildUnifiedSessionPlans(universe, sessionCount)
        plans.forEach { plan -> plan.filledSlots.forEach { it.setCount = 0 } }

        val timeline = context.resolveTrainingTimeline()
        val modelBuilder =
            ProgramSynthesisModel(
                weeklyTargets = weeklyTargets,
                context = context,
                unifiedUniverse = universe,
                timeline = timeline,
            )
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
                "warmStart regionFilters=" +
                    "${seed.regionFilterBySession.toSortedMap().values.joinToString(",") { it.name }} " +
                    "families=" +
                    "${seed.seedFamilyBySession.toSortedMap().values.joinToString(",") { it.name }} " +
                    "actives=${seed.activeSlotCount} totalSets=${seed.totalSets} " +
                    "days=${seed.weekdayBySession.toSortedMap().values.joinToString(",")}",
            )
        }

        val regionFilters = regionFiltersBySessionPosition(timeline)
        logInfo(
            "start days=$sessionCount cycleLength=${timeline.cycleLength} " +
                "trainSlots=${timeline.trainingSlotIndices} universe=${universe.size} " +
                "regionFiltersBySession=" +
                regionFilters.joinToString(";") { allowed ->
                    allowed.joinToString(",") { it.name }
                } +
                " slotVars=${plans.sumOf { it.filledSlots.size }} " +
                "deadlineMs=$deadlineMillis logSearch=$logSearchProgress warmStart=$useWarmStart " +
                "workers=$numSearchWorkers",
        )
        // Multi-line model size dump from OR-Tools (vars / constraints / domains).
        runCatching { model.modelStats() }.getOrNull()?.lineSequence()?.forEach { line ->
            if (line.isNotBlank()) logInfo("model $line")
        }

        val solver = buildSolver()

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
        lastSolveStats = SolveStats(
            status = status.name,
            wallTimeSeconds = solver.wallTime(),
            objective = solver.objectiveValue(),
            bestBound = solver.bestObjectiveBound(),
            relativeGap = relativeGap(
                objective = solver.objectiveValue(),
                bound = solver.bestObjectiveBound(),
            ),
            incumbentCount = incumbents.count,
            firstIncumbentAtSeconds = incumbents.first?.wallTimeSeconds,
            lastIncumbentAtSeconds = incumbents.last?.wallTimeSeconds,
            firstObjective = incumbents.first?.objective,
            lastObjective = incumbents.last?.objective,
            numBranches = solver.numBranches(),
            numConflicts = solver.numConflicts(),
        )
        return materializeSynthesisResult(universe, solution)
            .copy(objective = objective)
    }

    /**
     * Effective solver settings, read back from a freshly built solver. Public so wiring and
     * regression tests can observe the applied parameters without running a full solve and
     * without touching the mutable OR-Tools solver. Null [SolverParameters.numSearchWorkers]
     * means the OR-Tools default was left untouched.
     */
    fun appliedParameters(): SolverParameters {
        val parameters = buildSolver().getParameters()
        return SolverParameters(
            maxTimeSeconds = parameters.maxTimeInSeconds,
            numSearchWorkers = parameters.numSearchWorkers.takeIf { parameters.hasNumSearchWorkers() },
        )
    }

    private fun buildSolver(): CpSolver {
        return CpSolver().apply {
            getParameters().setMaxTimeInSeconds(deadlineMillis / MILLISECONDS_PER_SECOND)
            // Explicit only when the caller runs on known hardware; otherwise the OR-Tools
            // default (0 = all cores) applies.
            numSearchWorkers?.let { getParameters().setNumSearchWorkers(it) }
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
        val sessionCount = context.trainingDaysPerWeek
        require(sessionCount >= 1) {
            "trainingDaysPerWeek must be at least 1 (was $sessionCount)"
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
            if (sessionCount <= 1) {
                throw ProgramSynthesisException(
                    "Cannot specialize $muscle (MEV ${target.min}) with only one training day " +
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
            println("$LOG_TAG: $message")
        }

        @Synchronized
        fun ensureNativeLibrariesLoaded() {
            if (nativeLoaded) return
            try {
                Loader.loadNativeLibraries()
            } catch (t: Throwable) {
                val os = System.getProperty("os.name") ?: "unknown"
                val arch = System.getProperty("os.arch") ?: "unknown"
                throw UnsatisfiedLinkError(
                    "OR-Tools natives failed to load on $os/$arch via Loader.loadNativeLibraries(). " +
                        "Ensure ortools-java platform natives are on the classpath " +
                        "(desktop JVM / Cloud Run only — not packaged in the Android APK). " +
                        "Cause: ${t.message}",
                ).apply { initCause(t) }
            }
            nativeLoaded = true
        }
    }
}
