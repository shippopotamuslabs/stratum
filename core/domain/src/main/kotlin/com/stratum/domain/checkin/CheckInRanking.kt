package com.stratum.domain.checkin

import com.stratum.domain.lift.KinematicProfile

/**
 * Shared contender / swap-enrich ranking keys so reservation order matches one-lever batch
 * selection (G4). Isolation before compound; Dropping before Stable; lower sets first.
 */
object CheckInRanking {

    fun contenderComparator(): Comparator<CheckInProvisionalRecommendation> =
        compareBy<CheckInProvisionalRecommendation> { it.kind.sortPriority() }
            .thenByDescending { it.input.performanceTrend == PerformanceTrend.Dropping }
            .thenBy { it.input.currentSetCount }
            .thenBy { isolationRank(it.input.kinematicProfile) }
            .thenByDescending { it.input.rotationTenureWeeks }
            .thenBy { it.input.liftName.lowercase() }

    /**
     * Same keys as [contenderComparator] (without kind — all Swap) for pre-batch swap enrichment.
     */
    fun swapEnrichInputComparator(): Comparator<LiftCheckInInput> =
        compareByDescending<LiftCheckInInput> { it.performanceTrend == PerformanceTrend.Dropping }
            .thenBy { it.currentSetCount }
            .thenBy { isolationRank(it.kinematicProfile) }
            .thenByDescending { it.rotationTenureWeeks }
            .thenBy { it.liftName.lowercase() }

    /** Isolation first (0), unknown next, compounds last. */
    fun isolationRank(profile: KinematicProfile?): Int =
        when (profile) {
            KinematicProfile.Isolation -> 0
            null -> 1
            else -> 2
        }
}
