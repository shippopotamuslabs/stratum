package com.stratum.application.program.importing

import com.stratum.domain.di.DefaultDispatcher
import com.stratum.application.onboarding.CompleteOnboardingUseCase
import com.stratum.application.onboarding.UserTrainingProfileRepository
import com.stratum.application.program.ProgramRepository
import com.stratum.application.training.ProgramChangeEventRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import com.stratum.domain.program.importing.ProgramImportActivation
import com.stratum.domain.program.importing.ProgramTemplateImportOptions

/**
 * Onboarding thin wrapper over [ImportProgramTemplatesUseCase]:
 * [ProgramImportActivation.ActivateAsActive] + complete onboarding + regenerate event.
 *
 * Constructor keeps the same injectable deps as before so existing tests and Hilt wiring
 * remain source-compatible; the body delegates entirely to the core pipeline.
 */
class ImportProgramTemplateUseCase @Inject constructor(
    private val importPort: ProgramTemplateImportPort,
    private val resolver: ResolveProgramTemplateLiftsUseCase,
    private val repository: ProgramRepository,
    private val completeOnboarding: CompleteOnboardingUseCase,
    private val profileRepository: UserTrainingProfileRepository,
    private val programChangeEventRepository: ProgramChangeEventRepository,
    @DefaultDispatcher private val importDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val core =
        ImportProgramTemplatesUseCase(
            importPort = importPort,
            resolver = resolver,
            repository = repository,
            completeOnboarding = completeOnboarding,
            profileRepository = profileRepository,
            programChangeEventRepository = programChangeEventRepository,
            importDispatcher = importDispatcher,
        )

    suspend operator fun invoke(
        bytes: ByteArray,
        fileName: String,
        onProgress: ProgramTemplateImportProgressCallback = {},
    ): Result<Unit> =
        core(
            bytes = bytes,
            fileName = fileName,
            options =
                ProgramTemplateImportOptions(
                    activation = ProgramImportActivation.ActivateAsActive,
                    completeOnboarding = true,
                    appendProgramRegeneratedEvent = true,
                ),
            onProgress = onProgress,
        ).map { }
}
