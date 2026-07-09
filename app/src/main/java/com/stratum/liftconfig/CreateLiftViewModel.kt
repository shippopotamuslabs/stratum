package com.stratum.liftconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.lift.AddCustomLiftUseCase
import com.stratum.domain.lift.CustomLiftDefinition
import com.stratum.domain.lift.DEFAULT_ISOLATION_REST_SECONDS
import com.stratum.domain.lift.KinematicProfile
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.lift.SetLiftDefaultRestUseCase
import com.stratum.domain.lift.classifyLiftCategory
import com.stratum.domain.lift.identity.LiftId
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
)

@HiltViewModel
class CreateLiftViewModel @Inject constructor(
    private val addCustomLift: AddCustomLiftUseCase,
) : ViewModel() {

    private data class Draft(
        val name: String = "",
        val primary: Set<MuscleGroup> = emptySet(),
        val secondary: Set<MuscleGroup> = emptySet(),
        val patterns: Set<MovementPattern> = emptySet(),
        val bias: MuscleLengthBias = MuscleLengthBias.MidRange,
        val kinematic: KinematicProfile = KinematicProfile.Isolation,
        val load: LoadType = LoadType.Weighted,
        val equipment: String? = null,
        /** Explicit rest override; null means use domain-resolved compound/isolation default. */
        val restOverride: Int? = null,
    )

    private data class SheetState(
        val role: MuscleRoleFilter = MuscleRoleFilter.Primary,
        val showAdd: Boolean = false,
        val editing: LiftDetailEditField? = null,
        val draftPatterns: Set<MovementPattern> = emptySet(),
        val equipmentDraft: String = "",
        val restDraft: Int = DEFAULT_ISOLATION_REST_SECONDS,
        val saving: Boolean = false,
    )

    private val draft = MutableStateFlow(Draft())
    private val sheets = MutableStateFlow(SheetState())

    private val _events = Channel<CreateLiftEvent>(Channel.BUFFERED)
    val events: Flow<CreateLiftEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<CreateLiftUiState> =
        combine(draft, sheets) { d, s -> mapState(d, s) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                CreateLiftUiState(details = LiftDetailsEditorState(canEdit = true)),
            )

    fun onNameChange(value: String) {
        draft.update { it.copy(name = value) }
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
            LiftDetailEditField.Equipment ->
                sheets.update {
                    it.copy(equipmentDraft = current.equipment.orEmpty(), editing = field)
                }
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
        draft.update { it.copy(patterns = patterns) }
        sheets.update { it.copy(editing = null) }
    }

    fun onEquipmentDraftChange(value: String) {
        sheets.update { it.copy(equipmentDraft = value) }
    }

    fun onSaveEquipment() {
        draft.update {
            it.copy(equipment = sheets.value.equipmentDraft.trim().takeIf { t -> t.isNotEmpty() })
        }
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
            try {
                val result =
                    addCustomLift(
                        CustomLiftDefinition(
                            name = current.name,
                            movementPatterns = current.patterns,
                            primaryMuscles = current.primary,
                            secondaryMuscles = current.secondary,
                            equipment = current.equipment,
                            muscleLengthBias = current.bias,
                            kinematicProfile = current.kinematic,
                            loadType = current.load,
                            defaultRestSeconds = current.restOverride,
                        ),
                    )
                if (result.isSuccess) {
                    _events.send(CreateLiftEvent.NavigateBack)
                }
            } finally {
                sheets.update { it.copy(saving = false) }
            }
        }
    }

    private fun mapState(d: Draft, s: SheetState): CreateLiftUiState {
        val snapshot = draftLift(d)
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
                    equipment = d.equipment?.toTitleCaseWords(),
                    categoryLabel = snapshot.classifyLiftCategory().displayName,
                    defaultRestLabel = formatRestDisplay(resolvedRest),
                    showAddMuscleSheet = s.showAdd,
                    editingField = s.editing,
                    lengthBiasOptions = lengthBiasOptions(d.bias),
                    kinematicOptions = kinematicOptions(d.kinematic),
                    loadTypeOptions = loadTypeOptions(d.load),
                    movementPatternOptions = movementPatternOptions(s.draftPatterns),
                    equipmentDraft = s.equipmentDraft,
                    restDraftSeconds = s.restDraft,
                ),
        )
    }

    private fun draftLift(d: Draft): Lift =
        Lift(
            id = LiftId("draft"),
            name = d.name,
            movementPatterns = d.patterns,
            primaryMuscles = d.primary,
            secondaryMuscles = d.secondary,
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            equipment = d.equipment,
            enabled = true,
            isCustom = true,
            muscleLengthBias = d.bias,
            kinematicProfile = d.kinematic,
            loadType = d.load,
            defaultRestSeconds = d.restOverride,
        )
}
