package com.stratum.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.application.auth.AuthManager
import com.stratum.domain.auth.AuthState
import com.stratum.application.importing.CancelHistoricalImportUseCase
import com.stratum.application.importing.ExecuteHistoricalImportUseCase
import com.stratum.application.importing.HistoricalImportOrchestrationEvent
import com.stratum.domain.importing.HistoricalImportResumeState
import com.stratum.domain.importing.ImportFileInput
import com.stratum.domain.importing.ImportHistoryPersistPolicy
import com.stratum.application.lift.ObserveWeightDisplayPreferencesUseCase
import com.stratum.domain.lift.Weight
import com.stratum.domain.logging.LogAnalyticsEventUseCase
import com.stratum.domain.logging.LogErrorUseCase
import com.stratum.domain.logging.PARAM_ACTION
import com.stratum.domain.logging.PARAM_SCREEN
import com.stratum.domain.logging.USER_VISIBLE_ERROR_EVENT
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
    /** Prefill for the import weight-unit gate (mirrors Settings display preference). */
    val preferredWeightUnit: WeightUnitUi = WeightUnitUi.Lbs,
    val showWeightUnitPrompt: Boolean = false,
    /** After unit confirm, route should open the document picker once. */
    val launchFilePicker: Boolean = false,
)

@HiltViewModel
class ImportLogsViewModel @Inject constructor(
    private val executeImport: ExecuteHistoricalImportUseCase,
    private val cancelImport: CancelHistoricalImportUseCase,
    private val authManager: AuthManager,
    private val logError: LogErrorUseCase,
    private val logAnalytics: LogAnalyticsEventUseCase,
    observeWeightDisplayPreferences: ObserveWeightDisplayPreferencesUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ImportLogsUiState())
    val uiState: StateFlow<ImportLogsUiState> = _uiState.asStateFlow()

    private var importJob: Job? = null
    private var lastFiles: List<ImportFileInput> = emptyList()
    private var lastResume: HistoricalImportResumeState? = null
    /** Fallback unit for the current import session (new pick or resume). */
    private var sessionFallbackUnit: Weight.WeightUnit = Weight.WeightUnit.Lb

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
        viewModelScope.launch {
            observeWeightDisplayPreferences().collect { prefs ->
                _uiState.update {
                    it.copy(preferredWeightUnit = prefs.displayUnit.toWeightUnitUi())
                }
            }
        }
    }

    /** User tapped import — show unit gate before the file picker. */
    fun onBeginImportClick() {
        if (authManager.authState.value !is AuthState.Authenticated) {
            _uiState.update {
                it.copy(signedOutGuidanceVisible = true, errorMessage = null, progress = null)
            }
            return
        }
        _uiState.update {
            it.copy(
                showWeightUnitPrompt = true,
                signedOutGuidanceVisible = false,
                errorMessage = null,
            )
        }
    }

    fun onWeightUnitPromptDismissed() {
        _uiState.update { it.copy(showWeightUnitPrompt = false) }
    }

    fun onWeightUnitConfirmed(unit: WeightUnitUi) {
        sessionFallbackUnit = unit.toDomainWeightUnit()
        _uiState.update {
            it.copy(
                showWeightUnitPrompt = false,
                launchFilePicker = true,
            )
        }
    }

    fun onFilePickerLaunched() {
        _uiState.update { it.copy(launchFilePicker = false) }
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
        startImport(files = inputs, resume = null, fallbackWeightUnit = sessionFallbackUnit)
    }

    /**
     * Surfaces SAF permission/read failures from the UI layer (off main-thread load).
     * [logDetail] is for logs only — UI always gets [IMPORT_LOGS_READ_FILES_ERROR].
     */
    fun onReadFilesFailed(logDetail: String? = null) {
        logError(
            IMPORT_LOGS_LOG_TAG,
            "Could not read import files: ${logDetail?.takeIf { it.isNotBlank() } ?: "(no detail)"}",
        )
        logAnalytics(
            USER_VISIBLE_ERROR_EVENT,
            mapOf(PARAM_SCREEN to "import_logs", PARAM_ACTION to "read_files"),
        )
        _uiState.update {
            it.copy(
                progress = null,
                isImporting = false,
                mergeStats = null,
                errorMessage = IMPORT_LOGS_READ_FILES_ERROR,
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
            sessionFallbackUnit = resume.fallbackWeightUnit
            startImport(
                files = lastFiles,
                resume = resume,
                fallbackWeightUnit = resume.fallbackWeightUnit,
            )
        } else if (lastFiles.isNotEmpty()) {
            startImport(
                files = lastFiles,
                resume = null,
                fallbackWeightUnit = sessionFallbackUnit,
            )
        }
    }

    private fun startImport(
        files: List<ImportFileInput>,
        resume: HistoricalImportResumeState?,
        fallbackWeightUnit: Weight.WeightUnit,
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
                    fallbackWeightUnit = fallbackWeightUnit,
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
                            // Provider/server diagnostics (e.g. OpenRouter credits) stay in logs only.
                            logError(
                                IMPORT_LOGS_LOG_TAG,
                                "Historical import failed (kind=${event.kind}): ${event.message}",
                            )
                            logAnalytics(
                                USER_VISIBLE_ERROR_EVENT,
                                mapOf(PARAM_SCREEN to "import_logs", PARAM_ACTION to "import_history"),
                            )
                            _uiState.update {
                                it.copy(
                                    progress = null,
                                    isImporting = false,
                                    errorMessage = event.kind.toUserMessage(),
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
