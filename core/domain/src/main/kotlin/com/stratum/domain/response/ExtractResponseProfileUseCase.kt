package com.stratum.domain.response

import com.stratum.domain.importing.ImportAnalysisMetadataRepository
import com.stratum.domain.importing.LiftTrainingInsightsRepository
import com.stratum.domain.importing.MuscleVolumeBaselineRepository
import com.stratum.domain.importing.TrainingHistoryRepository
import com.stratum.domain.lift.LiftRepository
import com.stratum.domain.lift.identity.LiftId
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * v1 runs only from import orchestration. Future WorkManager worker should call [invoke] when
 * `Instant.now() - metadata.lastExtractedAt >= RE_EXTRACT_MIN_INTERVAL_DAYS`.
 */
class ExtractResponseProfileUseCase @Inject constructor(
    private val trainingHistoryRepository: TrainingHistoryRepository,
    private val liftRepository: LiftRepository,
    private val liftTrainingInsightsRepository: LiftTrainingInsightsRepository,
    private val muscleVolumeBaselineRepository: MuscleVolumeBaselineRepository,
    private val importAnalysisMetadataRepository: ImportAnalysisMetadataRepository,
    private val responseEpochRepository: ResponseEpochRepository,
    private val volumeResponseObservationRepository: VolumeResponseObservationRepository,
    private val liftEffectivenessObservationRepository: LiftEffectivenessObservationRepository,
    private val experimentBlockRepository: ExperimentBlockRepository,
    private val responseProfileMetadataRepository: ResponseProfileMetadataRepository,
    private val extractor: ResponseObservationExtractor,
    private val experimentRegistry: ExperimentRegistry,
) {
    suspend operator fun invoke(
        provenLiftIds: Set<LiftId> = emptySet(),
    ): Result<ExtractResponseProfileSummary> = runCatching {
        val sessions = trainingHistoryRepository.getAllSessions()
        val sets = trainingHistoryRepository.getAllSets()
        val lifts = liftRepository.observeLifts().first()
        val metadata = importAnalysisMetadataRepository.get()
        val input =
            ResponseExtractionInput(
                sessions = sessions,
                sets = sets,
                lifts = lifts,
                provenLiftIds = provenLiftIds.ifEmpty { metadata?.provenLiftIds?.toSet() ?: emptySet() },
                specializeMuscles = emptySet(),
                liftInsights = liftTrainingInsightsRepository.getAll(),
                volumeBaselines = muscleVolumeBaselineRepository.getAll(),
                analysisWindowStart = metadata?.windowStart ?: sessions.minOf { it.startedAt },
                analysisWindowEnd = metadata?.windowEnd ?: sessions.maxOf { it.startedAt },
                activeExperiments = experimentRegistry.active(),
            )
        val output = extractor.extract(input)

        responseEpochRepository.replaceAll(output.epochs)
        volumeResponseObservationRepository.replaceAll(output.volumeResponses)
        liftEffectivenessObservationRepository.replaceAll(output.liftEffectiveness)
        experimentBlockRepository.replaceAll(experimentRegistry.active())
        responseProfileMetadataRepository.save(
            ResponseProfileMetadata(
                epochsAnalyzed = output.epochs.size,
                analysisWindowStart = input.analysisWindowStart,
                analysisWindowEnd = input.analysisWindowEnd,
                lastExtractedAt = Instant.now(),
            ),
        )

        ExtractResponseProfileSummary(
            epochsSegmented = output.epochs.size,
            musclesWithVolumeResponse = output.volumeResponses.map { it.muscle }.distinct().size,
            patternsWithEffectiveness =
                output.liftEffectiveness
                    .filter { it.rank == 1 && it.liftId in input.provenLiftIds }
                    .map { it.pattern }
                    .distinct()
                    .size,
            experimentsRecorded = experimentRegistry.active().size,
        )
    }
}
