package com.stratum.feature.program

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.ObserveLiftCatalogUseCase
import com.stratum.domain.program.ComputeWorkoutMuscleVolumeUseCase
import com.stratum.domain.program.EstimateWorkoutDurationMinutesUseCase
import com.stratum.domain.program.ObserveProgramUseCase
import com.stratum.feature.program.ui.EditLiftState
import com.stratum.feature.program.ui.EditSetState
import com.stratum.feature.program.ui.EditWorkoutUiState
import com.stratum.feature.program.ui.PLACEHOLDER_LIFT_NAME
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Loads a workout for editing and holds in-memory mutations until persistence is wired.
 */
@HiltViewModel
class EditWorkoutViewModel @Inject constructor(
    private val observeProgram: ObserveProgramUseCase,
    private val observeLiftCatalog: ObserveLiftCatalogUseCase,
    private val computeWorkoutMuscleVolume: ComputeWorkoutMuscleVolumeUseCase,
    private val estimateWorkoutDurationMinutes: EstimateWorkoutDurationMinutesUseCase,
) : ViewModel() {
    private val targetWorkoutId = MutableStateFlow<String?>(null)
    private val hasSnapshot = MutableStateFlow(false)

    private val _uiState = MutableStateFlow<EditWorkoutUiState>(EditWorkoutUiState.Loading)
    val uiState: StateFlow<EditWorkoutUiState> = _uiState.asStateFlow()

    private var nextSupersetGroupId = 1
    private var catalogLifts: List<Lift> = emptyList()

    fun initialize(workoutId: String) {
        if (targetWorkoutId.value == workoutId && hasSnapshot.value) {
            return
        }
        targetWorkoutId.value = workoutId
        hasSnapshot.value = false
        _uiState.value = EditWorkoutUiState.Loading
        viewModelScope.launch {
            val (snapshot, catalog) =
                combine(
                    observeProgram(),
                    observeLiftCatalog(),
                ) { program, lifts ->
                    val liftNamesById = lifts.associate { lift -> lift.id.value to lift.name }
                    val state =
                        if (program == null) {
                            EditWorkoutUiState.NotFound
                        } else {
                            val workout =
                                program.workouts.firstOrNull { workout -> workout.id.value == workoutId }
                            if (workout == null) {
                                EditWorkoutUiState.NotFound
                            } else {
                                workout.toEditWorkoutUiState(liftNamesById)
                            }
                        }
                    state to lifts
                }.first { (state, _) -> state !is EditWorkoutUiState.Loading }
            catalogLifts = catalog
            nextSupersetGroupId =
                (snapshot as? EditWorkoutUiState.Loaded)
                    ?.lifts
                    ?.mapNotNull { lift -> lift.supersetGroupId }
                    ?.maxOrNull()
                    ?.plus(1)
                    ?: 1
            _uiState.value = applyDerivedMetrics(snapshot)
            hasSnapshot.value = true
        }
    }

    fun addSet(workoutLiftId: String) {
        updateLoaded { loaded ->
            loaded.copy(
                lifts =
                    loaded.lifts.map { lift ->
                        if (lift.workoutLiftId != workoutLiftId) {
                            lift
                        } else {
                            val newSet = defaultEditSetState(lift.sets.size + 1)
                            lift.copy(sets = renumberSets(lift.sets + newSet))
                        }
                    },
            )
        }
    }

    fun addLift() {
        updateLoaded { loaded ->
            val position = loaded.lifts.size
            val newLift =
                EditLiftState(
                    workoutLiftId = newWorkoutLiftId(),
                    liftId = newLiftId(),
                    name = PLACEHOLDER_LIFT_NAME,
                    position = position,
                    sets = listOf(defaultEditSetState(setNumber = 1)),
                )
            loaded.copy(lifts = loaded.lifts + newLift)
        }
    }

    fun removeLift(workoutLiftId: String) {
        updateLoaded { loaded ->
            loaded.copy(
                lifts =
                    cleanupSupersetGroups(
                        loaded.lifts
                            .filterNot { lift -> lift.workoutLiftId == workoutLiftId }
                            .mapIndexed { index, lift -> lift.copy(position = index) },
                    ),
            )
        }
    }

    fun toggleReorderMode() {
        updateLoaded { loaded ->
            if (loaded.isReorderMode) {
                loaded.copy(isReorderMode = false, pendingReorderLifts = null)
            } else {
                loaded.copy(
                    isReorderMode = true,
                    pendingReorderLifts = loaded.lifts.toReorderStates(),
                )
            }
        }
    }

    fun cancelReorderMode() {
        updateLoaded { loaded ->
            loaded.copy(isReorderMode = false, pendingReorderLifts = null)
        }
    }

    fun reorderLifts(fromIndex: Int, toIndex: Int) {
        updateLoaded { loaded ->
            val pending = loaded.pendingReorderLifts?.toMutableList() ?: return@updateLoaded loaded
            if (fromIndex !in pending.indices || toIndex !in pending.indices || fromIndex == toIndex) {
                return@updateLoaded loaded
            }
            val item = pending.removeAt(fromIndex)
            pending.add(toIndex, item)
            loaded.copy(pendingReorderLifts = pending)
        }
    }

    fun confirmReorder() {
        updateLoaded { loaded ->
            val pending = loaded.pendingReorderLifts ?: return@updateLoaded loaded
            val liftById = loaded.lifts.associateBy { lift -> lift.workoutLiftId }
            val reordered =
                pending.mapNotNull { row -> liftById[row.workoutLiftId] }
                    .mapIndexed { index, lift -> lift.copy(position = index) }
            loaded.copy(
                lifts = reordered,
                isReorderMode = false,
                pendingReorderLifts = null,
            )
        }
    }

    fun removeSet(setId: String) {
        updateLoaded { loaded ->
            loaded.copy(
                lifts =
                    loaded.lifts.map { lift ->
                        val updatedSets = lift.sets.filterNot { set -> set.setId == setId }
                        if (updatedSets.size == lift.sets.size) {
                            lift
                        } else {
                            lift.copy(sets = renumberSets(updatedSets))
                        }
                    },
            )
        }
    }

    fun setRestSeconds(setId: String, restSeconds: Int) {
        updateSet(setId) { set -> set.copy(restSeconds = restSeconds.coerceAtLeast(0)) }
    }

    fun setRestSecondsForAllSetsOnLift(workoutLiftId: String, restSeconds: Int) {
        val clamped = restSeconds.coerceAtLeast(0)
        updateLoaded { loaded ->
            loaded.copy(
                lifts =
                    loaded.lifts.map { lift ->
                        if (lift.workoutLiftId != workoutLiftId) {
                            lift
                        } else {
                            lift.copy(
                                sets = lift.sets.map { set -> set.copy(restSeconds = clamped) },
                            )
                        }
                    },
            )
        }
    }

    fun setRepMin(setId: String, repMin: Int) {
        updateSet(setId) { set ->
            val clamped = repMin.coerceAtLeast(0)
            val repMax = if (set.repMax in 1..<clamped) clamped else set.repMax
            set.copy(repMin = clamped, repMax = repMax)
        }
    }

    fun setRepMax(setId: String, repMax: Int) {
        updateSet(setId) { set ->
            val clamped = repMax.coerceAtLeast(0)
            set.copy(repMax = clamped)
        }
    }

    fun setRirGoal(setId: String, rirGoal: Int) {
        updateSet(setId) { set -> set.copy(rirGoal = rirGoal) }
    }

    private fun updateSet(setId: String, transform: (EditSetState) -> EditSetState) {
        updateLoaded { loaded ->
            loaded.copy(
                lifts =
                    loaded.lifts.map { lift ->
                        lift.copy(
                            sets =
                                lift.sets.map { set ->
                                    if (set.setId == setId) {
                                        transform(set)
                                    } else {
                                        set
                                    }
                                },
                        )
                    },
            )
        }
    }

    fun linkSupersetWithPrevious(workoutLiftId: String) {
        linkSuperset(workoutLiftId, SupersetLinkDirection.Previous)
    }

    fun linkSupersetWithNext(workoutLiftId: String) {
        linkSuperset(workoutLiftId, SupersetLinkDirection.Next)
    }

    fun removeFromSuperset(workoutLiftId: String) {
        updateLoaded { loaded ->
            loaded.copy(
                lifts = unlinkLiftFromSuperset(loaded.lifts, workoutLiftId),
            )
        }
    }

    private fun linkSuperset(
        workoutLiftId: String,
        direction: SupersetLinkDirection,
    ) {
        updateLoaded { loaded ->
            loaded.copy(
                lifts =
                    linkSupersetWithAdjacent(
                        lifts = loaded.lifts,
                        workoutLiftId = workoutLiftId,
                        direction = direction,
                        nextGroupId = { nextSupersetGroupId++ },
                    ),
            )
        }
    }

    private inline fun updateLoaded(transform: (EditWorkoutUiState.Loaded) -> EditWorkoutUiState.Loaded) {
        val current = _uiState.value
        if (current is EditWorkoutUiState.Loaded) {
            _uiState.value = applyDerivedMetrics(transform(current))
        }
    }

    private fun applyDerivedMetrics(state: EditWorkoutUiState): EditWorkoutUiState =
        when (state) {
            is EditWorkoutUiState.Loaded ->
                state.withDerivedMetrics(
                    liftCatalog = catalogLifts,
                    computeWorkoutMuscleVolume = computeWorkoutMuscleVolume,
                    estimateWorkoutDurationMinutes = estimateWorkoutDurationMinutes,
                )
            else -> state
        }
}
