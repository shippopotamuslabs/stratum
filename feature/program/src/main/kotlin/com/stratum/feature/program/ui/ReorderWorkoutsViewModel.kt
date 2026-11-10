package com.stratum.feature.program.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.application.program.ReorderProgramCycleDaysResult
import com.stratum.application.program.ReorderProgramCycleDaysUseCase
import com.stratum.domain.program.identity.ProgramId
import com.stratum.feature.program.DiagnosticDrain
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReorderWorkoutUiState(
    val dayId: String,
    val name: String,
)

data class ReorderWorkoutsUiState(
    val days: List<ReorderWorkoutUiState> = emptyList(),
    val isLoaded: Boolean = false,
)

/**
 * Persist-only reorder of an existing program's cycle days (Programs tab).
 * Create Program drafts host [com.stratum.core.ui.component.StratumReorderListScreen] locally.
 */
@HiltViewModel
class ReorderWorkoutsViewModel @Inject constructor(
    private val reorderProgramCycleDays: ReorderProgramCycleDaysUseCase,
    private val diagnosticDrain: DiagnosticDrain,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReorderWorkoutsUiState())
    val uiState: StateFlow<ReorderWorkoutsUiState> = _uiState.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private var programId: String = ""
    private var loadJob: Job? = null
    private var persistJob: Job? = null

    fun initialize(programId: String) {
        if (this.programId == programId && _uiState.value.isLoaded) return
        this.programId = programId
        _uiState.value = ReorderWorkoutsUiState()
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                val days =
                    reorderProgramCycleDays.loadReorderItems(ProgramId(programId)).map { item ->
                        ReorderWorkoutUiState(dayId = item.dayId, name = item.name)
                    }
                _uiState.value = ReorderWorkoutsUiState(days = days, isLoaded = true)
            }
    }

    fun reorderDays(fromIndex: Int, toIndex: Int) {
        val current = _uiState.value.days.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            _uiState.value = _uiState.value.copy(days = current)
        }
    }

    /**
     * Persists the current order. Invokes [onDone] only after a successful (or no-op) save so
     * navigation does not race a failed write.
     */
    fun confirmReorder(onDone: () -> Unit) {
        if (!_uiState.value.isLoaded) return
        if (persistJob?.isActive == true) return
        val orderedIds = _uiState.value.days.map { day -> day.dayId }
        if (orderedIds.isEmpty()) return
        persistJob =
            viewModelScope.launch {
                when (val result = reorderProgramCycleDays(ProgramId(programId), orderedIds)) {
                    ReorderProgramCycleDaysResult.Success -> onDone()
                    is ReorderProgramCycleDaysResult.ProgramNotFound ->
                        _userMessage.value = MSG_REORDER_FAILED
                    is ReorderProgramCycleDaysResult.PersistenceFailed -> {
                        diagnosticDrain.drain(result.diagnostics)
                        _userMessage.value = MSG_REORDER_FAILED
                    }
                }
            }
    }

    fun userMessageShown() {
        _userMessage.value = null
    }

    private companion object {
        const val MSG_REORDER_FAILED = "Couldn't reorder workouts. Try again."
    }
}
