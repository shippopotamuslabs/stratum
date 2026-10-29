package com.stratum.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.export.ExportFileResult
import com.stratum.domain.export.ExportLogRange
import com.stratum.application.export.ExportTrainingLogsUseCase
import com.stratum.application.export.PreviewExportTrainingLogsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ExportRangePresetUi {
    AllTime,
    Last30,
    Last90,
    YearToDate,
    Custom,
}

/**
 * User-facing export errors as presentation keys so the UI can resolve string resources.
 * [Generic] covers unexpected failures whose message is already human-readable.
 */
sealed interface ExportLogsErrorUi {
    data object InvalidDateOrder : ExportLogsErrorUi

    data object EmptyRange : ExportLogsErrorUi

    data class Generic(val message: String) : ExportLogsErrorUi
}

data class ExportLogsUiState(
    val rangePreset: ExportRangePresetUi = ExportRangePresetUi.AllTime,
    val customStart: LocalDate? = null,
    val customEnd: LocalDate? = null,
    val preview: ExportLogPreviewUi? = null,
    val isExporting: Boolean = false,
    val error: ExportLogsErrorUi? = null,
)

@HiltViewModel
class ExportLogsViewModel(
    private val previewExport: PreviewExportTrainingLogsUseCase,
    private val exportLogs: ExportTrainingLogsUseCase,
    private val zone: ZoneId,
) : ViewModel() {
    /**
     * Hilt entry: device-local [ZoneId.systemDefault]. Unit tests use the primary constructor
     * with a fixed zone for deterministic range bounds.
     */
    @Inject
    constructor(
        previewExport: PreviewExportTrainingLogsUseCase,
        exportLogs: ExportTrainingLogsUseCase,
    ) : this(previewExport, exportLogs, ZoneId.systemDefault())

    private val _uiState = MutableStateFlow(ExportLogsUiState())
    val uiState: StateFlow<ExportLogsUiState> = _uiState.asStateFlow()

    private val _shareEvents = MutableSharedFlow<ExportFileResult>(extraBufferCapacity = 1)
    val shareEvents: SharedFlow<ExportFileResult> = _shareEvents.asSharedFlow()

    init {
        refreshPreview()
    }

    fun onRangePresetSelected(preset: ExportRangePresetUi) {
        _uiState.update { state ->
            when (preset) {
                ExportRangePresetUi.Custom -> {
                    val end = state.customEnd ?: LocalDate.now(zone)
                    val start = state.customStart ?: end.minusDays(DEFAULT_CUSTOM_RANGE_DAYS)
                    state.copy(
                        rangePreset = preset,
                        customStart = start,
                        customEnd = end,
                        error = null,
                    )
                }
                else ->
                    state.copy(rangePreset = preset, error = null)
            }
        }
        refreshPreview()
    }

    fun onCustomDatesChanged(start: LocalDate?, end: LocalDate?) {
        _uiState.update {
            it.copy(
                customStart = start,
                customEnd = end,
                rangePreset = ExportRangePresetUi.Custom,
                error = null,
            )
        }
        refreshPreview()
    }

    fun onExportClick() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, error = null) }
            val range = currentRange()
            if (range == null) {
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        error = ExportLogsErrorUi.InvalidDateOrder,
                    )
                }
                return@launch
            }
            val result = exportLogs(range)
            result.fold(
                onSuccess = { file ->
                    _uiState.update { it.copy(isExporting = false, error = null) }
                    _shareEvents.emit(file)
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(
                            isExporting = false,
                            error = mapExportFailure(throwable),
                        )
                    }
                },
            )
        }
    }

    private fun refreshPreview() {
        viewModelScope.launch {
            val range = currentRange() ?: run {
                _uiState.update { it.copy(preview = ExportLogPreviewUi(0, 0)) }
                return@launch
            }
            val preview = previewExport(range).toUi()
            _uiState.update { it.copy(preview = preview) }
        }
    }

    private fun currentRange(): ExportLogRange? {
        val state = _uiState.value
        return when (state.rangePreset) {
            ExportRangePresetUi.AllTime -> ExportLogRange.AllTime
            ExportRangePresetUi.Last30 -> ExportLogRange.LastDays(days = 30, zone = zone)
            ExportRangePresetUi.Last90 -> ExportLogRange.LastDays(days = 90, zone = zone)
            ExportRangePresetUi.YearToDate -> ExportLogRange.YearToDate(zone = zone)
            ExportRangePresetUi.Custom -> {
                val start = state.customStart
                val end = state.customEnd
                when {
                    start == null || end == null -> null
                    start > end -> null
                    else ->
                        ExportLogRange.Custom(
                            startDate = start,
                            endDateInclusive = end,
                            zone = zone,
                        )
                }
            }
        }
    }

    private companion object {
        const val DEFAULT_CUSTOM_RANGE_DAYS = 30L
        const val EMPTY_RANGE_MESSAGE = "No training logs in the selected range."

        fun mapExportFailure(error: Throwable): ExportLogsErrorUi {
            val message = error.message
            return when {
                message == EMPTY_RANGE_MESSAGE -> ExportLogsErrorUi.EmptyRange
                !message.isNullOrBlank() -> ExportLogsErrorUi.Generic(message)
                else -> ExportLogsErrorUi.Generic("Export failed.")
            }
        }
    }
}
