package com.stratum.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.export.ExportFileResult
import com.stratum.application.export.ExportProgramsUseCase
import com.stratum.application.export.PreviewExportProgramsUseCase
import com.stratum.domain.program.identity.ProgramId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExportProgramRowUi(
    val id: String,
    val name: String,
    val isActive: Boolean,
)

data class ExportProgramsUiState(
    val programs: List<ExportProgramRowUi> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val isExporting: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class ExportProgramsViewModel @Inject constructor(
    private val previewPrograms: PreviewExportProgramsUseCase,
    private val exportPrograms: ExportProgramsUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExportProgramsUiState())
    val uiState: StateFlow<ExportProgramsUiState> = _uiState.asStateFlow()

    private val _shareEvents = MutableSharedFlow<ExportFileResult>(extraBufferCapacity = 1)
    val shareEvents: SharedFlow<ExportFileResult> = _shareEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            val options = previewPrograms()
            _uiState.update {
                it.copy(
                    programs =
                        options.map { opt ->
                            ExportProgramRowUi(
                                id = opt.id.value,
                                name = opt.name,
                                isActive = opt.isActive,
                            )
                        },
                )
            }
        }
    }

    fun onToggleProgram(id: String) {
        _uiState.update { state ->
            val next =
                if (id in state.selectedIds) {
                    state.selectedIds - id
                } else {
                    state.selectedIds + id
                }
            state.copy(selectedIds = next, errorMessage = null)
        }
    }

    fun onSelectAll() {
        _uiState.update { state ->
            state.copy(
                selectedIds = state.programs.map { it.id }.toSet(),
                errorMessage = null,
            )
        }
    }

    fun onClearSelection() {
        _uiState.update { state ->
            state.copy(selectedIds = emptySet(), errorMessage = null)
        }
    }

    fun onExportClick() {
        viewModelScope.launch {
            val selected = _uiState.value.selectedIds
            if (selected.isEmpty()) {
                _uiState.update {
                    it.copy(errorMessage = "Select at least one program to export.")
                }
                return@launch
            }
            _uiState.update { it.copy(isExporting = true, errorMessage = null) }
            val result = exportPrograms(selected.map { ProgramId(it) })
            result.fold(
                onSuccess = { file ->
                    _uiState.update { it.copy(isExporting = false, errorMessage = null) }
                    _shareEvents.emit(file)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isExporting = false,
                            errorMessage = error.message ?: "Export failed.",
                        )
                    }
                },
            )
        }
    }
}
