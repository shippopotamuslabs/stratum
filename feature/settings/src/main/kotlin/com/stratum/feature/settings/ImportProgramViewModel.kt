package com.stratum.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.auth.AuthManager
import com.stratum.domain.auth.AuthState
import com.stratum.domain.program.importing.ImportProgramTemplatesUseCase
import com.stratum.domain.program.importing.ProgramImportActivation
import com.stratum.domain.program.importing.ProgramTemplateImportOptions
import com.stratum.domain.program.importing.ProgramTemplateRejectedException
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
            val result =
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
            result.fold(
                onSuccess = { importResult ->
                    val imported = importResult.importedProgramIds.size
                    val skipped = importResult.skippedProgramCount
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
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            phase = null,
                            isImporting = false,
                            errorMessage = userFacingImportError(error),
                        )
                    }
                },
            )
        }
    }

    /** Surfaces SAF permission/read failures from the UI layer (off main-thread load). */
    fun onReadFilesFailed(message: String) {
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
 * Maps domain/Room failures to copy safe for users. Never surfaces SQLite/Room constraint text.
 */
internal fun userFacingImportError(error: Throwable): String {
    when (error) {
        is ProgramTemplateRejectedException -> {
            val msg = error.message?.takeIf { it.isNotBlank() }
            if (msg != null) return msg
        }
        else -> Unit
    }
    val raw = error.message.orEmpty()
    if (looksLikeDatabaseConstraint(raw) || looksLikeDatabaseConstraint(error.toString())) {
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
