package com.stratum.feature.program.ui

import androidx.lifecycle.ViewModel
import com.stratum.application.workout.ConfirmReorderWorkoutLiftsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ReorderLiftUiState(
    val workoutLiftId: String,
    val name: String,
)

data class ReorderLiftsUiState(
    val lifts: List<ReorderLiftUiState> = emptyList(),
    val isLoaded: Boolean = false,
)

@HiltViewModel
class ReorderLiftsViewModel @Inject constructor(
    private val confirmReorderWorkoutLifts: ConfirmReorderWorkoutLiftsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReorderLiftsUiState())
    val uiState: StateFlow<ReorderLiftsUiState> = _uiState.asStateFlow()

    fun initialize() {
        if (_uiState.value.isLoaded) return
        _uiState.value =
            ReorderLiftsUiState(
                lifts =
                    confirmReorderWorkoutLifts.loadReorderLifts().map { item ->
                        ReorderLiftUiState(
                            workoutLiftId = item.workoutLiftId,
                            name = item.name,
                        )
                    },
                isLoaded = true,
            )
    }

    fun reorderLifts(fromIndex: Int, toIndex: Int) {
        val current = _uiState.value.lifts.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            _uiState.value = _uiState.value.copy(lifts = current)
        }
    }

    /**
     * Emits the current order through the use case. No-op before [initialize]:
     * confirming an uninitialized (empty) list would publish an empty order that
     * the editor consumers map to "no lifts".
     */
    fun confirmReorder(workoutId: String) {
        if (!_uiState.value.isLoaded) return
        val orderedIds = _uiState.value.lifts.map { it.workoutLiftId }
        confirmReorderWorkoutLifts(workoutId, orderedIds)
    }
}
