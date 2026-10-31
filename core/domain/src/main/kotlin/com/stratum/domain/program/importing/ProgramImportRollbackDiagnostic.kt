package com.stratum.domain.program.importing

import com.stratum.domain.program.identity.ProgramId

/**
 * Typed record of a program-template import rollback delete that failed (STR-256).
 *
 * Phase-2 persist writes every prepared program, then rolls back by deleting each saved id when
 * a write fails. A failed delete leaves a possible orphan program behind the primary persist
 * failure. Diagnostics are values, not logs: `:core:domain` never logs, so the use case returns
 * them on its failure object and the terminal boundary drains each one exactly once through its
 * logging wiring. Diagnostics never reach the UI.
 */
sealed interface ProgramImportRollbackDiagnostic {
    /** The delete failure that left the program behind. */
    val cause: Throwable

    /**
     * Rollback delete of an already-persisted program failed; the program may remain as an
     * orphan invisible behind the primary persist failure.
     */
    data class RollbackDeleteFailed(
        val programId: ProgramId,
        override val cause: Throwable,
    ) : ProgramImportRollbackDiagnostic
}
