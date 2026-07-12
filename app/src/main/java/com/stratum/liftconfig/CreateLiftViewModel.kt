package com.stratum.liftconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.lift.AddCustomLiftUseCase
import com.stratum.domain.lift.CustomLiftDefinition
import com.stratum.domain.lift.DEFAULT_ISOLATION_REST_SECONDS
import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.KinematicProfile
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.lift.ObserveEquipmentCatalogUseCase
import com.stratum.domain.lift.SetLiftDefaultRestUseCase
import com.stratum.domain.lift.classifyLiftCategory
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.inferKinematicProfile
import com.stratum.domain.lift.resolvedDefaultRestSeconds
import com.stratum.domain.onboarding.MuscleGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface CreateLiftEvent {
    data object NavigateBack : CreateLiftEvent
}

data class CreateLiftUiState(
    val name: String = "",
    val details: LiftDetailsEditorState = LiftDetailsEditorState(canEdit = true),
    val canConfirm: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class CreateLiftViewModel @Inject constructor(
    private val addCustomLift: AddCustomLiftUseCase,
    private val observeEquipmentCatalog: ObserveEquipmentCatalogUseCase,
    private val equipmentPickResult: EquipmentPickResult,
) : ViewModel() {

    private data class Draft(
        val name: String = "",
        val primary: Set<MuscleGroup> = emptySet(),
        val secondary: Set<MuscleGroup> = emptySet(),
        val patterns: Set<MovementPattern> = emptySet(),
        val bias: MuscleLengthBias = MuscleLengthBias.MidRange,
        val kinematic: KinematicProfile = KinematicProfile.Isolation,
        val load: LoadType = LoadType.Weighted,
        val equipmentId: EquipmentId? = null,
        val restOverride: Int? = null,
    )

    private data class SheetState(
        val role: MuscleRoleFilter = MuscleRoleFilter.Primary,
        val showAdd: Boolean = false,
        val editing: LiftDetailEditField? = null,
        val draftPatterns: Set<MovementPattern> = emptySet(),
        val restDraft: Int = DEFAULT_ISOLATION_REST_SECONDS,
        val saving: Boolean = false,
    )

    private val draft = MutableStateFlow(Draft())
    private val sheets = MutableStateFlow(SheetState())
    private val errorOverride = MutableStateFlow<String?>(null)

    private var awaitingEquipmentPick = false
    private var lastSeenPickGen = 0L

    private val _events = Channel<CreateLiftEvent>(Channel.BUFFERED)
    val events: Flow<CreateLiftEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<CreateLiftUiState> =
        combine(
            draft,
            sheets,
            errorOverride,
            observeEquipmentCatalog(),
        ) { d, s, error, equipment ->
            mapState(d, s, equipment).copy(errorMessage = error)
        }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                CreateLiftUiState(details = LiftDetailsEditorState(canEdit = true)),
            )

    init {
        viewModelScope.launch {
            equipmentPickResult.result.collect { pick ->
                if (!awaitingEquipmentPick || pick == null) return@collect
                if (pick.generation <= lastSeenPickGen) return@collect
                lastSeenPickGen = pick.generation
                awaitingEquipmentPick = false
                draft.update { it.copy(equipmentId = pick.equipmentId) }
            }
        }
    }

    /** Call before navigating to Manage Equipment in selection mode. */
    fun onBeginChooseEquipment() {
        lastSeenPickGen = equipmentPickResult.currentGeneration()
        awaitingEquipmentPick = true
    }

    fun errorMessageShown() {
        errorOverride.value = null
    }

    fun onNameChange(value: String) {
        draft.update { current ->
            val nextName = value
            if (current.patterns.isEmpty()) {
                current.copy(name = nextName)
            } else {
                current.copy(
                    name = nextName,
                    kinematic = inferKinematicProfile(current.patterns, nextName),
                )
            }
        }
    }

    fun onMuscleRoleSelected(role: MuscleRoleFilter) {
        sheets.update { it.copy(role = role) }
    }

    fun onShowAddMuscleSheet(show: Boolean) {
        sheets.update { it.copy(showAdd = show) }
    }

    fun onAddMuscle(muscle: MuscleGroup) {
        val role = sheets.value.role
        draft.update { current ->
            if (role == MuscleRoleFilter.Primary) {
                current.copy(
                    primary = current.primary + muscle,
                    secondary = current.secondary - muscle,
                )
            } else if (muscle in current.primary) {
                current
            } else {
                current.copy(secondary = current.secondary + muscle)
            }
        }
    }

    fun onRemoveMuscle(muscle: MuscleGroup) {
        val role = sheets.value.role
        draft.update { current ->
            if (role == MuscleRoleFilter.Primary) {
                current.copy(primary = current.primary - muscle)
            } else {
                current.copy(secondary = current.secondary - muscle)
            }
        }
    }

    fun onOpenDetailEditor(field: LiftDetailEditField) {
        val current = draft.value
        when (field) {
            LiftDetailEditField.MovementPatterns ->
                sheets.update { it.copy(draftPatterns = current.patterns, editing = field) }
            LiftDetailEditField.DefaultRest ->
                sheets.update {
                    it.copy(
                        restDraft =
                            current.restOverride
                                ?: draftLift(current).resolvedDefaultRestSeconds(),
                        editing = field,
                    )
                }
            else -> sheets.update { it.copy(editing = field) }
        }
    }

    fun onDismissDetailEditor() {
        sheets.update { it.copy(editing = null) }
    }

    fun onSelectLengthBias(key: String) {
        val bias = MuscleLengthBias.entries.firstOrNull { it.name == key } ?: return
        draft.update { it.copy(bias = bias) }
        sheets.update { it.copy(editing = null) }
    }

    fun onSelectKinematic(key: String) {
        val profile = KinematicProfile.entries.firstOrNull { it.name == key } ?: return
        draft.update { it.copy(kinematic = profile) }
        sheets.update { it.copy(editing = null) }
    }

    fun onSelectLoadType(key: String) {
        val type = LoadType.entries.firstOrNull { it.name == key } ?: return
        draft.update { it.copy(load = type) }
        sheets.update { it.copy(editing = null) }
    }

    fun onToggleDraftMovementPattern(key: String) {
        val pattern = MovementPattern.entries.firstOrNull { it.name == key } ?: return
        sheets.update { current ->
            val selected = current.draftPatterns
            val next =
                if (pattern in selected) {
                    if (selected.size <= 1) selected else selected - pattern
                } else {
                    selected + pattern
                }
            current.copy(draftPatterns = next)
        }
    }

    fun onConfirmMovementPatterns() {
        val patterns = sheets.value.draftPatterns
        if (patterns.isEmpty()) return
        draft.update { current ->
            current.copy(
                patterns = patterns,
                kinematic = inferKinematicProfile(patterns, current.name),
            )
        }
        sheets.update { it.copy(editing = null) }
    }

    fun onClearEquipment() {
        draft.update { it.copy(equipmentId = null) }
        sheets.update { it.copy(editing = null) }
    }

    fun onRestDraftChange(seconds: Int) {
        sheets.update {
            it.copy(
                restDraft =
                    seconds.coerceIn(
                        SetLiftDefaultRestUseCase.MIN_REST_SECONDS,
                        SetLiftDefaultRestUseCase.MAX_REST_SECONDS,
                    ),
            )
        }
    }

    fun onSaveDefaultRest() {
        draft.update { it.copy(restOverride = sheets.value.restDraft) }
        sheets.update { it.copy(editing = null) }
    }

    fun onConfirm() {
        val current = draft.value
        if (sheets.value.saving) return
        if (current.name.trim().isEmpty()) return
        if (current.primary.isEmpty()) return
        if (current.patterns.isEmpty()) return
        viewModelScope.launch {
            sheets.update { it.copy(saving = true) }
            val result =
                addCustomLift(
                    CustomLiftDefinition(
                        name = current.name,
                        movementPatterns = current.patterns,
                        primaryMuscles = current.primary,
                        secondaryMuscles = current.secondary,
                        equipmentId = current.equipmentId,
                        muscleLengthBias = current.bias,
                        kinematicProfile = current.kinematic,
                        loadType = current.load,
                        defaultRestSeconds = current.restOverride,
                    ),
                )
            sheets.update { it.copy(saving = false) }
            if (result.isSuccess) {
                _events.send(CreateLiftEvent.NavigateBack)
            } else {
                errorOverride.value = result.exceptionOrNull()?.message
                    ?: "Couldn't create lift. Try again."
            }
        }
    }

    private fun mapState(
        d: Draft,
        s: SheetState,
        equipmentCatalog: List<Equipment>,
    ): CreateLiftUiState {
        val snapshot = draftLift(d, equipmentCatalog)
        val resolvedRest = snapshot.resolvedDefaultRestSeconds()
        val canConfirm =
            d.name.trim().isNotEmpty() &&
                d.primary.isNotEmpty() &&
                d.patterns.isNotEmpty() &&
                !s.saving
        return CreateLiftUiState(
            name = d.name,
            canConfirm = canConfirm,
            isSaving = s.saving,
            details =
                LiftDetailsEditorState(
                    canEdit = true,
                    muscleRole = s.role,
                    primaryMuscles = d.primary.toMuscleCards(),
                    secondaryMuscles = d.secondary.toMuscleCards(),
                    lengthBiasLabel = d.bias.toDetailLabel(),
                    movementPatternLabels = d.patterns.map { it.displayName }.sorted(),
                    kinematicLabel = d.kinematic.toDetailLabel(),
                    loadTypeLabel = d.load.toDetailLabel(),
                    equipment = snapshot.equipmentName,
                    categoryLabel = snapshot.classifyLiftCategory().displayName,
                    defaultRestLabel = formatRestDisplay(resolvedRest),
                    showAddMuscleSheet = s.showAdd,
                    editingField = s.editing,
                    lengthBiasOptions = lengthBiasOptions(d.bias),
                    kinematicOptions = kinematicOptions(d.kinematic),
                    loadTypeOptions = loadTypeOptions(d.load),
                    movementPatternOptions = movementPatternOptions(s.draftPatterns),
                    restDraftSeconds = s.restDraft,
                ),
        )
    }

    private fun draftLift(d: Draft, equipmentCatalog: List<Equipment> = emptyList()): Lift {
        val equipmentName =
            d.equipmentId?.let { id -> equipmentCatalog.firstOrNull { it.id == id }?.name }
        return Lift(
            id = LiftId("draft"),
            name = d.name,
            movementPatterns = d.patterns,
            primaryMuscles = d.primary,
            secondaryMuscles = d.secondary,
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            equipmentId = d.equipmentId,
            equipmentName = equipmentName,
            enabled = true,
            isCustom = true,
            muscleLengthBias = d.bias,
            kinematicProfile = d.kinematic,
            loadType = d.load,
            defaultRestSeconds = d.restOverride,
        )
    }
}
