package com.stratum.domain.training

import com.stratum.domain.training.identity.ProgramChangeEventId

/**
 * Diagnostic value for a stored `program_change_event` row that could not be
 * decoded into a [ProgramChangeEvent] (diagnostics as values, CODE_STYLE_RULES
 * § "Partial success"). The repository returns these on its read outcome; the
 * calling boundary drains them through `LoggingPort` exactly once. The skip
 * itself is unchanged: the row is absent from the read's events.
 */
data class SkippedProgramChangeEventRow(
    val id: ProgramChangeEventId,
    val eventType: ProgramChangeEventType,
)
