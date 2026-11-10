package com.stratum.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.application.lift.ObserveWeightDisplayPreferencesUseCase
import com.stratum.domain.lift.Weight
import com.stratum.application.training.GetWorkoutSessionDetailResult
import com.stratum.application.training.GetWorkoutSessionDetailUseCase
import com.stratum.application.training.UpdateCompletedSessionTimesResult
import com.stratum.application.training.UpdateCompletedSessionTimesUseCase
import com.stratum.domain.training.WorkoutSessionDetail
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.feature.history.ui.WorkoutSessionDetailUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// STR-216: STR-207 §2 exception — `userMessage` transient-snackbar side-flow
// alongside `uiState`; bless-vs-migrate decision recorded on STR-216.
@Suppress("ForbiddenMultipleStateFlowInViewModel")
@HiltViewModel
class WorkoutSessionDetailViewModel @Inject constructor(
    private val getWorkoutSessionDetail: GetWorkoutSessionDetailUseCase,
    private val updateCompletedSessionTimes: UpdateCompletedSessionTimesUseCase,
    private val observeWeightDisplayPreferences: ObserveWeightDisplayPreferencesUseCase,
    private val diagnosticDrain: DiagnosticDrain,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<WorkoutSessionDetailUiState>(WorkoutSessionDetailUiState.Loading)
    val uiState: StateFlow<WorkoutSessionDetailUiState> = _uiState.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private var currentSessionId: String? = null
    private var cachedDetail: WorkoutSessionDetail? = null
    private var displayUnit: Weight.WeightUnit = Weight.WeightUnit.Lb
    private var isUpdating: Boolean = false

    init {
        viewModelScope.launch {
            observeWeightDisplayPreferences().collect { prefs ->
                if (displayUnit == prefs.displayUnit) return@collect
                displayUnit = prefs.displayUnit
                cachedDetail?.let { detail ->
                    _uiState.value =
                        WorkoutSessionDetailUiMapper.toDetailLoaded(detail, displayUnit)
                }
            }
        }
    }

    fun load(sessionId: String) {
        val isFirstLoadForSession =
            currentSessionId != sessionId || _uiState.value !is WorkoutSessionDetailUiState.Loaded
        currentSessionId = sessionId
        viewModelScope.launch {
            loadInternal(sessionId, isInitial = isFirstLoadForSession)
        }
    }

    fun updateWorkoutTimes(
        startDate: LocalDate,
        startTime: LocalTime,
        finishDate: LocalDate,
        finishTime: LocalTime,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ) {
        val sessionId = currentSessionId ?: return
        if (isUpdating) return
        val startedAt = startDate.atTime(startTime).atZone(zoneId).toInstant()
        val endedAt = finishDate.atTime(finishTime).atZone(zoneId).toInstant()

        viewModelScope.launch {
            isUpdating = true
            try {
                val result =
                    updateCompletedSessionTimes(
                        sessionId = TrainingSessionId(sessionId),
                        startedAt = startedAt,
                        endedAt = endedAt,
                        // STR-240 chunk 7: the post-edit refresh's stage-failure
                        // diagnostics are delivered through the boundary callback
                        // and drained here.
                        onRefreshDiagnostics = { diagnostics -> diagnosticDrain.drain(diagnostics) },
                    )
                when (result) {
                    is UpdateCompletedSessionTimesResult.Success -> {
                        diagnosticDrain.drain(result.diagnostics)
                        loadInternal(sessionId, isInitial = false, zoneId = zoneId)
                    }
                    else ->
                        // Same user copy the former exception messages carried, sourced
                        // from the branch kind (STR-243).
                        _userMessage.value =
                            when (result) {
                                is UpdateCompletedSessionTimesResult.SessionNotFound ->
                                    "Training session ${result.sessionId.value} not found"
                                is UpdateCompletedSessionTimesResult.SessionNotCompleted ->
                                    "Training session ${result.sessionId.value} is not completed " +
                                        "(status=${result.status})"
                                is UpdateCompletedSessionTimesResult.StartAfterFinish ->
                                    "Start time (${result.startedAt}) cannot be after " +
                                        "finish time (${result.endedAt})"
                                is UpdateCompletedSessionTimesResult.PersistenceFailed -> {
                                    diagnosticDrain.drain(result.diagnostics)
                                    "Could not update workout time"
                                }
                                is UpdateCompletedSessionTimesResult.Success ->
                                    "Could not update workout time"
                            }
                }
            } finally {
                isUpdating = false
            }
        }
    }

    private suspend fun loadInternal(
        sessionId: String,
        isInitial: Boolean,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ) {
        if (isInitial) {
            _uiState.value = WorkoutSessionDetailUiState.Loading
        }
        displayUnit = observeWeightDisplayPreferences().first().displayUnit
        val result = getWorkoutSessionDetail(TrainingSessionId(sessionId))
        _uiState.value =
            when (result) {
                is GetWorkoutSessionDetailResult.PersistenceFailed -> {
                    diagnosticDrain.drain(result.diagnostics)
                    cachedDetail = null
                    WorkoutSessionDetailUiState.Error
                }
                is GetWorkoutSessionDetailResult.Success -> {
                    cachedDetail = result.detail
                    WorkoutSessionDetailUiMapper.toDetailLoaded(result.detail, displayUnit, zoneId)
                }
                else -> {
                    cachedDetail = null
                    WorkoutSessionDetailUiState.Error
                }
            }
    }

    fun userMessageShown() {
        _userMessage.value = null
    }
}
