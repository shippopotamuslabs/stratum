package com.stratum.feature.program

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.checkin.ApplyCheckInRecommendationsUseCase
import com.stratum.domain.checkin.CheckInSubjectiveFeedback
import com.stratum.domain.checkin.EvaluateCheckInUseCase
import com.stratum.domain.checkin.GetCheckInMuscleGroupsUseCase
import com.stratum.domain.checkin.ObservePendingCheckInUseCase
import com.stratum.domain.checkin.RecoverySignal
import com.stratum.domain.checkin.SkipCheckInUseCase
import com.stratum.domain.lift.RankSwapCandidatesUseCase
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.feature.program.ui.CheckInMuscleOptionUi
import com.stratum.feature.program.ui.CheckInMuscleSignalUi
import com.stratum.feature.program.ui.CheckInPhase
import com.stratum.feature.program.ui.CheckInRecommendationUi
import com.stratum.feature.program.ui.CheckInSwapCandidateUi
import com.stratum.feature.program.ui.CheckInUiState
import com.stratum.feature.program.ui.RecoverySignalUi
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class CheckInViewModel @Inject constructor(
    observePendingCheckIn: ObservePendingCheckInUseCase,
    private val skipCheckIn: SkipCheckInUseCase,
    private val evaluateCheckIn: EvaluateCheckInUseCase,
    private val applyRecommendations: ApplyCheckInRecommendationsUseCase,
    private val getCheckInMuscleGroups: GetCheckInMuscleGroupsUseCase,
    private val rankSwapCandidates: RankSwapCandidatesUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CheckInUiState())
    val uiState: StateFlow<CheckInUiState> = _uiState.asStateFlow()

    private val applyInFlight = AtomicBoolean(false)

    val showBanner: StateFlow<Boolean> =
        observePendingCheckIn().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    fun openCheckIn() {
        viewModelScope.launch {
            val muscles = getCheckInMuscleGroups()
            applyInFlight.set(false)
            _uiState.value =
                CheckInUiState(
                    isOpen = true,
                    phase = CheckInPhase.Subjective,
                    overallRecovery = RecoverySignalUi.Good,
                    hasLocalizedIssues = null,
                    availableMuscles =
                        muscles.map { muscle ->
                            CheckInMuscleOptionUi(
                                muscleKey = muscle.name,
                                displayName = muscle.displayLabel,
                            )
                        },
                )
        }
    }

    fun dismiss() {
        applyInFlight.set(false)
        _uiState.value = CheckInUiState()
    }

    fun skip() {
        viewModelScope.launch {
            skipCheckIn()
                .onSuccess { dismiss() }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "Could not skip check-in")
                    }
                }
        }
    }

    fun setOverallRecovery(signal: RecoverySignalUi) {
        if (signal == RecoverySignalUi.JointPain) return
        _uiState.update { it.copy(overallRecovery = signal) }
    }

    fun setHasLocalizedIssues(hasIssues: Boolean) {
        _uiState.update { state ->
            if (!hasIssues) {
                state.copy(
                    hasLocalizedIssues = false,
                    flaggedMuscleKeys = emptySet(),
                    muscleSignals = emptyList(),
                )
            } else {
                state.copy(hasLocalizedIssues = true)
            }
        }
    }

    fun toggleFlaggedMuscle(muscleKey: String) {
        _uiState.update { state ->
            val currentlyFlagged = muscleKey in state.flaggedMuscleKeys
            if (currentlyFlagged) {
                state.copy(
                    flaggedMuscleKeys = state.flaggedMuscleKeys - muscleKey,
                    muscleSignals = state.muscleSignals.filterNot { it.muscleKey == muscleKey },
                )
            } else {
                val displayName =
                    state.availableMuscles.firstOrNull { it.muscleKey == muscleKey }?.displayName
                        ?: muscleKey
                state.copy(
                    flaggedMuscleKeys = state.flaggedMuscleKeys + muscleKey,
                    muscleSignals =
                        state.muscleSignals +
                            CheckInMuscleSignalUi(
                                muscleKey = muscleKey,
                                displayName = displayName,
                                signal = RecoverySignalUi.MildSoreness,
                            ),
                )
            }
        }
    }

    fun setMuscleSignal(muscleKey: String, signal: RecoverySignalUi) {
        if (signal == RecoverySignalUi.Good) return
        _uiState.update { state ->
            state.copy(
                muscleSignals =
                    state.muscleSignals.map { row ->
                        if (row.muscleKey == muscleKey) row.copy(signal = signal) else row
                    },
            )
        }
    }

    fun goNext() {
        when (_uiState.value.phase) {
            CheckInPhase.Subjective -> evaluateAndAdvance()
            CheckInPhase.Recommendations -> applyAndComplete()
        }
    }

    fun goBack() {
        when (_uiState.value.phase) {
            CheckInPhase.Subjective -> dismiss()
            CheckInPhase.Recommendations ->
                _uiState.update {
                    it.copy(
                        phase = CheckInPhase.Subjective,
                        recommendations = emptyList(),
                        programLiftIds = emptySet(),
                        errorMessage = null,
                        isEvaluating = false,
                        swapPickerRecommendationId = null,
                        swapCandidates = emptyList(),
                    )
                }
        }
    }

    fun setAccepted(recommendationId: String, accepted: Boolean) {
        _uiState.update { state ->
            state.copy(
                recommendations =
                    state.recommendations.map { rec ->
                        if (rec.id == recommendationId) rec.withAccepted(accepted) else rec
                    },
            )
        }
    }

    fun openSwapPicker(recommendationId: String) {
        val rec =
            _uiState.value.recommendations
                .filterIsInstance<CheckInRecommendationUi.Swap>()
                .firstOrNull { it.id == recommendationId }
                ?: return
        viewModelScope.launch {
            val ranked = rankSwapCandidates(LiftId(rec.currentLiftId)).first()
            val exclude =
                buildSwapExcludeIds(
                    programLiftIds = _uiState.value.programLiftIds,
                    recommendations = _uiState.value.recommendations,
                    currentRecommendationId = recommendationId,
                    currentLiftId = rec.currentLiftId,
                )
            _uiState.update { state ->
                state.copy(
                    swapPickerRecommendationId = recommendationId,
                    swapCandidates =
                        ranked
                            .asSequence()
                            .filter { scored -> scored.lift.id.value !in exclude }
                            .take(24)
                            .map { scored ->
                                CheckInSwapCandidateUi(
                                    liftId = scored.lift.id.value,
                                    name = scored.lift.name,
                                    selected = scored.lift.id.value == rec.suggestedLiftId,
                                )
                            }
                            .toList(),
                )
            }
        }
    }

    fun selectSwapCandidate(liftId: String, name: String) {
        val recId = _uiState.value.swapPickerRecommendationId ?: return
        _uiState.update { state ->
            state.copy(
                swapPickerRecommendationId = null,
                swapCandidates = emptyList(),
                recommendations =
                    state.recommendations.map { rec ->
                        if (rec is CheckInRecommendationUi.Swap && rec.id == recId) {
                            rec.copy(
                                suggestedLiftId = liftId,
                                suggestedLiftName = name,
                                titleArgs = listOf(rec.liftName, name),
                            )
                        } else {
                            rec
                        }
                    },
            )
        }
    }

    fun dismissSwapPicker() {
        _uiState.update {
            it.copy(swapPickerRecommendationId = null, swapCandidates = emptyList())
        }
    }

    private fun evaluateAndAdvance() {
        val state = _uiState.value
        if (!state.canAdvanceSubjective) return
        viewModelScope.launch {
            _uiState.update { it.copy(isEvaluating = true, errorMessage = null) }
            val muscleMap =
                if (state.hasLocalizedIssues == true) {
                    state.muscleSignals.associate { row ->
                        MuscleGroup.valueOf(row.muscleKey) to row.signal.toDomain()
                    }
                } else {
                    emptyMap()
                }
            val subjective =
                CheckInSubjectiveFeedback(
                    overallRecovery = state.overallRecovery.toDomain(),
                    muscleSignals = muscleMap,
                )
            val evaluation = evaluateCheckIn(subjective)
            if (evaluation == null) {
                _uiState.update {
                    it.copy(
                        isEvaluating = false,
                        errorMessage = "No active program to evaluate",
                    )
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    isEvaluating = false,
                    phase = CheckInPhase.Recommendations,
                    programLiftIds = evaluation.programLiftIds.map { id -> id.value }.toSet(),
                    recommendations =
                        evaluation.recommendations.mapNotNull { rec -> rec.toUi(accepted = true) },
                )
            }
        }
    }

    private fun applyAndComplete() {
        if (!applyInFlight.compareAndSet(false, true)) return
        viewModelScope.launch {
            _uiState.update { it.copy(isApplying = true, errorMessage = null) }
            val commands =
                _uiState.value.recommendations.mapNotNull { it.toApplyCommand() }
            applyRecommendations(commands)
                .onSuccess {
                    applyInFlight.set(false)
                    dismiss()
                }
                .onFailure { error ->
                    applyInFlight.set(false)
                    _uiState.update {
                        it.copy(
                            isApplying = false,
                            errorMessage = error.message ?: "Could not apply check-in",
                        )
                    }
                }
        }
    }

    private fun buildSwapExcludeIds(
        programLiftIds: Set<String>,
        recommendations: List<CheckInRecommendationUi>,
        currentRecommendationId: String,
        currentLiftId: String,
    ): Set<String> {
        val otherAcceptedSwaps =
            recommendations
                .filterIsInstance<CheckInRecommendationUi.Swap>()
                .filter { it.accepted && it.id != currentRecommendationId }
                .map { it.suggestedLiftId }
                .toSet()
        return programLiftIds + otherAcceptedSwaps - currentLiftId
    }
}

private fun RecoverySignalUi.toDomain(): RecoverySignal =
    when (this) {
        RecoverySignalUi.Good -> RecoverySignal.Good
        RecoverySignalUi.MildSoreness -> RecoverySignal.MildSoreness
        RecoverySignalUi.SevereUnderRecovery -> RecoverySignal.SevereUnderRecovery
        RecoverySignalUi.JointPain -> RecoverySignal.JointPain
    }

