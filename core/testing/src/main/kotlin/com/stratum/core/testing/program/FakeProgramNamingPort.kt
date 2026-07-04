package com.stratum.core.testing.program

import com.stratum.domain.program.generation.naming.ProgramNamingPort
import com.stratum.domain.program.generation.naming.ProgramNamingRequest
import com.stratum.domain.program.generation.naming.ProgramNamingResult

class FakeProgramNamingPort : ProgramNamingPort {
    override suspend fun nameProgram(request: ProgramNamingRequest): Result<ProgramNamingResult> =
        Result.success(ProgramNamingResult(programName = null, workoutNames = emptyList()))
}
