package com.stratum.domain.response

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.identity.LiftId
import java.time.Instant

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

        return ranked.mapIndexed { index, score ->
            LiftEffectivenessObservation(
                pattern = pattern,
                liftId = score.liftId,
                epochId = epoch.id,
                effectivenessScore = score.effectivenessScore,
                peerLiftCount = peerCount,
                rank = index + 1,
                evidence = EvidenceLevel.Hypothesis,
                confidenceScore = score.confidenceScore,
            )
        }
    }

    private data class LiftScore(
        val liftId: LiftId,
        val effectivenessScore: Double,
        val confidenceScore: Double,
    )

    /**
     * Scores a lift by regressing its weekly e1RM outcome on its weekly hard-set volume (the
     * stimulus). The slope — e1RM gain per weekly hard set — is the effectiveness score, so a
     * lift whose strength rises with its own volume scores higher than one whose strength is
     * flat or regresses as volume climbs. When volume is collinear across weeks (e.g. a fixed
     * program), x falls back to week ordinal so the slope still captures progression over time.
     * This replaces the earlier endpoint-ratio (first/last delta ÷ mean volume), which
     * discarded intermediate weeks and could not detect volume↔progress correlation.
     */
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
        if (weeklyOutcomes.size < MIN_WEEKLY_POINTS_FOR_OLS) return null

        val ys = weeklyOutcomes.map { it.smoothedEstimatedOneRm ?: it.peakEstimatedOneRm ?: return null }
        val hardSets = weeklyOutcomes.map { it.hardSets }
        val xs =
            if (hardSets.distinct().size <= 1) {
                weeklyOutcomes.indices.map { it + 1.0 }
            } else {
                hardSets
            }
        val regression = ResponseRegressionSupport.linearRegression(xs, ys) ?: return null

        val effectivenessScore = regression.slope.coerceAtLeast(0.0)
        val sessionCoverage =
            (sessionCount.toDouble() / MIN_LIFT_SESSIONS_FOR_EFFECTIVENESS.toDouble()).coerceAtMost(1.0)
        val confidenceScore =
            (regression.rSquared * EFFECTIVENESS_R2_WEIGHT) +
                (sessionCoverage * EFFECTIVENESS_SESSION_WEIGHT)
        return LiftScore(
            liftId = liftId,
            effectivenessScore = effectivenessScore,
            confidenceScore = confidenceScore,
        )
    }
}

