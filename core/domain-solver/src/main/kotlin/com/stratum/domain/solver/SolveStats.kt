package com.stratum.domain.solver

/**
 * Compact CP-SAT solve statistics for the per-job trace waterfall (STR-13).
 * Answers "did we find a good solution early and then only prove optimality?"
 * All wall times are seconds from solve start.
 *
 * Lives next to its producer (CpSatProgramSynthesizer), not in the domain result —
 * the domain boundary stays free of solver internals.
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
) {
    /**
     * Serialize to a Firestore-safe map for the job doc trace.
     * Non-finite doubles (Infinity/NaN from unbounded or zero-objective solves)
     * are mapped to null — Firestore rejects them.
     */
    fun toTraceMap(): Map<String, Any?> {
        return mapOf(
            "status" to status,
            "wallTimeSeconds" to wallTimeSeconds.finiteOrNull(),
            "objective" to objective.finiteOrNull(),
            "bestBound" to bestBound.finiteOrNull(),
            "relativeGap" to relativeGap.finiteOrNull(),
            "incumbentCount" to incumbentCount,
            "firstIncumbentAtSeconds" to firstIncumbentAtSeconds?.finiteOrNull(),
            "lastIncumbentAtSeconds" to lastIncumbentAtSeconds?.finiteOrNull(),
            "firstObjective" to firstObjective?.finiteOrNull(),
            "lastObjective" to lastObjective?.finiteOrNull(),
            "numBranches" to numBranches,
            "numConflicts" to numConflicts,
        )
    }

    private fun Double.finiteOrNull(): Double? = if (isFinite()) this else null
}
