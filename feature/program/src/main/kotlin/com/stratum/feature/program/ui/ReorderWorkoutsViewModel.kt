package com.stratum.feature.program.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.program.ReorderProgramCycleDaysUseCase
import com.stratum.domain.program.identity.ProgramId
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

/**
 * Persist-only reorder of an existing program's cycle days (Programs tab).
 * Create Program drafts host [com.stratum.core.ui.component.StratumReorderListScreen] locally.
 */
@HiltViewModel
class ReorderWorkoutsViewModel @Inject constructor(
    private val reorderProgramCycleDays: ReorderProgramCycleDaysUseCase,
) : ViewModel() {

    private val _days = MutableStateFlow<List<ReorderWorkoutUiState>>(emptyList())
    val days: StateFlow<List<ReorderWorkoutUiState>> = _days.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private var programId: String = ""
    private var loadJob: Job? = null
    private var persistJob: Job? = null

    fun initialize(programId: String) {
        if (this.programId == programId && _isLoaded.value) return
        this.programId = programId
        _isLoaded.value = false
        _days.value = emptyList()
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                _days.value =
                    reorderProgramCycleDays.loadReorderItems(ProgramId(programId)).map { item ->
                        ReorderWorkoutUiState(dayId = item.dayId, name = item.name)
                    }
                _isLoaded.value = true
            }
    }

    fun reorderDays(fromIndex: Int, toIndex: Int) {
        val current = _days.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            _days.value = current
        }
    }

    /**
     * Persists the current order. Invokes [onDone] only after a successful (or no-op) save so
     * navigation does not race a failed write.
     */
    fun confirmReorder(onDone: () -> Unit) {
        if (!_isLoaded.value) return
        if (persistJob?.isActive == true) return
        val orderedIds = _days.value.map { day -> day.dayId }
        if (orderedIds.isEmpty()) return
        persistJob =
            viewModelScope.launch {
                reorderProgramCycleDays(ProgramId(programId), orderedIds)
                    .onSuccess { onDone() }
                    .onFailure { _userMessage.value = MSG_REORDER_FAILED }
            }
    }

    fun userMessageShown() {
        _userMessage.value = null
    }

    private companion object {
        const val MSG_REORDER_FAILED = "Couldn't reorder workouts. Try again."
    }
}
