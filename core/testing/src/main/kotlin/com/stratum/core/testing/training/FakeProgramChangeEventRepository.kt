package com.stratum.core.testing.training

import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.training.ProgramChangeEvent
import com.stratum.application.training.ProgramChangeEventRepository
import java.time.Instant

class FakeProgramChangeEventRepository : ProgramChangeEventRepository {
    private val events = mutableListOf<ProgramChangeEvent>()

    fun seed(vararg event: ProgramChangeEvent) {
        events.clear()
        events.addAll(event)
    }

    override suspend fun append(event: ProgramChangeEvent) {
        events.add(event)
    }

    override suspend fun getEventsForProgram(programId: ProgramId, since: Instant?): List<ProgramChangeEvent> =
        events.filter { it.programId == programId }
}
