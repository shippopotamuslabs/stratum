package com.stratum.application.training

import com.stratum.domain.program.identity.ProgramId
import java.time.Instant
import com.stratum.domain.training.ProgramChangeEvent

interface ProgramChangeEventRepository {
    suspend fun append(event: ProgramChangeEvent)

    /**
     * Reads a program's change events. Rows that cannot be decoded are not
     * dropped silently: they come back as diagnostics on the returned
     * [ProgramChangeEventRead] for the caller to drain (repository
     * implementations never log — CODE_STYLE_RULES layer table).
     */
    suspend fun getEventsForProgram(programId: ProgramId, since: Instant? = null): ProgramChangeEventRead
}
