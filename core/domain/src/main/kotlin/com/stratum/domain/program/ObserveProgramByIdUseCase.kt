package com.stratum.domain.program

import com.stratum.domain.program.identity.ProgramId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Observes a single program by id (active or library). */
class ObserveProgramByIdUseCase @Inject constructor(
    private val repository: ProgramRepository,
) {
    operator fun invoke(programId: ProgramId): Flow<Program?> = repository.observeProgramById(programId)
}
