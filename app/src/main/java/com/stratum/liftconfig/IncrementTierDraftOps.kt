package com.stratum.liftconfig

import com.stratum.core.ui.component.formatIncrementAmount
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.IncrementTiersValidationError
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.WeightConversion
import com.stratum.domain.lift.shortLabel
import com.stratum.domain.lift.warnedIndexesForSamples
import com.stratum.feature.workout.ui.ProposalTierArgs

/**
 * Shared draft ↔ domain helpers for lift and equipment increment editors.
 *
 * **Storage convention:** Room `increment_tiers` has no unit column — magnitudes are always
 * **pounds**. Domain [Weight] is tagged [Weight.WeightUnit.Lb] at the mapper boundary.
 * [displayUnit] only affects draft strings shown in the editor; save always re-encodes Lb.
 */
object IncrementTierDraftOps {
    const val DEFAULT_TIER_SPLIT_SPAN_LB = 50.0
    const val DEFAULT_TIER_SPLIT_AT_LB = 100.0
    const val DEFAULT_INCREMENT_LB = 5.0

    /** @deprecated Prefer *_LB constants; kept for call-site compatibility. */
    const val DEFAULT_TIER_SPLIT_SPAN = DEFAULT_TIER_SPLIT_SPAN_LB
    const val DEFAULT_TIER_SPLIT_AT = DEFAULT_TIER_SPLIT_AT_LB
    const val DEFAULT_INCREMENT_TEXT = "5"

    fun filterNumeric(value: String): String =
        value.filter { it.isDigit() || it == '.' || it == '-' }

    fun formatAmount(amount: Double): String = formatIncrementAmount(amount)

    fun unitLabel(displayUnit: Weight.WeightUnit): String = displayUnit.shortLabel()

    fun defaultDraft(displayUnit: Weight.WeightUnit = Weight.WeightUnit.Lb): IncrementTierDraft =
        IncrementTierDraft(
            lowerBound = "0",
            upperBound = null,
            increment =
                formatAmount(
                    WeightConversion.convertAmount(
                        DEFAULT_INCREMENT_LB,
                        Weight.WeightUnit.Lb,
                        displayUnit,
                    ),
                ),
            openEnded = true,
        )

    /**
     * Maps a domain tier into draft field strings in [displayUnit].
     * Source amounts are treated as pounds (storage convention).
     */
    fun toDraft(
        tier: IncrementTier,
        displayUnit: Weight.WeightUnit = Weight.WeightUnit.Lb,
    ): IncrementTierDraft {
        fun convert(amount: Double): Double =
            WeightConversion.convertAmount(amount, Weight.WeightUnit.Lb, displayUnit)
        return IncrementTierDraft(
            lowerBound = formatAmount(convert(tier.lowerBound.amount)),
            upperBound = tier.upperBound?.amount?.let { formatAmount(convert(it)) },
            increment = formatAmount(convert(tier.increment.amount)),
            openEnded = tier.upperBound == null,
        )
    }

    /**
     * Seeds editor drafts from proposed bands in storage pounds (e.g. a post-workout
     * equipment proposal's "Edit steps" prefill). Conversion mirrors [toDraft] so the
     * prefill matches what saving the same bands would persist; live validation still
     * applies on screen. Bands with non-positive steps are dropped — an empty result
     * falls back to the catalog tiers at the call site.
     */
    fun fromProposalArgs(
        args: List<ProposalTierArgs>,
        displayUnit: Weight.WeightUnit = Weight.WeightUnit.Lb,
    ): List<IncrementTierDraft> =
        args.mapNotNull { arg ->
            if (arg.incrementLb <= 0.0) {
                null
            } else {
                toDraft(
                    IncrementTier(
                        lowerBound = Weight(arg.lowerBoundLb, Weight.WeightUnit.Lb),
                        upperBound =
                            arg.upperBoundLb?.let { Weight(it, Weight.WeightUnit.Lb) },
                        increment = Weight(arg.incrementLb, Weight.WeightUnit.Lb),
                    ),
                    displayUnit,
                )
            }
        }

    /**
     * Returns the indexes of bands whose steps do not divide evenly with at least one of
     * [samplesLb] (storage pounds — e.g. a proposal's logged session weights). Used for a
     * non-blocking editor warning: saving stays allowed. Bands that fail to parse yield
     * no warnings; band math itself lives in domain
     * ([warnedIndexesForSamples][com.stratum.domain.lift.warnedIndexesForSamples]).
     */
    fun warnedIndexesForSamples(
        drafts: List<IncrementTierDraft>,
        displayUnit: Weight.WeightUnit = Weight.WeightUnit.Lb,
        samplesLb: List<Double>,
    ): Set<Int> {
        if (samplesLb.isEmpty()) return emptySet()
        val tiers = toDomainOrNull(drafts, displayUnit) ?: return emptySet()
        return tiers.warnedIndexesForSamples(samplesLb)
    }

    /**
     * Parses draft fields as [displayUnit] magnitudes and returns domain tiers stamped as **Lb**
     * for Room persistence. Null if any band fails to parse.
     */
    fun toDomainOrNull(
        tiers: List<IncrementTierDraft>,
        displayUnit: Weight.WeightUnit = Weight.WeightUnit.Lb,
    ): List<IncrementTier>? {
        if (tiers.isEmpty()) return emptyList()
        val result = ArrayList<IncrementTier>(tiers.size)
        for ((index, draft) in tiers.withIndex()) {
            val tier = draft.toDomainTier(index == tiers.lastIndex, displayUnit) ?: return null
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
                IncrementTiersValidationError.NegativeLowerBound ->
                    "The first band cannot start below 0"
            }
        }

    /** Formats a split-point amount that was computed in display units. */
    fun formatDisplayAmount(amount: Double): String = formatAmount(amount)

    fun defaultSplitSpan(displayUnit: Weight.WeightUnit): Double =
        WeightConversion.convertAmount(
            DEFAULT_TIER_SPLIT_SPAN_LB,
            Weight.WeightUnit.Lb,
            displayUnit,
        )

    fun defaultSplitAt(displayUnit: Weight.WeightUnit): Double =
        WeightConversion.convertAmount(
            DEFAULT_TIER_SPLIT_AT_LB,
            Weight.WeightUnit.Lb,
            displayUnit,
        )

    private fun IncrementTierDraft.toDomainTier(
        isLast: Boolean,
        displayUnit: Weight.WeightUnit,
    ): IncrementTier? {
        val lowerDisplay = lowerBound.toDoubleOrNull() ?: return null
        val incDisplay = increment.toDoubleOrNull() ?: return null
        if (incDisplay <= 0.0) return null
        val upperDisplay =
            when {
                openEnded || (isLast && upperBound.isNullOrBlank()) -> null
                else -> upperBound?.toDoubleOrNull() ?: return null
            }
        fun toLb(amount: Double): Double =
            WeightConversion.convertAmount(amount, displayUnit, Weight.WeightUnit.Lb)
        val storage = Weight.WeightUnit.Lb
        return IncrementTier(
            lowerBound = Weight(toLb(lowerDisplay), storage),
            upperBound = upperDisplay?.let { Weight(toLb(it), storage) },
            increment = Weight(toLb(incDisplay), storage),
        )
    }
}
