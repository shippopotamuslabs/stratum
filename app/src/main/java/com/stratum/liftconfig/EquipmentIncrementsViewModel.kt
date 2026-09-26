package com.stratum.liftconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.featureflag.ObserveEquipmentManagementV2UseCase
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
import com.stratum.domain.lift.DismissEquipmentProposalUseCase
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.shortLabel
import com.stratum.feature.workout.ui.ProposalEditContext
import com.stratum.feature.workout.ui.ProposalTierArgs
import com.stratum.feature.workout.ui.samplesLb
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    val tiers: List<IncrementTierDraft> = emptyList(),
    /** Adder stop drafts (display-unit strings); empty = no adder. */
    val microStops: List<String> = emptyList(),
    val validationMessage: String? = null,
    /**
     * Band indexes whose steps miss a proposal's logged sample weights (yellow,
     * non-blocking — saving stays allowed). Empty for all non-proposal entry points.
     */
    val warnedTierIndexes: Set<Int> = emptySet(),
    val isSaving: Boolean = false,
    val saveSucceeded: Boolean = false,
    /** Lifts inheriting this equipment's defaults (includes disabled). */
    val affectedLiftCount: Int = 0,
    /** Names of affected lifts, sorted; UI shows first 3 + "+M more". */
    val affectedLiftNames: List<String> = emptyList(),
    /** Short unit label for field chrome (`lb` / `kg`) from Settings. */
    val unitLabel: String = "lb",
    /** Lifts inheriting these defaults (id + display name + enabled for the sheet). */
    val affectedLifts: List<AffectedLiftUi> = emptyList(),
    /**
     * Gates the v2 equipment UI (Update Shared / Save As New bar, tappable affects
     * banner). One flag, read by each call site, so the surfaces cannot drift apart.
     */
    val equipmentV2Enabled: Boolean = false,
    /**
     * Adder card visibility, derived from [equipmentV2Enabled] in [buildState] so
     * the rollout decision lives in state construction — never a literal at a
     * call site.
     */
    val showMicroCard: Boolean = false,
    /**
     * Launch-flag gate for the AI scan prefill. PR-5 wires state only — no
     * composable reads these yet, so the scan entry stays hidden.
     */
    val scanAvailable: Boolean = false,
    /**
     * Sealed scan request state (idle, in flight, failed, scanned). User-visible
     * text mapping happens at the composable boundary (PR-6 owns strings.xml).
     */
    val scanUi: IncrementScanState = IncrementScanState.Idle,
)

/** Presentation row for the affected-lifts sheet (domain stays out of composables). */
data class AffectedLiftUi(
    val id: String,
    val name: String,
    val enabled: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
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
    private val equipmentPickResult: EquipmentPickResult,
    observeWeightDisplayPreferences: ObserveWeightDisplayPreferencesUseCase,
    observeEquipmentManagementV2: ObserveEquipmentManagementV2UseCase,
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
    private val validationOverride = MutableStateFlow<String?>(null)
    private val displayUnit = MutableStateFlow(Weight.WeightUnit.Lb)
    private val equipmentV2Enabled = MutableStateFlow(false)

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
            observeEquipmentManagementV2().collect { enabled ->
                equipmentV2Enabled.value = enabled
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
    )

    private data class RolloutInputs(
        val equipmentV2Enabled: Boolean,
        val scan: IncrementScanDelegate.ScanUi,
    )

    private data class Flags(
        val drafts: EditorDrafts?,
        val proposalInput: ProposalInput?,
        val saving: Boolean,
        val saved: Boolean,
        val validation: String?,
        val equipmentV2Enabled: Boolean,
        val scan: IncrementScanDelegate.ScanUi,
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
            // Typed combine stages producing named holders: the save pair and the
            // rollout pair (v2 flag plus delegate-owned scan) ride single slots.
            combine(
                combine(isSaving, saveSucceeded, ::SaveInputs),
                drafts,
                proposalSession.input,
                validationOverride,
                combine(equipmentV2Enabled, scanDelegate.observeScanUi(), ::RolloutInputs),
            ) { save, editorDrafts, input, validation, rollout ->
                Flags(
                    editorDrafts,
                    input,
                    save.saving,
                    save.saved,
                    validation,
                    rollout.equipmentV2Enabled,
                    rollout.scan,
                )
            },
        ) { id, catalog, affected, unit, flags ->
            buildState(
                id = id,
                catalog = catalog,
                affected = affected,
                unit = unit,
                equipmentV2Enabled = flags.equipmentV2Enabled,
                drafts = flags.drafts,
                proposalInput = flags.proposalInput,
                saving = flags.saving,
                saved = flags.saved,
                validation = flags.validation,
                scan = flags.scan,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            EquipmentIncrementsUiState(),
        )

    fun initialize(
        id: String,
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
            remaining[remaining.lastIndex] =
                remaining.last().copy(openEnded = true, upperBound = null)
            remaining
        }
    }

    fun save() {
        val id = equipmentId.value ?: return
        val domainTiers = validatedDraftTiers() ?: return
        // Validate the draft stops (not the derived uiState list, which lags a
        // combine cycle): back-to-back edits and save observe the same value.
        val domainMicro =
            validatedDraftMicro(
                drafts.value?.microStops ?: uiState.value.microStops,
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

    /**
     * Forks the equipment under `"<name> (copy)"` with the on-screen tiers, leaving the
     * shared defaults untouched. When launched from a lift ([sourceLiftId]), that lift is
     * reassigned to the copy so it inherits the edited bands.
     */
    fun saveAsNew() {
        val id = equipmentId.value ?: return
        val domainTiers = validatedDraftTiers() ?: return
        // Bands and stops both validated so the copy never inherits a broken draft;
        // the edited stops ride onto the copy (never silently dropped).
        val domainMicro =
            validatedDraftMicro(
                drafts.value?.microStops ?: uiState.value.microStops,
                domainTiers,
                displayUnit.value,
            ) ?: return
        viewModelScope.launch {
            isSaving.value = true
            try {
                val copyName = "${uiState.value.equipmentName.ifBlank { "Equipment" }} (copy)"
                val liftId = sourceLiftId.value?.let(::LiftId)
                val result =
                    forkEquipmentWithTiers(EquipmentId(id), copyName, domainTiers, liftId, domainMicro)
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
            IncrementTierDraftOps.toDomainOrNull(uiState.value.tiers, displayUnit.value)
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
        equipmentV2Enabled: Boolean,
        drafts: EditorDrafts?,
        proposalInput: ProposalInput?,
        saving: Boolean,
        saved: Boolean,
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
            tiers = tiers,
            microStops = microStops,
            validationMessage = validation ?: liveValidationMessage(tiers, microStops, unit),
            warnedTierIndexes = warnedTierIndexes,
            isSaving = saving,
            saveSucceeded = saved,
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
            unitLabel = unit.shortLabel(),
            equipmentV2Enabled = equipmentV2Enabled,
            showMicroCard = equipmentV2Enabled,
            scanAvailable = scan.available,
            scanUi = scan.state,
        )
    }

    private fun updateDraft(transform: (List<IncrementTierDraft>) -> List<IncrementTierDraft>) {
        // Read-modify-write on the source flow (not the derived uiState, which lags
        // a combine cycle): back-to-back edits can never overwrite each other.
        drafts.update { current ->
            val tiers = transform(current?.tiers ?: uiState.value.tiers)
            (current ?: EditorDrafts(null, null)).copy(tiers = tiers)
        }
        validationOverride.value = null
        saveSucceeded.value = false
    }

    private fun updateMicroDraft(transform: (List<String>) -> List<String>) {
        // Same source-flow discipline as [updateDraft]: the derived uiState list
        // lags recombine, so rapid edits must chain on drafts.value instead.
        drafts.update { current ->
            val stops = transform(current?.microStops ?: uiState.value.microStops)
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
