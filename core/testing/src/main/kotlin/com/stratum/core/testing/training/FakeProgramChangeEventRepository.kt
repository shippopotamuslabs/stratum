package com.stratum.core.testing.training

import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.training.ProgramChangeEvent
import com.stratum.domain.training.SkippedProgramChangeEventRow
import com.stratum.application.training.ProgramChangeEventRead
import com.stratum.application.training.ProgramChangeEventRepository
import java.time.Instant

class FakeProgramChangeEventRepository : ProgramChangeEventRepository {
    private val events = mutableListOf<ProgramChangeEvent>()
    private val skippedRows = mutableListOf<SkippedProgramChangeEventRow>()

    fun seed(vararg event: ProgramChangeEvent) {
        events.clear()
        events.addAll(event)
    }

    /** Diagnostics the next reads report, as if those stored rows failed to decode. */
    fun seedSkippedRows(vararg row: SkippedProgramChangeEventRow) {
        skippedRows.clear()
        skippedRows.addAll(row)
    }

    override suspend fun append(event: ProgramChangeEvent) {
        events.add(event)
    }

    override suspend fun getEventsForProgram(programId: ProgramId, since: Instant?): ProgramChangeEventRead =
        ProgramChangeEventRead(
            events = events.filter { it.programId == programId },
            skippedRows = skippedRows.toList(),
        )
}
