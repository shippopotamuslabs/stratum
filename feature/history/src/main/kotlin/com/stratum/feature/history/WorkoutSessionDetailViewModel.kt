package com.stratum.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.core.ui.component.formatSetPerformance
import com.stratum.core.ui.component.formatWorkoutDuration
import com.stratum.core.ui.component.toLowerVolumeCards
import com.stratum.core.ui.component.toUpperVolumeCards
import com.stratum.domain.training.GetWorkoutSessionDetailUseCase
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.feature.history.ui.HistoryLiftUi
import com.stratum.feature.history.ui.HistorySetUi
import com.stratum.feature.history.ui.WorkoutSessionDetailUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class WorkoutSessionDetailViewModel @Inject constructor(
    private val getWorkoutSessionDetail: GetWorkoutSessionDetailUseCase,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<WorkoutSessionDetailUiState>(WorkoutSessionDetailUiState.Loading)
    val uiState: StateFlow<WorkoutSessionDetailUiState> = _uiState.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    fun load(sessionId: String) {
        viewModelScope.launch {
            _uiState.value = WorkoutSessionDetailUiState.Loading
            val result = getWorkoutSessionDetail(TrainingSessionId(sessionId))
            _uiState.value =
                result.fold(
                    onSuccess = { detail ->
                        WorkoutSessionDetailUiState.Loaded(
                            workoutName = detail.workoutName,
                            completedAtLabel = formatCompletedAt(detail.completedAt),
                            durationFormatted = formatWorkoutDuration(detail.durationMillis),
                            totalSetsLabel = detail.totalSets.toString(),
                            personalRecordsLabel = detail.personalRecordCount.toString(),
                            upperVolumeByMuscle = detail.muscleVolumes.toUpperVolumeCards(),
                            lowerVolumeByMuscle = detail.muscleVolumes.toLowerVolumeCards(),
                            lifts =
                                detail.lifts.map { lift ->
                                    HistoryLiftUi(
                                        liftName = lift.liftName,
                                        sets =
                                            lift.sets.map { set ->
                                                HistorySetUi(
                                                    setIndex = set.setIndex,
                                                    performanceLabel =
                                                        formatSetPerformance(
                                                            set.weight.amount,
                                                            set.reps,
                                                        ),
                                                    rir = set.rir,
                                                    isPersonalRecord = set.isPersonalRecord,
                                                )
                                            },
                                    )
                                },
                        )
                    },
                    onFailure = { WorkoutSessionDetailUiState.Error },
                )
        }
    }

    fun userMessageShown() {
        _userMessage.value = null
    }

    private fun formatCompletedAt(instant: java.time.Instant): String {
        val zoned = instant.atZone(ZoneId.systemDefault())
        return COMPLETED_AT_FORMATTER.format(zoned)
    }

    private companion object {
        val COMPLETED_AT_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a", Locale.US)
    }
}
