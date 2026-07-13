package com.stratum.feature.program

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.core.ui.component.LiftSwapFilterIntent
import com.stratum.core.ui.component.LiftSwapOptionState
import com.stratum.core.ui.component.LiftSwapSheetState
import com.stratum.domain.lift.RankSwapCandidatesUseCase
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.SwapWorkoutLiftUseCase
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.workout.WorkoutEditSession
import com.stratum.feature.program.navigation.LiftSwapCommitMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Handles presentation logic and filtering for the Lift Swap screen (ARCHITECTURE.md v1.0).
 * State is computed by combining ranked candidates from [RankSwapCandidatesUseCase] with
 * locally held [LiftSwapFilters] and selection overlay, mapped via [buildLiftSwapSheetState].
 *
 * Commit mode is nav-owned ([LiftSwapCommitMode]): Programs uses [LiftSwapCommitMode.PersistToProgram];
 * Edit / Create / live Workout use [LiftSwapCommitMode.EditSession].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LiftSwapViewModel @Inject constructor(
    private val rankSwapCandidates: RankSwapCandidatesUseCase,
    private val workoutEditSession: WorkoutEditSession,
    private val swapWorkoutLift: SwapWorkoutLiftUseCase,
) : ViewModel() {
    private val targetWorkoutId = MutableStateFlow<String?>(null)
    private val targetWorkoutLiftId = MutableStateFlow<String?>(null)
    private val targetLiftId = MutableStateFlow<String?>(null)
    private val targetLiftName = MutableStateFlow<String?>(null)
    private var commitMode: LiftSwapCommitMode = LiftSwapCommitMode.EditSession
    private val swapFilters = MutableStateFlow(LiftSwapFilters())
    private val selectedOptions = MutableStateFlow<List<LiftSwapOptionState>>(emptyList())
    private val addAsSuperset = MutableStateFlow(false)
    private var persistSwapJob: Job? = null

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    fun userMessageShown() {
        _userMessage.value = null
    }

    val liftSwapState: StateFlow<LiftSwapSheetState?> =
        combine(targetLiftId.asStateFlow(), targetLiftName.asStateFlow()) { id, name ->
            id to name
        }.flatMapLatest { (id, name) ->
            if (name == null) {
                flowOf(null)
            } else {
                val nonBlankId = id ?: "add_lift"
                val rankedFlow = rankSwapCandidates(LiftId(nonBlankId))
                combine(
                    rankedFlow,
                    swapFilters.asStateFlow(),
                    selectedOptions.asStateFlow(),
                    addAsSuperset.asStateFlow(),
                ) { ranked, filters, selected, asSuperset ->
                    val base =
                        buildLiftSwapSheetState(
                            targetLiftName = name,
                            ranked = ranked,
                            filters = filters,
                        )
                    base.copy(
                        selectedOptions = selected,
                        addAsSuperset = asSuperset && selected.size >= 2 && base.isAddMode,
                    )
                }
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    fun initialize(
        workoutId: String,
        workoutLiftId: String?,
        liftId: String?,
        liftName: String?,
        commitMode: LiftSwapCommitMode = LiftSwapCommitMode.EditSession,
    ) {
        val sameTarget =
            targetWorkoutId.value == workoutId &&
                targetWorkoutLiftId.value == workoutLiftId &&
                targetLiftId.value == liftId &&
                this.commitMode == commitMode
        if (!sameTarget) {
            targetWorkoutId.value = workoutId
            targetWorkoutLiftId.value = workoutLiftId
            targetLiftId.value = liftId
            targetLiftName.value = liftName ?: com.stratum.feature.program.ui.PLACEHOLDER_LIFT_NAME
            this.commitMode = commitMode
            swapFilters.value = LiftSwapFilters()
        }
        // Always drop ephemeral selection when the route binds — covers re-entry with a
        // retained ViewModel after Back without confirming.
        clearSelection()
    }

    fun clearSelection() {
        selectedOptions.value = emptyList()
        addAsSuperset.value = false
    }

    fun onFilterIntent(intent: LiftSwapFilterIntent) {
        swapFilters.value =
            when (intent) {
                is LiftSwapFilterIntent.ToggleMuscle ->
                    swapFilters.value.copy(
                        selectedMuscles = swapFilters.value.selectedMuscles.toggle(intent.muscle),
                    )

                is LiftSwapFilterIntent.SelectBias ->
                    swapFilters.value.copy(selectedBias = intent.bias)

                is LiftSwapFilterIntent.SelectCategory ->
                    swapFilters.value.copy(selectedCategory = intent.category)

                is LiftSwapFilterIntent.SelectLibrary ->
                    swapFilters.value.copy(selectedLibrary = intent.library)

                LiftSwapFilterIntent.ResetMuscles ->
                    swapFilters.value.copy(selectedMuscles = emptySet())

                LiftSwapFilterIntent.ResetAll -> LiftSwapFilters()
            }
    }

    fun onSearchQueryChanged(query: String) {
        swapFilters.value = swapFilters.value.copy(searchQuery = query)
    }

    /**
     * Add mode: toggles multi-select. Swap mode: immediately commits the replacement and
     * invokes [onSwapComplete] (original one-tap swap behavior).
     */
    fun onLiftClicked(option: LiftSwapOptionState, onSwapComplete: () -> Unit = {}) {
        if (isAddMode()) {
            toggleLiftSelection(option)
        } else {
            selectLift(newLiftId = option.id, onComplete = onSwapComplete)
        }
    }

    fun toggleLiftSelection(option: LiftSwapOptionState) {
        if (!isAddMode()) return
        // PersistToProgram is one-tap swap only; multi-add always uses the edit session.
        if (commitMode == LiftSwapCommitMode.PersistToProgram) return

        val current = selectedOptions.value
        val existingIndex = current.indexOfFirst { selected -> selected.id == option.id }
        if (existingIndex >= 0) {
            selectedOptions.value = current.toMutableList().also { it.removeAt(existingIndex) }
            if (selectedOptions.value.size < 2) {
                addAsSuperset.value = false
            }
            return
        }

        selectedOptions.value = current + option
    }

    fun deselectLift(liftId: String) {
        selectedOptions.value = selectedOptions.value.filterNot { option -> option.id == liftId }
        if (selectedOptions.value.size < 2) {
            addAsSuperset.value = false
        }
    }

    fun setAddAsSuperset(enabled: Boolean) {
        if (selectedOptions.value.size < 2) {
            addAsSuperset.value = false
            return
        }
        addAsSuperset.value = enabled
    }

    /**
     * Commits multi-select add via [WorkoutEditSession]. No-op when nothing is selected,
     * not in add mode, or [LiftSwapCommitMode.PersistToProgram] (add is edit-session only).
     */
    fun confirmSelections(onComplete: () -> Unit) {
        if (!isAddMode()) return
        if (commitMode == LiftSwapCommitMode.PersistToProgram) return
        val workoutId = targetWorkoutId.value ?: return
        val selected = selectedOptions.value
        if (selected.isEmpty()) return

        val asSuperset = addAsSuperset.value && selected.size >= 2
        workoutEditSession.selectLifts(
            workoutId = workoutId,
            liftIds = selected.map { option -> option.id },
            asSuperset = asSuperset,
        )
        clearSelection()
        onComplete()
    }

    fun selectLift(newLiftId: String, onComplete: () -> Unit) {
        val workoutId = targetWorkoutId.value ?: return
        val workoutLiftId = targetWorkoutLiftId.value
        when (commitMode) {
            LiftSwapCommitMode.PersistToProgram -> {
                if (workoutLiftId.isNullOrBlank()) return
                if (persistSwapJob?.isActive == true) return
                persistSwapJob =
                    viewModelScope.launch {
                        swapWorkoutLift(WorkoutLiftId(workoutLiftId), LiftId(newLiftId))
                            .onSuccess { onComplete() }
                            .onFailure { _userMessage.value = MSG_SWAP_FAILED }
                    }
            }
            LiftSwapCommitMode.EditSession -> {
                workoutEditSession.selectLift(workoutId, workoutLiftId, newLiftId)
                onComplete()
            }
        }
    }

    private fun isAddMode(): Boolean =
        targetLiftName.value == com.stratum.feature.program.ui.PLACEHOLDER_LIFT_NAME

    private fun <T> Set<T>.toggle(value: T): Set<T> =
        if (value in this) this - value else this + value

    private companion object {
        const val MSG_SWAP_FAILED = "Couldn't swap that lift. Try again."
    }
}
