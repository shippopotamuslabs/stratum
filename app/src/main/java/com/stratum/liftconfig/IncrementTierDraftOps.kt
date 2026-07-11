package com.stratum.liftconfig

import com.stratum.core.ui.component.formatIncrementAmount
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.IncrementTiersValidationError
import com.stratum.domain.lift.Weight

/**
 * Shared draft ↔ domain helpers for lift and equipment increment editors.
 */
object IncrementTierDraftOps {
    const val DEFAULT_TIER_SPLIT_SPAN = 50.0
    const val DEFAULT_TIER_SPLIT_AT = 100.0
    const val DEFAULT_INCREMENT_TEXT = "5"

    fun filterNumeric(value: String): String =
        value.filter { it.isDigit() || it == '.' || it == '-' }

    fun formatAmount(amount: Double): String = formatIncrementAmount(amount)

    fun defaultDraft(): IncrementTierDraft =
        IncrementTierDraft(
            lowerBound = "0",
            upperBound = null,
            increment = DEFAULT_INCREMENT_TEXT,
            openEnded = true,
        )

    fun toDraft(tier: IncrementTier): IncrementTierDraft =
        IncrementTierDraft(
            lowerBound = formatAmount(tier.lowerBound.amount),
            upperBound = tier.upperBound?.amount?.let { formatAmount(it) },
            increment = formatAmount(tier.increment.amount),
            openEnded = tier.upperBound == null,
        )

    fun toDomainOrNull(tiers: List<IncrementTierDraft>): List<IncrementTier>? {
        if (tiers.isEmpty()) return emptyList()
        val unit = Weight.WeightUnit.Lb
        val result = ArrayList<IncrementTier>(tiers.size)
        for ((index, draft) in tiers.withIndex()) {
            val tier = draft.toDomainTier(index == tiers.lastIndex, unit) ?: return null
            result += tier
        }
        return result
    }

    fun toUserMessage(errors: Set<IncrementTiersValidationError>): String =
        errors.joinToString(separator = ". ") { error ->
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
}
