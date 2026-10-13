package com.stratum.liftconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.EquipmentAffectedLifts
import com.stratum.domain.lift.ForkEquipmentWithTiersUseCase
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.IncrementTiersValidationResult
import com.stratum.domain.lift.ObserveEquipmentAffectedLiftsUseCase
import com.stratum.domain.lift.ObserveEquipmentCatalogUseCase
import com.stratum.domain.lift.ObserveWeightDisplayPreferencesUseCase
import com.stratum.domain.lift.MicroIncrementsValidationResult
import com.stratum.domain.lift.ScanPhoto
import com.stratum.domain.lift.SetEquipmentIncrementTiersUseCase
import com.stratum.domain.lift.SetLiftEquipmentUseCase
import com.stratum.domain.lift.ValidateIncrementTiersUseCase
import com.stratum.domain.lift.ValidateMicroIncrementsUseCase
import com.stratum.domain.lift.DeleteCustomEquipmentUseCase
import com.stratum.domain.lift.GetEquipmentDeleteImpactUseCase
import com.stratum.domain.lift.SeedEquipmentIds
import com.stratum.domain.lift.DismissEquipmentProposalUseCase
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.EquipmentPickResult
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.shortLabel
import com.stratum.feature.workout.ui.ProposalEditContext
import com.stratum.feature.workout.ui.ProposalTierArgs
import com.stratum.feature.workout.ui.samplesLb
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EquipmentIncrementsUiState(
    val equipmentId: String = "",
    val equipmentName: String = "",
    /** STR-105 eligibility: hidden system rows show no delete action. */
    val canDelete: Boolean = false,
    /** True once delete succeeds; the route navigates back, mirroring save. */
    val deleteSucceeded: Boolean = false,
    /** Shared editor content; see [IncrementsEditorData]. */
    val editor: IncrementsEditorData = IncrementsEditorData(),
    /** Lifts inheriting this equipment's defaults (includes disabled). */
    val affectedLiftCount: Int = 0,
    /** Names of affected lifts, sorted; UI shows first 3 + "+M more". */
    val affectedLiftNames: List<String> = emptyList(),
    /** Lifts inheriting these defaults (id + display name + enabled for the sheet). */
    val affectedLifts: List<AffectedLiftUi> = emptyList(),
)

/** Presentation row for the affected-lifts sheet (domain stays out of composables). */
data class AffectedLiftUi(
    val id: String,
    val name: String,
    val enabled: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
@Suppress("TooManyFunctions") // Single-screen editor VM: many small draft actions by design.
class EquipmentIncrementsViewModel @Inject constructor(
    private val observeEquipmentCatalog: ObserveEquipmentCatalogUseCase,
    private val observeEquipmentAffectedLifts: ObserveEquipmentAffectedLiftsUseCase,
    private val setEquipmentIncrementTiers: SetEquipmentIncrementTiersUseCase,
    private val validateIncrementTiers: ValidateIncrementTiersUseCase,
    private val validateMicroIncrements: ValidateMicroIncrementsUseCase,
    private val forkEquipmentWithTiers: ForkEquipmentWithTiersUseCase,
    /** Owned by the quick-swap path: applies a picked equipment to the pending lift. */
    private val setLiftEquipment: SetLiftEquipmentUseCase,
    private val dismissEquipmentProposal: DismissEquipmentProposalUseCase,
    private val deleteCustomEquipment: DeleteCustomEquipmentUseCase,
    private val getDeleteImpact: GetEquipmentDeleteImpactUseCase,
    private val equipmentPickResult: EquipmentPickResult,
    observeWeightDisplayPreferences: ObserveWeightDisplayPreferencesUseCase,
    private val scanDelegate: IncrementScanDelegate,
) : ViewModel() {

    private val equipmentId = MutableStateFlow<String?>(null)
    private val sourceLiftId = MutableStateFlow<String?>(null)
    /**
     * Combined editor drafts; a null section seeds from stored/catalog values on
     * next build (see [buildState]). One flow so tier and stop edits share a
     * single reset and combine path instead of parallel per-section plumbing.
     */
    private val drafts = MutableStateFlow<EditorDrafts?>(null)
    /** Shared proposal-prefill session (prefill bands, sample warnings, dismiss-on-save). */
    private val proposalSession = ProposalEditorSession()
    private val isSaving = MutableStateFlow(false)
    private val saveSucceeded = MutableStateFlow(false)
    private val deleteSucceeded = MutableStateFlow(false)
    private val _deleteDialog = MutableStateFlow<PendingDeleteUi?>(null)

    /** STR-105 delete dialog staging (impact + single replacement pick). */
    val deleteDialog: StateFlow<PendingDeleteUi?> = _deleteDialog
    /** Test-visible: the editor operation message channel (save/fork/delete/swap errors share it). */
    internal val validationOverride = MutableStateFlow<String?>(null)
    private val displayUnit = MutableStateFlow(Weight.WeightUnit.Lb)

    private data class PendingSwap(
        val liftId: String,
        val sinceGeneration: Long,
    )

    private val pendingSwap = MutableStateFlow<PendingSwap?>(null)
    private val swapNav = Channel<String>(Channel.BUFFERED)

    /** One-shot navigation requests for the sheet's quick swap (see [requestSwapForLift]). */
    val swapNavRequests: Flow<String> = swapNav.receiveAsFlow()

    init {
        viewModelScope.launch {
            observeWeightDisplayPreferences().collect { prefs ->
                if (displayUnit.value != prefs.displayUnit) {
                    resetDrafts()
                    scanDelegate.onDisplayUnitChanged()
                    displayUnit.value = prefs.displayUnit
                }
            }
        }
        viewModelScope.launch {
            pendingSwap.filterNotNull().flatMapLatest { request ->
                equipmentPickResult.picksAfter(request.sinceGeneration).map { pick ->
                    request.liftId to pick
                }
            }.collect { (liftId, pick) ->
                pendingSwap.value = null
                val result = setLiftEquipment(LiftId(liftId), pick.equipmentId)
                if (result.isFailure) {
                    validationOverride.value = result.exceptionOrNull()?.message
                        ?: "Could not change equipment"
                }
            }
        }
    }

    /**
     * Single entry for the sheet's quick swap: arms the pending request and emits the
     * Manage-Equipment navigation in one call, so the UI cannot misorder the two halves.
     */
    fun requestSwapForLift(liftId: String) {
        pendingSwap.value =
            PendingSwap(liftId, sinceGeneration = equipmentPickResult.currentGeneration())
        swapNav.trySend(liftId)
    }

    /** Both editor sections in one nullable holder; null seeds from stored values. */
    private data class EditorDrafts(
        val tiers: List<IncrementTierDraft>?,
        val microStops: List<String>?,
    )

    private data class SaveInputs(
        val saving: Boolean,
        val saved: Boolean,
        val deleted: Boolean,
    )

    private data class Flags(
        val drafts: EditorDrafts?,
        val proposalInput: ProposalInput?,
        val saving: Boolean,
        val saved: Boolean,
        val validation: String?,
        val scan: IncrementScanDelegate.ScanUi,
        val deleted: Boolean,
    )

    private val affectedLifts =
        equipmentId.flatMapLatest { id ->
            if (id.isNullOrBlank()) {
                flowOf(emptyAffected())
            } else {
                observeEquipmentAffectedLifts(EquipmentId(id))
            }
        }

    val uiState: StateFlow<EquipmentIncrementsUiState> =
        combine(
            equipmentId,
            observeEquipmentCatalog(),
            affectedLifts,
            displayUnit,
            // Typed combine stages producing named holders: the save pair rides a
            // single slot alongside the delegate-owned scan state.
            combine(
                combine(isSaving, saveSucceeded, deleteSucceeded, ::SaveInputs),
                drafts,
                proposalSession.input,
                validationOverride,
                scanDelegate.observeScanUi(),
            ) { save, editorDrafts, input, validation, scan ->
                Flags(
                    editorDrafts,
                    input,
                    save.saving,
                    save.saved,
                    validation,
                    scan,
                    save.deleted,
                )
            },
        ) { id, catalog, affected, unit, flags ->
            buildState(
                id = id,
                catalog = catalog,
                affected = affected,
                unit = unit,
                drafts = flags.drafts,
                proposalInput = flags.proposalInput,
                saving = flags.saving,
                saved = flags.saved,
                deleted = flags.deleted,
                validation = flags.validation,
                scan = flags.scan,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            EquipmentIncrementsUiState(),
        )

    fun initialize(
        id: String?,
        sourceLiftId: String? = null,
        initialTiersLb: List<ProposalTierArgs> = emptyList(),
        proposalContext: ProposalEditContext? = null,
    ) {
        val inputChanged = proposalSession.resolveInput(initialTiersLb, proposalContext)
        if (
            equipmentId.value == id &&
            this.sourceLiftId.value == sourceLiftId &&
            !inputChanged
        ) {
            return
        }
        equipmentId.value = id
        this.sourceLiftId.value = sourceLiftId
        resetDrafts()
        scanDelegate.onOwnerChanged(id)
        saveSucceeded.value = false
        deleteSucceeded.value = false
        validationOverride.value = null
    }

    /**
     * AI scan prefill: runs the photo through the scan delegate and applies the
     * returned drafts (bands plus adder stops) for review — never auto-saves.
     * Stale completions (after initialize or a unit flip) are dropped by the
     * delegate's generation guard. Failures keep the existing drafts.
     */
    fun scan(photo: ScanPhoto) {
        scanDelegate.requestScan(
            scope = viewModelScope,
            photo = photo,
            displayUnit = displayUnit.value,
            ownerId = equipmentId.value,
        ) { scanned ->
            drafts.update { current ->
                (current ?: EditorDrafts(null, null)).copy(
                    tiers = scanned.tierDrafts,
                    microStops = scanned.microStopDrafts,
                )
            }
            validationOverride.value = null
            saveSucceeded.value = false
        }
    }

    /** Clears both editor sections so the next build re-seeds from stored values. */
    private fun resetDrafts() {
        drafts.value = null
    }

    fun onMicroChanged(index: Int, value: String) {
        updateMicroDraft { stops ->
            stops.mapIndexed { i, stop ->
                if (i == index) IncrementTierDraftOps.filterNumeric(value) else stop
            }
        }
    }

    fun onAddMicro() {
        val unit = displayUnit.value
        updateMicroDraft { stops ->
            stops + IncrementTierDraftOps.defaultMicroStop(unit)
        }
    }

    fun onRemoveMicro(index: Int) {
        updateMicroDraft { stops ->
            if (index !in stops.indices) stops else stops.toMutableList().also { it.removeAt(index) }
        }
    }

    fun onTierLowerChanged(index: Int, value: String) {
        updateDraft { tiers ->
            tiers.mapIndexed { i, tier ->
                if (i == index) {
                    tier.copy(lowerBound = IncrementTierDraftOps.filterNumeric(value))
                } else {
                    tier
                }
            }
        }
    }

    fun onTierUpperChanged(index: Int, value: String) {
        updateDraft { tiers ->
            tiers.mapIndexed { i, tier ->
                if (i == index) {
                    tier.copy(upperBound = IncrementTierDraftOps.filterNumeric(value))
                } else {
                    tier
                }
            }
        }
    }

    fun onTierIncrementChanged(index: Int, value: String) {
        updateDraft { tiers ->
            tiers.mapIndexed { i, tier ->
                if (i == index) {
                    tier.copy(increment = IncrementTierDraftOps.filterNumeric(value))
                } else {
                    tier
                }
            }
        }
    }

    /**
     * Band start edit (STR-101, STR-102): band 0's start is stored state — it
     * has no predecessor to tile from. Editing a later band's start moves the
     * previous band's end to match via [IncrementBandTiling.retileAfterEndEdit],
     * and re-tiling derives the band's own start from that end — the whole new
     * band is set up in the sheet without closing it, and bands never gap or
     * overlap.
     */
    fun onBandStartChanged(index: Int, value: String) {
        updateDraft { tiers ->
            if (index !in tiers.indices) return@updateDraft tiers
            if (index == 0) {
                tiers.toMutableList().also { updated ->
                    updated[0] =
                        updated[0].copy(lowerBound = IncrementTierDraftOps.filterNumeric(value))
                }
            } else {
                IncrementBandTiling.retileAfterEndEdit(tiers, index - 1, value)
            }
        }
    }

    /**
     * Single entry point for band draft-field edits (one callback in
     * [IncrementEditorBandActions] instead of three parallel ones); dispatches
     * to the per-field handlers above. Thin adapters kept because call sites
     * (and tests) address fields individually.
     */
    fun onBandFieldChanged(index: Int, field: BandField, value: String) {
        when (field) {
            BandField.START -> onBandStartChanged(index, value)
            BandField.END -> onBandEndChanged(index, value)
            BandField.STEP -> onTierIncrementChanged(index, value)
        }
    }

    /**
     * Band-strip end edit (STR-58): replaces the band's end and re-tiles
     * neighbors so bands never gap or overlap. Blank edits are ignored — the
     * sheet (STR-103) holds half-typed text locally and only commits valid
     * numeric ends here, so a cleared field never collapses a band and an
     * invalid value never reaches the draft.
     */
    fun onBandEndChanged(index: Int, value: String) {
        updateDraft { tiers ->
            IncrementBandTiling.retileAfterEndEdit(tiers, index, value)
        }
    }

    fun onOpenEndedToggled(index: Int, openEnded: Boolean) {
        updateDraft { tiers ->
            if (index != tiers.lastIndex) return@updateDraft tiers
            tiers.mapIndexed { i, tier ->
                if (i == index) {
                    tier.copy(
                        openEnded = openEnded,
                        upperBound = if (openEnded) null else tier.upperBound ?: "",
                    )
                } else {
                    tier
                }
            }
        }
    }

    fun addTier() {
        val unit = displayUnit.value
        updateDraft { tiers ->
            val last = tiers.lastOrNull()
            val splitAt =
                last?.upperBound?.toDoubleOrNull()
                    ?: last?.lowerBound?.toDoubleOrNull()?.plus(
                        IncrementTierDraftOps.defaultSplitSpan(unit),
                    )
                    ?: IncrementTierDraftOps.defaultSplitAt(unit)
            val closedLast =
                last?.copy(
                    openEnded = false,
                    upperBound = IncrementTierDraftOps.formatDisplayAmount(splitAt),
                )
            val next =
                IncrementTierDraft(
                    lowerBound = IncrementTierDraftOps.formatDisplayAmount(splitAt),
                    upperBound = null,
                    increment =
                        last?.increment
                            ?: IncrementTierDraftOps.defaultDraft(unit).increment,
                    openEnded = true,
                )
            if (closedLast != null) {
                tiers.dropLast(1) + closedLast + next
            } else {
                listOf(next)
            }
        }
    }

    fun removeTier(index: Int) {
        updateDraft { tiers ->
            if (tiers.size <= 1) return@updateDraft tiers
            val remaining = tiers.toMutableList().also { it.removeAt(index) }
            IncrementBandTiling.retiled(remaining)
        }
    }

    /**
     * Returns the edited equipment id, or null after surfacing a validation message when
     * the launch has no equipment (equipment-less lift, fork proposal with no cluster
     * equipment) — the editor degrades to a seeded, non-saving state instead of no-op.
     */
    private fun requireEquipmentId(action: String): String? {
        val id = equipmentId.value
        if (id.isNullOrBlank()) {
            validationOverride.value = "Nothing to $action: no equipment is being edited."
            return null
        }
        return id
    }

    fun save() {
        val id = requireEquipmentId("save") ?: return
        val domainTiers = validatedDraftTiers() ?: return
        // Validate the draft stops (not the derived uiState list, which lags a
        // combine cycle): back-to-back edits and save observe the same value.
        val domainMicro =
            validatedDraftMicro(
                drafts.value?.microStops ?: uiState.value.editor.microStops,
                domainTiers,
                displayUnit.value,
            ) ?: return
        viewModelScope.launch {
            isSaving.value = true
            val result = setEquipmentIncrementTiers(EquipmentId(id), domainTiers, domainMicro)
            isSaving.value = false
            if (result.isSuccess) {
                dismissProposalContext()
                saveSucceeded.value = true
                validationOverride.value = null
            } else {
                validationOverride.value = result.exceptionOrNull()?.message
                    ?: "Could not save increments"
            }
        }
    }

    private var deleteLoadJob: Job? = null

    /** Arms the STR-105 delete dialog for the open equipment. */
    fun onStartDelete() {
        val id = equipmentId.value?.takeIf { it.isNotBlank() } ?: return
        deleteLoadJob?.cancel()
        deleteLoadJob =
            viewModelScope.launch {
                val impact = getDeleteImpact(EquipmentId(id))
                _deleteDialog.value =
                    impact?.toPendingDeleteUi()
                        ?: PendingDeleteUi(id = id, name = uiState.value.equipmentName)
            }
    }

    private fun PendingDeleteUi?.needsReplacementPick(): Boolean =
        this != null && !canConfirmDelete

    fun onSelectDeleteReplacement(replacementId: String) {
        val pending = _deleteDialog.value ?: return
        _deleteDialog.value = pending.copy(selectedReplacementId = replacementId)
    }

    fun onDismissDelete() {
        deleteLoadJob?.cancel()
        deleteLoadJob = null
        _deleteDialog.value = null
    }

    /**
     * Tombstones the open equipment with its chosen replacement. The route
     * navigates back on [EquipmentIncrementsUiState.deleteSucceeded].
     */
    fun deleteEquipment() {
        val pending = _deleteDialog.value
        // Delete the staged row, never whatever the live flow opened since.
        val id = pending?.id ?: requireEquipmentId("delete") ?: return
        if (pending.needsReplacementPick()) return
        val replacement =
            if (pending != null && pending.hasAffectedPointers && !pending.usesBodyweightFallback) {
                pending.selectedReplacementId?.let(::EquipmentId)
            } else {
                null
            }
        _deleteDialog.value = null
        viewModelScope.launch {
            val result = deleteCustomEquipment(EquipmentId(id), replacement)
            if (result.isSuccess) {
                deleteSucceeded.value = true
                validationOverride.value = null
            } else {
                validationOverride.value = result.exceptionOrNull()?.message
                    ?: "Could not delete equipment"
            }
        }
    }

    /** Name prefill for the save-as-new sheet: `"<name> (copy)"`. */
    fun forkNameSuggestion(): String =
        "${uiState.value.equipmentName.ifBlank { "Equipment" }} (copy)"

    /**
     * Forks the equipment under [copyName] (blank falls back to [forkNameSuggestion])
     * with the on-screen tiers, leaving the shared defaults untouched. When launched
     * from a lift ([sourceLiftId]), that lift is reassigned to the copy so it
     * inherits the edited bands.
     */
    fun saveAsNew(copyName: String = "") {
        val id = requireEquipmentId("fork") ?: return
        val domainTiers = validatedDraftTiers() ?: return
        // Bands and stops both validated so the copy never inherits a broken draft;
        // the edited stops ride onto the copy (never silently dropped).
        val domainMicro =
            validatedDraftMicro(
                drafts.value?.microStops ?: uiState.value.editor.microStops,
                domainTiers,
                displayUnit.value,
            ) ?: return
        viewModelScope.launch {
            isSaving.value = true
            try {
                val trimmed = copyName.trim()
                val resolvedName = trimmed.ifBlank { forkNameSuggestion() }
                val liftId = sourceLiftId.value?.let(::LiftId)
                val result =
                    forkEquipmentWithTiers(
                        EquipmentId(id),
                        resolvedName,
                        domainTiers,
                        liftId,
                        domainMicro,
                    )
                if (result.isSuccess) {
                    dismissProposalContext()
                    saveSucceeded.value = true
                    validationOverride.value = null
                } else {
                    validationOverride.value = result.exceptionOrNull()?.message
                        ?: "Could not copy equipment"
                }
            } finally {
                isSaving.value = false
            }
        }
    }

    /**
     * A proposal-launched save resolves the proposal even when the hand-set tiers still
     * miss logged weights — the user took ownership. Cancellation paths never call this.
     */
    private suspend fun dismissProposalContext() {
        proposalSession.dismissIfPresent(dismissEquipmentProposal)
    }

    private fun validatedDraftTiers(): List<IncrementTier>? {
        val domainTiers =
            IncrementTierDraftOps.toDomainOrNull(uiState.value.editor.tiers, displayUnit.value)
        if (domainTiers == null) {
            validationOverride.value = "Enter valid numbers for all bands"
            return null
        }
        when (val result = validateIncrementTiers(domainTiers)) {
            is IncrementTiersValidationResult.Invalid -> {
                validationOverride.value = IncrementTierDraftOps.toUserMessage(result.errors)
                return null
            }
            IncrementTiersValidationResult.Valid -> Unit
        }
        return domainTiers
    }

    private fun validatedDraftMicro(
        stops: List<String>,
        domainTiers: List<IncrementTier>,
        unit: Weight.WeightUnit,
    ): List<Weight>? {
        val check = checkMicroStops(domainTiers, stops, unit)
        if (check.message != null) {
            validationOverride.value = check.message
            return null
        }
        return check.micro
    }

    /**
     * Parsed stops plus their user-facing message in one place, shared by the live hint
     * and the save blocker so both apply the same rule. Parse failure carries the
     * save-path message; call sites map it to live-null behavior.
     */
    private data class MicroCheck(val micro: List<Weight>?, val message: String?)

    private fun checkMicroStops(
        domainTiers: List<IncrementTier>,
        microStops: List<String>,
        unit: Weight.WeightUnit,
    ): MicroCheck {
        val micro = IncrementTierDraftOps.microToDomainOrNull(microStops, unit)
            ?: return MicroCheck(null, "Enter valid numbers for adder stops")
        return when (val result = validateMicroIncrements(domainTiers, micro)) {
            is MicroIncrementsValidationResult.Invalid ->
                MicroCheck(null, IncrementTierDraftOps.toMicroUserMessage(result.errors))
            MicroIncrementsValidationResult.Valid -> MicroCheck(micro, null)
        }
    }

    private fun buildState(
        id: String?,
        catalog: List<Equipment>,
        affected: EquipmentAffectedLifts,
        unit: Weight.WeightUnit,
        drafts: EditorDrafts?,
        proposalInput: ProposalInput?,
        saving: Boolean,
        saved: Boolean,
        deleted: Boolean,
        validation: String?,
        scan: IncrementScanDelegate.ScanUi,
    ): EquipmentIncrementsUiState {
        val equipment = catalog.firstOrNull { it.id.value == id }
        val tiers =
            drafts?.tiers
                ?: IncrementTierDraftOps.fromProposalArgs(
                    proposalInput?.initialTiersLb.orEmpty(),
                    unit,
                ).takeIf { it.isNotEmpty() }
                ?: equipment?.incrementTiers?.map { IncrementTierDraftOps.toDraft(it, unit) }
                ?: listOf(IncrementTierDraftOps.defaultDraft(unit))
        val microStops =
            drafts?.microStops
                ?: equipment?.microIncrements?.let {
                    IncrementTierDraftOps.toMicroDrafts(it, unit)
                }
                ?: emptyList()
        val microLb =
            IncrementTierDraftOps.microToDomainOrNull(microStops, unit)
                ?.map { it.amount }
                .orEmpty()
        val warnedTierIndexes =
            IncrementTierDraftOps.warnedIndexesForSamples(
                drafts = tiers,
                displayUnit = unit,
                samplesLb = proposalInput?.context?.samplesLb.orEmpty(),
                microLb = microLb,
            )
        return EquipmentIncrementsUiState(
            equipmentId = id.orEmpty(),
            equipmentName = equipment?.name.orEmpty(),
            canDelete =
                equipment != null &&
                    equipment.id !in SeedEquipmentIds.hiddenFromCatalog &&
                    !equipment.isDeleted,
            editor =
                IncrementsEditorData(
                    tiers = tiers,
                    microStops = microStops,
                    validationMessage =
                        validation ?: liveValidationMessage(tiers, microStops, unit),
                    warnedTierIndexes = warnedTierIndexes,
                    isSaving = saving,
                    saveSucceeded = saved,
                    unitLabel = unit.shortLabel(),
                    scanAvailable = scan.available,
                    scanUi = scan.state,
                ),
            affectedLiftCount = affected.count,
            affectedLiftNames = affected.lifts.map { it.name },
            affectedLifts =
                affected.lifts.map { lift ->
                    AffectedLiftUi(
                        id = lift.id.value,
                        name = lift.name,
                        enabled = lift.enabled,
                    )
                },
            deleteSucceeded = deleted,
        )
    }

    private fun updateDraft(transform: (List<IncrementTierDraft>) -> List<IncrementTierDraft>) {
        // Read-modify-write on the source flow (not the derived uiState, which lags
        // a combine cycle): back-to-back edits can never overwrite each other.
        drafts.update { current ->
            val tiers = transform(current?.tiers ?: uiState.value.editor.tiers)
            (current ?: EditorDrafts(null, null)).copy(tiers = tiers)
        }
        validationOverride.value = null
        saveSucceeded.value = false
    }

    private fun updateMicroDraft(transform: (List<String>) -> List<String>) {
        // Same source-flow discipline as [updateDraft]: the derived uiState list
        // lags recombine, so rapid edits must chain on drafts.value instead.
        drafts.update { current ->
            val stops = transform(current?.microStops ?: uiState.value.editor.microStops)
            (current ?: EditorDrafts(null, null)).copy(microStops = stops)
        }
        validationOverride.value = null
        saveSucceeded.value = false
    }

    private fun liveValidationMessage(
        tiers: List<IncrementTierDraft>,
        microStops: List<String>,
        unit: Weight.WeightUnit,
    ): String? {
        val domain = IncrementTierDraftOps.toDomainOrNull(tiers, unit) ?: return null
        when (val result = validateIncrementTiers(domain)) {
            is IncrementTiersValidationResult.Invalid ->
                return IncrementTierDraftOps.toUserMessage(result.errors)
            IncrementTiersValidationResult.Valid -> Unit
        }
        val check = checkMicroStops(domain, microStops, unit)
        if (check.micro == null) return null
        return check.message
    }

    private fun emptyAffected(): EquipmentAffectedLifts =
        EquipmentAffectedLifts(
            equipmentId = EquipmentId(""),
            count = 0,
            lifts = emptyList(),
        )
}
