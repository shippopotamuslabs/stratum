package com.stratum.liftconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.core.ui.component.StratumMuscleFilterCardState
import com.stratum.core.ui.component.toVolumeDisplayName
import com.stratum.domain.lift.CustomLiftDetails
import com.stratum.domain.lift.KinematicProfile
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.lift.ObserveLiftCatalogUseCase
import com.stratum.domain.lift.UpdateCustomLiftDetailsUseCase
import com.stratum.domain.lift.UpdateLiftMusclesUseCase
import com.stratum.domain.lift.classifyLiftCategory
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.training.GetLiftHistoryUseCase
import com.stratum.domain.training.LiftHistory
import com.stratum.domain.training.LoggedSet
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LiftInfoTab {
    Instructions,
    Details,
    History,
}

enum class MuscleRoleFilter {
    Primary,
    Secondary,
}

/** Which Details-tab field sheet is open (custom lifts only). */
enum class LiftDetailEditField {
    LengthBias,
    MovementPatterns,
    Kinematic,
    LoadType,
    Equipment,
}

data class LiftHistorySetUi(
    val setIndex: Int,
    val weightRepsText: String,
    val rir: Int?,
)

data class LiftHistorySessionUi(
    val sessionId: String,
    val dateText: String,
    val workoutLabel: String,
    val bestSetText: String,
    val sets: List<LiftHistorySetUi>,
    val expanded: Boolean,
)

data class LiftPrUi(
    val dateText: String,
    val weightRepsText: String,
    val rir: Int?,
    val e1rmText: String,
)

data class LiftDetailOptionUi(
    val key: String,
    val label: String,
    val selected: Boolean,
)

/** Callbacks for Details-tab muscle edits and custom-lift field editors. */
data class LiftInformationActions(
    val onTabSelected: (LiftInfoTab) -> Unit,
    val onMuscleRoleSelected: (MuscleRoleFilter) -> Unit,
    val onSessionExpandedToggled: (String) -> Unit,
    val onShowAddMuscleSheet: (Boolean) -> Unit,
    val onAddMuscle: (MuscleGroup) -> Unit,
    val onRemoveMuscle: (MuscleGroup) -> Unit,
    val onOpenDetailEditor: (LiftDetailEditField) -> Unit,
    val onDismissDetailEditor: () -> Unit,
    val onSelectLengthBias: (String) -> Unit,
    val onSelectKinematic: (String) -> Unit,
    val onSelectLoadType: (String) -> Unit,
    val onToggleDraftMovementPattern: (String) -> Unit,
    val onConfirmMovementPatterns: () -> Unit,
    val onEquipmentDraftChange: (String) -> Unit,
    val onSaveEquipment: () -> Unit,
)

data class LiftInformationUiState(
    val liftId: String = "",
    val liftName: String = "",
    val isCustom: Boolean = false,
    val selectedTab: LiftInfoTab = LiftInfoTab.Instructions,
    val imageUrls: List<String> = emptyList(),
    val description: String? = null,
    val muscleRole: MuscleRoleFilter = MuscleRoleFilter.Primary,
    val primaryMuscles: List<StratumMuscleFilterCardState> = emptyList(),
    val secondaryMuscles: List<StratumMuscleFilterCardState> = emptyList(),
    val lengthBiasLabel: String = "",
    val movementPatternLabels: List<String> = emptyList(),
    val kinematicLabel: String = "",
    val loadTypeLabel: String = "",
    val equipment: String? = null,
    val categoryLabel: String = "",
    val personalRecord: LiftPrUi? = null,
    val historySessions: List<LiftHistorySessionUi> = emptyList(),
    val showAddMuscleSheet: Boolean = false,
    val editingField: LiftDetailEditField? = null,
    val lengthBiasOptions: List<LiftDetailOptionUi> = emptyList(),
    val kinematicOptions: List<LiftDetailOptionUi> = emptyList(),
    val loadTypeOptions: List<LiftDetailOptionUi> = emptyList(),
    val movementPatternOptions: List<LiftDetailOptionUi> = emptyList(),
    val equipmentDraft: String = "",
)

@HiltViewModel
class LiftInformationViewModel @Inject constructor(
    private val observeLiftCatalog: ObserveLiftCatalogUseCase,
    private val updateLiftMuscles: UpdateLiftMusclesUseCase,
    private val updateCustomLiftDetails: UpdateCustomLiftDetailsUseCase,
    private val getLiftHistory: GetLiftHistoryUseCase,
) : ViewModel() {

    private val liftId = MutableStateFlow<String?>(null)
    private val selectedTab = MutableStateFlow(LiftInfoTab.Instructions)
    private val muscleRole = MutableStateFlow(MuscleRoleFilter.Primary)
    private val expandedSessions = MutableStateFlow<Set<String>>(emptySet())
    private val history = MutableStateFlow<LiftHistory?>(null)
    private val showAddMuscleSheet = MutableStateFlow(false)
    private val editingField = MutableStateFlow<LiftDetailEditField?>(null)
    private val draftMovementPatterns = MutableStateFlow<Set<MovementPattern>>(emptySet())
    private val equipmentDraft = MutableStateFlow("")

    private data class SheetControls(
        val showAdd: Boolean,
        val editing: LiftDetailEditField?,
        val draftPatterns: Set<MovementPattern>,
        val equipmentDraft: String,
    )

    private data class UiControls(
        val tab: LiftInfoTab,
        val role: MuscleRoleFilter,
        val expanded: Set<String>,
        val history: LiftHistory?,
        val sheets: SheetControls,
    )

    val uiState: StateFlow<LiftInformationUiState> =
        combine(
            liftId,
            observeLiftCatalog(),
            combine(
                selectedTab,
                muscleRole,
                expandedSessions,
                history,
                combine(
                    showAddMuscleSheet,
                    editingField,
                    draftMovementPatterns,
                    equipmentDraft,
                ) { showAdd, editing, drafts, equipDraft ->
                    SheetControls(showAdd, editing, drafts, equipDraft)
                },
            ) { tab, role, expanded, hist, sheets ->
                UiControls(tab, role, expanded, hist, sheets)
            },
        ) { id, catalog, controls ->
            val lift = catalog.firstOrNull { it.id.value == id }
            mapState(id, lift, controls)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            LiftInformationUiState(),
        )

    fun initialize(id: String) {
        if (liftId.value == id) return
        liftId.value = id
        selectedTab.value = LiftInfoTab.Instructions
        muscleRole.value = MuscleRoleFilter.Primary
        expandedSessions.value = emptySet()
        showAddMuscleSheet.value = false
        editingField.value = null
        draftMovementPatterns.value = emptySet()
        equipmentDraft.value = ""
        viewModelScope.launch {
            history.value = getLiftHistory(LiftId(id))
        }
    }

    fun onTabSelected(tab: LiftInfoTab) {
        selectedTab.value = tab
    }

    fun onMuscleRoleSelected(role: MuscleRoleFilter) {
        muscleRole.value = role
    }

    fun onSessionExpandedToggled(sessionId: String) {
        expandedSessions.value =
            if (sessionId in expandedSessions.value) {
                expandedSessions.value - sessionId
            } else {
                expandedSessions.value + sessionId
            }
    }

    fun onShowAddMuscleSheet(show: Boolean) {
        if (show && !uiState.value.isCustom) return
        showAddMuscleSheet.value = show
    }

    fun onAddMuscle(muscle: MuscleGroup) {
        if (!uiState.value.isCustom) return
        val id = liftId.value ?: return
        viewModelScope.launch {
            val currentPrimary =
                uiState.value.primaryMuscles.map { it.muscle }.toSet()
            val currentSecondary =
                uiState.value.secondaryMuscles.map { it.muscle }.toSet()
            val role = muscleRole.value
            val primary =
                if (role == MuscleRoleFilter.Primary) currentPrimary + muscle else currentPrimary
            val secondary =
                if (role == MuscleRoleFilter.Secondary) {
                    (currentSecondary + muscle) - primary
                } else {
                    currentSecondary - muscle
                }
            updateLiftMuscles(LiftId(id), primary, secondary)
            showAddMuscleSheet.value = false
        }
    }

    fun onRemoveMuscle(muscle: MuscleGroup) {
        if (!uiState.value.isCustom) return
        val id = liftId.value ?: return
        viewModelScope.launch {
            val currentPrimary =
                uiState.value.primaryMuscles.map { it.muscle }.toSet()
            val currentSecondary =
                uiState.value.secondaryMuscles.map { it.muscle }.toSet()
            val role = muscleRole.value
            val primary =
                if (role == MuscleRoleFilter.Primary) {
                    (currentPrimary - muscle).ifEmpty { currentPrimary }
                } else {
                    currentPrimary
                }
            val secondary =
                if (role == MuscleRoleFilter.Secondary) {
                    currentSecondary - muscle
                } else {
                    currentSecondary
                }
            updateLiftMuscles(LiftId(id), primary, secondary)
        }
    }

    fun onOpenDetailEditor(field: LiftDetailEditField) {
        if (!uiState.value.isCustom) return
        val lift = latestLift.value ?: return
        when (field) {
            LiftDetailEditField.MovementPatterns ->
                draftMovementPatterns.value = lift.movementPatterns
            LiftDetailEditField.Equipment ->
                equipmentDraft.value = lift.equipment.orEmpty()
            else -> Unit
        }
        editingField.value = field
    }

    fun onDismissDetailEditor() {
        editingField.value = null
    }

    fun onEquipmentDraftChange(value: String) {
        equipmentDraft.value = value
    }

    fun onSelectLengthBias(key: String) {
        val bias = MuscleLengthBias.entries.firstOrNull { it.name == key } ?: return
        persistDetails { it.copy(muscleLengthBias = bias) }
    }

    fun onSelectKinematic(key: String) {
        val profile = KinematicProfile.entries.firstOrNull { it.name == key } ?: return
        persistDetails { it.copy(kinematicProfile = profile) }
    }

    fun onSelectLoadType(key: String) {
        val type = LoadType.entries.firstOrNull { it.name == key } ?: return
        persistDetails { it.copy(loadType = type) }
    }

    fun onToggleDraftMovementPattern(key: String) {
        val pattern = MovementPattern.entries.firstOrNull { it.name == key } ?: return
        val current = draftMovementPatterns.value
        draftMovementPatterns.value =
            if (pattern in current) {
                // Keep at least one pattern selected in the draft.
                if (current.size <= 1) current else current - pattern
            } else {
                current + pattern
            }
    }

    fun onConfirmMovementPatterns() {
        val patterns = draftMovementPatterns.value
        if (patterns.isEmpty()) return
        persistDetails { it.copy(movementPatterns = patterns) }
    }

    fun onSaveEquipment() {
        persistDetails { it.copy(equipment = equipmentDraft.value) }
    }

    private fun persistDetails(transform: (CustomLiftDetails) -> CustomLiftDetails) {
        if (!uiState.value.isCustom) return
        val id = liftId.value ?: return
        val lift = latestLift.value ?: return
        viewModelScope.launch {
            updateCustomLiftDetails(
                LiftId(id),
                transform(lift.toDetails()),
            )
            editingField.value = null
        }
    }

    /** Latest domain lift from the catalog stream — write paths read this snapshot. */
    private val latestLift = MutableStateFlow<Lift?>(null)

    private fun mapState(
        id: String?,
        lift: Lift?,
        controls: UiControls,
    ): LiftInformationUiState {
        latestLift.value = lift
        if (lift == null) {
            return LiftInformationUiState(
                liftId = id.orEmpty(),
                selectedTab = controls.tab,
                muscleRole = controls.role,
                showAddMuscleSheet = controls.sheets.showAdd,
                editingField = controls.sheets.editing,
                equipmentDraft = controls.sheets.equipmentDraft,
            )
        }
        val pr = controls.history?.personalRecord?.toPrUi()
        val sessions =
            controls.history?.sessions.orEmpty().map { session ->
                LiftHistorySessionUi(
                    sessionId = session.sessionId.value,
                    dateText = formatDate(session.completedAt),
                    workoutLabel = session.workoutLabel,
                    bestSetText = session.bestSet.toWeightRepsText(),
                    sets =
                        session.sets.map { set ->
                            LiftHistorySetUi(
                                setIndex = set.setIndex + 1,
                                weightRepsText = set.toWeightRepsText(),
                                rir = set.rir,
                            )
                        },
                    expanded = session.sessionId.value in controls.expanded,
                )
            }
        val draftPatterns = controls.sheets.draftPatterns
        return LiftInformationUiState(
            liftId = lift.id.value,
            liftName = lift.name,
            isCustom = lift.isCustom,
            selectedTab = controls.tab,
            imageUrls = lift.imageUris,
            description = lift.description,
            muscleRole = controls.role,
            primaryMuscles =
                lift.primaryMuscles
                    .sortedBy { it.name }
                    .map { StratumMuscleFilterCardState(it, it.toVolumeDisplayName()) },
            secondaryMuscles =
                lift.secondaryMuscles
                    .sortedBy { it.name }
                    .map { StratumMuscleFilterCardState(it, it.toVolumeDisplayName()) },
            lengthBiasLabel = lift.muscleLengthBias.toLabel(),
            movementPatternLabels =
                lift.movementPatterns.map { it.displayName }.sorted(),
            kinematicLabel = lift.kinematicProfile.toLabel(),
            loadTypeLabel = lift.loadType.toLabel(),
            equipment = lift.equipment?.toTitleCaseWords(),
            categoryLabel = lift.classifyLiftCategory().displayName,
            personalRecord = pr,
            historySessions = sessions,
            showAddMuscleSheet = controls.sheets.showAdd && lift.isCustom,
            editingField = if (lift.isCustom) controls.sheets.editing else null,
            lengthBiasOptions =
                MuscleLengthBias.entries.map { bias ->
                    LiftDetailOptionUi(
                        key = bias.name,
                        label = bias.toLabel(),
                        selected = bias == lift.muscleLengthBias,
                    )
                },
            kinematicOptions =
                KinematicProfile.entries.map { profile ->
                    LiftDetailOptionUi(
                        key = profile.name,
                        label = profile.toLabel(),
                        selected = profile == lift.kinematicProfile,
                    )
                },
            loadTypeOptions =
                LoadType.entries.map { type ->
                    LiftDetailOptionUi(
                        key = type.name,
                        label = type.toLabel(),
                        selected = type == lift.loadType,
                    )
                },
            movementPatternOptions =
                MovementPattern.entries.map { pattern ->
                    LiftDetailOptionUi(
                        key = pattern.name,
                        label = pattern.displayName,
                        selected = pattern in draftPatterns,
                    )
                },
            equipmentDraft = controls.sheets.equipmentDraft,
        )
    }

    private fun Lift.toDetails(): CustomLiftDetails =
        CustomLiftDetails(
            movementPatterns = movementPatterns,
            muscleLengthBias = muscleLengthBias,
            kinematicProfile = kinematicProfile,
            loadType = loadType,
            equipment = equipment,
        )

    private fun LoggedSet.toPrUi(): LiftPrUi =
        LiftPrUi(
            dateText = formatDate(completedAt),
            weightRepsText = toWeightRepsText(),
            rir = rir,
            e1rmText =
                estimatedOneRm?.let { e1 ->
                    formatWeight(e1, weight.unit)
                } ?: "—",
        )

    private fun LoggedSet.toWeightRepsText(): String {
        val w = formatWeight(weight.amount, weight.unit)
        return "$w × $reps"
    }

    private fun formatWeight(amount: Double, unit: com.stratum.domain.lift.Weight.WeightUnit): String {
        val number =
            if (amount % 1.0 == 0.0) amount.toInt().toString() else "%.1f".format(amount)
        val unitLabel =
            when (unit) {
                com.stratum.domain.lift.Weight.WeightUnit.Lb -> "lb"
                com.stratum.domain.lift.Weight.WeightUnit.Kg -> "kg"
            }
        return "$number $unitLabel"
    }

    private fun formatDate(instant: Instant): String =
        DATE_FORMAT.format(instant.atZone(ZoneId.systemDefault()))

    private fun MuscleLengthBias.toLabel(): String =
        when (this) {
            MuscleLengthBias.Lengthened -> "Lengthened"
            MuscleLengthBias.MidRange -> "Mid-range"
            MuscleLengthBias.Shortened -> "Shortened"
        }

    private fun KinematicProfile.toLabel(): String =
        when (this) {
            KinematicProfile.AxialCompound -> "Axial Compound"
            KinematicProfile.NonAxialCompound -> "Non-Axial Compound"
            KinematicProfile.Isolation -> "Isolation"
        }

    private fun LoadType.toLabel(): String =
        when (this) {
            LoadType.Weighted -> "Weighted"
            LoadType.Bodyweight -> "Bodyweight"
            LoadType.Assisted -> "Assisted"
        }

    /** Title-cases freeform equipment copy so seed values like "cable" match other detail labels. */
    private fun String.toTitleCaseWords(): String =
        trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { ch -> ch.titlecase() }
            }

    private companion object {
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
    }
}
