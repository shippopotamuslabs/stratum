package com.stratum.domain.checkin

import com.stratum.domain.training.ProgramChangeEventType
import com.stratum.domain.training.identity.ProgramChangeEventId

/**
 * Diagnostic value for a stored `program_change_event` row that could not be
 * decoded during a check-in evaluation (diagnostics as values, CODE_STYLE_RULES
 * § "Partial success"). It carries the same skipped-row facts as
 * [com.stratum.domain.training.SkippedProgramChangeEventRow] — the row id and
 * its stored event type — on the [CheckInEvaluation] outcome; the presentation
 * boundary (CheckInViewModel, via its module's DiagnosticDrain) records each
 * one as a warning exactly once. Diagnostics never reach UI state.
 */
data class CheckInSkippedRowDiagnostic(
    val id: ProgramChangeEventId,
    val eventType: ProgramChangeEventType,
)
