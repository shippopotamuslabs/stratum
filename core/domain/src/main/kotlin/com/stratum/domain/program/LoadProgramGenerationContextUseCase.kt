package com.stratum.domain.program

import com.stratum.domain.importing.ImportAnalysisMetadataRepository
import com.stratum.domain.importing.LiftTrainingInsightsRepository
import com.stratum.domain.importing.MuscleVolumeBaselineRepository
import com.stratum.domain.response.LiftEffectivenessObservationRepository
import com.stratum.domain.response.MuscleVolumeResponseSnapshotRepository
import com.stratum.domain.response.ResponseProfileMetadataRepository
import com.stratum.domain.response.VolumeResponseObservationRepository
import com.stratum.domain.response.modeling.toHighlights
import com.stratum.domain.lift.LiftRepository
import com.stratum.domain.onboarding.ObserveSpecializationUseCase
import com.stratum.domain.onboarding.UserTrainingProfileRepository
import com.stratum.domain.onboarding.UserTrainingProfile
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.context.ProgramGenerationContextMapper
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class LoadProgramGenerationContextUseCase @Inject constructor(
    private val userTrainingProfileRepository: UserTrainingProfileRepository,
    private val observeSpecialization: ObserveSpecializationUseCase,
    private val liftRepository: LiftRepository,
    private val liftTrainingInsightsRepository: LiftTrainingInsightsRepository,
    private val importAnalysisMetadataRepository: ImportAnalysisMetadataRepository,
    private val volumeResponseObservationRepository: VolumeResponseObservationRepository,
    private val liftEffectivenessObservationRepository: LiftEffectivenessObservationRepository,
    private val responseProfileMetadataRepository: ResponseProfileMetadataRepository,
    private val muscleVolumeResponseSnapshotRepository: MuscleVolumeResponseSnapshotRepository,
    private val muscleVolumeBaselineRepository: MuscleVolumeBaselineRepository,
) : ProgramGenerationContextLoader {
    override suspend fun load(): ProgramGenerationContext = invoke()

    suspend operator fun invoke(): ProgramGenerationContext {
        val profile = userTrainingProfileRepository.observeProfile().first() ?: UserTrainingProfile.Empty
        val specialization = observeSpecialization().first()
        val insightsByLift = liftTrainingInsightsRepository.getAll().associateBy { it.liftId }
        val metadata = importAnalysisMetadataRepository.get()
        val provenIds = metadata?.provenLiftIds?.toSet() ?: emptySet()
        val lifts = liftRepository.observeEnabledLifts().first()
        val (proven, available) = lifts
            .partition { lift -> lift.id in provenIds && insightsByLift[lift.id] != null }
            .let { (provenLifts, availableLifts) ->
                provenLifts.map { lift -> LiftWithInsights(lift, insightsByLift[lift.id]) } to
                    availableLifts.map { lift -> LiftWithInsights(lift, insightsByLift[lift.id]) }
            }
        val responseProfile = assembleResponseProfilePayload()
        return ProgramGenerationContextMapper.map(
            profile = profile,
            specialization = specialization,
            provenLifts = proven,
            availableLifts = available,
            responseProfile = responseProfile,
        )
    }

    private suspend fun assembleResponseProfilePayload(): ResponseProfilePayload? {
        val responseMetadata = responseProfileMetadataRepository.get() ?: return null
        val baselines = muscleVolumeBaselineRepository.getAll()
        val volumeResponses = volumeResponseObservationRepository.getAll()
        val persistedSnapshots = muscleVolumeResponseSnapshotRepository.getAll()
        val volumeSnapshots =
            persistedSnapshots
                .filter { snapshot -> baselines.any { it.muscle == snapshot.muscle } }
                .toHighlights()
        return ResponseProfilePayload.fromObservations(
            volumeObservations = volumeResponses,
            effectivenessObservations = liftEffectivenessObservationRepository.getAll(),
            volumeSnapshots = volumeSnapshots,
            epochsAnalyzed = responseMetadata.epochsAnalyzed,
            analysisWindowStart = responseMetadata.analysisWindowStart,
            analysisWindowEnd = responseMetadata.analysisWindowEnd,
        )
    }
}
