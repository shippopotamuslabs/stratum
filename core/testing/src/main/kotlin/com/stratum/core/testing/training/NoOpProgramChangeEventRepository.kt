package com.stratum.core.testing.training

import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.training.ProgramChangeEvent
import com.stratum.application.training.ProgramChangeEventRepository
import java.time.Instant

class NoOpProgramChangeEventRepository : ProgramChangeEventRepository {
    override suspend fun append(event: ProgramChangeEvent) = Unit

    override suspend fun getEventsForProgram(programId: ProgramId, since: Instant?): List<ProgramChangeEvent> =
        emptyList()
}
