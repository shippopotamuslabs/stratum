package com.stratum.domain.importing

import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession

/**
 * Filters sets that would break downstream insight math (e.g. 1RM with reps ≤ 0).
 * Server-side validation should catch these first; this is a client safety net.
 *
 * Dropping unusable imported sets is an import decision and stays here; the
 * rule for which sets may feed insight math is the model's own
 * ([LoggedSet.isInsightCreditable], STR-168) and is consumed, not re-encoded.
 * The blank-name and session-reference checks are import-usability rules and
 * remain local to this filter.
 */
object ImportedHistorySanitizer {
    fun sanitize(
        sessions: List<TrainingSession>,
        sets: List<LoggedSet>,
    ): Pair<List<TrainingSession>, List<LoggedSet>> {
        val validSets =
            sets.filter { set ->
                set.isInsightCreditable &&
                    set.exerciseNameRaw.isNotBlank()
            }
        val sessionIds = sessions.map { it.id.value }.toSet()
        val referencedSets = validSets.filter { it.sessionId.value in sessionIds }
        return sessions to referencedSets
    }
}
