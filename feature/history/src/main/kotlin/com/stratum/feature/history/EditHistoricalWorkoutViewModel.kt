package com.stratum.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.core.ui.component.formatWeightAmount
import com.stratum.core.ui.component.workoutlog.WorkoutLogLiftUi
import com.stratum.core.ui.component.workoutlog.WorkoutLogSetUi
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.SetType
import com.stratum.domain.training.GetCompletedSessionForEditUseCase
import com.stratum.domain.training.LoggedLiftContext
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.UpdateCompletedSessionSetsUseCase
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.feature.history.ui.EditHistoricalWorkoutEvent
import com.stratum.feature.history.ui.EditHistoricalWorkoutUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class EditHistoricalWorkoutViewModel @Inject constructor(
    private val getCompletedSessionForEdit: GetCompletedSessionForEditUseCase,
    private val updateCompletedSessionSets: UpdateCompletedSessionSetsUseCase,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<EditHistoricalWorkoutUiState>(EditHistoricalWorkoutUiState.Loading)
    val uiState: StateFlow<EditHistoricalWorkoutUiState> = _uiState.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _events = MutableSharedFlow<EditHistoricalWorkoutEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<EditHistoricalWorkoutEvent> = _events.asSharedFlow()

    private var sessionId: TrainingSessionId? = null
    private var draftLifts: List<DraftLift> = emptyList()
    private var baselineSignature: String = ""
    private var workoutName: String = ""
    private var isSaving: Boolean = false

    fun load(sessionIdValue: String) {
        viewModelScope.launch {
            _uiState.value = EditHistoricalWorkoutUiState.Loading
            val id = TrainingSessionId(sessionIdValue)
            sessionId = id
            val result = getCompletedSessionForEdit(id)
            result.fold(
                onSuccess = { snapshot ->
                    workoutName = snapshot.workoutName
                    draftLifts = mapSnapshotToDraft(snapshot.sets, snapshot.loadTypeByLiftId)
                    baselineSignature = signature(draftLifts)
                    publishLoaded()
                },
                onFailure = {
                    _uiState.value = EditHistoricalWorkoutUiState.Error
                },
            )
        }
    }

    fun onWeightChange(liftKey: String, setId: String, text: String) {
        mutateSet(liftKey, setId) { it.copy(weightText = text) }
    }

    fun onRepsChange(liftKey: String, setId: String, text: String) {
        mutateSet(liftKey, setId) { it.copy(repsText = text) }
    }

    fun onRirChange(liftKey: String, setId: String, rir: Int) {
        mutateSet(liftKey, setId) { it.copy(rir = rir) }
    }

    fun onAddSet(liftKey: String) {
        val lifts = draftLifts.toMutableList()
        val liftIndex = lifts.indexOfFirst { it.key == liftKey }
        if (liftIndex < 0) return
        val lift = lifts[liftIndex]
        val template = lift.sets.lastOrNull() ?: return
        val nextIndex = (lift.sets.maxOfOrNull { it.setIndex } ?: -1) + 1
        val newSet =
            template.copy(
                id = UUID.randomUUID().toString(),
                setIndex = nextIndex,
                positionLabel = (lift.sets.size + 1).toString(),
                setTypeLabel = SetType.Standard.name,
                completedAt = template.completedAt,
            )
        lifts[liftIndex] =
            lift.copy(
                sets =
                    (lift.sets + newSet).mapIndexed { index, set ->
                        set.copy(positionLabel = (index + 1).toString())
                    },
            )
        draftLifts = lifts
        publishLoaded()
    }

    fun onDeleteSet(liftKey: String, setId: String) {
        val lifts = draftLifts.toMutableList()
        val liftIndex = lifts.indexOfFirst { it.key == liftKey }
        if (liftIndex < 0) return
        val lift = lifts[liftIndex]
        val totalSets = draftLifts.sumOf { it.sets.size }
        if (totalSets <= 1) {
            _userMessage.value = "Keep at least one set"
            return
        }
        val remaining = lift.sets.filterNot { it.id == setId }
        if (remaining.isEmpty()) {
            lifts.removeAt(liftIndex)
        } else {
            lifts[liftIndex] =
                lift.copy(
                    sets =
                        remaining.mapIndexed { index, set ->
                            set.copy(
                                setIndex = index,
                                positionLabel = (index + 1).toString(),
                            )
                        },
                )
        }
        draftLifts = lifts
        publishLoaded()
    }

    fun confirm() {
        val id = sessionId ?: return
        if (isSaving) return
        viewModelScope.launch {
            isSaving = true
            publishLoaded()
            val desired =
                runCatching { buildDesiredSets(id) }.getOrElse { error ->
                    isSaving = false
                    publishLoaded()
                    _userMessage.value = error.message ?: "Invalid set values"
                    return@launch
                }
            val result = updateCompletedSessionSets(id, desired)
            isSaving = false
            result.fold(
                onSuccess = {
                    baselineSignature = signature(draftLifts)
                    publishLoaded()
                    _events.tryEmit(EditHistoricalWorkoutEvent.NavigateBack)
                },
                onFailure = { error ->
                    publishLoaded()
                    _userMessage.value = error.message ?: "Could not save workout"
                },
            )
        }
    }

    fun userMessageShown() {
        _userMessage.value = null
    }

    private fun mapSnapshotToDraft(
        sets: List<LoggedSet>,
        loadTypeByLiftId: Map<LiftId, LoadType>,
    ): List<DraftLift> =
        sets
            .groupBy { set ->
                set.liftId?.value
                    ?: set.liftContext.liftName.takeIf { it.isNotBlank() }
                    ?: set.exerciseNameRaw
            }
            .entries
            .sortedBy { (_, group) -> group.minOf { it.setIndex } }
            .map { (groupKey, group) ->
                val ordered = group.sortedWith(compareBy({ it.setIndex }, { it.completedAt }))
                val first = ordered.first()
                val liftId = first.liftId
                val loadType =
                    liftId?.let { loadTypeByLiftId[it] } ?: LoadType.Weighted
                val assistMode = loadType == LoadType.Assisted
                val showWeight = loadType != LoadType.Bodyweight
                DraftLift(
                    key = groupKey,
                    liftId = liftId,
                    liftName =
                        first.liftContext.liftName.takeIf { it.isNotBlank() }
                            ?: first.exerciseNameRaw,
                    showWeightColumn = showWeight,
                    weightHeaderLabel =
                        when (loadType) {
                            LoadType.Bodyweight -> ""
                            LoadType.Assisted -> "Assist"
                            LoadType.Weighted -> "Weight"
                        },
                    assistMode = assistMode,
                    sets =
                        ordered.mapIndexed { index, set ->
                            DraftSet(
                                id = set.id.value,
                                setIndex = set.setIndex,
                                setTypeLabel =
                                    set.liftContext.setType?.name ?: SetType.Standard.name,
                                weightText =
                                    if (!showWeight) {
                                        ""
                                    } else {
                                        formatWeightAmount(set.weight.amount, assistMode)
                                    },
                                repsText = set.reps.toString(),
                                rir = set.rir ?: set.liftContext.rirGoal ?: 2,
                                completedAt = set.completedAt,
                                exerciseNameRaw = set.exerciseNameRaw,
                                liftContext = set.liftContext,
                                weightUnit = set.weight.unit,
                                positionLabel = (index + 1).toString(),
                            )
                        },
                )
            }

    private fun mutateSet(liftKey: String, setId: String, transform: (DraftSet) -> DraftSet) {
        draftLifts =
            draftLifts.map { lift ->
                if (lift.key != liftKey) {
                    lift
                } else {
                    lift.copy(
                        sets =
                            lift.sets.map { set ->
                                if (set.id == setId) transform(set) else set
                            },
                    )
                }
            }
        publishLoaded()
    }

    private fun publishLoaded() {
        val dirty = signature(draftLifts) != baselineSignature
        _uiState.value =
            EditHistoricalWorkoutUiState.Loaded(
                workoutName = workoutName,
                lifts = draftLifts.map { it.toUi() },
                isDirty = dirty,
                isSaving = isSaving,
            )
    }

    private fun DraftLift.toUi(): WorkoutLogLiftUi =
        WorkoutLogLiftUi(
            key = key,
            liftName = liftName,
            showWeightColumn = showWeightColumn,
            weightHeaderLabel = weightHeaderLabel,
            sets =
                sets.map { set ->
                    WorkoutLogSetUi(
                        setKey = set.id,
                        positionLabel = set.positionLabel,
                        setTypeLabel = set.setTypeLabel,
                        previousLabel = "—",
                        weightText = set.weightText,
                        weightPlaceholder = set.weightText,
                        repsText = set.repsText,
                        repsPlaceholder = set.repsText,
                        rir = set.rir,
                        isComplete = true,
                        fieldsEnabled = true,
                        showWeightInput = showWeightColumn,
                        showCheckbox = false,
                        assistMode = assistMode,
                    )
                },
        )

    private fun buildDesiredSets(sessionId: TrainingSessionId): List<LoggedSet> {
        val result = mutableListOf<LoggedSet>()
        for (lift in draftLifts) {
            for (set in lift.sets) {
                val reps =
                    set.repsText.toIntOrNull()
                        ?: throw IllegalArgumentException("Enter reps for ${lift.liftName}")
                require(reps > 0) { "Reps must be positive for ${lift.liftName}" }
                val weightAmount =
                    if (!lift.showWeightColumn) {
                        0.0
                    } else {
                        val parsed =
                            set.weightText.toDoubleOrNull()
                                ?: throw IllegalArgumentException(
                                    "Enter weight for ${lift.liftName}",
                                )
                        if (lift.assistMode) -kotlin.math.abs(parsed) else parsed
                    }
                result +=
                    LoggedSet(
                        id = LoggedSetId(set.id),
                        sessionId = sessionId,
                        exerciseNameRaw = set.exerciseNameRaw,
                        liftId = lift.liftId,
                        setIndex = set.setIndex,
                        weight = Weight(weightAmount, set.weightUnit),
                        reps = reps,
                        rir = set.rir,
                        completedAt = set.completedAt,
                        liftContext = set.liftContext,
                    )
            }
        }
        require(result.isNotEmpty()) { "Keep at least one set" }
        return result
    }

    private fun signature(lifts: List<DraftLift>): String =
        lifts.joinToString("|") { lift ->
            lift.sets.joinToString(",") { set ->
                "${set.id}:${set.weightText}:${set.repsText}:${set.rir}:${set.setIndex}"
            }
        }

    private data class DraftLift(
        val key: String,
        val liftId: LiftId?,
        val liftName: String,
        val showWeightColumn: Boolean,
        val weightHeaderLabel: String,
        val assistMode: Boolean,
        val sets: List<DraftSet>,
    )

    private data class DraftSet(
        val id: String,
        val setIndex: Int,
        val setTypeLabel: String,
        val weightText: String,
        val repsText: String,
        val rir: Int,
        val completedAt: Instant,
        val exerciseNameRaw: String,
        val liftContext: LoggedLiftContext,
        val weightUnit: Weight.WeightUnit,
        val positionLabel: String,
    )
}
