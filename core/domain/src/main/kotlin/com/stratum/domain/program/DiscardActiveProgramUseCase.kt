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
    suspend operator fun invoke(): Result<Unit> =
        runCatchingLogged(loggingPort, TAG, "Failed to discard active program") {
            val active = programRepository.getProgram() ?: return@runCatchingLogged
            deleteProgram(active.id).getOrThrow()
        }

    private companion object {
        const val TAG = "DiscardActiveProgram"
    }
}
