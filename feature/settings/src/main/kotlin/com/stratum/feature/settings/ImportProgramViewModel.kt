package com.stratum.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.application.auth.AuthManager
import com.stratum.domain.auth.AuthState
import com.stratum.application.program.importing.ImportProgramTemplatesUseCase
import com.stratum.application.program.importing.ProgramTemplateImportOutcome
import com.stratum.domain.program.importing.ProgramImportActivation
import com.stratum.domain.program.importing.ProgramImportRollbackDiagnostic
import com.stratum.application.diagnostics.OperationalDiagnostic
import com.stratum.domain.program.importing.ProgramTemplateImportOptions
import com.stratum.core.infrastructure.telemetry.PARAM_ACTION
import com.stratum.core.infrastructure.telemetry.PARAM_SCREEN
import com.stratum.core.infrastructure.telemetry.USER_VISIBLE_ERROR_EVENT
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImportProgramUiState(
    val isAuthenticated: Boolean = false,
    val phase: ImportProgramPhaseUi? = null,
    val errorMessage: String? = null,
    val signedOutGuidanceVisible: Boolean = false,
    val maxTimelineIndex: Int = 0,
    val isImporting: Boolean = false,
    val importedCount: Int = 0,
    val skippedCount: Int = 0,
)

@HiltViewModel
class ImportProgramViewModel @Inject constructor(
    private val importPrograms: ImportProgramTemplatesUseCase,
    private val authManager: AuthManager,
    private val logAnalytics: AnalyticsRecorder,
    private val diagnosticDrain: DiagnosticDrain,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ImportProgramUiState())
    val uiState: StateFlow<ImportProgramUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authManager.authState.collect { state ->
                val authenticated = state is AuthState.Authenticated
                _uiState.update {
                    it.copy(isAuthenticated = authenticated)
                }
            }
        }
    }

    fun onFileSelected(bytes: ByteArray, fileName: String) {
        if (authManager.authState.value !is AuthState.Authenticated) {
            _uiState.update {
                it.copy(
                    signedOutGuidanceVisible = true,
                    errorMessage = null,
                    phase = null,
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    signedOutGuidanceVisible = false,
                    errorMessage = null,
                    isImporting = true,
                    maxTimelineIndex = 0,
                    importedCount = 0,
                    skippedCount = 0,
                    phase = ImportProgramPhaseUi.Uploading,
                )
            }
            // Cancellation is never a user-visible import error: the use case rethrows
            // it, so this coroutine cancels before any analytics logging or uiState
            // update below.
            val outcome =
                importPrograms(
                    bytes = bytes,
                    fileName = fileName,
                    options =
                        ProgramTemplateImportOptions(
                            activation = ProgramImportActivation.LibraryOnly,
                            completeOnboarding = false,
                            appendProgramRegeneratedEvent = false,
                        ),
                    onProgress = { domainPhase ->
                        val phaseUi = domainPhase.toUi()
                        val step = phaseUi.programImportTimelineIndex()
                        _uiState.update {
                            it.copy(
                                phase = phaseUi,
                                maxTimelineIndex = maxOf(it.maxTimelineIndex, step),
                            )
                        }
                    },
                )
            when (outcome) {
                is ProgramTemplateImportOutcome.Success -> {
                    // STR-240 chunk 8b: a degraded-but-successful import (lift-name
                    // embedding batch failed during resolution) carries its record
                    // on the Success branch as diagnostics and is drained here
                    // exactly once; diagnostics never reach the UI state.
                    diagnosticDrain.drain(outcome.diagnostics)
                    val imported = outcome.result.importedProgramIds.size
                    val skipped = outcome.result.skippedProgramCount
                    _uiState.update {
                        it.copy(
                            phase = ImportProgramPhaseUi.Complete,
                            isImporting = false,
                            maxTimelineIndex = PROGRAM_TIMELINE_STEP_COUNT,
                            importedCount = imported,
                            skippedCount = skipped,
                            errorMessage = null,
                        )
                    }
                }
                else -> {
                    // STR-237: an operational failure rides the OperationalFailed
                    // branch as diagnostics and is drained here exactly once.
                    if (outcome is ProgramTemplateImportOutcome.OperationalFailed) {
                        diagnosticDrain.drain(outcome.diagnostics)
                    }
                    // STR-256: a failed orphan rollback rides the PersistFailed branch as
                    // diagnostics and is drained here exactly once. User copy still derives
                    // from the primary message via userFacingImportError below.
                    if (outcome is ProgramTemplateImportOutcome.PersistFailed) {
                        // Fix 7: a degraded resolve's embedding diagnostic rides
                        // this branch too and drains here exactly once (the
                        // Success drain never runs for this import).
                        diagnosticDrain.drain(outcome.embeddingDiagnostics)
                        outcome.diagnostics.forEach { diagnostic ->
                            when (diagnostic) {
                                is ProgramImportRollbackDiagnostic.RollbackDeleteFailed ->
                                    diagnosticDrain.drain(
                                        OperationalDiagnostic(
                                            tag = TAG_IMPORT_ROLLBACK,
                                            message = "Program import rollback delete failed: " +
                                                "program=${diagnostic.programId.value}",
                                            cause = diagnostic.cause,
                                        ),
                                    )
                            }
                        }
                    }
                    logAnalytics(
                        USER_VISIBLE_ERROR_EVENT,
                        mapOf(PARAM_SCREEN to "import_program", PARAM_ACTION to "import_templates"),
                    )
                    _uiState.update {
                        it.copy(
                            phase = null,
                            isImporting = false,
                            errorMessage = userFacingImportError(outcome),
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val TAG_IMPORT_ROLLBACK = "ProgramTemplateImport"
    }

    /** Surfaces SAF permission/read failures from the UI layer (off main-thread load). */
    fun onReadFilesFailed(message: String) {
        logAnalytics(
            USER_VISIBLE_ERROR_EVENT,
            mapOf(PARAM_SCREEN to "import_program", PARAM_ACTION to "read_files"),
        )
        _uiState.update {
            it.copy(
                phase = null,
                isImporting = false,
                errorMessage = message.ifBlank { "Could not read the selected file." },
            )
        }
    }
}

/**
 * Maps an import outcome branch to copy safe for users (STR-243: the branch is
 * the contract, not a Throwable). A rejection surfaces its server message
 * verbatim; failure branches carrying a message pass through
 * [sanitizedImportMessage] so SQLite/Room constraint text never surfaces.
 */
internal fun userFacingImportError(outcome: ProgramTemplateImportOutcome): String =
    when (outcome) {
        is ProgramTemplateImportOutcome.Rejected ->
            outcome.message.takeIf { it.isNotBlank() } ?: "Import failed."
        is ProgramTemplateImportOutcome.Failed -> sanitizedImportMessage(outcome.message)
        is ProgramTemplateImportOutcome.PersistFailed -> sanitizedImportMessage(outcome.message)
        ProgramTemplateImportOutcome.ReadyWithoutPath ->
            sanitizedImportMessage("Ready job has no template path.")
        is ProgramTemplateImportOutcome.NonTerminal ->
            sanitizedImportMessage("Import ended in non-terminal status: ${outcome.status}")
        ProgramTemplateImportOutcome.TimedOut ->
            sanitizedImportMessage("Timed out waiting for import to finish.")
        is ProgramTemplateImportOutcome.OperationalFailed -> "Import failed."
        is ProgramTemplateImportOutcome.Success -> "Import failed."
    }

/** Sanitizes a branch-carried failure message for users. Never surfaces SQLite/Room constraint text. */
private fun sanitizedImportMessage(message: String?): String {
    val raw = message.orEmpty()
    if (looksLikeDatabaseConstraint(raw)) {
        return "Couldn't save this program. Try again or pick a different file."
    }
    if (raw.isNotBlank() && !looksLikeInternalException(raw)) {
        return raw
    }
    return "Import failed."
}

private fun looksLikeDatabaseConstraint(text: String): Boolean {
    val lower = text.lowercase()
    return lower.contains("foreign key") ||
        lower.contains("sqlite_constraint") ||
        lower.contains("constraint failed") ||
        lower.contains("unique constraint") ||
        lower.contains("room cannot verify")
}

private fun looksLikeInternalException(text: String): Boolean {
    val lower = text.lowercase()
    return lower.contains("exception") ||
        lower.contains("error code") ||
        lower.contains("android.database") ||
        lower.contains("sqlexception")
}
