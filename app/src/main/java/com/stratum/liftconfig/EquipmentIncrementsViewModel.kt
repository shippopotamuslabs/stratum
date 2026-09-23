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
import com.stratum.domain.lift.SetEquipmentIncrementTiersUseCase
import com.stratum.domain.lift.SetLiftEquipmentUseCase
import com.stratum.domain.lift.ValidateIncrementTiersUseCase
import com.stratum.domain.lift.DismissEquipmentProposalUseCase
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.shortLabel
import com.stratum.feature.workout.ui.ProposalEditContext
import com.stratum.feature.workout.ui.ProposalTierArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    private val forkEquipmentWithTiers: ForkEquipmentWithTiersUseCase,
    /** Owned by the quick-swap path: applies a picked equipment to the pending lift. */
    private val setLiftEquipment: SetLiftEquipmentUseCase,
    private val dismissEquipmentProposal: DismissEquipmentProposalUseCase,
    private val equipmentPickResult: EquipmentPickResult,
    observeWeightDisplayPreferences: ObserveWeightDisplayPreferencesUseCase,
    observeEquipmentManagementV2: ObserveEquipmentManagementV2UseCase,
) : ViewModel() {

    private val equipmentId = MutableStateFlow<String?>(null)
    private val sourceLiftId = MutableStateFlow<String?>(null)
    private val draftTiers = MutableStateFlow<List<IncrementTierDraft>?>(null)
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
                    draftTiers.value = null
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

    private data class Flags(
        val draft: List<IncrementTierDraft>?,
        val proposalInput: ProposalInput?,
        val saving: Boolean,
        val saved: Boolean,
        val validation: String?,
        val equipmentV2Enabled: Boolean,
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
            // Five typed flows: this coroutines version has no 6-flow combine overload,
            // so the two save flags travel paired (see Lift VM's flat five for the same
            // shape without the V2 flag).
            combine(
                combine(isSaving, saveSucceeded) { saving, saved -> saving to saved },
                draftTiers,
                proposalSession.input,
                validationOverride,
                equipmentV2Enabled,
            ) { savingPair, draft, input, validation, equipmentV2 ->
                Flags(
                    draft,
                    input,
                    savingPair.first,
                    savingPair.second,
                    validation,
                    equipmentV2,
                )
            },
        ) { id, catalog, affected, unit, flags ->
            buildState(
                id = id,
                catalog = catalog,
                affected = affected,
                unit = unit,
                equipmentV2Enabled = flags.equipmentV2Enabled,
                draft = flags.draft,
                proposalInput = flags.proposalInput,
                saving = flags.saving,
                saved = flags.saved,
                validation = flags.validation,
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
        val id = equipmentId.value ?: return
        val domainTiers = validatedDraftTiers() ?: return
        viewModelScope.launch {
            isSaving.value = true
            val result = setEquipmentIncrementTiers(EquipmentId(id), domainTiers)
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
        viewModelScope.launch {
            isSaving.value = true
            try {
                val copyName = "${uiState.value.equipmentName.ifBlank { "Equipment" }} (copy)"
                val liftId = sourceLiftId.value?.let(::LiftId)
                val result =
                    forkEquipmentWithTiers(EquipmentId(id), copyName, domainTiers, liftId)
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

    private fun buildState(
        id: String?,
        catalog: List<Equipment>,
        affected: EquipmentAffectedLifts,
        unit: Weight.WeightUnit,
        equipmentV2Enabled: Boolean,
        draft: List<IncrementTierDraft>?,
        proposalInput: ProposalInput?,
        saving: Boolean,
        saved: Boolean,
        validation: String?,
    ): EquipmentIncrementsUiState {
        val equipment = catalog.firstOrNull { it.id.value == id }
        val tiers =
            draft
                ?: IncrementTierDraftOps.fromProposalArgs(
                    proposalInput?.initialTiersLb.orEmpty(),
                    unit,
                ).takeIf { it.isNotEmpty() }
                ?: equipment?.incrementTiers?.map { IncrementTierDraftOps.toDraft(it, unit) }
                ?: listOf(IncrementTierDraftOps.defaultDraft(unit))
        val warnedTierIndexes =
            IncrementTierDraftOps.warnedIndexesForSamples(
                drafts = tiers,
                displayUnit = unit,
                samplesLb = proposalInput?.context?.sampleWeightsLb.orEmpty(),
            )
        return EquipmentIncrementsUiState(
            equipmentId = id.orEmpty(),
            equipmentName = equipment?.name.orEmpty(),
            tiers = tiers,
            validationMessage = validation ?: liveValidationMessage(tiers, unit),
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
        )
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

    private fun emptyAffected(): EquipmentAffectedLifts =
        EquipmentAffectedLifts(
            equipmentId = EquipmentId(""),
            count = 0,
            lifts = emptyList(),
        )
}
