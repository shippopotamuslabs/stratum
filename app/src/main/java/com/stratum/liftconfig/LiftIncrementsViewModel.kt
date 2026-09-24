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
import com.stratum.domain.lift.SetLiftMicroOverrideUseCase
import com.stratum.domain.lift.ValidateIncrementTiersUseCase
import com.stratum.domain.lift.ValidateMicroIncrementsUseCase
import com.stratum.domain.lift.DismissEquipmentProposalUseCase
import com.stratum.domain.lift.Weight
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
    val tiers: List<IncrementTierDraft> = emptyList(),
    /**
     * Adder stop drafts (display-unit strings); empty = no adder. Owned by the
     * caller: the equipment editor manages them (see its draft flow); the lift
     * editor leaves the default until it wires drafts.
     */
    val microStops: List<String> = emptyList(),
    /**
     * Whether the adder card renders. The equipment route drives this from its
     * V2 flag state; the lift route leaves the default until it wires drafts.
     */
    val showMicroCard: Boolean = false,
    val validationMessage: String? = null,
    /**
     * Band indexes whose steps miss a proposal's logged sample weights (yellow,
     * non-blocking — saving stays allowed). Empty for all non-proposal entry points.
     */
    val warnedTierIndexes: Set<Int> = emptySet(),
    val isSaving: Boolean = false,
    val saveSucceeded: Boolean = false,
    /** Non-null when the lift is inheriting equipment/fallback defaults (no override rows). */
    val inheritanceBanner: String? = null,
    val showResetToEquipment: Boolean = false,
    /** Short unit label for field chrome (`lb` / `kg`) from Settings. */
    val unitLabel: String = "lb",
)

@HiltViewModel
class LiftIncrementsViewModel @Inject constructor(
    private val observeLiftCatalog: ObserveLiftCatalogUseCase,
    private val setLiftIncrementTiers: SetLiftIncrementTiersUseCase,
    private val setLiftMicroOverride: SetLiftMicroOverrideUseCase,
    private val validateIncrementTiers: ValidateIncrementTiersUseCase,
    private val validateMicroIncrements: ValidateMicroIncrementsUseCase,
    private val dismissEquipmentProposal: DismissEquipmentProposalUseCase,
    observeWeightDisplayPreferences: ObserveWeightDisplayPreferencesUseCase,
) : ViewModel() {

    private val liftId = MutableStateFlow<String?>(null)
    private val draftTiers = MutableStateFlow<List<IncrementTierDraft>?>(null)
    /**
     * Null seeds from the lift's effective stops on next build (see [buildState]) and
     * doubles as the touched flag: saves leave the micro column alone until the user
     * edits stops, so band-only saves never silently convert inherit to explicit.
     */
    private val draftMicro = MutableStateFlow<List<String>?>(null)
    /** Shared proposal-prefill session (prefill bands, sample warnings, dismiss-on-save). */
    private val proposalSession = ProposalEditorSession()
    private val isSaving = MutableStateFlow(false)
    private val saveSucceeded = MutableStateFlow(false)
    private val validationOverride = MutableStateFlow<String?>(null)
    private val displayUnit = MutableStateFlow(Weight.WeightUnit.Lb)

    private data class DraftFlags(
        val draft: List<IncrementTierDraft>?,
        val microDraft: List<String>?,
        val proposalInput: ProposalInput?,
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
                    draftMicro.value = null
                    displayUnit.value = prefs.displayUnit
                }
            }
        }
    }

    val uiState: StateFlow<LiftIncrementsUiState> =
        combine(
            liftId,
            observeLiftCatalog(),
            displayUnit,
            combine(
                combine(draftTiers, draftMicro) { tiers, micro -> tiers to micro },
                proposalSession.input,
                isSaving,
                saveSucceeded,
                validationOverride,
            ) { draftsPair, input, saving, saved, validation ->
                DraftFlags(
                    draftsPair.first,
                    draftsPair.second,
                    input,
                    saving,
                    saved,
                    validation,
                )
            },
        ) { id, catalog, unit, flags ->
            buildState(id, catalog, unit, flags)
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
        draftMicro.value = null
        saveSucceeded.value = false
        validationOverride.value = null
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
            remaining[remaining.lastIndex] =
                remaining.last().copy(openEnded = true, upperBound = null)
            remaining
        }
    }

    fun save() {
        val id = liftId.value ?: return
        val unit = displayUnit.value
        val domainTiers = IncrementTierDraftOps.toDomainOrNull(uiState.value.tiers, unit)
        if (domainTiers == null) {
            validationOverride.value = "Enter valid numbers for all bands"
            return
        }
        when (val result = validateIncrementTiers(domainTiers)) {
            is IncrementTiersValidationResult.Invalid -> {
                validationOverride.value = IncrementTierDraftOps.toUserMessage(result.errors)
                return
            }
            IncrementTiersValidationResult.Valid -> Unit
        }
        // Untouched stops leave the micro column alone; edited stops replace the
        // override (empty = explicitly no adder).
        val domainMicro =
            if (draftMicro.value == null) {
                null
            } else {
                validatedDraftMicro(domainTiers, unit) ?: return
            }
        viewModelScope.launch {
            isSaving.value = true
            val result = setLiftIncrementTiers(LiftId(id), domainTiers, domainMicro)
            isSaving.value = false
            if (result.isSuccess) {
                // A proposal-launched save resolves the proposal even when the hand-set
                // tiers still miss logged weights — the user took ownership.
                proposalSession.dismissIfPresent(dismissEquipmentProposal)
                saveSucceeded.value = true
                validationOverride.value = null
            } else {
                validationOverride.value = result.exceptionOrNull()?.message
                    ?: "Could not save increments"
            }
        }
    }

    private fun validatedDraftMicro(
        domainTiers: List<IncrementTier>,
        unit: Weight.WeightUnit,
    ): List<Weight>? {
        val micro =
            IncrementTierDraftOps.microToDomainOrNull(uiState.value.microStops, unit)
        if (micro == null) {
            validationOverride.value = "Enter valid numbers for adder stops"
            return null
        }
        when (val result = validateMicroIncrements(domainTiers, micro)) {
            is MicroIncrementsValidationResult.Invalid -> {
                validationOverride.value = IncrementTierDraftOps.toMicroUserMessage(result.errors)
                return null
            }
            MicroIncrementsValidationResult.Valid -> Unit
        }
        return micro
    }

    private fun buildState(
        id: String?,
        catalog: List<Lift>,
        unit: Weight.WeightUnit,
        flags: DraftFlags,
    ): LiftIncrementsUiState {
        val lift = catalog.firstOrNull { it.id.value == id }
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
            flags.microDraft
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
                    null // overridden — banner handled via showReset
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
            tiers = tiers,
            microStops = microStops,
            showMicroCard = true,
            validationMessage = flags.validation ?: liveValidationMessage(tiers, microStops, unit),
            warnedTierIndexes = warnedTierIndexes,
            isSaving = flags.saving,
            saveSucceeded = flags.saved,
            inheritanceBanner = inheritanceBanner,
            showResetToEquipment =
                lift != null &&
                (!lift.usesEquipmentDefaultIncrements || !lift.usesEquipmentDefaultMicro),
            unitLabel = unit.shortLabel(),
        )
    }

    fun resetToEquipmentDefaults() {
        val id = liftId.value ?: return
        viewModelScope.launch {
            isSaving.value = true
            // One atomic write: bands and knob clear together, so a failure cannot
            // leave inherited bands with a stale micro override (half-reset).
            val result = resetLiftIncrementsToEquipment(LiftId(id))
            isSaving.value = false
            if (result.isSuccess) {
                draftTiers.value = null
                draftMicro.value = MicroDraft.Untouched
                saveSucceeded.value = true
                validationOverride.value = null
            } else {
                validationOverride.value = result.exceptionOrNull()?.message
                    ?: "Could not reset increments"
            }
        }
    }

    private fun updateDraft(transform: (List<IncrementTierDraft>) -> List<IncrementTierDraft>) {
        val current = uiState.value.tiers
        draftTiers.value = transform(current)
        validationOverride.value = null
        saveSucceeded.value = false
    }

    private fun updateMicroDraft(transform: (List<String>) -> List<String>) {
        val current = uiState.value.microStops
        draftMicro.value = transform(current)
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
        val micro =
            IncrementTierDraftOps.microToDomainOrNull(microStops, unit) ?: return null
        return when (val result = validateMicroIncrements(domain, micro)) {
            is MicroIncrementsValidationResult.Invalid ->
                IncrementTierDraftOps.toMicroUserMessage(result.errors)
            MicroIncrementsValidationResult.Valid -> null
        }
    }
}
