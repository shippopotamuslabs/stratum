package com.stratum.domain.program

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Read-only observation of the persisted program for the Programs screen (OS-7 D3). Wraps the
 * repository so the ViewModel depends on a use case, not a repository port (ARCHITECTURE.md §4.4 —
 * the detekt `ForbiddenRepositoryInViewModel` contract).
 */
class ObserveProgramUseCase @Inject constructor(
    private val repository: ProgramRepository,
) {
    operator fun invoke(): Flow<Program?> = repository.observeProgram()
}
