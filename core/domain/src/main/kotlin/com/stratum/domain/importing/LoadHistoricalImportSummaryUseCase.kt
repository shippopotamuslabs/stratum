package com.stratum.domain.importing

import com.stratum.domain.lift.LiftRepository
import com.stratum.domain.response.EvidenceLevel
import com.stratum.domain.response.HYPOTHESIS_CONFIDENCE_PAYLOAD_MIN
import com.stratum.domain.response.LiftEffectivenessObservationRepository
import com.stratum.domain.response.MuscleVolumeLandmarkEstimateRepository
import com.stratum.domain.response.ResponseEvidenceMerger
import com.stratum.domain.response.VolumeResponseObservation
import com.stratum.domain.response.VolumeResponseObservationRepository
import com.stratum.domain.response.modeling.toHighlights
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class LoadHistoricalImportSummaryUseCase @Inject constructor(
    private val importAnalysisMetadataRepository: ImportAnalysisMetadataRepository,
    private val muscleVolumeBaselineRepository: MuscleVolumeBaselineRepository,
    private val liftTrainingInsightsRepository: LiftTrainingInsightsRepository,
    private val supersetAffinityRepository: SupersetAffinityRepository,
    private val volumeResponseObservationRepository: VolumeResponseObservationRepository,
    private val liftEffectivenessObservationRepository: LiftEffectivenessObservationRepository,
    private val muscleVolumeLandmarkEstimateRepository: MuscleVolumeLandmarkEstimateRepository,
    private val liftRepository: LiftRepository,
) {
    suspend operator fun invoke(): HistoricalImportSummary? {
        val metadata = importAnalysisMetadataRepository.get() ?: return null
        val liftsById = liftRepository.observeLifts().first().associateBy { it.id }
        val baselines = muscleVolumeBaselineRepository.getAll()
        val liftInsights = liftTrainingInsightsRepository.getAll()
        val effectiveness = liftEffectivenessObservationRepository.getAll()
        val volumeResponses = volumeResponseObservationRepository.getAll()
        val volumeHighlights =
            muscleVolumeLandmarkEstimateRepository.getAll()
                .filter { estimate -> baselines.any { it.muscle == estimate.muscle && it.meanWeeklyHardSets > 0.0 } }
                .sortedByDescending { estimate ->
                    baselines.firstOrNull { it.muscle == estimate.muscle }?.meanWeeklyHardSets ?: 0.0
                }
                .toHighlights()

        return HistoricalImportSummary(
            totalSessions = metadata.totalSessions,
            totalSets = metadata.totalSets,
            analysisWeeks = analysisWeekCount(metadata),
            enabledLiftCount = liftsById.values.count { it.enabled },
            provenLiftCount = metadata.provenLiftIds.size,
            customLiftsCreated = liftsById.values.count { lift -> lift.isCustom && lift.enabled },
            liftsWithInsights = liftInsights.size,
            supersetPairs = supersetAffinityRepository.getAll().size,
            volumeHighlights = volumeHighlights,
            effectiveLifts = EffectiveLiftSummaryBuilder.build(effectiveness, liftsById),
            volumeResponses = buildVolumeResponseHighlights(volumeResponses),
        )
    }

    private fun analysisWeekCount(metadata: ImportAnalysisMetadata): Int {
        val days = ChronoUnit.DAYS.between(metadata.windowStart, metadata.windowEnd)
        return (days / 7 + 1).toInt().coerceAtLeast(1)
    }

    private fun buildVolumeResponseHighlights(
        observations: List<VolumeResponseObservation>,
    ): List<VolumeResponseHighlight> =
        observations
            .groupBy { it.muscle }
            .mapNotNull { (muscle, muscleObservations) ->
                val mergedEvidence = ResponseEvidenceMerger.mergeVolumeEvidence(muscleObservations)
                if (mergedEvidence == EvidenceLevel.InsufficientData) return@mapNotNull null
                val representative =
                    when (mergedEvidence) {
                        EvidenceLevel.Proven ->
                            muscleObservations.maxBy { it.confidenceScore }.copy(evidence = EvidenceLevel.Proven)
                        else -> muscleObservations.maxBy { it.confidenceScore }
                    }
                if (
                    representative.evidence == EvidenceLevel.Hypothesis &&
                    representative.confidenceScore < HYPOTHESIS_CONFIDENCE_PAYLOAD_MIN
                ) {
                    return@mapNotNull null
                }
                VolumeResponseHighlight(
                    muscle = muscle,
                    evidence = representative.evidence,
                    respondsToVolume = representative.correlationSlope > 0,
                )
            }
            .sortedByDescending { highlight ->
                observations
                    .filter { it.muscle == highlight.muscle }
                    .maxOf { it.confidenceScore }
            }
}
