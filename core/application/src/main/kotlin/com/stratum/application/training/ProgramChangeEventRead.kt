package com.stratum.application.training

import com.stratum.domain.training.ProgramChangeEvent
import com.stratum.domain.training.SkippedProgramChangeEventRow

/**
 * Outcome of a [ProgramChangeEventRepository.getEventsForProgram] read: the
 * decoded events plus one [SkippedProgramChangeEventRow] diagnostic per stored
 * row that could not be decoded. Diagnostics are values (CODE_STYLE_RULES
 * § "Partial success"): the repository never logs; the caller drains
 * [skippedRows] through `LoggingPort` exactly once.
 */
data class ProgramChangeEventRead(
    val events: List<ProgramChangeEvent>,
    val skippedRows: List<SkippedProgramChangeEventRow>,
)
