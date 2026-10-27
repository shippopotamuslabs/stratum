package com.stratum.liftconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.core.ui.component.formatWeightForDisplay
import com.stratum.domain.lift.CustomLiftDetails
import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.lift.ObserveLiftCatalogUseCase
import com.stratum.domain.lift.ObserveWeightDisplayPreferencesUseCase
import com.stratum.domain.lift.SeedEquipmentIds
import com.stratum.domain.lift.SetLiftDefaultRestUseCase
import com.stratum.domain.lift.SetLiftEquipmentUseCase
import com.stratum.domain.lift.UpdateCustomLiftDetailsUseCase
import com.stratum.domain.lift.UpdateLiftMusclesUseCase
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.classifyLiftCategory
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.EquipmentPickResult
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LiftInfoTab {
    Instructions,
    Details,
    History,
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

/** Callbacks for tab, history, and Details-tab editors. */
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
    val onSelectFatigue: (String) -> Unit,
    val onSelectLoadType: (String) -> Unit,
    val onToggleDraftMovementPattern: (String) -> Unit,
    val onConfirmMovementPatterns: () -> Unit,
    val onAssignBodyweightEquipment: () -> Unit,
    val onChooseEquipment: () -> Unit,
    val onRestDraftChange: (Int) -> Unit,
    val onSaveDefaultRest: () -> Unit,
)

data class LiftInformationUiState(
    val liftId: String = "",
    val liftName: String = "",
    val isCustom: Boolean = false,
    val selectedTab: LiftInfoTab = LiftInfoTab.Instructions,
    val imageUrls: List<String> = emptyList(),
    val description: String? = null,
    val personalRecord: LiftPrUi? = null,
    val historySessions: List<LiftHistorySessionUi> = emptyList(),
    val details: LiftDetailsEditorState = LiftDetailsEditorState(),
)

@HiltViewModel
class LiftInformationViewModel @Inject constructor(
    private val observeLiftCatalog: ObserveLiftCatalogUseCase,
    observeWeightDisplayPreferences: ObserveWeightDisplayPreferencesUseCase,
    private val updateLiftMuscles: UpdateLiftMusclesUseCase,
    private val updateCustomLiftDetails: UpdateCustomLiftDetailsUseCase,
    private val setLiftDefaultRest: SetLiftDefaultRestUseCase,
    private val setLiftEquipment: SetLiftEquipmentUseCase,
    private val getLiftHistory: GetLiftHistoryUseCase,
    private val equipmentPickResult: EquipmentPickResult,
) : ViewModel() {

    private val liftId = MutableStateFlow<String?>(null)
    private var awaitingEquipmentPick = false
    private var lastSeenPickGen = 0L
    private val selectedTab = MutableStateFlow(LiftInfoTab.Instructions)
    private val muscleRole = MutableStateFlow(MuscleRoleFilter.Primary)
    private val expandedSessions = MutableStateFlow<Set<String>>(emptySet())
    private val history = MutableStateFlow<LiftHistory?>(null)
    private val showAddMuscleSheet = MutableStateFlow(false)
    private val editingField = MutableStateFlow<LiftDetailEditField?>(null)
    private val draftMovementPatterns = MutableStateFlow<Set<MovementPattern>>(emptySet())
    private val restDraftSeconds = MutableStateFlow(DEFAULT_STANDARD_REST_DISPLAY)
    /**
     * Local primary/secondary while the add-muscle sheet is open. Flushed once on Done/dismiss
     * so multi-select never races the catalog combine (see thermos High #1).
     */
    private val muscleDraft = MutableStateFlow<MuscleDraft?>(null)

    private data class MuscleDraft(
        val primary: Set<MuscleGroup>,
        val secondary: Set<MuscleGroup>,
    )

    private data class SheetControls(
        val showAdd: Boolean,
        val editing: LiftDetailEditField?,
        val draftPatterns: Set<MovementPattern>,
        val restDraftSeconds: Int,
        val muscleDraft: MuscleDraft?,
    )

    private data class UiControls(
        val tab: LiftInfoTab,
        val role: MuscleRoleFilter,
        val expanded: Set<String>,
        val history: LiftHistory?,
        val sheets: SheetControls,
        val displayUnit: Weight.WeightUnit,
    )

    val uiState: StateFlow<LiftInformationUiState> =
        combine(
            liftId,
            observeLiftCatalog(),
            observeWeightDisplayPreferences(),
            combine(
                selectedTab,
                muscleRole,
                expandedSessions,
                history,
                combine(
                    showAddMuscleSheet,
                    editingField,
                    draftMovementPatterns,
                    restDraftSeconds,
                    muscleDraft,
                ) { showAdd, editing, drafts, restDraft, draft ->
                    SheetControls(showAdd, editing, drafts, restDraft, draft)
                },
            ) { tab, role, expanded, hist, sheets ->
                // displayUnit filled in the outer combine
                UiControls(tab, role, expanded, hist, sheets, Weight.WeightUnit.Lb)
            },
        ) { id, catalog, weightPrefs, controls ->
            val lift = catalog.firstOrNull { it.id.value == id }
            mapState(
                id,
                lift,
                controls.copy(displayUnit = weightPrefs.displayUnit),
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            LiftInformationUiState(),
        )

    init {
        viewModelScope.launch {
            equipmentPickResult.result.collect { pick ->
                if (!awaitingEquipmentPick || pick == null) return@collect
                if (pick.generation <= lastSeenPickGen) return@collect
                lastSeenPickGen = pick.generation
                awaitingEquipmentPick = false
                val id = liftId.value ?: return@collect
                persistEquipment(LiftId(id), pick.equipmentId)
            }
        }
    }

    /** Call before navigating to Manage Equipment in selection mode. */
    fun onBeginChooseEquipment() {
        lastSeenPickGen = equipmentPickResult.currentGeneration()
        awaitingEquipmentPick = true
    }

    fun initialize(id: String) {
        if (liftId.value == id) return
        liftId.value = id
        selectedTab.value = LiftInfoTab.Instructions
        muscleRole.value = MuscleRoleFilter.Primary
        expandedSessions.value = emptySet()
        showAddMuscleSheet.value = false
        muscleDraft.value = null
        editingField.value = null
        draftMovementPatterns.value = emptySet()
        restDraftSeconds.value = DEFAULT_STANDARD_REST_DISPLAY
        viewModelScope.launch {
            history.value = getLiftHistory(LiftId(id))
            // getLiftHistory logs failures and returns empty; no UI toast required.
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
        if (show) {
            val lift = latestLift.value ?: return
            muscleDraft.value =
                MuscleDraft(
                    primary = lift.primaryMuscles,
                    secondary = lift.secondaryMuscles,
                )
            showAddMuscleSheet.value = true
            return
        }
        // Done / dismiss: flush local multi-select once, then clear draft.
        val pending = muscleDraft.value
        showAddMuscleSheet.value = false
        muscleDraft.value = null
        if (pending == null) return
        val id = liftId.value ?: return
        viewModelScope.launch {
            updateLiftMuscles(LiftId(id), pending.primary, pending.secondary)
                .onFailure { _userMessage.value = MSG_SAVE_FAILED }
        }
    }

    fun onAddMuscle(muscle: MuscleGroup) {
        if (!uiState.value.isCustom) return
        val draft = muscleDraft.value
        if (draft != null) {
            // Sheet open — multi-select stays local until Done.
            val role = muscleRole.value
            muscleDraft.value =
                if (role == MuscleRoleFilter.Primary) {
                    draft.copy(
                        primary = draft.primary + muscle,
                        secondary = draft.secondary - muscle,
                    )
                } else if (muscle in draft.primary) {
                    draft
                } else {
                    draft.copy(secondary = draft.secondary + muscle)
                }
            return
        }
        // Outside sheet (should be rare for add) — persist immediately.
        val id = liftId.value ?: return
        val lift = latestLift.value ?: return
        viewModelScope.launch {
            val role = muscleRole.value
            val primary =
                if (role == MuscleRoleFilter.Primary) lift.primaryMuscles + muscle
                else lift.primaryMuscles
            val secondary =
                if (role == MuscleRoleFilter.Secondary) {
                    (lift.secondaryMuscles + muscle) - primary
                } else {
                    lift.secondaryMuscles - muscle
                }
            updateLiftMuscles(LiftId(id), primary, secondary)
                .onFailure { _userMessage.value = MSG_SAVE_FAILED }
        }
    }

    fun onRemoveMuscle(muscle: MuscleGroup) {
        if (!uiState.value.isCustom) return
        val draft = muscleDraft.value
        if (draft != null) {
            val role = muscleRole.value
            muscleDraft.value =
                if (role == MuscleRoleFilter.Primary) {
                    val next = draft.primary - muscle
                    // Keep at least one primary so flush can satisfy UpdateLiftMusclesUseCase.
                    draft.copy(primary = next.ifEmpty { draft.primary })
                } else {
                    draft.copy(secondary = draft.secondary - muscle)
                }
            return
        }
        val id = liftId.value ?: return
        val lift = latestLift.value ?: return
        viewModelScope.launch {
            val role = muscleRole.value
            val primary =
                if (role == MuscleRoleFilter.Primary) {
                    (lift.primaryMuscles - muscle).ifEmpty { lift.primaryMuscles }
                } else {
                    lift.primaryMuscles
                }
            val secondary =
                if (role == MuscleRoleFilter.Secondary) {
                    lift.secondaryMuscles - muscle
                } else {
                    lift.secondaryMuscles
                }
            updateLiftMuscles(LiftId(id), primary, secondary)
                .onFailure { _userMessage.value = MSG_SAVE_FAILED }
        }
    }

    fun onOpenDetailEditor(field: LiftDetailEditField) {
        val lift = latestLift.value ?: return
        val isRestOrEquipment =
            field == LiftDetailEditField.DefaultRest || field == LiftDetailEditField.Equipment
        if (!isRestOrEquipment && !uiState.value.isCustom) return
        when (field) {
            LiftDetailEditField.MovementPatterns ->
                draftMovementPatterns.value = lift.movementPatterns
            LiftDetailEditField.DefaultRest ->
                restDraftSeconds.value = lift.resolvedDefaultRestSeconds()
            else -> Unit
        }
        editingField.value = field
    }

    fun onDismissDetailEditor() {
        editingField.value = null
    }

    /** Assign bodyweight-only equipment; [SetLiftEquipmentUseCase] couples [LoadType]. */
    fun onAssignBodyweightEquipment() {
        val id = liftId.value ?: return
        viewModelScope.launch {
            persistEquipment(LiftId(id), SeedEquipmentIds.Bodyweight)
        }
    }

    fun onRestDraftChange(seconds: Int) {
        restDraftSeconds.value =
            seconds.coerceIn(
                SetLiftDefaultRestUseCase.MIN_REST_SECONDS,
                SetLiftDefaultRestUseCase.MAX_REST_SECONDS,
            )
    }

    fun onSaveDefaultRest() {
        val id = liftId.value ?: return
        viewModelScope.launch {
            setLiftDefaultRest(LiftId(id), restDraftSeconds.value)
                .onSuccess { editingField.value = null }
                .onFailure { _userMessage.value = MSG_SAVE_FAILED }
        }
    }

    fun onSelectLengthBias(key: String) {
        val bias = MuscleLengthBias.entries.firstOrNull { it.name == key } ?: return
        persistDetails { it.copy(muscleLengthBias = bias) }
    }

    fun onSelectFatigue(key: String) {
        val profile = FatigueProfile.entries.firstOrNull { it.name == key } ?: return
        persistDetails { it.copy(fatigueProfile = profile) }
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

    /**
     * Assigns [equipmentId] (catalog or custom) in one write; load type is coupled in
     * [SetLiftEquipmentUseCase].
     */
    private suspend fun persistEquipment(id: LiftId, equipmentId: EquipmentId?) {
        setLiftEquipment(id, equipmentId)
            .onSuccess { editingField.value = null }
            .onFailure { _userMessage.value = MSG_SAVE_FAILED }
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
                .onSuccess { editingField.value = null }
                .onFailure { _userMessage.value = MSG_SAVE_FAILED }
        }
    }

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    fun userMessageShown() {
        _userMessage.value = null
    }

    /** Latest domain lift from the catalog stream — write paths read this snapshot. */
    private val latestLift = MutableStateFlow<Lift?>(null)

    private fun mapState(
        id: String?,
        lift: Lift?,
        controls: UiControls,
    ): LiftInformationUiState {
        // Catalog snapshot for field editors only — not used as a multi-select write buffer.
        latestLift.value = lift
        if (lift == null) {
            return LiftInformationUiState(
                liftId = id.orEmpty(),
                selectedTab = controls.tab,
                details =
                    LiftDetailsEditorState(
                        muscleRole = controls.role,
                        showAddMuscleSheet = controls.sheets.showAdd,
                        editingField = controls.sheets.editing,
                        restDraftSeconds = controls.sheets.restDraftSeconds,
                    ),
            )
        }
        val displayUnit = controls.displayUnit
        val pr = controls.history?.personalRecord?.toPrUi(displayUnit)
        val sessions =
            controls.history?.sessions.orEmpty().map { session ->
                LiftHistorySessionUi(
                    sessionId = session.sessionId.value,
                    dateText = formatDate(session.completedAt),
                    workoutLabel = session.workoutLabel,
                    bestSetText = session.bestSet.toWeightRepsText(displayUnit),
                    sets =
                        session.sets.map { set ->
                            LiftHistorySetUi(
                                setIndex = set.setIndex + 1,
                                weightRepsText = set.toWeightRepsText(displayUnit),
                                rir = set.rir,
                            )
                        },
                    expanded = session.sessionId.value in controls.expanded,
                )
            }
        val draftPatterns = controls.sheets.draftPatterns
        val muscleSource = controls.sheets.muscleDraft
        val primaryMuscles = muscleSource?.primary ?: lift.primaryMuscles
        val secondaryMuscles = muscleSource?.secondary ?: lift.secondaryMuscles
        return LiftInformationUiState(
            liftId = lift.id.value,
            liftName = lift.name,
            isCustom = lift.isCustom,
            selectedTab = controls.tab,
            imageUrls = lift.imageUris,
            description = lift.description,
            personalRecord = pr,
            historySessions = sessions,
            details =
                LiftDetailsEditorState(
                    canEdit = lift.isCustom,
                    muscleRole = controls.role,
                    primaryMuscles = primaryMuscles.toMuscleCards(),
                    secondaryMuscles = secondaryMuscles.toMuscleCards(),
                    lengthBiasLabel = lift.muscleLengthBias.toDetailLabel(),
                    movementPatternLabels =
                        lift.movementPatterns.map { it.displayName }.sorted(),
                    fatigueLabel = lift.fatigueProfile.toDetailLabel(),
                    loadTypeLabel = lift.loadType.toDetailLabel(),
                    showLoadType = showsLoadType(lift.equipmentId, lift.loadType),
                    equipment = equipmentDetailLabel(lift.equipmentId, lift.equipmentName),
                    categoryLabel = lift.classifyLiftCategory().displayName,
                    defaultRestLabel = formatRestDisplay(lift.resolvedDefaultRestSeconds()),
                    showAddMuscleSheet = controls.sheets.showAdd && lift.isCustom,
                    editingField =
                        when (val editing = controls.sheets.editing) {
                            null -> null
                            LiftDetailEditField.DefaultRest,
                            LiftDetailEditField.Equipment,
                            -> editing
                            else -> editing.takeIf { lift.isCustom }
                        },
                    lengthBiasOptions = lengthBiasOptions(lift.muscleLengthBias),
                    fatigueOptions = fatigueOptions(lift.fatigueProfile),
                    loadTypeOptions = loadTypeOptions(lift.loadType),
                    movementPatternOptions = movementPatternOptions(draftPatterns),
                    restDraftSeconds = controls.sheets.restDraftSeconds,
                ),
        )
    }

    private fun Lift.toDetails(): CustomLiftDetails =
        CustomLiftDetails(
            movementPatterns = movementPatterns,
            muscleLengthBias = muscleLengthBias,
            fatigueProfile = fatigueProfile,
            loadType = loadType,
            equipmentId = equipmentId,
        )

    private fun LoggedSet.toPrUi(displayUnit: Weight.WeightUnit): LiftPrUi =
        LiftPrUi(
            dateText = formatDate(completedAt),
            weightRepsText = toWeightRepsText(displayUnit),
            rir = rir,
            e1rmText =
                estimatedOneRm?.let { e1 ->
                    // e1RM amount is in the same unit as the logged weight.
                    formatWeightForDisplay(
                        Weight(e1, weight.unit),
                        displayUnit = displayUnit,
                        includeUnit = true,
                    )
                } ?: "—",
        )

    private fun LoggedSet.toWeightRepsText(displayUnit: Weight.WeightUnit): String {
        val w = formatWeightForDisplay(weight, displayUnit = displayUnit, includeUnit = true)
        return "$w × $reps"
    }

    private fun formatDate(instant: Instant): String =
        DATE_FORMAT.format(instant.atZone(ZoneId.systemDefault()))

    private companion object {
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
        const val DEFAULT_STANDARD_REST_DISPLAY = 120
        const val MSG_SAVE_FAILED = "Couldn't save lift changes. Try again."
    }
}
