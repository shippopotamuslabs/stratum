package com.stratum.domain.response

import com.stratum.domain.importing.ImportAnalysisMetadataRepository
import com.stratum.domain.importing.InsightsAnalysisScope
import com.stratum.domain.importing.LiftTrainingInsightsRepository
import com.stratum.domain.importing.MuscleVolumeBaselineRepository
import com.stratum.domain.lift.LiftRepository
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.training.TrainingLogRepository
import com.stratum.domain.training.toMovementPatternSet
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Runs the full dose-response pipeline ([ResponseObservationExtractor] → Model-tier tables) and
 * replaces the extracted profile via [ResponseProfilePersistencePort.replaceExtractedProfile].
 *
 * Call sites:
 * - Import orchestration ([com.stratum.domain.importing.PersistImportedHistoryUseCase]) with
 *   [InsightsAnalysisScope.ImportOnly] so a re-import never pulls live sessions into the import
 *   provenance window.
 * - Background worker ([RE_EXTRACT_MIN_COMPLETED_WORKOUTS] completed sessions since last extract)
 *   with [InsightsAnalysisScope.All] so live workouts refresh outcome indices, volume posture,
 *   and MEV/PV/RC landmarks.
 */
class ExtractResponseProfileUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val liftRepository: LiftRepository,
    private val liftTrainingInsightsRepository: LiftTrainingInsightsRepository,
    private val muscleVolumeBaselineRepository: MuscleVolumeBaselineRepository,
    private val importAnalysisMetadataRepository: ImportAnalysisMetadataRepository,
    private val responseProfilePersistence: ResponseProfilePersistencePort,
    private val extractor: ResponseObservationExtractor,
    private val experimentRegistry: ExperimentRegistry,
) {
    suspend operator fun invoke(
        provenLiftIds: Set<LiftId> = emptySet(),
        scope: InsightsAnalysisScope = InsightsAnalysisScope.ImportOnly,
    ): Result<ExtractResponseProfileSummary> = runCatching {
        // All-history refresh must not clobber proven-lift provenance recorded at import.
        val effectiveProvenLiftIds =
            if (scope == InsightsAnalysisScope.All && provenLiftIds.isEmpty()) {
                importAnalysisMetadataRepository.get()?.provenLiftIds?.toSet() ?: emptySet()
            } else {
                provenLiftIds.ifEmpty {
                    importAnalysisMetadataRepository.get()?.provenLiftIds?.toSet() ?: emptySet()
                }
            }
        val sessions =
            if (scope == InsightsAnalysisScope.All) {
                trainingLogRepository.getAllSessions()
            } else {
                trainingLogRepository.getImportSessions()
            }
        val sets =
            if (scope == InsightsAnalysisScope.All) {
                trainingLogRepository.getAllSets()
            } else {
                trainingLogRepository.getImportSets()
            }
        val lifts = liftRepository.observeLifts().first()
        val metadata = importAnalysisMetadataRepository.get()
        // ImportOnly keeps the import-analysis window for provenance. All uses the full session
        // span so live workouts expand the modeling window past the original import bounds.
        val analysisWindowStart =
            if (scope == InsightsAnalysisScope.All) {
                sessions.minOfOrNull { it.startedAt } ?: Instant.EPOCH
            } else {
                metadata?.windowStart
                    ?: sessions.minOfOrNull { it.startedAt }
                    ?: Instant.EPOCH
            }
        val analysisWindowEnd =
            if (scope == InsightsAnalysisScope.All) {
                sessions.maxOfOrNull { it.startedAt } ?: Instant.now()
            } else {
                metadata?.windowEnd
                    ?: sessions.maxOfOrNull { it.startedAt }
                    ?: Instant.now()
            }
        val input =
            ResponseExtractionInput(
                sessions = sessions,
                sets = sets,
                lifts = lifts,
                provenLiftIds = effectiveProvenLiftIds,
                specializeMuscles = emptySet(),
                liftInsights = liftTrainingInsightsRepository.getAll(),
                volumeBaselines = muscleVolumeBaselineRepository.getAll(),
                analysisWindowStart = analysisWindowStart,
                analysisWindowEnd = analysisWindowEnd,
                activeExperiments = experimentRegistry.active(),
            )
        val output = extractor.extract(input)

        val modeling = output.modeling
        responseProfilePersistence.replaceExtractedProfile(
            ExtractedResponseProfile(
                epochs = output.epochs,
                volumeResponses = output.volumeResponses,
                liftEffectiveness = output.liftEffectiveness,
                experimentBlocks = experimentRegistry.active(),
                metadata =
                    ResponseProfileMetadata(
                        epochsAnalyzed = output.epochs.size,
                        analysisWindowStart = input.analysisWindowStart,
                        analysisWindowEnd = input.analysisWindowEnd,
                        lastExtractedAt = Instant.now(),
                    ),
                weeklyStimulus = modeling?.weeklyStimulus.orEmpty(),
                liftPerformance = modeling?.liftPerformance.orEmpty(),
                outcomeIndices = modeling?.outcomeIndices.orEmpty(),
                volumeSnapshots = modeling?.volumeSnapshots.orEmpty(),
                modelDiagnostics = modeling?.diagnostics.orEmpty(),
                dosePoints = modeling?.dosePoints.orEmpty(),
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

    companion object {
        data class SnapshotStimulusResult(
            val horizontalPushCredit: Double,
            val verticalPullCredit: Double,
        )

        fun extractStimulusFromSnapshots(
            sets: List<com.stratum.domain.training.LoggedSet>,
            mutatedCatalogMovementBitmask: Int,
        ): SnapshotStimulusResult {
            val horizontalPushCredit =
                sets.sumOf { set ->
                    if (
                        com.stratum.domain.lift.MovementPattern.HorizontalPress in
                        set.liftContext.movementPatternsBitmask.toMovementPatternSet()
                    ) {
                        1.0
                    } else {
                        0.0
                    }
                }
            val verticalPullFromCatalog =
                if (mutatedCatalogMovementBitmask and (1 shl com.stratum.domain.lift.MovementPattern.VerticalPull.ordinal) != 0) {
                    1.0
                } else {
                    0.0
                }
            val verticalPullCredit =
                if (
                    sets.any {
                        com.stratum.domain.lift.MovementPattern.VerticalPull in
                            it.liftContext.movementPatternsBitmask.toMovementPatternSet()
                    }
                ) {
                    verticalPullFromCatalog
                } else {
                    0.0
                }
            return SnapshotStimulusResult(
                horizontalPushCredit = horizontalPushCredit,
                verticalPullCredit = verticalPullCredit,
            )
        }

        fun extractVolumeBaselinesFromSnapshots(
            sets: List<com.stratum.domain.training.LoggedSet>,
        ): List<com.stratum.domain.importing.MuscleVolumeBaseline> =
            com.stratum.domain.importing.TrainingInsightsExtractor.computeVolumeBaselinesFromSnapshots(sets)
    }
}
