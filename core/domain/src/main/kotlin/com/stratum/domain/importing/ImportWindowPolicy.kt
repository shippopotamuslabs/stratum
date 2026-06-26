package com.stratum.domain.importing

import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Import uses the full converted history for insights (volume landmarks, efficacy, fatigue).
 * Lift catalog enablement and [ImportAnalysisMetadata.provenLiftIds] use a recent window only —
 * reflecting what the user is actively training now, not years-old exercise variety.
 */
object ImportWindowPolicy {
    /** ~6 months of recent training for Tier A/B lift enablement. */
    const val LIFT_ENABLEMENT_WINDOW_DAYS = 183L

    fun liftEnablementCutoff(sets: List<LoggedSet>): Instant? {
        if (sets.isEmpty()) return null
        val latest = sets.maxOf { it.completedAt }
        return latest.minus(LIFT_ENABLEMENT_WINDOW_DAYS, ChronoUnit.DAYS)
    }

    fun recentSetsForLiftEnablement(sets: List<LoggedSet>): List<LoggedSet> {
        val cutoff = liftEnablementCutoff(sets) ?: return emptyList()
        return sets.filter { it.completedAt >= cutoff }
    }

    fun isInLiftEnablementWindow(set: LoggedSet, cutoff: Instant): Boolean =
        !set.completedAt.isBefore(cutoff)
}
