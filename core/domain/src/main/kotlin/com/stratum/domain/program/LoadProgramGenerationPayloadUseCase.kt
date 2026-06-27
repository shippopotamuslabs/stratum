package com.stratum.domain.program

import com.stratum.domain.importing.ImportAnalysisMetadataRepository
import com.stratum.domain.importing.LiftTrainingInsightsRepository
import com.stratum.domain.importing.MuscleVolumeBaselineRepository
import com.stratum.domain.importing.SupersetAffinityRepository
import com.stratum.domain.response.LiftEffectivenessObservationRepository
import com.stratum.domain.response.MuscleVolumeLandmarkEstimateRepository
import com.stratum.domain.response.ResponseProfileMetadataRepository
import com.stratum.domain.response.VolumeResponseObservationRepository
import com.stratum.domain.response.modeling.toHighlights
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
    private val muscleVolumeLandmarkEstimateRepository: MuscleVolumeLandmarkEstimateRepository,
    private val promptBuilder: ProgramGenerationPromptBuilder,
) : ProgramGenerationPayloadLoader {
    /** @see ProgramGenerationPayloadLoader */
    override suspend fun load(): String = invoke()

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
        val responseProfile = assembleResponseProfilePayload()
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

    private suspend fun assembleResponseProfilePayload(): ResponseProfilePayload? {
        val responseMetadata = responseProfileMetadataRepository.get() ?: return null
        val baselines = muscleVolumeBaselineRepository.getAll()
        val volumeResponses = volumeResponseObservationRepository.getAll()
        val persistedLandmarks = muscleVolumeLandmarkEstimateRepository.getAll()
        val volumeLandmarks =
            persistedLandmarks
                .filter { estimate -> baselines.any { it.muscle == estimate.muscle } }
                .toHighlights()
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
