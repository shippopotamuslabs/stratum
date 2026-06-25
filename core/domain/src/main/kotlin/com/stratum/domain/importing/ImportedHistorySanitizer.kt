package com.stratum.domain.importing

/**
 * Filters sets that would break downstream insight math (e.g. 1RM with reps ≤ 0).
 * Server-side validation should catch these first; this is a client safety net.
 */
object ImportedHistorySanitizer {
    fun sanitize(
        sessions: List<HistoricalSession>,
        sets: List<LoggedSet>,
    ): Pair<List<HistoricalSession>, List<LoggedSet>> {
        val validSets =
            sets.filter { set ->
                set.reps > 0 &&
                    set.weight.amount >= 0 &&
                    set.exerciseNameRaw.isNotBlank()
            }
        val sessionIds = sessions.map { it.id.value }.toSet()
        val referencedSets = validSets.filter { it.sessionId.value in sessionIds }
        return sessions to referencedSets
    }
}
