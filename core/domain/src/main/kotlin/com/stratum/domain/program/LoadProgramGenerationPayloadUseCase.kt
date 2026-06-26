package com.stratum.domain.program

import com.stratum.domain.importing.ImportAnalysisMetadataRepository
import com.stratum.domain.importing.LiftTrainingInsightsRepository
import com.stratum.domain.importing.MuscleVolumeBaselineRepository
import com.stratum.domain.importing.SupersetAffinityRepository
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
    private val promptBuilder: ProgramGenerationPromptBuilder,
) {
    suspend operator fun invoke(): String {
        val profile = userTrainingProfileRepository.observeProfile().first() ?: UserTrainingProfile.Empty
        val specialization = observeSpecialization().first()
        val insightsByLift = liftTrainingInsightsRepository.getAll().associateBy { it.liftId }
        val metadata = importAnalysisMetadataRepository.get()
        val provenIds = metadata?.provenLiftIds?.toSet() ?: emptySet()
        val (proven, available) = liftRepository.observeLifts().first()
            .filter { it.enabled }
            .partition { lift -> lift.id in provenIds && insightsByLift[lift.id] != null }
            .let { (proven, available) ->
                proven.map { lift -> LiftWithInsights(lift, insightsByLift[lift.id]) } to
                    available.map { lift -> LiftWithInsights(lift, insightsByLift[lift.id]) }
            }
        return promptBuilder.buildPayload(
            profile = profile,
            specialization = specialization,
            provenLifts = proven,
            availableLifts = available,
            supersetAffinities = supersetAffinityRepository.getAll(),
            volumeBaselines = muscleVolumeBaselineRepository.getAll(),
            analysisMetadata = metadata,
        )
    }
}
