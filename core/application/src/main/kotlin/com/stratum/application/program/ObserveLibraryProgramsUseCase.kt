package com.stratum.application.program

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import com.stratum.domain.program.Program

/**
 * Read-only observation of inactive user programs (library inventory). Excludes the active program
 * and import-history.
 */
class ObserveLibraryProgramsUseCase @Inject constructor(
    private val repository: ProgramRepository,
) {
    operator fun invoke(): Flow<List<Program>> = repository.observeLibraryPrograms()
}
