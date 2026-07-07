package com.stratum.feature.program.ui

import androidx.lifecycle.ViewModel
import com.stratum.domain.workout.WorkoutEditSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class ReorderLiftUiState(
    val workoutLiftId: String,
    val name: String,
)

@HiltViewModel
class ReorderLiftsViewModel @Inject constructor(
    private val workoutEditSession: WorkoutEditSession
) : ViewModel() {

    private val _lifts = MutableStateFlow<List<ReorderLiftUiState>>(emptyList())
    val lifts: StateFlow<List<ReorderLiftUiState>> = _lifts.asStateFlow()

    fun initialize() {
        _lifts.value = workoutEditSession.getReorderLifts().map { item ->
            ReorderLiftUiState(
                workoutLiftId = item.workoutLiftId,
                name = item.name,
            )
        }
    }

    fun reorderLifts(fromIndex: Int, toIndex: Int) {
        val current = _lifts.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            _lifts.value = current
        }
    }

    fun confirmReorder(workoutId: String) {
        val orderedIds = _lifts.value.map { it.workoutLiftId }
        workoutEditSession.confirmReorder(workoutId, orderedIds)
    }
}
