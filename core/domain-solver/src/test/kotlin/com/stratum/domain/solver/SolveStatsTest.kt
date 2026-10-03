package com.stratum.domain.solver

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * STR-13: the worker uploads [SolveStats.toTraceMap] to the job doc for CP-SAT
 * gap-tuning analysis. Firestore rejects non-finite doubles, so they must map
 * to null — never crash the upload or poison the trace.
 */
class SolveStatsTest {
    private fun stats(
        wallTimeSeconds: Double = 27.6,
        objective: Double = 42.0,
        bestBound: Double = 40.0,
        relativeGap: Double = 0.047,
        firstIncumbentAtSeconds: Double? = 1.5,
        lastIncumbentAtSeconds: Double? = 20.0,
        firstObjective: Double? = 60.0,
        lastObjective: Double? = 42.0,
    ) = SolveStats(
        status = "OPTIMAL",
        wallTimeSeconds = wallTimeSeconds,
        objective = objective,
        bestBound = bestBound,
        relativeGap = relativeGap,
        incumbentCount = 3,
        firstIncumbentAtSeconds = firstIncumbentAtSeconds,
        lastIncumbentAtSeconds = lastIncumbentAtSeconds,
        firstObjective = firstObjective,
        lastObjective = lastObjective,
        numBranches = 100L,
        numConflicts = 50L,
    )

    @Test
    fun `toTraceMap carries incumbent progression and gap fields`() {
        val map = stats().toTraceMap()

        assertEquals("OPTIMAL", map["status"])
        assertEquals(27.6, map["wallTimeSeconds"])
        assertEquals(42.0, map["objective"])
        assertEquals(40.0, map["bestBound"])
        assertEquals(0.047, map["relativeGap"])
        assertEquals(3, map["incumbentCount"])
        assertEquals(1.5, map["firstIncumbentAtSeconds"])
        assertEquals(20.0, map["lastIncumbentAtSeconds"])
        assertEquals(60.0, map["firstObjective"])
        assertEquals(42.0, map["lastObjective"])
        assertEquals(100L, map["numBranches"])
        assertEquals(50L, map["numConflicts"])
    }

    @Test
    fun `toTraceMap maps non-finite doubles to null for Firestore`() {
        val map =
            stats(
                objective = Double.POSITIVE_INFINITY,
                bestBound = Double.NaN,
                relativeGap = Double.POSITIVE_INFINITY,
                firstObjective = Double.NaN,
            ).toTraceMap()

        assertNull(map["objective"])
        assertNull(map["bestBound"])
        assertNull(map["relativeGap"])
        assertNull(map["firstObjective"])
        assertEquals(27.6, map["wallTimeSeconds"])
    }

    @Test
    fun `toTraceMap preserves absent incumbents as null`() {
        val map =
            stats(
                firstIncumbentAtSeconds = null,
                lastIncumbentAtSeconds = null,
                firstObjective = null,
                lastObjective = null,
            ).toTraceMap()

        assertNull(map["firstIncumbentAtSeconds"])
        assertNull(map["lastIncumbentAtSeconds"])
        assertNull(map["firstObjective"])
        assertNull(map["lastObjective"])
    }
}
