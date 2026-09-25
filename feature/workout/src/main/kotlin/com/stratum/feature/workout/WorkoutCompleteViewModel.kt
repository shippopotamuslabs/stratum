package com.stratum.feature.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.core.ui.component.RIR_NONE_LABEL
import com.stratum.core.ui.component.formatSetPerformance
import com.stratum.core.ui.component.formatWeightAmountForDisplay
import com.stratum.core.ui.component.formatWorkoutDuration
import com.stratum.domain.healthconnect.ClearHealthConnectSyncErrorUseCase
import com.stratum.domain.healthconnect.ObserveHealthConnectSyncErrorsUseCase
import com.stratum.domain.lift.AnalyzeSessionEquipmentProposalsUseCase
import com.stratum.domain.lift.ApplyEquipmentIncrementProposalUseCase
import com.stratum.domain.lift.EquipmentIncrementRemedy
import com.stratum.domain.lift.EquipmentProposalPreferences
import com.stratum.domain.lift.ObserveWeightDisplayPreferencesUseCase
import com.stratum.domain.training.GetWorkoutSummaryUseCase
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.domain.workout.AnalyzeSessionRepRangeShiftProposalsUseCase
import com.stratum.domain.workout.ApplyRepRangeShiftResult
import com.stratum.domain.workout.ApplyRepRangeShiftUseCase
import com.stratum.domain.workout.RepRangeShiftProposalPreferences
import com.stratum.feature.workout.ui.EquipmentProposalUiMapper
import com.stratum.feature.workout.ui.LiftBestSetUi
import com.stratum.feature.workout.ui.RepRangeShiftUiMapper
import com.stratum.feature.workout.ui.WorkoutCompleteEvent
import com.stratum.feature.workout.ui.WorkoutCompleteUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Orchestrates workout-complete summary + equipment-increment and rep-range shift proposals.
 *
 * Domain analysis/apply live in use cases; card copy and session state live in mappers /
 * [EquipmentProposalSession] / [RepRangeShiftProposalSession].
 */
@HiltViewModel
class WorkoutCompleteViewModel @Inject constructor(
    private val getWorkoutSummary: GetWorkoutSummaryUseCase,
    private val analyzeSessionEquipmentProposals: AnalyzeSessionEquipmentProposalsUseCase,
    private val applyEquipmentIncrementProposal: ApplyEquipmentIncrementProposalUseCase,
    private val equipmentProposalPreferences: EquipmentProposalPreferences,
    private val analyzeSessionRepRangeShiftProposals: AnalyzeSessionRepRangeShiftProposalsUseCase,
    private val applyRepRangeShift: ApplyRepRangeShiftUseCase,
    private val repRangeShiftProposalPreferences: RepRangeShiftProposalPreferences,
    private val observeWeightDisplayPreferences: ObserveWeightDisplayPreferencesUseCase,
    observeHealthConnectSyncErrors: ObserveHealthConnectSyncErrorsUseCase,
    private val clearHealthConnectSyncError: ClearHealthConnectSyncErrorUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<WorkoutCompleteUiState>(WorkoutCompleteUiState.Loading)
    val uiState: StateFlow<WorkoutCompleteUiState> = _uiState.asStateFlow()

    private val _events = Channel<WorkoutCompleteEvent>(Channel.BUFFERED)
    val events: Flow<WorkoutCompleteEvent> = _events.receiveAsFlow()

    private val equipmentSession = EquipmentProposalSession()
    private val repRangeSession = RepRangeShiftProposalSession()

    private var loadJob: Job? = null
    private var loadGeneration: Long = 0L
    private val repRangeApplyMutex = Mutex()
    private val pendingUserMessages = ArrayDeque<String>()

    init {
        observeHealthConnectSyncErrors()
            .onEach { error ->
                enqueueUserMessage(error)
                publishNextUserMessage()
                clearHealthConnectSyncError()
            }
            .launchIn(viewModelScope)
    }

    fun loadSummary(sessionId: String) {
        loadJob?.cancel()
        val generation = ++loadGeneration
        loadJob =
            viewModelScope.launch {
                // Summary weights are canonical Lb; convert for display preference.
                val displayUnit = observeWeightDisplayPreferences().first().displayUnit
                val result = getWorkoutSummary(TrainingSessionId(sessionId))
                if (generation != loadGeneration) return@launch
                _uiState.value =
                    result.fold(
                        onSuccess = { summary ->
                            val trainingSessionId = TrainingSessionId(sessionId)
                            // Parallel analyze; each use case fails soft to empty list.
                            val (equipmentProposals, repRangeProposals) =
                                coroutineScope {
                                    val equipmentDeferred =
                                        async {
                                            analyzeSessionEquipmentProposals(trainingSessionId)
                                        }
                                    val repRangeDeferred =
                                        async {
                                            analyzeSessionRepRangeShiftProposals(trainingSessionId)
                                        }
                                    equipmentDeferred.await() to repRangeDeferred.await()
                                }
                            if (generation != loadGeneration) {
                                return@fold _uiState.value
                            }
                            val equipmentSnap =
                                equipmentSession.load(
                                    equipmentProposals.proposals,
                                    equipmentProposals.baselineMicroByProposalId,
                                )
                            val repRangeSnap = repRangeSession.load(repRangeProposals)
                            pendingUserMessages.clear()
                            WorkoutCompleteUiState.Loaded(
                                workoutName = summary.workoutName,
                                durationFormatted =
                                    formatWorkoutDuration(summary.durationMillis),
                                bestSets =
                                    summary.bestSets.map { best ->
                                        LiftBestSetUi(
                                            liftName = best.liftName,
                                            performanceLabel =
                                                formatSetPerformance(
                                                    weight = best.weight,
                                                    reps = best.reps,
                                                    displayUnit = displayUnit,
                                                    includeUnit = false,
                                                    partialReps = best.partialReps,
                                                ),
                                            rirLabel =
                                                best.rir?.toString() ?: RIR_NONE_LABEL,
                                            estimatedOneRmLabel =
                                                best.estimatedOneRm?.let { e1 ->
                                                    formatWeightAmountForDisplay(
                                                        amount = e1,
                                                        sourceUnit = best.weight.unit,
                                                        displayUnit = displayUnit,
                                                    ).ifEmpty { "0" }
                                                } ?: RIR_NONE_LABEL,
                                            isPersonalRecord = best.isPersonalRecord,
                                        )
                                    },
                                equipmentProposals = equipmentSnap.cards,
                                repRangeProposals = repRangeSnap.cards,
                                blastRadiusConfirm = equipmentSnap.blastRadiusConfirm,
                                userMessage = null,
                            )
                        },
                        onFailure = { WorkoutCompleteUiState.Error },
                    )
            }
    }

    fun onAcceptPrimary(proposalId: String) {
        when (val action = equipmentSession.acceptPrimary(proposalId)) {
            is EquipmentProposalSession.AcceptAction.NeedsConfirm ->
                applyEquipmentSnapshot(
                    equipmentSession.stageConfirm(action.proposalId, action.remedy),
                )
            is EquipmentProposalSession.AcceptAction.ApplyNow ->
                launchEquipmentApply(action.proposalId, action.remedy)
            EquipmentProposalSession.AcceptAction.Ignored -> Unit
        }
    }

    fun onAcceptAlternative(proposalId: String) {
        when (val action = equipmentSession.acceptAlternative(proposalId)) {
            is EquipmentProposalSession.AcceptAction.NeedsConfirm ->
                applyEquipmentSnapshot(
                    equipmentSession.stageConfirm(action.proposalId, action.remedy),
                )
            is EquipmentProposalSession.AcceptAction.ApplyNow ->
                launchEquipmentApply(action.proposalId, action.remedy)
            EquipmentProposalSession.AcceptAction.Ignored -> Unit
        }
    }

    fun onAcceptRepRangeShift(proposalId: String) {
        val proposal = repRangeSession.beginApply(proposalId) ?: return
        applyRepRangeSnapshot(repRangeSession.snapshot())
        viewModelScope.launch {
            repRangeApplyMutex.withLock {
                var settled = false
                try {
                    val result =
                        applyRepRangeShift(
                            workoutLiftId = proposal.workoutLiftId,
                            expectedCurrentRepMin = proposal.currentRepMin,
                            expectedCurrentRepMax = proposal.currentRepMax,
                            proposedRepMin = proposal.proposedRepMin,
                            proposedRepMax = proposal.proposedRepMax,
                            fatigueSlope = proposal.fatigueSlope,
                            setCount = proposal.setCount,
                        )
                    when (result) {
                        is ApplyRepRangeShiftResult.Success -> {
                            settled = true
                            enqueueUserMessage(RepRangeShiftUiMapper.successMessage(proposal))
                            applyRepRangeSnapshot(
                                repRangeSession.completeSuccess(
                                    proposalId = proposalId,
                                    message = "",
                                ),
                            )
                            publishNextUserMessage()
                        }
                        else -> {
                            settled = true
                            enqueueUserMessage(RepRangeShiftUiMapper.MSG_APPLY_FAILED)
                            applyRepRangeSnapshot(
                                repRangeSession.completeFailure(
                                    proposalId = proposalId,
                                    message = "",
                                ),
                            )
                            publishNextUserMessage()
                        }
                    }
                } finally {
                    if (!settled) {
                        applyRepRangeSnapshot(repRangeSession.releaseApply(proposalId))
                    }
                }
            }
        }
    }

    fun onConfirmAccept() {
        when (val action = equipmentSession.confirmAccept()) {
            is EquipmentProposalSession.AcceptAction.ApplyNow ->
                launchEquipmentApply(action.proposalId, action.remedy)
            else -> Unit
        }
    }

    fun onDismissConfirm() {
        applyEquipmentSnapshot(equipmentSession.dismissConfirm())
    }

    /**
     * "Edit steps" on an equipment card: emit the editor request seeded with the
     * proposal's tiers, then drop the card session-side (like Skip — no fingerprint).
     * Removing up front avoids a stale Accept overwriting the user's manual correction;
     * cancelling the editor resurfaces the proposal next session if still off-lattice.
     */
    /**
     * Re-runs equipment-increment analysis for [sessionId] and reconciles the cards —
     * called when returning from the increments editor so the screen reflects saved
     * tiers. A fixed stack analyzes clean (card stays gone); cancelling without saving
     * re-surfaces the card only if the weights are still off-lattice. Touches equipment
     * cards only; summary, rep-range cards, and queued messages are preserved.
     */
    fun refreshEquipmentProposals(sessionId: String) {
        // Guarded like loadSummary: a newer load wins over this refresh.
        val generation = loadGeneration
        viewModelScope.launch {
            val analysis =
                analyzeSessionEquipmentProposals(TrainingSessionId(sessionId))
            if (generation == loadGeneration) {
                applyEquipmentSnapshot(
                    equipmentSession.load(
                        analysis.proposals,
                        analysis.baselineMicroByProposalId,
                    ),
                )
            }
        }
    }

    fun onEditProposal(proposalId: String) {
        val proposal = equipmentSession.proposal(proposalId) ?: return
        val request = EquipmentProposalUiMapper.toEditRequest(proposal)
        applyEquipmentSnapshot(equipmentSession.remove(proposalId))
        _events.trySend(WorkoutCompleteEvent.OpenEquipmentEditor(request))
    }

    fun onSkipProposal(proposalId: String) {
        if (equipmentSession.proposal(proposalId) != null) {
            applyEquipmentSnapshot(equipmentSession.remove(proposalId))
            return
        }
        if (repRangeSession.proposal(proposalId) != null) {
            applyRepRangeSnapshot(repRangeSession.remove(proposalId))
        }
    }

    fun onNotNowProposal(proposalId: String) {
        viewModelScope.launch {
            when {
                equipmentSession.proposal(proposalId) != null -> {
                    equipmentProposalPreferences.dismiss(proposalId)
                    applyEquipmentSnapshot(equipmentSession.remove(proposalId))
                }
                repRangeSession.proposal(proposalId) != null -> {
                    if (repRangeSession.isApplying(proposalId)) return@launch
                    repRangeShiftProposalPreferences.dismiss(proposalId)
                    applyRepRangeSnapshot(repRangeSession.remove(proposalId))
                }
            }
        }
    }

    fun userMessageShown() {
        // Drop the message currently displayed; show the next queued one if any.
        equipmentSession.clearUserMessage()
        repRangeSession.clearUserMessage()
        if (pendingUserMessages.isNotEmpty()) {
            pendingUserMessages.removeFirst()
        }
        val next = pendingUserMessages.firstOrNull()
        updateLoaded { loaded -> loaded.copy(userMessage = next) }
    }

    private fun launchEquipmentApply(
        proposalId: String,
        remedy: EquipmentIncrementRemedy,
    ) {
        if (equipmentSession.beginApply(proposalId) == null) return
        applyEquipmentSnapshot(equipmentSession.snapshot())
        viewModelScope.launch {
            var settled = false
            try {
                val result = applyEquipmentIncrementProposal(remedy)
                result
                    .onSuccess { outcome ->
                        settled = true
                        enqueueUserMessage(
                            EquipmentProposalUiMapper.successMessage(outcome, remedy),
                        )
                        applyEquipmentSnapshot(
                            equipmentSession.completeSuccess(
                                proposalId = proposalId,
                                message = "",
                            ).copy(userMessage = null),
                        )
                        publishNextUserMessage()
                    }
                    .onFailure {
                        settled = true
                        enqueueUserMessage(EquipmentProposalUiMapper.MSG_APPLY_FAILED)
                        applyEquipmentSnapshot(
                            equipmentSession.completeFailure(
                                proposalId = proposalId,
                                message = "",
                            ).copy(userMessage = null),
                        )
                        publishNextUserMessage()
                    }
            } finally {
                if (!settled) {
                    applyEquipmentSnapshot(equipmentSession.releaseApply(proposalId))
                }
            }
        }
    }

    private fun enqueueUserMessage(message: String) {
        if (message.isBlank()) return
        pendingUserMessages.addLast(message)
    }

    private fun publishNextUserMessage() {
        val next = pendingUserMessages.firstOrNull()
        updateLoaded { loaded ->
            if (loaded.userMessage == next) loaded else loaded.copy(userMessage = next)
        }
    }

    private fun applyEquipmentSnapshot(snap: EquipmentProposalSession.Snapshot?) {
        if (snap == null) return
        updateLoaded { loaded ->
            loaded.copy(
                equipmentProposals = snap.cards,
                blastRadiusConfirm = snap.blastRadiusConfirm,
            )
        }
    }

    private fun applyRepRangeSnapshot(snap: RepRangeShiftProposalSession.Snapshot?) {
        if (snap == null) return
        updateLoaded { loaded ->
            loaded.copy(repRangeProposals = snap.cards)
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
}
