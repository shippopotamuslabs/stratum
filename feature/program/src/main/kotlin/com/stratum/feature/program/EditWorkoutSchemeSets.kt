package com.stratum.feature.program

import com.stratum.domain.program.ProgressionRepRangePolicy
import com.stratum.domain.program.ProgressionScheme
import com.stratum.feature.program.ui.EditSetKind
import com.stratum.feature.program.ui.EditSetState

/**
 * Feature-layer adapters over [ProgressionRepRangePolicy]. Edit UI models stay out of domain;
 * all scheme/rep-range rules live in the policy object.
 */

/** Whether the rep-range cell is shown/editable for [set] under [scheme]. */
internal fun showsRepRange(scheme: ProgressionScheme, set: EditSetState): Boolean =
    ProgressionRepRangePolicy.storesRepRange(scheme, set.isTopSet)

private fun EditSetState.toRepSpec(): ProgressionRepRangePolicy.WorkingSetRepSpec =
    ProgressionRepRangePolicy.WorkingSetRepSpec(
        repMin = repMin,
        repMax = repMax,
        isTopSet = isTopSet,
    )

/**
 * Rewrites [sets] via [ProgressionRepRangePolicy.normalize] (ids/rest/RIR preserved).
 * Sorts by [EditSetState.setNumber] so top is always the first prescribed set.
 *
 * @param fallbackRepMin optional band used only when [sets] carry no positive range
 *   (e.g. removing the Double top set — pass the removed top's range so it is not lost).
 */
internal fun applyProgressionSchemeToSets(
    sets: List<EditSetState>,
    scheme: ProgressionScheme,
    fallbackRepMin: Int = 0,
    fallbackRepMax: Int = 0,
): List<EditSetState> {
    if (sets.isEmpty()) return sets
    val ordered = sets.sortedBy { it.setNumber }
    val normalized =
        ProgressionRepRangePolicy.normalize(
            scheme = scheme,
            orderedSets = ordered.map { it.toRepSpec() },
            fallbackRepMin = fallbackRepMin,
            fallbackRepMax = fallbackRepMax,
        )
    val withScheme =
        ordered.zip(normalized).map { (set, spec) ->
            set.copy(
                repMin = spec.repMin,
                repMax = spec.repMax,
                isTopSet = spec.isTopSet,
            )
        }
    // Match backoffs stay RIR-only non-top regardless of Double/Linear scheme normalize.
    val shaped =
        withScheme.mapIndexed { index, set ->
            if (isEditMatchBackoff(withScheme, index)) {
                set.copy(
                    isTopSet = false,
                    repMin = 0,
                    repMax = 0,
                    setKind = EditSetKind.MyoRepMatch,
                )
            } else {
                set
            }
        }
    return renumberSets(shaped)
}

/**
 * Applies a user-typed band under [scheme] (Linear fan-out / Double top-only).
 * Ids/rest/RIR preserved; order sorted by [EditSetState.setNumber].
 */
internal fun applyUserRepBand(
    sets: List<EditSetState>,
    scheme: ProgressionScheme,
    repMin: Int,
    repMax: Int,
): List<EditSetState> {
    if (sets.isEmpty()) return sets
    val ordered = sets.sortedBy { it.setNumber }
    val normalized =
        ProgressionRepRangePolicy.applyBand(
            scheme = scheme,
            setCount = ordered.size,
            repMin = repMin,
            repMax = repMax,
        )
    return ordered.zip(normalized).map { (set, spec) ->
        set.copy(
            repMin = spec.repMin,
            repMax = spec.repMax,
            isTopSet = spec.isTopSet,
        )
    }.let(::renumberSets)
}

/** After add/remove, re-assert scheme invariants (optionally keep a removed top's band). */
internal fun normalizeSetsForScheme(
    sets: List<EditSetState>,
    scheme: ProgressionScheme,
    fallbackRepMin: Int = 0,
    fallbackRepMax: Int = 0,
): List<EditSetState> =
    applyProgressionSchemeToSets(
        sets = renumberSets(sets),
        scheme = scheme,
        fallbackRepMin = fallbackRepMin,
        fallbackRepMax = fallbackRepMax,
    )

/**
 * Shell for a newly appended set. Range / [EditSetState.isTopSet] are finalized by
 * [normalizeSetsForScheme] immediately after append.
 */
internal fun newEditSetShell(
    setNumber: Int,
    restSeconds: Int,
): EditSetState =
    defaultEditSetState(setNumber = setNumber, restSeconds = restSeconds)
