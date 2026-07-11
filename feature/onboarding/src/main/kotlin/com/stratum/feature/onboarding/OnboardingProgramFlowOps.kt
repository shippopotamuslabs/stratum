package com.stratum.feature.onboarding

import com.stratum.domain.program.ProgramGenerationProgress
import com.stratum.domain.program.importing.ProgramTemplateImportPhase
import com.stratum.domain.program.importing.ProgramTemplateRejectedException

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

    fun importProgramFailed(state: OnboardingEphemeralState, error: Throwable): OnboardingEphemeralState =
        state.copy(
            importProgramState = importProgramStateFor(error),
            importMaxTimelineIndex = 0,
        )

    fun importProgramStateFor(error: Throwable): ImportProgramState =
        if (error is ProgramTemplateRejectedException) {
            ImportProgramState.Rejected(null)
        } else {
            ImportProgramState.Error(null)
        }

    /**
     * Log detail for import failures. UI keeps a generic message so Firebase codes never leak;
     * server rejection text is still logged for debugging (calculator vs empty vs not-a-program).
     */
    fun importFailureLogMessage(error: Throwable): String =
        if (error is ProgramTemplateRejectedException) {
            val serverMessage = error.message?.takeIf { it.isNotBlank() }
            "Program import rejected: ${serverMessage ?: "(no server message)"}"
        } else {
            MSG_IMPORT_FAILED
        }

    fun fileReadFailureLogMessage(message: String?): String =
        "File read failed: ${message ?: "unknown"}"
}
