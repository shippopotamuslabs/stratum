package com.stratum.domain.program

import com.stratum.domain.importing.ImportAnalysisMetadataRepository
import com.stratum.domain.importing.LiftTrainingInsightsRepository
import com.stratum.domain.importing.MuscleVolumeBaselineRepository
import com.stratum.domain.importing.SupersetAffinityRepository
import com.stratum.domain.importing.TrainingHistoryRepository
import com.stratum.domain.importing.VolumeLandmarkSummaryBuilder
import com.stratum.domain.response.LiftEffectivenessObservationRepository
import com.stratum.domain.response.ResponseProfileMetadataRepository
import com.stratum.domain.response.VolumeResponseObservationRepository
import com.stratum.domain.lift.LiftRepository
import com.stratum.domain.onboarding.ObserveSpecializationUseCase
import com.stratum.domain.onboarding.UserTrainingProfileRepository
import com.stratum.domain.onboarding.UserTrainingProfile
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class LoadProgramGenerationPayloadUseCase @Inject constructor(
    private val userTrainingProfileRepository: UserTrainingProfileRepository,
    private val observeSpecialization: ObserveSpecializationUseCase,
    private val liftRepository: LiftRepository,
    private val liftTrainingInsightsRepository: LiftTrainingInsightsRepository,
    private val supersetAffinityRepository: SupersetAffinityRepository,
    private val muscleVolumeBaselineRepository: MuscleVolumeBaselineRepository,
    private val importAnalysisMetadataRepository: ImportAnalysisMetadataRepository,
    private val volumeResponseObservationRepository: VolumeResponseObservationRepository,
    private val liftEffectivenessObservationRepository: LiftEffectivenessObservationRepository,
    private val responseProfileMetadataRepository: ResponseProfileMetadataRepository,
    private val trainingHistoryRepository: TrainingHistoryRepository,
    private val promptBuilder: ProgramGenerationPromptBuilder,
) {
    suspend operator fun invoke(): String {
        val profile = userTrainingProfileRepository.observeProfile().first() ?: UserTrainingProfile.Empty
        val specialization = observeSpecialization().first()
        val insightsByLift = liftTrainingInsightsRepository.getAll().associateBy { it.liftId }
        val metadata = importAnalysisMetadataRepository.get()
        val provenIds = metadata?.provenLiftIds?.toSet() ?: emptySet()
        val lifts = liftRepository.observeLifts().first()
        val (proven, available) = lifts
            .filter { it.enabled }
            .partition { lift -> lift.id in provenIds && insightsByLift[lift.id] != null }
            .let { (proven, available) ->
                proven.map { lift -> LiftWithInsights(lift, insightsByLift[lift.id]) } to
                    available.map { lift -> LiftWithInsights(lift, insightsByLift[lift.id]) }
            }
        val responseProfile = assembleResponseProfilePayload(metadata, lifts)
        return promptBuilder.buildPayload(
            profile = profile,
            specialization = specialization,
            provenLifts = proven,
            availableLifts = available,
            supersetAffinities = supersetAffinityRepository.getAll(),
            volumeBaselines = muscleVolumeBaselineRepository.getAll(),
            analysisMetadata = metadata,
            responseProfile = responseProfile,
        )
    }

    private suspend fun assembleResponseProfilePayload(
        importMetadata: com.stratum.domain.importing.ImportAnalysisMetadata?,
        lifts: List<com.stratum.domain.lift.Lift>,
    ): ResponseProfilePayload? {
        val responseMetadata = responseProfileMetadataRepository.get() ?: return null
        val baselines = muscleVolumeBaselineRepository.getAll()
        val volumeResponses = volumeResponseObservationRepository.getAll()
        val volumeLandmarks =
            if (importMetadata != null && baselines.isNotEmpty()) {
                val sessions = trainingHistoryRepository.getAllSessions()
                val sets = trainingHistoryRepository.getAllSets()
                VolumeLandmarkSummaryBuilder.build(
                    metadata = importMetadata,
                    sessions = sessions,
                    sets = sets,
                    lifts = lifts,
                    baselines = baselines,
                    volumeResponses = volumeResponses,
                )
            } else {
                emptyList()
            }
        return ResponseProfilePayload.fromObservations(
            volumeObservations = volumeResponses,
            effectivenessObservations = liftEffectivenessObservationRepository.getAll(),
            volumeLandmarks = volumeLandmarks,
            epochsAnalyzed = responseMetadata.epochsAnalyzed,
            analysisWindowStart = responseMetadata.analysisWindowStart,
            analysisWindowEnd = responseMetadata.analysisWindowEnd,
        )
    }
}
