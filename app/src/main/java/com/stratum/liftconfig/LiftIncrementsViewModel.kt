package com.stratum.liftconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.IncrementTiersValidationResult
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MicroIncrementsValidationResult
import com.stratum.domain.lift.ObserveLiftCatalogUseCase
import com.stratum.domain.lift.ObserveWeightDisplayPreferencesUseCase
import com.stratum.domain.lift.SetLiftIncrementTiersUseCase
import com.stratum.domain.lift.ValidateIncrementTiersUseCase
import com.stratum.domain.lift.ValidateMicroIncrementsUseCase
import com.stratum.domain.lift.DismissEquipmentProposalUseCase
import com.stratum.domain.lift.ForkEquipmentWithTiersUseCase
import com.stratum.domain.lift.ScanPhoto
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.shortLabel
import com.stratum.feature.workout.ui.ProposalEditContext
import com.stratum.feature.workout.ui.ProposalTierArgs
import com.stratum.feature.workout.ui.samplesLb
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class IncrementTierDraft(
    val lowerBound: String,
    val upperBound: String?,
    val increment: String,
    val openEnded: Boolean,
)

data class LiftIncrementsUiState(
    val liftId: String = "",
    val liftName: String = "",
    /** Shared editor content; see [IncrementsEditorData]. */
    val editor: IncrementsEditorData = IncrementsEditorData(),
    /** Non-null when the lift is inheriting equipment/fallback defaults (no override rows). */
    val inheritanceBanner: String? = null,
    /**
     * Equipment id backing a save-as-new fork (null = fallback bands with
     * nothing to fork from; the fork entry stays hidden).
     */
    val forkSourceId: String? = null,
    /** Name prefill for the save-as-new sheet: `"<equipment-or-lift> (copy)"`. */
    val forkSuggestion: String = "",
)

@HiltViewModel
class LiftIncrementsViewModel @Inject constructor(
    private val observeLiftCatalog: ObserveLiftCatalogUseCase,
    private val setLiftIncrementTiers: SetLiftIncrementTiersUseCase,
    private val validateIncrementTiers: ValidateIncrementTiersUseCase,
    private val validateMicroIncrements: ValidateMicroIncrementsUseCase,
    private val dismissEquipmentProposal: DismissEquipmentProposalUseCase,
    private val forkEquipmentWithTiers: ForkEquipmentWithTiersUseCase,
    observeWeightDisplayPreferences: ObserveWeightDisplayPreferencesUseCase,
    private val scanDelegate: IncrementScanDelegate,
) : ViewModel() {

    /**
     * Explicit adder-draft model: [Untouched] seeds from the lift's effective stops on
     * next build (see [buildState]) and tells saves to leave the micro column alone, so
     * band-only saves never silently convert inherit to explicit. [Edited] carries the
     * user's stops (empty = explicitly no adder).
     */
    private sealed interface MicroDraft {
        data object Untouched : MicroDraft
        data class Edited(val stops: List<String>) : MicroDraft
    }

    private val liftId = MutableStateFlow<String?>(null)
    private val draftTiers = MutableStateFlow<List<IncrementTierDraft>?>(null)
    private val draftMicro = MutableStateFlow<MicroDraft>(MicroDraft.Untouched)
    /** Shared proposal-prefill session (prefill bands, sample warnings, dismiss-on-save). */
    private val proposalSession = ProposalEditorSession()
    private val isSaving = MutableStateFlow(false)
    private val saveSucceeded = MutableStateFlow(false)
    private val validationOverride = MutableStateFlow<String?>(null)
    private val displayUnit = MutableStateFlow(Weight.WeightUnit.Lb)

    private data class OwnerInputs(
        val id: String?,
        val catalog: List<Lift>,
        val unit: Weight.WeightUnit,
    )

    private data class DraftFlags(
        val draft: List<IncrementTierDraft>?,
        val microDraft: MicroDraft,
        val proposalInput: ProposalInput?,
    )

    private data class SaveFlags(
        val saving: Boolean,
        val saved: Boolean,
        val validation: String?,
    )

    init {
        viewModelScope.launch {
            observeWeightDisplayPreferences().collect { prefs ->
                if (displayUnit.value != prefs.displayUnit) {
                    // Unit flip: drop dirty drafts so bands and stops re-seed converted.
                    draftTiers.value = null
                    draftMicro.value = MicroDraft.Untouched
                    scanDelegate.onDisplayUnitChanged()
                    displayUnit.value = prefs.displayUnit
                }
            }
        }
    }

    // Typed combine stages producing named holders: the scan pair arrives as one
    // delegate-owned ScanUi instead of positional vararg slots.
    val uiState: StateFlow<LiftIncrementsUiState> =
        combine(
            combine(liftId, observeLiftCatalog(), displayUnit, ::OwnerInputs),
            combine(draftTiers, draftMicro, proposalSession.input, ::DraftFlags),
            combine(isSaving, saveSucceeded, validationOverride, ::SaveFlags),
            scanDelegate.observeScanUi(),
        ) { owner, flags, save, scan ->
            buildState(owner, flags, save, scan)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            LiftIncrementsUiState(),
        )

    fun initialize(
        id: String,
        initialTiersLb: List<ProposalTierArgs> = emptyList(),
        proposalContext: ProposalEditContext? = null,
    ) {
        val inputChanged = proposalSession.resolveInput(initialTiersLb, proposalContext)
        if (liftId.value == id && !inputChanged) return
        liftId.value = id
        draftTiers.value = null
        draftMicro.value = MicroDraft.Untouched
        scanDelegate.onOwnerChanged(id)
        saveSucceeded.value = false
        validationOverride.value = null
    }

    /**
     * AI scan prefill: runs the photo through the scan delegate and applies the
     * returned drafts (bands plus adder stops via [MicroDraft.Edited]) for
     * review — never auto-saves. Stale completions (after initialize or a unit
     * flip) are dropped by the delegate's generation guard. Failures keep the
     * existing drafts.
     */
    fun scan(photo: ScanPhoto) {
        scanDelegate.requestScan(
            scope = viewModelScope,
            photo = photo,
            displayUnit = displayUnit.value,
            ownerId = liftId.value,
        ) { scanned ->
            draftTiers.value = scanned.tierDrafts
            draftMicro.value = MicroDraft.Edited(scanned.microStopDrafts)
            validationOverride.value = null
            saveSucceeded.value = false
        }
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
            stops + IncrementTierDraftOps.defaultDraft(unit).increment
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
            IncrementBandTiling.retiled(remaining)
        }
    }

    /**
     * Band-strip end edit (STR-61): replaces the band's end and re-tiles
     * neighbors so bands never gap or overlap. Starts are derived, never typed.
     */
    fun onBandEndChanged(index: Int, value: String) {
        updateDraft { tiers ->
            IncrementBandTiling.retileAfterEndEdit(tiers, index, value)
        }
    }

    fun save() {
        val id = liftId.value ?: return
        val edits = resolveValidatedEdits() ?: return
        viewModelScope.launch {
            runSaving(
                persist = { setLiftIncrementTiers(LiftId(id), edits.tiers, edits.micro) },
                failureMessage = "Could not save increments",
            )
        }
    }

    /** Name prefill for the save-as-new sheet, preferring the equipment name. */
    fun forkNameSuggestion(): String = uiState.value.forkSuggestion.ifBlank { "Equipment (copy)" }

    /**
     * Forks the lift's equipment under [copyName] (blank falls back to
     * [forkNameSuggestion]) carrying the on-screen bands, and reassigns this
     * lift to the copy so the shared defaults stay untouched. Untouched adder
     * stops inherit the parent's knob; edited stops ride onto the copy.
     * No-op when the lift has no equipment (fallback bands).
     */
    fun saveAsNew(copyName: String = "") {
        val id = liftId.value ?: return
        val sourceId = uiState.value.forkSourceId?.let(::EquipmentId) ?: return
        val edits = resolveValidatedEdits() ?: return
        val resolvedName = copyName.trim().ifBlank { forkNameSuggestion() }
        viewModelScope.launch {
            runSaving(
                persist = {
                    forkEquipmentWithTiers(
                        sourceId,
                        resolvedName,
                        edits.tiers,
                        LiftId(id),
                        edits.micro,
                    )
                },
                failureMessage = "Could not copy equipment",
            )
        }
    }

    /** Validated bands plus stops; micro null means "leave the column alone". */
    private data class ValidatedEdits(
        val tiers: List<IncrementTier>,
        val micro: List<Weight>?,
    )

    /**
     * Shared edit-to-domain pipeline for [save] and [saveAsNew]: draft
     * mapping, tier validation, and stop validation, setting
     * [validationOverride] on failure. Untouched stops yield null (the column
     * is left alone / the fork inherits the parent's knob); edited stops
     * replace the override (empty = explicitly no adder).
     */
    private fun resolveValidatedEdits(): ValidatedEdits? {
        val unit = displayUnit.value
        val domainTiers = IncrementTierDraftOps.toDomainOrNull(uiState.value.editor.tiers, unit)
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
        val domainMicro =
            when (val microDraft = draftMicro.value) {
                MicroDraft.Untouched -> null
                is MicroDraft.Edited ->
                    validatedDraftMicro(microDraft.stops, domainTiers, unit) ?: return null
            }
        return ValidatedEdits(domainTiers, domainMicro)
    }

    /** Shared save bookkeeping: flags, proposal dismissal, success/error paths. */
    private suspend fun runSaving(
        persist: suspend () -> Result<*>,
        failureMessage: String,
    ) {
        isSaving.value = true
        try {
            val result = persist()
            if (result.isSuccess) {
                // A proposal-launched save resolves the proposal even when the hand-set
                // tiers still miss logged weights — the user took ownership.
                proposalSession.dismissIfPresent(dismissEquipmentProposal)
                saveSucceeded.value = true
                validationOverride.value = null
            } else {
                validationOverride.value = result.exceptionOrNull()?.message ?: failureMessage
            }
        } finally {
            isSaving.value = false
        }
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
        owner: OwnerInputs,
        flags: DraftFlags,
        save: SaveFlags,
        scan: IncrementScanDelegate.ScanUi,
    ): LiftIncrementsUiState {
        val id = owner.id
        val unit = owner.unit
        val lift = owner.catalog.firstOrNull { it.id.value == id }
        val proposalTiers = flags.proposalInput?.initialTiersLb.orEmpty()
        val tiers =
            flags.draft
                ?: IncrementTierDraftOps.fromProposalArgs(proposalTiers, unit)
                    .takeIf { it.isNotEmpty() }
                ?: lift?.incrementTiers?.map { IncrementTierDraftOps.toDraft(it, unit) }
                ?: listOf(IncrementTierDraftOps.defaultDraft(unit))
        // Micro seeds from the lift's effective stops (equipment-inherited until the
        // lift carries an override), so an override starts with the machine's knob.
        val microStops =
            (flags.microDraft as? MicroDraft.Edited)?.stops
                ?: lift?.microIncrements?.let {
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
                samplesLb = flags.proposalInput?.context?.samplesLb.orEmpty(),
                microLb = microLb,
            )
        val inheriting = lift?.usesEquipmentDefaultIncrements == true && flags.draft == null
        val inheritanceBanner =
            when {
                !inheriting && lift != null && !lift.usesEquipmentDefaultIncrements ->
                    null // overridden — no banner; the editor simply shows the lift's own bands
                // Only claim "from {equipment}" when non-empty equipment tiers actually applied
                // (empty lattices e.g. bodyweight fall through to global fallback).
                inheriting && lift.inheritsFromEquipmentLattice && !lift.equipmentName.isNullOrBlank() ->
                    // Resolved in the composable with string resources when possible; keep text here for VM purity.
                    "Using defaults from ${lift.equipmentName}. Save to override for this lift only."
                inheriting ->
                    "No equipment defaults — these are global fallback bands. Save to override for this lift only."
                else -> null
            }
        return LiftIncrementsUiState(
            liftId = id.orEmpty(),
            liftName = lift?.name.orEmpty(),
            editor =
                IncrementsEditorData(
                    tiers = tiers,
                    microStops = microStops,
                    validationMessage =
                        save.validation ?: liveValidationMessage(tiers, microStops, unit),
                    warnedTierIndexes = warnedTierIndexes,
                    isSaving = save.saving,
                    saveSucceeded = save.saved,
                    unitLabel = unit.shortLabel(),
                    scanAvailable = scan.available,
                    scanUi = scan.state,
                ),
            inheritanceBanner = inheritanceBanner,
            forkSourceId = lift?.equipmentId?.value,
            forkSuggestion =
                "${(lift?.equipmentName ?: lift?.name)?.takeIf { it.isNotBlank() } ?: "Equipment"} (copy)",
        )
    }

    private fun updateDraft(transform: (List<IncrementTierDraft>) -> List<IncrementTierDraft>) {
        val current = uiState.value.editor.tiers
        draftTiers.value = transform(current)
        validationOverride.value = null
        saveSucceeded.value = false
    }

    private fun updateMicroDraft(transform: (List<String>) -> List<String>) {
        // A first touch seeds from the effective stops; later edits build on the draft
        // directly, so back-to-back edits and save never observe a stale derived value.
        val current =
            (draftMicro.value as? MicroDraft.Edited)?.stops ?: uiState.value.editor.microStops
        draftMicro.value = MicroDraft.Edited(transform(current))
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
}
