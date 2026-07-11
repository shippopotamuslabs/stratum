package com.stratum.liftconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.EquipmentAffectedLifts
import com.stratum.domain.lift.IncrementTiersValidationResult
import com.stratum.domain.lift.ObserveEquipmentAffectedLiftsUseCase
import com.stratum.domain.lift.ObserveEquipmentCatalogUseCase
import com.stratum.domain.lift.SetEquipmentIncrementTiersUseCase
import com.stratum.domain.lift.ValidateIncrementTiersUseCase
import com.stratum.domain.lift.identity.EquipmentId
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
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EquipmentIncrementsViewModel @Inject constructor(
    private val observeEquipmentCatalog: ObserveEquipmentCatalogUseCase,
    private val observeEquipmentAffectedLifts: ObserveEquipmentAffectedLiftsUseCase,
    private val setEquipmentIncrementTiers: SetEquipmentIncrementTiersUseCase,
    private val validateIncrementTiers: ValidateIncrementTiersUseCase,
) : ViewModel() {

    private val equipmentId = MutableStateFlow<String?>(null)
    private val draftTiers = MutableStateFlow<List<IncrementTierDraft>?>(null)
    private val isSaving = MutableStateFlow(false)
    private val saveSucceeded = MutableStateFlow(false)
    private val validationOverride = MutableStateFlow<String?>(null)

    private data class Flags(
        val draft: List<IncrementTierDraft>?,
        val saving: Boolean,
        val saved: Boolean,
        val validation: String?,
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
            combine(draftTiers, isSaving, saveSucceeded, validationOverride) { draft, saving, saved, validation ->
                Flags(draft, saving, saved, validation)
            },
        ) { id, catalog, affected, flags ->
            buildState(
                id = id,
                catalog = catalog,
                affected = affected,
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

    fun initialize(id: String) {
        if (equipmentId.value == id) return
        equipmentId.value = id
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
        updateDraft { tiers ->
            val last = tiers.lastOrNull()
            val splitAt =
                last?.upperBound?.toDoubleOrNull()
                    ?: last?.lowerBound?.toDoubleOrNull()?.plus(IncrementTierDraftOps.DEFAULT_TIER_SPLIT_SPAN)
                    ?: IncrementTierDraftOps.DEFAULT_TIER_SPLIT_AT
            val closedLast =
                last?.copy(
                    openEnded = false,
                    upperBound = IncrementTierDraftOps.formatAmount(splitAt),
                )
            val next =
                IncrementTierDraft(
                    lowerBound = IncrementTierDraftOps.formatAmount(splitAt),
                    upperBound = null,
                    increment = last?.increment ?: IncrementTierDraftOps.DEFAULT_INCREMENT_TEXT,
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
        val domainTiers = IncrementTierDraftOps.toDomainOrNull(uiState.value.tiers)
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

    private fun buildState(
        id: String?,
        catalog: List<Equipment>,
        affected: EquipmentAffectedLifts,
        draft: List<IncrementTierDraft>?,
        saving: Boolean,
        saved: Boolean,
        validation: String?,
    ): EquipmentIncrementsUiState {
        val equipment = catalog.firstOrNull { it.id.value == id }
        val tiers =
            draft
                ?: equipment?.incrementTiers?.map { IncrementTierDraftOps.toDraft(it) }
                ?: listOf(IncrementTierDraftOps.defaultDraft())
        return EquipmentIncrementsUiState(
            equipmentId = id.orEmpty(),
            equipmentName = equipment?.name.orEmpty(),
            tiers = tiers,
            validationMessage = validation ?: liveValidationMessage(tiers),
            isSaving = saving,
            saveSucceeded = saved,
            affectedLiftCount = affected.count,
            affectedLiftNames = affected.lifts.map { it.name },
        )
    }

    private fun updateDraft(transform: (List<IncrementTierDraft>) -> List<IncrementTierDraft>) {
        val current = uiState.value.tiers
        draftTiers.value = transform(current)
        validationOverride.value = null
        saveSucceeded.value = false
    }

    private fun liveValidationMessage(tiers: List<IncrementTierDraft>): String? {
        val domain = IncrementTierDraftOps.toDomainOrNull(tiers) ?: return null
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
