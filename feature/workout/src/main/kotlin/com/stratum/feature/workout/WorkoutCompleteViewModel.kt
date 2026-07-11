package com.stratum.feature.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.core.ui.component.RIR_NONE_LABEL
import com.stratum.core.ui.component.formatSetPerformance
import com.stratum.core.ui.component.formatWeightAmount
import com.stratum.core.ui.component.formatWorkoutDuration
import com.stratum.domain.lift.AnalyzeSessionEquipmentProposalsUseCase
import com.stratum.domain.lift.ApplyEquipmentIncrementProposalUseCase
import com.stratum.domain.lift.EquipmentIncrementRemedy
import com.stratum.domain.lift.EquipmentProposalPreferences
import com.stratum.domain.training.EstimatedOneRm
import com.stratum.domain.training.GetWorkoutSummaryUseCase
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.feature.workout.ui.EquipmentProposalUiMapper
import com.stratum.feature.workout.ui.LiftBestSetUi
import com.stratum.feature.workout.ui.WorkoutCompleteUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Orchestrates workout-complete summary + equipment-increment proposals.
 *
 * Domain analysis/apply live in use cases; card copy and session state live in
 * [EquipmentProposalUiMapper] / [EquipmentProposalSession].
 */
@HiltViewModel
class WorkoutCompleteViewModel @Inject constructor(
    private val getWorkoutSummary: GetWorkoutSummaryUseCase,
    private val analyzeSessionEquipmentProposals: AnalyzeSessionEquipmentProposalsUseCase,
    private val applyEquipmentIncrementProposal: ApplyEquipmentIncrementProposalUseCase,
    private val equipmentProposalPreferences: EquipmentProposalPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow<WorkoutCompleteUiState>(WorkoutCompleteUiState.Loading)
    val uiState: StateFlow<WorkoutCompleteUiState> = _uiState.asStateFlow()

    private val proposalSession = EquipmentProposalSession()

    fun loadSummary(sessionId: String) {
        viewModelScope.launch {
            val result = getWorkoutSummary(TrainingSessionId(sessionId))
            _uiState.value =
                result.fold(
                    onSuccess = { summary ->
                        // Analyze logs failures internally and returns empty list.
                        val proposals =
                            analyzeSessionEquipmentProposals(TrainingSessionId(sessionId))
                        val sessionSnap = proposalSession.load(proposals)
                        WorkoutCompleteUiState.Loaded(
                            workoutName = summary.workoutName,
                            durationFormatted = formatWorkoutDuration(summary.durationMillis),
                            bestSets =
                                summary.bestSets.map { best ->
                                    LiftBestSetUi(
                                        liftName = best.liftName,
                                        performanceLabel =
                                            formatSetPerformance(best.weight, best.reps),
                                        rirLabel = best.rir?.toString() ?: RIR_NONE_LABEL,
                                        estimatedOneRmLabel =
                                            best.estimatedOneRm?.let {
                                                formatWeightAmount(it).ifEmpty { "0" }
                                            }
                                                ?: formatEstimatedOneRm(
                                                    best.weight,
                                                    best.reps,
                                                    best.rir,
                                                ),
                                        isPersonalRecord = best.isPersonalRecord,
                                    )
                                },
                            equipmentProposals = sessionSnap.cards,
                            blastRadiusConfirm = sessionSnap.blastRadiusConfirm,
                            userMessage = sessionSnap.userMessage,
                        )
                    },
                    onFailure = { WorkoutCompleteUiState.Error },
                )
        }
    }

    fun onAcceptPrimary(proposalId: String) {
        when (val action = proposalSession.acceptPrimary(proposalId)) {
            is EquipmentProposalSession.AcceptAction.NeedsConfirm ->
                applySessionSnapshot(
                    proposalSession.stageConfirm(action.proposalId, action.remedy),
                )
            is EquipmentProposalSession.AcceptAction.ApplyNow ->
                launchApply(action.proposalId, action.remedy)
            EquipmentProposalSession.AcceptAction.Ignored -> Unit
        }
    }

    fun onAcceptAlternative(proposalId: String) {
        when (val action = proposalSession.acceptAlternative(proposalId)) {
            is EquipmentProposalSession.AcceptAction.NeedsConfirm ->
                applySessionSnapshot(
                    proposalSession.stageConfirm(action.proposalId, action.remedy),
                )
            is EquipmentProposalSession.AcceptAction.ApplyNow ->
                launchApply(action.proposalId, action.remedy)
            EquipmentProposalSession.AcceptAction.Ignored -> Unit
        }
    }

    fun onConfirmAccept() {
        when (val action = proposalSession.confirmAccept()) {
            is EquipmentProposalSession.AcceptAction.ApplyNow ->
                launchApply(action.proposalId, action.remedy)
            else -> Unit
        }
    }

    fun onDismissConfirm() {
        applySessionSnapshot(proposalSession.dismissConfirm())
    }

    fun onSkipProposal(proposalId: String) {
        applySessionSnapshot(proposalSession.remove(proposalId))
    }

    fun onNotNowProposal(proposalId: String) {
        viewModelScope.launch {
            equipmentProposalPreferences.dismiss(proposalId)
            applySessionSnapshot(proposalSession.remove(proposalId))
        }
    }

    fun userMessageShown() {
        applySessionSnapshot(proposalSession.clearUserMessage())
    }

    private fun launchApply(
        proposalId: String,
        remedy: EquipmentIncrementRemedy,
    ) {
        if (proposalSession.beginApply(proposalId) == null) return
        // Reflect isApplying on cards immediately.
        applySessionSnapshot(proposalSession.snapshot())
        viewModelScope.launch {
            var settled = false
            try {
                val result = applyEquipmentIncrementProposal(remedy)
                result
                    .onSuccess { outcome ->
                        settled = true
                        applySessionSnapshot(
                            proposalSession.completeSuccess(
                                proposalId = proposalId,
                                message =
                                    EquipmentProposalUiMapper.successMessage(outcome, remedy),
                            ),
                        )
                    }
                    .onFailure {
                        settled = true
                        applySessionSnapshot(
                            proposalSession.completeFailure(
                                proposalId = proposalId,
                                message = EquipmentProposalUiMapper.MSG_APPLY_FAILED,
                            ),
                        )
                    }
            } finally {
                // Cancellation mid-apply: release in-flight without dropping the card.
                if (!settled) {
                    applySessionSnapshot(proposalSession.releaseApply(proposalId))
                }
            }
        }
    }

    private fun applySessionSnapshot(snap: EquipmentProposalSession.Snapshot?) {
        if (snap == null) return
        updateLoaded { loaded ->
            loaded.copy(
                equipmentProposals = snap.cards,
                blastRadiusConfirm = snap.blastRadiusConfirm,
                userMessage = snap.userMessage,
            )
        }
    }

    private fun updateLoaded(
        transform: (WorkoutCompleteUiState.Loaded) -> WorkoutCompleteUiState.Loaded,
    ) {
        val current = _uiState.value
        if (current is WorkoutCompleteUiState.Loaded) {
            _uiState.value = transform(current)
        }
    }

    private fun formatEstimatedOneRm(weight: Double, reps: Int, rir: Int?): String {
        val effectiveReps = reps + (rir ?: 0)
        val e1Rm = EstimatedOneRm.calculate(weight, effectiveReps)
        return formatWeightAmount(e1Rm).ifEmpty { "0" }
    }
}
