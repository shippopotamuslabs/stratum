package com.stratum.feature.onboarding

import com.stratum.domain.program.ProgramGenerationProgress
import com.stratum.domain.program.importing.ProgramTemplateImportPhase
import com.stratum.application.program.importing.ProgramImportRollbackException
import com.stratum.application.program.importing.ProgramTemplateImportOutcome
import com.stratum.application.program.importing.ProgramTemplateRejectedException

/**
 * Pure generate / program-import progress and error mapping for [OnboardingViewModel].
 * Keeps the wizard ViewModel under Detekt LargeClass by housing presentation-only transforms.
 */
internal object OnboardingProgramFlowOps {

    const val TAG = "OnboardingViewModel"
    const val TAG_IMPORT = "ProgramTemplateImport"
    const val MSG_NAVIGATION_FAILED = "Couldn't save progress. Try again."
    const val MSG_GENERATE_FAILED = "Program generation failed"
    const val MSG_IMPORT_FAILED = "Program import failed"

    fun withGenerateProgress(
        state: OnboardingEphemeralState,
        progress: ProgramGenerationProgress,
    ): OnboardingEphemeralState {
        val ui = progress.toUi()
        val index = ui.phase.activeTimelineIndex()
        return state.copy(
            generateProgress = ui,
            generateMaxTimelineIndex = maxOf(state.generateMaxTimelineIndex, index),
        )
    }

    fun withImportProgramProgress(
        state: OnboardingEphemeralState,
        phase: ProgramTemplateImportPhase,
    ): OnboardingEphemeralState {
        val ui = phase.toUi()
        val index = ui.activeImportTimelineIndex()
        return state.copy(
            importProgramState = ImportProgramState.Loading(ui),
            importMaxTimelineIndex = maxOf(state.importMaxTimelineIndex, index),
        )
    }

    fun generateFailed(state: OnboardingEphemeralState): OnboardingEphemeralState =
        state.copy(
            generateState = GenerateState.Error,
            generateProgress = null,
            generateMaxTimelineIndex = 0,
            generateErrorMessage = null,
        )

    fun importProgramFailed(
        state: OnboardingEphemeralState,
        outcome: ProgramTemplateImportOutcome,
    ): OnboardingEphemeralState =
        state.copy(
            importProgramState = importProgramStateFor(outcome),
            importMaxTimelineIndex = 0,
        )

    fun importProgramStateFor(outcome: ProgramTemplateImportOutcome): ImportProgramState =
        if (outcome is ProgramTemplateImportOutcome.Rejected) {
            ImportProgramState.Rejected(null)
        } else {
            ImportProgramState.Error(null)
        }

    /**
     * Log detail for import failures. UI keeps a generic message so Firebase codes never leak;
     * server rejection text is still logged for debugging (calculator vs empty vs not-a-program).
     */
    fun importFailureLogMessage(outcome: ProgramTemplateImportOutcome): String =
        if (outcome is ProgramTemplateImportOutcome.Rejected) {
            val serverMessage = outcome.message.takeIf { it.isNotBlank() }
            "Program import rejected: ${serverMessage ?: "(no server message)"}"
        } else {
            MSG_IMPORT_FAILED
        }

    /**
     * Exception value for the import-failure log record only (STR-243): the outcome
     * branch carries the typed facts, and the record keeps the legacy exception shape
     * built from them. [ProgramTemplateImportOutcome.OperationalFailed] carries its
     * record as diagnostics drained by the ViewModel (STR-237), so it has no record
     * value here.
     */
    fun importFailureLogThrowable(outcome: ProgramTemplateImportOutcome): Throwable? =
        when (outcome) {
            is ProgramTemplateImportOutcome.Rejected ->
                ProgramTemplateRejectedException(outcome.message)
            is ProgramTemplateImportOutcome.Failed ->
                IllegalStateException(outcome.message)
            is ProgramTemplateImportOutcome.PersistFailed ->
                if (outcome.diagnostics.isNotEmpty()) {
                    ProgramImportRollbackException(
                        cause = IllegalStateException(outcome.message),
                        diagnostics = outcome.diagnostics,
                    )
                } else {
                    IllegalStateException(outcome.message)
                }
            ProgramTemplateImportOutcome.ReadyWithoutPath ->
                IllegalStateException("Ready job has no template path.")
            is ProgramTemplateImportOutcome.NonTerminal ->
                IllegalStateException("Import ended in non-terminal status: ${outcome.status}")
            ProgramTemplateImportOutcome.TimedOut ->
                IllegalStateException("Timed out waiting for import to finish.")
            is ProgramTemplateImportOutcome.OperationalFailed -> null
            is ProgramTemplateImportOutcome.Success -> null
        }

    fun fileReadFailureLogMessage(message: String?): String =
        "File read failed: ${message ?: "unknown"}"
}
