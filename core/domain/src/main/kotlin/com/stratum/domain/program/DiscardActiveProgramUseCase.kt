package com.stratum.domain.program

import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import javax.inject.Inject

/**
 * Deletes the user's active program template if one exists (e.g. onboarding preview
 * "Discard Program"). No-op when there is no active program. History soft-refs are handled by
 * [DeleteProgramUseCase] / [ProgramRepository.deleteProgram].
 */
class DiscardActiveProgramUseCase @Inject constructor(
    private val programRepository: ProgramRepository,
    private val deleteProgram: DeleteProgramUseCase,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(): Result<Unit> {
        val active = runCatchingLogged(loggingPort, TAG, "Failed to inspect active program") {
            programRepository.getProgram()
        }.getOrElse { error -> return Result.failure(error) } ?: return Result.success(Unit)

        return deleteProgram(active.id)
    }

    private companion object {
        const val TAG = "DiscardActiveProgram"
    }
}
