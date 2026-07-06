package com.stratum.domain.importing

import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession

/**
 * Filters sets that would break downstream insight math (e.g. 1RM with reps ≤ 0).
 * Server-side validation should catch these first; this is a client safety net.
 */
object ImportedHistorySanitizer {
    fun sanitize(
        sessions: List<TrainingSession>,
        sets: List<LoggedSet>,
    ): Pair<List<TrainingSession>, List<LoggedSet>> {
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
