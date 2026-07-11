package com.stratum.domain.program

/**
 * Canonical scheme-aware rep-range / top-set conventions for working sets
 * (`docs/onboarding-vertical-slices.md` §2.3).
 *
 * | Scheme | Who stores a rep range | `isTopSet` |
 * |--------|------------------------|------------|
 * | **Double** | Top set only; others `0/0` (RIR-only) | Exactly one — first working set after normalize |
 * | **Linear** | Every working set (shared band) | First set is the load-progression anchor |
 *
 * Stored positive bands are clamped to at least [MIN_STORED_REP_MIN] (same value as the
 * live recommended-rep floor). Double backoffs remain `0/0` (RIR-only).
 *
 * Call sites (program generation, template import, edit-workout save/toggle) must go through
 * these helpers so the conventions cannot drift.
 *
 * **Three intents:**
 * - [generate] — cold construction with a planned band
 * - [normalize] — heal shape from existing sets (does **not** invent 8–12 when no band exists)
 * - [applyBand] — user typed a band; rewrite scheme shape from that explicit band
 */
object ProgressionRepRangePolicy {

    const val DEFAULT_REP_MIN: Int = 8
    const val DEFAULT_REP_MAX: Int = 12

    /**
     * Global floor for any **stored** positive rep band (top-set / Linear shared range).
     * Aligns with [com.stratum.domain.workout.BACKOFF_REP_FLOOR]: templates and generation
     * never prescribe a top-of-range floor below 5. Double backoffs stay `0/0` (RIR-only).
     */
    const val MIN_STORED_REP_MIN: Int = 5

    /**
     * Minimal set-shape input for [normalize] / [applyBand]. Callers map their own set
     * models into this and map the result back — domain stays free of UI/draft types.
     */
    data class WorkingSetRepSpec(
        val repMin: Int,
        val repMax: Int,
        val isTopSet: Boolean,
    )

    /**
     * Whether a set with [isTopSet] under [scheme] is allowed to show/store a rep range.
     * Double backoffs are RIR-only; Linear shows the shared band on every set.
     */
    fun storesRepRange(scheme: ProgressionScheme, isTopSet: Boolean): Boolean =
        when (scheme) {
            ProgressionScheme.Linear -> true
            ProgressionScheme.Double -> isTopSet
        }

    /**
     * Single-set range clamp: Double non-top → `0/0`; otherwise keep a coherent band
     * (`max ≥ min`, non-negative, and positive mins floored at [MIN_STORED_REP_MIN]).
     */
    fun normalizeRange(
        scheme: ProgressionScheme,
        isTopSet: Boolean,
        repMin: Int,
        repMax: Int,
    ): Pair<Int, Int> {
        if (!storesRepRange(scheme, isTopSet)) return 0 to 0
        if (repMin <= 0 && repMax <= 0) return 0 to 0
        val min = repMin.coerceAtLeast(0)
        val max = when {
            repMax <= 0 -> min
            else -> repMax.coerceAtLeast(min)
        }
        // A max-only entry (min was 0) becomes min=max so the band is usable.
        val resolvedMin = if (min <= 0 && max > 0) max else min
        if (resolvedMin <= 0) return 0 to 0
        val flooredMin = resolvedMin.coerceAtLeast(MIN_STORED_REP_MIN)
        val flooredMax = max.coerceAtLeast(flooredMin)
        return flooredMin to flooredMax
    }

    private fun hasPositiveBand(spec: WorkingSetRepSpec): Boolean =
        spec.repMin > 0 || spec.repMax > 0

    /**
     * Resolves the shared / top-set band from existing specs.
     *
     * Prefers an explicit top set with a positive range, then any set with a range.
     * When nothing is found:
     * - if [fallbackRepMin] / [fallbackRepMax] are both positive → use them (generation / new set)
     * - otherwise → `0 to 0` (do **not** invent defaults during heal/save)
     */
    fun resolveSharedBand(
        sets: List<WorkingSetRepSpec>,
        fallbackRepMin: Int = 0,
        fallbackRepMax: Int = 0,
    ): Pair<Int, Int> {
        val fromTop =
            sets.firstOrNull { it.isTopSet && hasPositiveBand(it) }
                ?: sets.firstOrNull { hasPositiveBand(it) }
        if (fromTop != null) {
            return normalizeRange(
                scheme = ProgressionScheme.Linear, // force keep band shape
                isTopSet = true,
                repMin = fromTop.repMin,
                repMax = fromTop.repMax,
            )
        }
        if (fallbackRepMin > 0 || fallbackRepMax > 0) {
            return normalizeRange(
                scheme = ProgressionScheme.Linear,
                isTopSet = true,
                repMin = fallbackRepMin,
                repMax = fallbackRepMax,
            )
        }
        return 0 to 0
    }

    /**
     * Rewrites position-ordered working sets to match [scheme] (ids/rest/RIR are caller-owned).
     *
     * - **Linear:** copy the shared band onto every set; index 0 is the progression anchor.
     * - **Double:** index 0 is the sole top set and owns the band; remaining sets are `0/0`.
     *
     * Does not invent a default band when [orderedSets] carry no positive range (and no
     * positive [fallbackRepMin]/[fallbackRepMax]). Empty input is returned unchanged.
     */
    fun normalize(
        scheme: ProgressionScheme,
        orderedSets: List<WorkingSetRepSpec>,
        fallbackRepMin: Int = 0,
        fallbackRepMax: Int = 0,
    ): List<WorkingSetRepSpec> {
        if (orderedSets.isEmpty()) return orderedSets
        val (repMin, repMax) = resolveSharedBand(orderedSets, fallbackRepMin, fallbackRepMax)
        return applyBandInternal(scheme, orderedSets.size, repMin, repMax)
    }

    /**
     * User-authored band: apply [repMin]/[repMax] under [scheme] to a list of the given size,
     * ignoring prior set ranges. Use for edit-workout `setReps` so Linear non-top edits are not
     * discarded by top-preferring [resolveSharedBand].
     */
    fun applyBand(
        scheme: ProgressionScheme,
        setCount: Int,
        repMin: Int,
        repMax: Int,
    ): List<WorkingSetRepSpec> {
        val count = setCount.coerceAtLeast(0)
        if (count == 0) return emptyList()
        val (min, max) =
            normalizeRange(
                scheme = ProgressionScheme.Linear,
                isTopSet = true,
                repMin = repMin,
                repMax = repMax,
            )
        return applyBandInternal(scheme, count, min, max)
    }

    /**
     * Builds a fresh ordered list of [setCount] specs for generation from a planned band.
     */
    fun generate(
        scheme: ProgressionScheme,
        setCount: Int,
        plannedRepMin: Int,
        plannedRepMax: Int,
    ): List<WorkingSetRepSpec> {
        val count = setCount.coerceAtLeast(0)
        if (count == 0) return emptyList()
        val (min, max) =
            normalizeRange(
                scheme = ProgressionScheme.Linear,
                isTopSet = true,
                repMin = plannedRepMin,
                repMax = plannedRepMax,
            ).let { (a, b) ->
                // Generation always needs a positive planned band; fall back to product defaults
                // only if the planner returned zeros.
                if (a > 0) a to b else DEFAULT_REP_MIN to DEFAULT_REP_MAX
            }
        return applyBandInternal(scheme, count, min, max)
    }

    private fun applyBandInternal(
        scheme: ProgressionScheme,
        setCount: Int,
        repMin: Int,
        repMax: Int,
    ): List<WorkingSetRepSpec> =
        List(setCount) { index ->
            val isTopSet = index == 0
            val (min, max) = normalizeRange(scheme, isTopSet, repMin, repMax)
            WorkingSetRepSpec(repMin = min, repMax = max, isTopSet = isTopSet)
        }
}
