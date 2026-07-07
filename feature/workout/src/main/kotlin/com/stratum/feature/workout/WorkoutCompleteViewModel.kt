package com.stratum.feature.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.training.EstimatedOneRm
import com.stratum.domain.training.GetWorkoutSummaryUseCase
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.feature.workout.ui.LiftBestSetUi
import com.stratum.feature.workout.ui.WorkoutCompleteUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class WorkoutCompleteViewModel @Inject constructor(
    private val getWorkoutSummary: GetWorkoutSummaryUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<WorkoutCompleteUiState>(WorkoutCompleteUiState.Loading)
    val uiState: StateFlow<WorkoutCompleteUiState> = _uiState.asStateFlow()

    fun loadSummary(sessionId: String) {
        viewModelScope.launch {
            val result = getWorkoutSummary(TrainingSessionId(sessionId))
            _uiState.value = result.fold(
                onSuccess = { summary ->
                    WorkoutCompleteUiState.Loaded(
                        workoutName = summary.workoutName,
                        durationFormatted = formatDuration(summary.durationMillis),
                        bestSets = summary.bestSets.map { best ->
                            LiftBestSetUi(
                                liftName = best.liftName,
                                performanceLabel = formatPerformance(best.weight, best.reps),
                                rirLabel = best.rir?.toString() ?: RIR_NONE,
                                estimatedOneRmLabel = best.estimatedOneRm?.let { formatWeight(it) }
                                    ?: formatEstimatedOneRm(best.weight, best.reps, best.rir),
                                isPersonalRecord = best.isPersonalRecord,
                            )
                        },
                    )
                },
                onFailure = { WorkoutCompleteUiState.Error },
            )
        }
    }

    private fun formatDuration(millis: Long): String {
        val totalSec = millis / MILLIS_PER_SEC
        val hrs = totalSec / SECS_PER_HOUR
        val mins = (totalSec % SECS_PER_HOUR) / SECS_PER_MIN
        val secs = totalSec % SECS_PER_MIN
        return if (hrs > 0) {
            String.format(java.util.Locale.US, "%d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format(java.util.Locale.US, "%dm %02ds", mins, secs)
        }
    }

    private fun formatPerformance(weight: Double, reps: Int): String =
        if (weight == 0.0) reps.toString() else "${formatWeight(weight)} × $reps"

    private fun formatEstimatedOneRm(weight: Double, reps: Int, rir: Int?): String {
        val effectiveReps = reps + (rir ?: 0)
        val e1Rm = EstimatedOneRm.calculate(weight, effectiveReps)
        return formatWeight(e1Rm)
    }

    private fun formatWeight(weight: Double): String {
        val rounded = (weight * TEN_FACTOR).toInt() / TEN_FACTOR.toDouble()
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    }

    private companion object {
        const val MILLIS_PER_SEC = 1000
        const val SECS_PER_HOUR = 3600
        const val SECS_PER_MIN = 60
        const val TEN_FACTOR = 10
        const val RIR_NONE = "—"
    }
}
