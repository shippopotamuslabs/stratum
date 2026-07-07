package com.stratum.domain.training

import com.stratum.domain.program.identity.ProgramId
import java.time.Instant

interface ProgramChangeEventRepository {
    suspend fun append(event: ProgramChangeEvent)
    suspend fun getEventsForProgram(programId: ProgramId, since: Instant? = null): List<ProgramChangeEvent>
}
