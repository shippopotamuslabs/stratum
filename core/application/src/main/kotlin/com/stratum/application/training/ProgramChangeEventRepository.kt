package com.stratum.application.training

import com.stratum.domain.program.identity.ProgramId
import java.time.Instant
import com.stratum.domain.training.ProgramChangeEvent

interface ProgramChangeEventRepository {
    suspend fun append(event: ProgramChangeEvent)
    suspend fun getEventsForProgram(programId: ProgramId, since: Instant? = null): List<ProgramChangeEvent>
}
