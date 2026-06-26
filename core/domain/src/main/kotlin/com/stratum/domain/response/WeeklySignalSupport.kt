package com.stratum.domain.response

import com.stratum.domain.lift.identity.LiftId
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

internal object WeeklySignalSupport {
    fun weekStartFor(instant: Instant): Instant {
        val monday =
            instant.atZone(ZoneOffset.UTC)
                .toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return monday.atStartOfDay().toInstant(ZoneOffset.UTC)
    }

    fun workingSetsInEpoch(
        epoch: Epoch,
        cleanSets: List<CleanTrainingSet>,
        liftId: LiftId? = null,
    ): List<CleanTrainingSet> =
        cleanSets.filter { row ->
            row.isWorkingSet &&
                row.sourceSet.liftId != null &&
                row.sourceSet.completedAt in epoch.start..epoch.end &&
                (liftId == null || row.sourceSet.liftId == liftId)
        }

    fun groupByWeek(
        sets: List<CleanTrainingSet>,
    ): Map<Instant, List<CleanTrainingSet>> =
        sets.groupBy { weekStartFor(it.sourceSet.completedAt) }
}
