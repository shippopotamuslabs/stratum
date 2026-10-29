package com.stratum.application.program

import com.stratum.domain.program.identity.ProgramId
import javax.inject.Inject
import com.stratum.domain.program.Program

/** Loads any user program by id (active or library). */
class GetProgramByIdUseCase @Inject constructor(
    private val repository: ProgramRepository,
) {
    suspend operator fun invoke(programId: ProgramId): Program? = repository.getProgramById(programId)
}
