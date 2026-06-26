package com.stratum.domain.response

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.identity.LiftId
import java.time.Instant
import java.time.temporal.ChronoUnit

object LiftEffectivenessAnalyzer {
    fun analyze(
        pattern: MovementPattern,
        epoch: Epoch,
        input: ResponseExtractionInput,
        deloadWeeks: Set<Instant>,
    ): List<LiftEffectivenessObservation> {
        val cleanSets = TrainingSignalCleaner.clean(input)
        val patternLifts = input.lifts.filter { it.movementPattern == pattern }.map { it.id }
        val scoredLifts =
            patternLifts.mapNotNull { liftId ->
                scoreLift(liftId, epoch, cleanSets, deloadWeeks)
            }
        if (scoredLifts.isEmpty()) return emptyList()

        val ranked = scoredLifts.sortedByDescending { it.effectivenessScore }
        val peerCount = ranked.size
        if (peerCount < MIN_PEERS_FOR_PATTERN_RANK) {
            val only = ranked.first()
            return listOf(
                LiftEffectivenessObservation(
                    pattern = pattern,
                    liftId = only.liftId,
                    epochId = epoch.id,
                    effectivenessScore = only.effectivenessScore,
                    peerLiftCount = peerCount,
                    rank = 1,
                    evidence = EvidenceLevel.Hypothesis,
                    confidenceScore = only.confidenceScore,
                ),
            )
        }

        val top = ranked[0]
        val runnerUp = ranked[1]
        val marginBeatsRunnerUp =
            runnerUp.effectivenessScore <= 0.0 ||
                (top.effectivenessScore - runnerUp.effectivenessScore) / runnerUp.effectivenessScore >=
                EFFECTIVENESS_RANK_MARGIN

        return ranked.mapIndexed { index, score ->
            LiftEffectivenessObservation(
                pattern = pattern,
                liftId = score.liftId,
                epochId = epoch.id,
                effectivenessScore = score.effectivenessScore,
                peerLiftCount = peerCount,
                rank = index + 1,
                evidence =
                    if (index == 0 && marginBeatsRunnerUp) {
                        EvidenceLevel.Hypothesis
                    } else {
                        EvidenceLevel.Hypothesis
                    },
                confidenceScore = score.confidenceScore,
            )
        }
    }

    private data class LiftScore(
        val liftId: LiftId,
        val effectivenessScore: Double,
        val confidenceScore: Double,
    )

    private fun scoreLift(
        liftId: LiftId,
        epoch: Epoch,
        cleanSets: List<CleanTrainingSet>,
        deloadWeeks: Set<Instant>,
    ): LiftScore? {
        val working =
            WeeklySignalSupport.workingSetsInEpoch(epoch, cleanSets, liftId)
                .filter { WeeklySignalSupport.weekStartFor(it.sourceSet.completedAt) !in deloadWeeks }
        val sessionCount = working.map { it.sourceSet.sessionId }.distinct().size
        if (sessionCount < MIN_LIFT_SESSIONS_FOR_EFFECTIVENESS) return null

        val weeklyOutcomes =
            OutcomeSeriesBuilder.weeklyAnchorOutcomes(epoch, liftId, cleanSets)
                .filter { it.weekStart !in deloadWeeks }
                .sortedBy { it.weekStart }
        if (weeklyOutcomes.size < 2) return null

        val firstOutcome = weeklyOutcomes.first().smoothedEstimatedOneRm ?: weeklyOutcomes.first().peakEstimatedOneRm
        val lastOutcome = weeklyOutcomes.last().smoothedEstimatedOneRm ?: weeklyOutcomes.last().peakEstimatedOneRm
        if (firstOutcome == null || lastOutcome == null) return null

        val weekSpan =
            (ChronoUnit.DAYS.between(weeklyOutcomes.first().weekStart, weeklyOutcomes.last().weekStart) / 7)
                .coerceAtLeast(1)
                .toDouble()
        val deltaPerWeek = (lastOutcome - firstOutcome) / weekSpan
        val meanWeeklyHardSets =
            WeeklySignalSupport.groupByWeek(working).values.map { it.size.toDouble() }.average()
                .coerceAtLeast(1.0)
        val effectivenessScore = deltaPerWeek / meanWeeklyHardSets
        val confidenceScore = (sessionCount.toDouble() / MIN_LIFT_SESSIONS_FOR_EFFECTIVENESS.toDouble()).coerceAtMost(1.0)
        return LiftScore(
            liftId = liftId,
            effectivenessScore = effectivenessScore,
            confidenceScore = confidenceScore,
        )
    }
}
