package com.stratum.liftconfig

/**
 * Band-strip tiling math (STR-58): starts are derived, never edited, and the
 * last band is always open-ended. Single home for the start-derivation rule so
 * band edits, adds, and removes cannot drift apart. Operates on display-unit
 * draft strings; domain conversion stays in [IncrementTierDraftOps].
 */
object IncrementBandTiling {
    /**
     * Derived start of band [index]: `"0"` for the first band, otherwise the
     * previous band's end. Empty when the previous end is blank.
     */
    fun startAt(tiers: List<IncrementTierDraft>, index: Int): String {
        if (index <= 0) return "0"
        return tiers.getOrNull(index - 1)?.upperBound.orEmpty()
    }

    /**
     * Replaces band [index]'s end with [newEnd] and re-tiles: every later band's
     * lower follows its predecessor's end, and the last band stays open-ended.
     * Edits to the last band's end are ignored (it has no end). Blank [newEnd]
     * keeps the current end so a cleared field never silently collapses a band.
     */
    fun retileAfterEndEdit(
        tiers: List<IncrementTierDraft>,
        index: Int,
        newEnd: String,
    ): List<IncrementTierDraft> {
        if (index !in tiers.indices) return tiers
        val filtered = IncrementTierDraftOps.filterNumeric(newEnd)
        val withEnd =
            tiers.mapIndexed { i, tier ->
                if (i == index && i != tiers.lastIndex && filtered.isNotBlank()) {
                    tier.copy(upperBound = filtered)
                } else {
                    tier
                }
            }
        return retiled(withEnd)
    }

    /**
     * Re-derives every lower bound from [startAt] (first band starts at 0) and
     * forces the last band open-ended. Bands with a blank predecessor end keep
     * their current lower so a half-typed edit never wipes a field; validation
     * still flags the resulting gap.
     */
    fun retiled(tiers: List<IncrementTierDraft>): List<IncrementTierDraft> =
        tiers.mapIndexed { i, tier ->
            val lower = startAt(tiers, i).ifBlank { tier.lowerBound }
            if (i == tiers.lastIndex) {
                tier.copy(lowerBound = lower, openEnded = true, upperBound = null)
            } else {
                tier.copy(lowerBound = lower)
            }
        }
}
