package com.stratum.liftconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.IncrementTiersValidationError
import com.stratum.domain.lift.IncrementTiersValidationResult
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.ObserveLiftCatalogUseCase
import com.stratum.domain.lift.SetLiftIncrementTiersUseCase
import com.stratum.domain.lift.ValidateIncrementTiersUseCase
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
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
    val isSaving: Boolean = false,
    val saveSucceeded: Boolean = false,
)

@HiltViewModel
class LiftIncrementsViewModel @Inject constructor(
    private val observeLiftCatalog: ObserveLiftCatalogUseCase,
    private val setLiftIncrementTiers: SetLiftIncrementTiersUseCase,
    private val validateIncrementTiers: ValidateIncrementTiersUseCase,
) : ViewModel() {

    private val liftId = MutableStateFlow<String?>(null)
    private val draftTiers = MutableStateFlow<List<IncrementTierDraft>?>(null)
    private val isSaving = MutableStateFlow(false)
    private val saveSucceeded = MutableStateFlow(false)
    private val validationOverride = MutableStateFlow<String?>(null)

    private data class DraftFlags(
        val draft: List<IncrementTierDraft>?,
        val saving: Boolean,
        val saved: Boolean,
        val validation: String?,
    )

    val uiState: StateFlow<LiftIncrementsUiState> =
        combine(
            liftId,
            observeLiftCatalog(),
            combine(draftTiers, isSaving, saveSucceeded, validationOverride) { draft, saving, saved, validation ->
                DraftFlags(draft, saving, saved, validation)
            },
        ) { id, catalog, flags ->
            buildState(id, catalog, flags)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            LiftIncrementsUiState(),
        )

    fun initialize(id: String) {
        if (liftId.value == id) return
        liftId.value = id
        draftTiers.value = null
        saveSucceeded.value = false
        validationOverride.value = null
    }

    fun onTierLowerChanged(index: Int, value: String) {
        updateDraft { tiers ->
            tiers.mapIndexed { i, tier ->
                if (i == index) tier.copy(lowerBound = value.filterNumeric()) else tier
            }
        }
    }

    fun onTierUpperChanged(index: Int, value: String) {
        updateDraft { tiers ->
            tiers.mapIndexed { i, tier ->
                if (i == index) tier.copy(upperBound = value.filterNumeric()) else tier
            }
        }
    }

    fun onTierIncrementChanged(index: Int, value: String) {
        updateDraft { tiers ->
            tiers.mapIndexed { i, tier ->
                if (i == index) tier.copy(increment = value.filterNumeric()) else tier
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
                    ?: last?.lowerBound?.toDoubleOrNull()?.plus(DEFAULT_TIER_SPLIT_SPAN)
                    ?: DEFAULT_TIER_SPLIT_AT
            val closedLast =
                last?.copy(
                    openEnded = false,
                    upperBound = formatAmount(splitAt),
                )
            val next =
                IncrementTierDraft(
                    lowerBound = formatAmount(splitAt),
                    upperBound = null,
                    increment = last?.increment ?: DEFAULT_INCREMENT_TEXT,
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
        val domainTiers = uiState.value.tiers.toDomainOrNull()
        if (domainTiers == null) {
            validationOverride.value = "Enter valid numbers for all bands"
            return
        }
        when (val result = validateIncrementTiers(domainTiers)) {
            is IncrementTiersValidationResult.Invalid -> {
                validationOverride.value = result.errors.toUserMessage()
                return
            }
            IncrementTiersValidationResult.Valid -> Unit
        }
        viewModelScope.launch {
            isSaving.value = true
            val result = setLiftIncrementTiers(LiftId(id), domainTiers)
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
        catalog: List<Lift>,
        flags: DraftFlags,
    ): LiftIncrementsUiState {
        val lift = catalog.firstOrNull { it.id.value == id }
        val tiers =
            flags.draft
                ?: lift?.incrementTiers?.map { it.toDraft() }
                ?: listOf(defaultDraft())
        return LiftIncrementsUiState(
            liftId = id.orEmpty(),
            liftName = lift?.name.orEmpty(),
            tiers = tiers,
            validationMessage = flags.validation ?: liveValidationMessage(tiers),
            isSaving = flags.saving,
            saveSucceeded = flags.saved,
        )
    }

    private fun updateDraft(transform: (List<IncrementTierDraft>) -> List<IncrementTierDraft>) {
        val current = uiState.value.tiers
        draftTiers.value = transform(current)
        validationOverride.value = null
        saveSucceeded.value = false
    }

    private fun liveValidationMessage(tiers: List<IncrementTierDraft>): String? {
        val domain = tiers.toDomainOrNull() ?: return null
        return when (val result = validateIncrementTiers(domain)) {
            is IncrementTiersValidationResult.Invalid -> result.errors.toUserMessage()
            IncrementTiersValidationResult.Valid -> null
        }
    }

    private fun IncrementTier.toDraft(): IncrementTierDraft =
        IncrementTierDraft(
            lowerBound = formatAmount(lowerBound.amount),
            upperBound = upperBound?.amount?.let { formatAmount(it) },
            increment = formatAmount(increment.amount),
            openEnded = upperBound == null,
        )

    private fun defaultDraft(): IncrementTierDraft =
        IncrementTierDraft(
            lowerBound = "0",
            upperBound = null,
            increment = "5",
            openEnded = true,
        )

    private fun formatAmount(amount: Double): String =
        if (amount % 1.0 == 0.0) amount.toInt().toString() else amount.toString()

    private fun String.filterNumeric(): String =
        filter { it.isDigit() || it == '.' || it == '-' }

    private fun List<IncrementTierDraft>.toDomainOrNull(): List<IncrementTier>? {
        if (isEmpty()) return emptyList()
        val unit = Weight.WeightUnit.Lb
        val result = ArrayList<IncrementTier>(size)
        for ((index, draft) in withIndex()) {
            val tier = draft.toDomainTier(index == lastIndex, unit) ?: return null
            result += tier
        }
        return result
    }

    private fun IncrementTierDraft.toDomainTier(
        isLast: Boolean,
        unit: Weight.WeightUnit,
    ): IncrementTier? {
        val lower = lowerBound.toDoubleOrNull() ?: return null
        val inc = increment.toDoubleOrNull() ?: return null
        if (inc <= 0.0) return null
        val upperAmount =
            when {
                openEnded || (isLast && upperBound.isNullOrBlank()) -> null
                else -> upperBound?.toDoubleOrNull() ?: return null
            }
        return IncrementTier(
            lowerBound = Weight(lower, unit),
            upperBound = upperAmount?.let { Weight(it, unit) },
            increment = Weight(inc, unit),
        )
    }

    private fun Set<IncrementTiersValidationError>.toUserMessage(): String =
        joinToString(separator = ". ") { error ->
            when (error) {
                IncrementTiersValidationError.MixedUnits -> "All tiers must use the same unit"
                IncrementTiersValidationError.NotContiguous ->
                    "Bands must tile without gaps (each From equals previous To)"
                IncrementTiersValidationError.ZeroWidthBand -> "Each band needs a positive width"
                IncrementTiersValidationError.IntermediateTierMissingUpperBound ->
                    "Only the last band can be open-ended"
                IncrementTiersValidationError.FinalTierMustBeOpenEnded ->
                    "The last band must be open-ended (and above)"
                IncrementTiersValidationError.IncrementDecreased ->
                    "Step size should not decrease as weight increases"
            }
        }

    private companion object {
        const val DEFAULT_TIER_SPLIT_SPAN = 50.0
        const val DEFAULT_TIER_SPLIT_AT = 100.0
        const val DEFAULT_INCREMENT_TEXT = "5"
    }
}
