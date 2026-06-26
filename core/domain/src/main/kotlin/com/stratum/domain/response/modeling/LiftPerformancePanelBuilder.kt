@file:Suppress("MagicNumber")

package com.stratum.domain.response.modeling

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.response.CleanTrainingSet
import com.stratum.domain.response.Epoch
import com.stratum.domain.response.IntensityProxyCalculator
import com.stratum.domain.response.OutlierGuard
import com.stratum.domain.response.TrainingSignalQualityFlag
import com.stratum.domain.response.WeeklySignalSupport
import kotlin.math.ln

object LiftPerformancePanelBuilder {
    fun build(
        epoch: Epoch,
        cleanSets: List<CleanTrainingSet>,
    ): List<LiftWeeklyPerformance> {
        val liftIds =
            cleanSets.mapNotNull { it.sourceSet.liftId }
                .distinct()
        return liftIds.flatMap { liftId ->
            buildForLift(epoch, liftId, cleanSets)
        }.sortedWith(compareBy({ it.liftId.value }, { it.weekStart }))
    }

    private fun buildForLift(
        epoch: Epoch,
        liftId: LiftId,
        cleanSets: List<CleanTrainingSet>,
    ): List<LiftWeeklyPerformance> {
        val working = WeeklySignalSupport.workingSetsInEpoch(epoch, cleanSets, liftId)
        if (working.isEmpty()) return emptyList()
        val weeklySignals =
            OutlierGuard.winsorizeWeeklySignals(
                WeeklySignalSupport.groupByWeek(working).map { (weekStart, weekSets) ->
                    val e1rms =
                        weekSets.map { it.rirAdjustedEstimatedOneRm ?: it.estimatedOneRm }.sortedDescending()
                    val peak = e1rms.first()
                    val smoothed =
                        if (e1rms.size >= 2) {
                            (e1rms[0] + e1rms[1]) / 2.0
                        } else {
                            peak
                        }
                    com.stratum.domain.response.WeeklyTrainingSignal(
                        weekStart = weekStart,
                        liftId = liftId,
                        muscle = null,
                        hardSets = weekSets.size.toDouble(),
                        sessions = weekSets.map { it.sourceSet.sessionId }.distinct().size,
                        meanRir = weekSets.mapNotNull { it.sourceSet.rir }.average()
                            .takeIf { weekSets.any { s -> s.sourceSet.rir != null } },
                        intensityProxy = IntensityProxyCalculator.forWeek(weekSets, liftId),
                        peakEstimatedOneRm = peak,
                        smoothedEstimatedOneRm = smoothed,
                        qualityFlags = weekSets.flatMap { it.qualityFlags }.toSet(),
                    )
                }.sortedBy { it.weekStart },
            )
        return weeklySignals.map { signal ->
            val e1rm = signal.smoothedEstimatedOneRm ?: signal.peakEstimatedOneRm ?: return@map null
            val flags = signal.qualityFlags
            val reliability = liftReliability(flags, signal.sessions)
            LiftWeeklyPerformance(
                liftId = liftId,
                weekStart = signal.weekStart,
                peakE1rm = signal.peakEstimatedOneRm ?: e1rm,
                smoothedE1rm = e1rm,
                logE1rm = ln(e1rm.coerceAtLeast(1.0)),
                hardSets = signal.hardSets,
                sessions = signal.sessions,
                meanRir = signal.meanRir,
                reliability = reliability,
                qualityFlags = flags,
            )
        }.filterNotNull()
    }

    private fun liftReliability(
        flags: Set<TrainingSignalQualityFlag>,
        sessions: Int,
    ): Double {
        var score = 1.0
        if (TrainingSignalQualityFlag.MissingRir in flags) score *= 0.85
        if (TrainingSignalQualityFlag.MixedUnits in flags) score *= 0.5
        if (TrainingSignalQualityFlag.OutlierWinsorized in flags) score *= 0.9
        if (TrainingSignalQualityFlag.SuspiciousE1rmJump in flags) score *= 0.75
        if (sessions <= 1) score *= 0.9
        return score.coerceIn(0.1, 1.0)
    }
}
