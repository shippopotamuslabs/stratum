package com.stratum.liftconfig

import com.stratum.core.ui.component.formatIncrementAmount
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.IncrementTiersValidationError
import com.stratum.domain.lift.MicroIncrementsValidationError
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

/**
 * Editable tier row in the increment editors. Raw strings; [IncrementTierDraftOps] converts
 * to domain [IncrementTier] on save. (Moved here from the deleted per-lift
 * `LiftIncrementsViewModel` — STR-86 collapsed the per-lift editor into the shared equipment
 * editor, but this draft model is package-wide.)
 */
data class IncrementTierDraft(
    val lowerBound: String,
    val upperBound: String?,
    val increment: String,
    val openEnded: Boolean,
)

object IncrementTierDraftOps {
    const val DEFAULT_TIER_SPLIT_SPAN_LB = 50.0
    const val DEFAULT_TIER_SPLIT_AT_LB = 100.0
    const val DEFAULT_INCREMENT_LB = 5.0
    /**
     * Default fresh adder-stop row, storage pounds. Owned by micro editing (not
     * derived from tier-band defaults) so band changes can never ripple into
     * micro behavior; matches the smallest common pin stop.
     */
    const val DEFAULT_MICRO_STOP_LB = 5.0

    /** @deprecated Prefer *_LB constants; kept for call-site compatibility. */
    const val DEFAULT_TIER_SPLIT_SPAN = DEFAULT_TIER_SPLIT_SPAN_LB
    const val DEFAULT_TIER_SPLIT_AT = DEFAULT_TIER_SPLIT_AT_LB
    const val DEFAULT_INCREMENT_TEXT = "5"

    /** Default fresh adder-stop row in [displayUnit] (see [DEFAULT_MICRO_STOP_LB]). */
    fun defaultMicroStop(displayUnit: Weight.WeightUnit = Weight.WeightUnit.Lb): String =
        formatAmount(
            WeightConversion.convertAmount(
                DEFAULT_MICRO_STOP_LB,
                Weight.WeightUnit.Lb,
                displayUnit,
            ),
        )

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
     *
     * Samples that land on `base + adder stop` ([microLb], storage pounds) are knob use,
     * not band misses, and are excluded so the adder never trips the warning.
     */
    fun warnedIndexesForSamples(
        drafts: List<IncrementTierDraft>,
        displayUnit: Weight.WeightUnit = Weight.WeightUnit.Lb,
        samplesLb: List<Double>,
        microLb: List<Double> = emptyList(),
    ): Set<Int> {
        if (samplesLb.isEmpty()) return emptySet()
        val tiers = toDomainOrNull(drafts, displayUnit) ?: return emptySet()
        val micro = microLb.map { Weight(it, Weight.WeightUnit.Lb) }
        return tiers.warnedIndexesForSamples(samplesLb, micro)
    }

    /**
     * Converts storage-Lb amounts to display-unit field strings. Shared scalar core
     * for stop drafts (tier drafts keep their struct-shaped mapping in [toDraft]).
     */
    fun lbAmountsToDisplayStrings(
        amounts: List<Double>,
        displayUnit: Weight.WeightUnit = Weight.WeightUnit.Lb,
    ): List<String> =
        amounts.map { amount ->
            formatAmount(
                WeightConversion.convertAmount(amount, Weight.WeightUnit.Lb, displayUnit),
            )
        }

    /**
     * Parses display-unit magnitudes to domain stops stamped as **Lb** for Room
     * persistence. Shared scalar core (tier drafts keep their struct-shaped parsing
     * in [toDomainOrNull]). Empty in → empty out. Null if any value fails to parse.
     */
    fun displayStringsToLbWeights(
        drafts: List<String>,
        displayUnit: Weight.WeightUnit = Weight.WeightUnit.Lb,
    ): List<Weight>? {
        val result = ArrayList<Weight>(drafts.size)
        for (draft in drafts) {
            val display = draft.toDoubleOrNull() ?: return null
            result += Weight(
                WeightConversion.convertAmount(display, displayUnit, Weight.WeightUnit.Lb),
                Weight.WeightUnit.Lb,
            )
        }
        return result
    }

    /**
     * Maps domain adder stops (storage pounds) into draft field strings in [displayUnit].
     */
    fun toMicroDrafts(
        stops: List<Weight>,
        displayUnit: Weight.WeightUnit = Weight.WeightUnit.Lb,
    ): List<String> = lbAmountsToDisplayStrings(stops.map { it.amount }, displayUnit)

    /**
     * Parses stop drafts as [displayUnit] magnitudes and returns domain stops stamped as
     * **Lb** for Room persistence (mirrors [toDomainOrNull]). Empty in → empty out.
     * Null if any stop fails to parse.
     */
    fun microToDomainOrNull(
        stops: List<String>,
        displayUnit: Weight.WeightUnit = Weight.WeightUnit.Lb,
    ): List<Weight>? = displayStringsToLbWeights(stops, displayUnit)

    fun toMicroUserMessage(errors: Set<MicroIncrementsValidationError>): String =
        IncrementValidationMessages.join(errors.map(IncrementValidationMessages::microMessage))

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
        IncrementValidationMessages.join(errors.map(IncrementValidationMessages::tierMessage))

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

/**
 * Single home for increments-editor validation copy. Tier and micro error sets keep
 * their own per-error sentences, but joining lives here so both pipelines read
 * identically. (Plain strings: the state layer carries messages without a Context;
 * resource migration belongs with the MR8/MR9 card wiring if localization lands.)
 */
object IncrementValidationMessages {
    fun tierMessage(error: IncrementTiersValidationError): String =
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

    fun microMessage(error: MicroIncrementsValidationError): String =
        when (error) {
            MicroIncrementsValidationError.MixedUnits ->
                "Adder stops must use the same unit as the bands"
            MicroIncrementsValidationError.NegativeStop ->
                "Adder stops cannot be negative"
            MicroIncrementsValidationError.UnsortedOrDuplicate ->
                "Adder stops must be in order with no duplicates"
            MicroIncrementsValidationError.MeaninglessZero ->
                "A lone 0 stop is meaningless — clear the stops for no adder"
            MicroIncrementsValidationError.ExceedsLargestIncrement ->
                "Each adder stop must fit within the largest band step"
            MicroIncrementsValidationError.MicroWithoutTiers ->
                "Adder stops need bands — add a tier first"
        }

    fun join(messages: List<String>): String = messages.joinToString(separator = ". ")
}
