package com.stratum.liftconfig

/**
 * Band-strip tiling math (STR-58, STR-101): the first band's start is stored
 * state — user-editable, read via [firstBandStartOrZero] — while every later
 * band's start is derived from the previous band's end via [startAt], and the
 * last band is always open-ended. The two are deliberately separate functions
 * so callers can tell editable state from derived state at a glance.
 * Operates on display-unit draft strings; domain conversion stays in
 * [IncrementTierDraftOps].
 */
object IncrementBandTiling {
    /**
     * Stored start of the first band: its own lower bound (`"0"` when blank,
     * so existing configs read as unchanged). Editable state — the first
     * band has no predecessor to tile from.
     */
    fun firstBandStartOrZero(tiers: List<IncrementTierDraft>): String =
        tiers.getOrNull(0)?.lowerBound.orEmpty().ifBlank { "0" }

    /**
     * Derived start of band [index]: the previous band's end. Empty when the
     * previous end is blank. Requires [index] > 0 — band 0's start is stored
     * state, see [firstBandStartOrZero]; there is no predecessor to derive
     * from, so deriving would silently fabricate a value.
     */
    fun startAt(tiers: List<IncrementTierDraft>, index: Int): String {
        require(index > 0) { "Band 0's start is stored state; use firstBandStartOrZero" }
        return tiers.getOrNull(index - 1)?.upperBound.orEmpty()
    }

    /**
     * Replaces band [index]'s end with [newEnd] and re-tiles: every later band's
     * lower follows its predecessor's end, and the last band stays open-ended.
     * Edits to the last band's end are ignored (it has no end). Blank [newEnd]
     * keeps the current end so a cleared field never silently collapses a band.
     * (STR-102: a later band's start edit routes here — the start is a derived
     * value, so a cleared start snaps back by design.)
     * (STR-103: the sheet holds half-typed text locally and only commits valid
     * ends here, so the draft never sees a blank or non-numeric end.)
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
     * Field-level validity for the sheet's Ends-at candidate (STR-103): a blank
     * or non-numeric end is invalid and renders the red error border. The last
     * band has no end (its field is disabled), so any candidate there is valid;
     * an out-of-bounds index is never invalid. The open-ended-last-band rule
     * lives here so callers never recompute it.
     */
    fun List<IncrementTierDraft>.isEndInvalid(index: Int, end: String?): Boolean {
        if (index !in indices || index == lastIndex) return false
        return end.isNullOrBlank() || end.toDoubleOrNull() == null
    }

    /**
     * Re-derives lower bounds: the first band keeps its own start; every
     * later band follows its predecessor's end. Forces the last band
     * open-ended. A band with a blank predecessor end keeps its current lower
     * as a defensive fallback (edits can never introduce a blank end —
     * [retileAfterEndEdit] ignores them — but stored drafts predate the
     * invariant).
     */
    fun retiled(tiers: List<IncrementTierDraft>): List<IncrementTierDraft> =
        tiers.mapIndexed { i, tier ->
            val lower =
                if (i == 0) {
                    firstBandStartOrZero(tiers)
                } else {
                    startAt(tiers, i).ifBlank { tier.lowerBound }
                }
            if (i == tiers.lastIndex) {
                tier.copy(lowerBound = lower, openEnded = true, upperBound = null)
            } else {
                tier.copy(lowerBound = lower)
            }
        }
}
