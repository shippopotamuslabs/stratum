package com.stratum.domain.importing

import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.response.Epoch
import com.stratum.domain.response.EpochSplitReason
import com.stratum.domain.response.EvidenceLevel
import com.stratum.domain.response.HYPOTHESIS_CONFIDENCE_PAYLOAD_MIN
import com.stratum.domain.response.LiftEffectivenessObservation
import com.stratum.domain.response.OutcomeSeriesBuilder
import com.stratum.domain.response.ResponseEvidenceMerger
import com.stratum.domain.response.ResponseExtractionInput
import com.stratum.domain.response.StimulusSeriesBuilder
import com.stratum.domain.response.TrainingSignalCleaner
import com.stratum.domain.response.VolumeResponseObservation
import com.stratum.domain.response.identity.EpochId
import com.stratum.domain.onboarding.MuscleGroup
import java.time.Instant

object EffectiveLiftSummaryBuilder {
    fun build(
        observations: List<LiftEffectivenessObservation>,
        liftsById: Map<LiftId, Lift>,
    ): List<EffectiveLiftHighlight> {
        val eligible =
            observations.filter { observation ->
                observation.rank == 1 &&
                    observation.evidence != EvidenceLevel.InsufficientData &&
                    (
                        observation.evidence == EvidenceLevel.Proven ||
                            observation.confidenceScore >= HYPOTHESIS_CONFIDENCE_PAYLOAD_MIN
                        )
            }

        val byMuscle = mutableMapOf<MuscleGroup, MutableList<LiftEffectivenessObservation>>()
        for (observation in eligible) {
            val lift = liftsById[observation.liftId] ?: continue
            for (muscle in lift.primaryMuscles) {
                byMuscle.getOrPut(muscle) { mutableListOf() }.add(observation)
            }
        }

        val highlights =
            byMuscle.mapNotNull { (muscle, muscleObservations) ->
                val representative = muscleObservations.maxBy { it.effectivenessScore }
                val rankOnes = muscleObservations.filter { it.rank == 1 }
                val mergedEvidence = ResponseEvidenceMerger.mergeLiftEffectivenessEvidence(rankOnes)
                val evidence =
                    if (mergedEvidence == EvidenceLevel.Proven) {
                        EvidenceLevel.Proven
                    } else {
                        representative.evidence
                    }
                liftsById[representative.liftId]?.let { lift ->
                    EffectiveLiftHighlight(
                        liftId = representative.liftId,
                        liftName = lift.name,
                        muscle = muscle,
                        evidence = evidence,
                        effectivenessScore = representative.effectivenessScore,
                        effectivenessTier = UserEffectivenessTier.ModeratelyEffective,
                        confidenceTier =
                            efficacyConfidenceTier(
                                evidence = evidence,
                                epochCount = muscleObservations.map { it.epochId }.distinct().size,
                                peerLiftCount = rankOnes.map { it.liftId }.distinct().size,
                            ),
                    )
                }
            }
                .sortedByDescending { it.effectivenessScore }

        return assignEffectivenessTiers(highlights)
    }

    private fun efficacyConfidenceTier(
        evidence: EvidenceLevel,
        epochCount: Int,
        peerLiftCount: Int,
    ): LandmarkConfidenceTier =
        when {
            evidence == EvidenceLevel.Proven && epochCount >= 2 && peerLiftCount >= 2 ->
                LandmarkConfidenceTier.High
            evidence == EvidenceLevel.Proven ||
                (epochCount >= 1 && peerLiftCount >= 2) ->
                LandmarkConfidenceTier.Medium
            else -> LandmarkConfidenceTier.Low
        }

    private fun assignEffectivenessTiers(
        lifts: List<EffectiveLiftHighlight>,
    ): List<EffectiveLiftHighlight> {
        if (lifts.isEmpty()) return lifts
        val count = lifts.size
        return lifts.mapIndexed { index, lift ->
            val tier =
                when {
                    count == 1 ->
                        if (lift.effectivenessScore > 0.0) {
                            UserEffectivenessTier.ModeratelyEffective
                        } else {
                            UserEffectivenessTier.SomewhatEffective
                        }
                    count == 2 ->
                        if (index == 0) {
                            UserEffectivenessTier.VeryEffective
                        } else {
                            UserEffectivenessTier.SomewhatEffective
                        }
                    index < count / 3 -> UserEffectivenessTier.VeryEffective
                    index < (2 * count) / 3 -> UserEffectivenessTier.ModeratelyEffective
                    else -> UserEffectivenessTier.SomewhatEffective
                }
            lift.copy(effectivenessTier = tier)
        }
    }
}

object VolumeLandmarkSummaryBuilder {
    fun build(
        metadata: ImportAnalysisMetadata,
        sessions: List<HistoricalSession>,
        sets: List<LoggedSet>,
        lifts: List<Lift>,
        baselines: List<MuscleVolumeBaseline>,
        volumeResponses: List<VolumeResponseObservation>,
    ): List<MuscleVolumeHighlight> {
        if (baselines.isEmpty()) return emptyList()

        val epoch = analysisEpoch(metadata)
        val extractionInput =
            ResponseExtractionInput(
                sessions = sessions,
                sets = sets,
                lifts = lifts,
                provenLiftIds = metadata.provenLiftIds.toSet(),
                specializeMuscles = emptySet(),
                liftInsights = emptyList(),
                volumeBaselines = baselines,
                analysisWindowStart = metadata.windowStart,
                analysisWindowEnd = metadata.windowEnd,
                activeExperiments = emptyList(),
            )
        val cleanSets = TrainingSignalCleaner.clean(extractionInput)
        val weeklyByMuscle = StimulusSeriesBuilder.weeklyHardSetsByMuscle(epoch, cleanSets, lifts)
        val volumeResponseByMuscle = volumeResponses.groupBy { it.muscle }

        return baselines
            .filter { it.meanWeeklyHardSets > 0.0 }
            .sortedByDescending { it.meanWeeklyHardSets }
            .map { baseline ->
                val muscle = baseline.muscle
                val response = volumeResponseByMuscle[muscle]?.maxByOrNull { it.confidenceScore }
                val anchorLiftId = response?.anchorLiftId ?: anchorLiftForMuscle(muscle, lifts)
                val weeklyPoints =
                    buildWeeklyPoints(
                        muscle = muscle,
                        epoch = epoch,
                        cleanSets = cleanSets,
                        weeklyByMuscle = weeklyByMuscle,
                        anchorLiftId = anchorLiftId,
                    )
                val rirCoverage = rirCoverageForMuscle(muscle, cleanSets, lifts)
                val estimate =
                    VolumeLandmarkEstimator.estimate(
                        VolumeLandmarkEstimator.Input(
                            muscle = muscle,
                            weeklyPoints = weeklyPoints,
                            volumeResponse = response,
                            rirCoverage = rirCoverage,
                        ),
                    )
                MuscleVolumeHighlight(
                    muscle = muscle,
                    estimatedMev = estimate.estimatedMev,
                    estimatedMrv = estimate.estimatedMrv,
                    confidenceTier = estimate.confidenceTier,
                )
            }
    }

    private fun analysisEpoch(metadata: ImportAnalysisMetadata): Epoch =
        Epoch(
            id = EpochId("import-summary"),
            start = metadata.windowStart,
            end = metadata.windowEnd,
            anchorLiftIds = emptySet(),
            splitReason = EpochSplitReason.WindowEnd,
        )

    private fun anchorLiftForMuscle(muscle: MuscleGroup, lifts: List<Lift>): LiftId? =
        lifts.firstOrNull { muscle in it.primaryMuscles }?.id

    private fun buildWeeklyPoints(
        muscle: MuscleGroup,
        epoch: Epoch,
        cleanSets: List<com.stratum.domain.response.CleanTrainingSet>,
        weeklyByMuscle: Map<MuscleGroup, List<com.stratum.domain.response.WeeklyTrainingSignal>>,
        anchorLiftId: LiftId?,
    ): List<MuscleWeeklyVolumePoint> {
        val muscleWeeks = weeklyByMuscle[muscle].orEmpty().associateBy { it.weekStart }
        val outcomeByWeek =
            anchorLiftId?.let { liftId ->
                OutcomeSeriesBuilder.weeklyAnchorOutcomes(epoch, liftId, cleanSets)
                    .associateBy { it.weekStart }
            } ?: emptyMap()

        val weekStarts = muscleWeeks.keys.union(outcomeByWeek.keys).sorted()
        val rawPeaks = weekStarts.map { weekStart ->
            outcomeByWeek[weekStart]?.smoothedEstimatedOneRm ?: outcomeByWeek[weekStart]?.peakEstimatedOneRm
        }
        // Smooth the weekly e1RM series with a centered rolling mean so a single good/bad week
        // does not drive the week-over-week delta that MEV/MRV estimation keys off of. Weeks with
        // null outcome are excluded from the window; the delta is null until two smoothed points
        // exist. This mirrors OutlierGuard's intent without discarding legitimate signal.
        val smoothedPeaks = centeredRollingMean(rawPeaks, VolumeLandmarkEstimatorConstants.DELTA_SMOOTHING_WINDOW)
        return weekStarts.mapIndexed { index, weekStart ->
            val hardSets = muscleWeeks[weekStart]?.hardSets ?: 0.0
            val peak = smoothedPeaks[index]
            val previousPeak = smoothedPeaks.getOrNull(index - 1)
            val delta = if (peak != null && previousPeak != null) peak - previousPeak else null
            MuscleWeeklyVolumePoint(
                weekStart = weekStart,
                hardSets = hardSets,
                anchorPeakE1rm = rawPeaks[index] ?: peak,
                weekOverWeekE1rmDelta = delta,
            )
        }
    }

    /**
     * Centered rolling mean over [values], skipping nulls. Returns null for indices whose window
     * contains no non-null values. Window is symmetric: [halfWindow] neighbors on each side.
     */
    internal fun centeredRollingMean(values: List<Double?>, halfWindow: Int): List<Double?> {
        if (values.isEmpty()) return emptyList()
        return values.indices.map { index ->
            val from = (index - halfWindow).coerceAtLeast(0)
            val to = (index + halfWindow).coerceAtMost(values.lastIndex)
            val window = values.subList(from, to + 1).filterNotNull()
            if (window.isEmpty()) null else window.average()
        }
    }

    private fun rirCoverageForMuscle(
        muscle: MuscleGroup,
        cleanSets: List<com.stratum.domain.response.CleanTrainingSet>,
        lifts: List<Lift>,
    ): Double {
        val liftById = lifts.associateBy { it.id }
        val muscleSets =
            cleanSets.filter { row ->
                val lift = liftById[row.sourceSet.liftId] ?: return@filter false
                muscle in lift.primaryMuscles && row.isWorkingSet
            }
        if (muscleSets.isEmpty()) return 0.0
        val withRir = muscleSets.count { it.sourceSet.rir != null }
        return withRir.toDouble() / muscleSets.size.toDouble()
    }
}
