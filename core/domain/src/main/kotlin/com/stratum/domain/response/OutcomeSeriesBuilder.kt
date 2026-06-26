package com.stratum.domain.response

import com.stratum.domain.lift.identity.LiftId

object OutcomeSeriesBuilder {
    fun weeklyAnchorOutcomes(
        epoch: Epoch,
        anchorLiftId: LiftId,
        cleanSets: List<CleanTrainingSet>,
    ): List<WeeklyTrainingSignal> {
        val working = WeeklySignalSupport.workingSetsInEpoch(epoch, cleanSets, anchorLiftId)
        return OutlierGuard.winsorizeWeeklySignals(
            WeeklySignalSupport.groupByWeek(working).map { (weekStart, weekSets) ->
                val e1rms = weekSets.map { it.estimatedOneRm }.sortedDescending()
                val peak = e1rms.first()
                val topTwoMean =
                    if (e1rms.size >= 2) {
                        (e1rms[0] + e1rms[1]) / 2.0
                    } else {
                        peak
                    }
                WeeklyTrainingSignal(
                    weekStart = weekStart,
                    liftId = anchorLiftId,
                    muscle = null,
                    hardSets = weekSets.size,
                    sessions = weekSets.map { it.sourceSet.sessionId }.distinct().size,
                    meanRir = weekSets.mapNotNull { it.sourceSet.rir }.average().takeIf { weekSets.any { s -> s.sourceSet.rir != null } },
                    intensityProxy = IntensityProxyCalculator.forWeek(weekSets, anchorLiftId),
                    peakEstimatedOneRm = peak,
                    smoothedEstimatedOneRm = topTwoMean,
                    qualityFlags = weekSets.flatMap { it.qualityFlags }.toSet(),
                )
            }.sortedBy { it.weekStart },
        )
    }
}
