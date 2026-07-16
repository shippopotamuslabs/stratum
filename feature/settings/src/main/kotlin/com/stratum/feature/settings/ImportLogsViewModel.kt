package com.stratum.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.auth.AuthManager
import com.stratum.domain.auth.AuthState
import com.stratum.domain.importing.CancelHistoricalImportUseCase
import com.stratum.domain.importing.ExecuteHistoricalImportUseCase
import com.stratum.domain.importing.HistoricalImportOrchestrationEvent
import com.stratum.domain.importing.HistoricalImportResumeState
import com.stratum.domain.importing.ImportFileInput
import com.stratum.domain.importing.ImportHistoryPersistPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImportLogsUiState(
    val isAuthenticated: Boolean = false,
    val progress: ImportProgressUi? = null,
    val errorMessage: String? = null,
    val mergeStats: MergeStatsUi? = null,
    val signedOutGuidanceVisible: Boolean = false,
    /** Furthest timeline step reached this run (monotonic). */
    val maxTimelineIndex: Int = 0,
    val isImporting: Boolean = false,
)

@HiltViewModel
class ImportLogsViewModel @Inject constructor(
    private val executeImport: ExecuteHistoricalImportUseCase,
    private val cancelImport: CancelHistoricalImportUseCase,
    private val authManager: AuthManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ImportLogsUiState())
    val uiState: StateFlow<ImportLogsUiState> = _uiState.asStateFlow()

    private var importJob: Job? = null
    private var lastFiles: List<ImportFileInput> = emptyList()
    private var lastResume: HistoricalImportResumeState? = null

    init {
        viewModelScope.launch {
            authManager.authState.collect { state ->
                val authenticated = state is AuthState.Authenticated
                _uiState.update {
                    it.copy(
                        isAuthenticated = authenticated,
                        signedOutGuidanceVisible = !authenticated && it.signedOutGuidanceVisible,
                    )
                }
            }
        }
    }

    /**
     * Accepts picked file bytes from the UI layer (no domain type in composables).
     * Maps to [ImportFileInput] at the ViewModel boundary.
     */
    fun onFilesSelected(files: List<Pair<ByteArray, String>>) {
        val inputs = files.map { (bytes, name) -> ImportFileInput(bytes, name) }
        if (authManager.authState.value !is AuthState.Authenticated) {
            _uiState.update {
                it.copy(
                    signedOutGuidanceVisible = true,
                    errorMessage = null,
                    progress = null,
                )
            }
            return
        }
        lastFiles = inputs
        lastResume = null
        startImport(files = inputs, resume = null)
    }

    /** Surfaces SAF permission/read failures from the UI layer (off main-thread load). */
    fun onReadFilesFailed(message: String) {
        _uiState.update {
            it.copy(
                progress = null,
                isImporting = false,
                mergeStats = null,
                errorMessage = message.ifBlank { "Could not read the selected files." },
            )
        }
    }

    fun onCancelImport() {
        viewModelScope.launch {
            importJob?.cancel()
            importJob = null
            runCatching { cancelImport() }
            _uiState.update {
                it.copy(
                    progress = null,
                    errorMessage = null,
                    isImporting = false,
                    maxTimelineIndex = 0,
                )
            }
        }
    }

    fun onRetryFromFailedFile() {
        if (authManager.authState.value !is AuthState.Authenticated) {
            _uiState.update { it.copy(signedOutGuidanceVisible = true) }
            return
        }
        val resume = lastResume
        if (resume != null) {
            startImport(files = lastFiles, resume = resume)
        } else if (lastFiles.isNotEmpty()) {
            startImport(files = lastFiles, resume = null)
        }
    }

    private fun startImport(
        files: List<ImportFileInput>,
        resume: HistoricalImportResumeState?,
    ) {
        importJob?.cancel()
        importJob =
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        signedOutGuidanceVisible = false,
                        errorMessage = null,
                        mergeStats = null,
                        isImporting = true,
                        maxTimelineIndex = 0,
                        progress =
                            ImportProgressUi(
                                phase = ImportLogsPhaseUi.Uploading,
                            ),
                    )
                }
                executeImport(
                    files = files,
                    resume = resume,
                    persistPolicy = ImportHistoryPersistPolicy.MergeSkipDuplicates,
                ).collect { event ->
                    when (event) {
                        is HistoricalImportOrchestrationEvent.Progress -> {
                            val progressUi = event.progress.toUi()
                            val stepIndex = progressUi.phase.historicalImportTimelineIndex()
                            _uiState.update {
                                it.copy(
                                    isImporting = true,
                                    maxTimelineIndex = maxOf(it.maxTimelineIndex, stepIndex),
                                    progress = progressUi,
                                )
                            }
                        }
                        is HistoricalImportOrchestrationEvent.Completed -> {
                            _uiState.update {
                                it.copy(
                                    progress = null,
                                    isImporting = false,
                                    maxTimelineIndex = HISTORICAL_TIMELINE_STEP_COUNT,
                                    mergeStats = event.mergeStats?.toUi(),
                                    errorMessage = null,
                                )
                            }
                        }
                        is HistoricalImportOrchestrationEvent.Failed -> {
                            lastResume = event.resume
                            _uiState.update {
                                it.copy(
                                    progress = null,
                                    isImporting = false,
                                    errorMessage = event.message,
                                )
                            }
                        }
                        is HistoricalImportOrchestrationEvent.ValidationFailed -> {
                            _uiState.update {
                                it.copy(
                                    progress = null,
                                    isImporting = false,
                                    errorMessage = event.reason.toUserMessage(),
                                )
                            }
                        }
                    }
                }
            }
    }
}
