package com.stratum.domain.program

import com.stratum.domain.onboarding.CompleteOnboardingUseCase
import com.stratum.domain.onboarding.UserTrainingProfile
import com.stratum.domain.onboarding.UserTrainingProfileRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Orchestrates program generation: load the onboarding payload → call the generator → persist the
 * program → mark onboarding complete. Composed so the ViewModel depends on a single use case
 * (D4 / ARCHITECTURE.md §4.4).
 *
 * Depends on [ProgramGenerationPayloadLoader] (a narrow seam) rather than the concrete
 * [LoadProgramGenerationPayloadUseCase], so orchestration tests substitute a fake loader without
 * wiring the loader's many repository dependencies.
 *
 * Ordering guarantee (D2): generation runs to success (`getOrThrow`) **before** persist or
 * completion, so a failed generation never leaves a partial program nor a completed onboarding.
 * The whole flow is wrapped in `runCatching` so any failure surfaces as [Result.failure] with no
 * partial state. Infrastructure adapters log failures; this use case returns [Result] only.
 */
class GenerateProgramUseCase @Inject constructor(
    private val loadPayload: ProgramGenerationPayloadLoader,
    private val generator: ProgramGeneratorPort,
    private val repository: ProgramRepository,
    private val completeOnboarding: CompleteOnboardingUseCase,
    private val profileRepository: UserTrainingProfileRepository,
) {
    suspend operator fun invoke(): Result<Unit> =
        runCatching {
            val payload = loadPayload.load()
            val program = generator.generateProgram(payload).getOrThrow()
            repository.saveProgram(program)
            val profile = profileRepository.observeProfile().first() ?: UserTrainingProfile.Empty
            completeOnboarding(profile)
        }
}
