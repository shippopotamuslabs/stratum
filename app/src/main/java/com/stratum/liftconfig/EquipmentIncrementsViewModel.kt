package com.stratum.liftconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.featureflag.ObserveEquipmentManagementV2UseCase
import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.EquipmentAffectedLifts
import com.stratum.domain.lift.ForkEquipmentUseCase
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.IncrementTiersValidationResult
import com.stratum.domain.lift.ObserveEquipmentAffectedLiftsUseCase
import com.stratum.domain.lift.ObserveEquipmentCatalogUseCase
import com.stratum.domain.lift.ObserveWeightDisplayPreferencesUseCase
import com.stratum.domain.lift.SetEquipmentIncrementTiersUseCase
import com.stratum.domain.lift.SetLiftEquipmentUseCase
import com.stratum.domain.lift.ValidateIncrementTiersUseCase
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.shortLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EquipmentIncrementsUiState(
    val equipmentId: String = "",
    val equipmentName: String = "",
    val tiers: List<IncrementTierDraft> = emptyList(),
    val validationMessage: String? = null,
    val isSaving: Boolean = false,
    val saveSucceeded: Boolean = false,
    /** Lifts inheriting this equipment's defaults (includes disabled). */
    val affectedLiftCount: Int = 0,
    /** Names of affected lifts, sorted; UI shows first 3 + "+M more". */
    val affectedLiftNames: List<String> = emptyList(),
    /** Short unit label for field chrome (`lb` / `kg`) from Settings. */
    val unitLabel: String = "lb",
    /** True when the Update Shared / Save As New bar replaces the legacy Save button. */
    val showForkSaveBar: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EquipmentIncrementsViewModel @Inject constructor(
    private val observeEquipmentCatalog: ObserveEquipmentCatalogUseCase,
    private val observeEquipmentAffectedLifts: ObserveEquipmentAffectedLiftsUseCase,
    private val setEquipmentIncrementTiers: SetEquipmentIncrementTiersUseCase,
    private val validateIncrementTiers: ValidateIncrementTiersUseCase,
    private val forkEquipment: ForkEquipmentUseCase,
    private val setLiftEquipment: SetLiftEquipmentUseCase,
    observeWeightDisplayPreferences: ObserveWeightDisplayPreferencesUseCase,
    observeEquipmentManagementV2: ObserveEquipmentManagementV2UseCase,
) : ViewModel() {

    private val equipmentId = MutableStateFlow<String?>(null)
    private val sourceLiftId = MutableStateFlow<String?>(null)
    private val draftTiers = MutableStateFlow<List<IncrementTierDraft>?>(null)
    private val isSaving = MutableStateFlow(false)
    private val saveSucceeded = MutableStateFlow(false)
    private val validationOverride = MutableStateFlow<String?>(null)
    private val displayUnit = MutableStateFlow(Weight.WeightUnit.Lb)
    private val showForkSaveBar = MutableStateFlow(false)

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
                showForkSaveBar.value = enabled
            }
        }
    }

    private data class Flags(
        val draft: List<IncrementTierDraft>?,
        val saving: Boolean,
        val saved: Boolean,
        val validation: String?,
    )

    private data class BaseInputs(
        val id: String?,
        val catalog: List<Equipment>,
        val affected: EquipmentAffectedLifts,
        val unit: Weight.WeightUnit,
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
            combine(
                equipmentId,
                observeEquipmentCatalog(),
                affectedLifts,
                displayUnit,
            ) { id, catalog, affected, unit ->
                BaseInputs(id, catalog, affected, unit)
            },
            showForkSaveBar,
            combine(draftTiers, isSaving, saveSucceeded, validationOverride) { draft, saving, saved, validation ->
                Flags(draft, saving, saved, validation)
            },
        ) { base, showForkBar, flags ->
            buildState(
                id = base.id,
                catalog = base.catalog,
                affected = base.affected,
                unit = base.unit,
                showForkBar = showForkBar,
                draft = flags.draft,
                saving = flags.saving,
                saved = flags.saved,
                validation = flags.validation,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            EquipmentIncrementsUiState(),
        )

    fun initialize(id: String, sourceLiftId: String? = null) {
        if (equipmentId.value == id && this.sourceLiftId.value == sourceLiftId) return
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
            val copyName = "${uiState.value.equipmentName.ifBlank { "Equipment" }} (copy)"
            val newId =
                forkEquipment(EquipmentId(id), copyName).getOrElse { error ->
                    isSaving.value = false
                    validationOverride.value = error.message ?: "Could not copy equipment"
                    return@launch
                }
            val written = setEquipmentIncrementTiers(newId, domainTiers)
            if (written.isFailure) {
                isSaving.value = false
                validationOverride.value = written.exceptionOrNull()?.message
                    ?: "Could not save increments"
                return@launch
            }
            sourceLiftId.value?.let { liftId ->
                val reassigned = setLiftEquipment(LiftId(liftId), newId)
                if (reassigned.isFailure) {
                    isSaving.value = false
                    validationOverride.value = reassigned.exceptionOrNull()?.message
                        ?: "Could not move lift to the copy"
                    return@launch
                }
            }
            isSaving.value = false
            saveSucceeded.value = true
            validationOverride.value = null
        }
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
        showForkBar: Boolean,
        draft: List<IncrementTierDraft>?,
        saving: Boolean,
        saved: Boolean,
        validation: String?,
    ): EquipmentIncrementsUiState {
        val equipment = catalog.firstOrNull { it.id.value == id }
        val tiers =
            draft
                ?: equipment?.incrementTiers?.map { IncrementTierDraftOps.toDraft(it, unit) }
                ?: listOf(IncrementTierDraftOps.defaultDraft(unit))
        return EquipmentIncrementsUiState(
            equipmentId = id.orEmpty(),
            equipmentName = equipment?.name.orEmpty(),
            tiers = tiers,
            validationMessage = validation ?: liveValidationMessage(tiers, unit),
            isSaving = saving,
            saveSucceeded = saved,
            affectedLiftCount = affected.count,
            affectedLiftNames = affected.lifts.map { it.name },
            unitLabel = unit.shortLabel(),
            showForkSaveBar = showForkBar,
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
