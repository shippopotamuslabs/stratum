package com.stratum.liftconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.lift.IncrementTiersValidationResult
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.ObserveLiftCatalogUseCase
import com.stratum.domain.lift.ObserveWeightDisplayPreferencesUseCase
import com.stratum.domain.lift.SetLiftIncrementTiersUseCase
import com.stratum.domain.lift.ValidateIncrementTiersUseCase
import com.stratum.domain.lift.DismissEquipmentProposalUseCase
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.shortLabel
import com.stratum.feature.workout.ui.ProposalEditContext
import com.stratum.feature.workout.ui.ProposalTierArgs
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
    private val validateIncrementTiers: ValidateIncrementTiersUseCase,
    private val dismissEquipmentProposal: DismissEquipmentProposalUseCase,
    observeWeightDisplayPreferences: ObserveWeightDisplayPreferencesUseCase,
) : ViewModel() {

    private val liftId = MutableStateFlow<String?>(null)
    private val draftTiers = MutableStateFlow<List<IncrementTierDraft>?>(null)
    /** Shared proposal-prefill session (prefill bands, sample warnings, dismiss-on-save). */
    private val proposalSession = ProposalEditorSession()
    private val isSaving = MutableStateFlow(false)
    private val saveSucceeded = MutableStateFlow(false)
    private val validationOverride = MutableStateFlow<String?>(null)
    private val displayUnit = MutableStateFlow(Weight.WeightUnit.Lb)

    private data class DraftFlags(
        val draft: List<IncrementTierDraft>?,
        val proposalInput: ProposalInput?,
        val saving: Boolean,
        val saved: Boolean,
        val validation: String?,
    )

    init {
        viewModelScope.launch {
            observeWeightDisplayPreferences().collect { prefs ->
                if (displayUnit.value != prefs.displayUnit) {
                    // Unit flip: drop dirty draft so bands re-seed converted from storage.
                    draftTiers.value = null
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
                draftTiers,
                proposalSession.input,
                isSaving,
                saveSucceeded,
                validationOverride,
            ) { draft, input, saving, saved, validation ->
                DraftFlags(draft, input, saving, saved, validation)
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
        saveSucceeded.value = false
        validationOverride.value = null
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
        viewModelScope.launch {
            isSaving.value = true
            val result = setLiftIncrementTiers(LiftId(id), domainTiers)
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
        val warnedTierIndexes =
            IncrementTierDraftOps.warnedIndexesForSamples(
                drafts = tiers,
                displayUnit = unit,
                samplesLb = flags.proposalInput?.context?.sampleWeightsLb.orEmpty(),
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
            validationMessage = flags.validation ?: liveValidationMessage(tiers, unit),
            warnedTierIndexes = warnedTierIndexes,
            isSaving = flags.saving,
            saveSucceeded = flags.saved,
            inheritanceBanner = inheritanceBanner,
            showResetToEquipment = lift != null && !lift.usesEquipmentDefaultIncrements,
            unitLabel = unit.shortLabel(),
        )
    }

    fun resetToEquipmentDefaults() {
        val id = liftId.value ?: return
        viewModelScope.launch {
            isSaving.value = true
            val result = setLiftIncrementTiers(LiftId(id), emptyList())
            isSaving.value = false
            if (result.isSuccess) {
                draftTiers.value = null
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

    private fun liveValidationMessage(
        tiers: List<IncrementTierDraft>,
        unit: Weight.WeightUnit,
    ): String? {
        val domain = IncrementTierDraftOps.toDomainOrNull(tiers, unit) ?: return null
        return when (val result = validateIncrementTiers(domain)) {
            is IncrementTiersValidationResult.Invalid ->
                IncrementTierDraftOps.toUserMessage(result.errors)
            IncrementTiersValidationResult.Valid -> null
        }
    }
}
