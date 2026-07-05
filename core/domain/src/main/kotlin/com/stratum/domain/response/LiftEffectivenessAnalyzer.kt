package com.stratum.domain.response

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.training.toMovementPatternSet
import java.time.Instant

object LiftEffectivenessAnalyzer {
    fun analyze(
        pattern: MovementPattern,
        epoch: Epoch,
        input: ResponseExtractionInput,
        deloadWeeks: Set<Instant>,
    ): List<LiftEffectivenessObservation> {
        if (input.sets.any { it.movementPatternsSnapshot != null && it.movementPatternsSnapshot != 0 }) {
            return analyzeFromImportSnapshots(pattern, epoch, input)
        }

        val cleanSets = TrainingSignalCleaner.clean(input)
        val patternLifts = input.lifts.filter { pattern in it.movementPatterns }.map { it.id }
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

    private fun analyzeFromImportSnapshots(
        pattern: MovementPattern,
        epoch: Epoch,
        input: ResponseExtractionInput,
    ): List<LiftEffectivenessObservation> {
        val patternLiftIds =
            input.sets
                .filter { set ->
                    set.liftId != null &&
                        set.movementPatternsSnapshot != null &&
                        pattern in set.movementPatternsSnapshot.toMovementPatternSet()
                }
                .map { it.liftId!! }
                .distinct()
        if (patternLiftIds.isEmpty()) return emptyList()

        return patternLiftIds.map { liftId ->
            val snapshotSets =
                input.sets
                    .filter { it.liftId == liftId }
                    .map { importSet ->
                        com.stratum.domain.training.LoggedSet(
                            id = importSet.id,
                            sessionId = com.stratum.domain.training.identity.TrainingSessionId(importSet.sessionId.value),
                            exerciseNameRaw = importSet.exerciseNameRaw,
                            liftId = importSet.liftId,
                            setIndex = importSet.setIndex,
                            weight = importSet.weight,
                            reps = importSet.reps,
                            rir = importSet.rir,
                            completedAt = importSet.completedAt,
                            liftContext =
                                com.stratum.domain.training.LoggedLiftContext(
                                    liftName = importSet.exerciseNameRaw,
                                    movementPatternsBitmask = importSet.movementPatternsSnapshot ?: 0,
                                    primaryMusclesBitmask = importSet.primaryMusclesSnapshot ?: 0,
                                    secondaryMusclesBitmask = importSet.secondaryMusclesSnapshot ?: 0,
                                    kinematicProfile = com.stratum.domain.lift.KinematicProfile.NonAxialCompound,
                                    muscleLengthBias = com.stratum.domain.lift.MuscleLengthBias.MidRange,
                                ),
                        )
                    }
            val score = effectivenessFromSnapshot(pattern, snapshotSets, liftId)
            LiftEffectivenessObservation(
                pattern = pattern,
                liftId = liftId,
                epochId = epoch.id,
                effectivenessScore = score,
                peerLiftCount = patternLiftIds.size,
                rank = 1,
                evidence = EvidenceLevel.Hypothesis,
                confidenceScore = score.coerceAtMost(1.0),
            )
        }
    }

    fun effectivenessFromSnapshot(
        pattern: MovementPattern,
        sets: List<com.stratum.domain.training.LoggedSet>,
        liftId: LiftId,
    ): Double {
        val matching =
            sets.filter { set ->
                set.liftId == liftId &&
                    pattern in set.liftContext.movementPatternsBitmask.toMovementPatternSet()
            }
        return matching.size.toDouble().coerceAtLeast(if (matching.isNotEmpty()) 1.0 else 0.0)
    }
}

