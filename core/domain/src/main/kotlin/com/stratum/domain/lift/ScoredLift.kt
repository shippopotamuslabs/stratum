package com.stratum.domain.lift

import com.stratum.domain.lift.Lift

data class ScoredLift(
    val lift: Lift,
    val similarity: Double,
)

/**
 * Ranks the user's *enabled* catalog by biomechanical similarity to a target lift — the
 * deterministic engine behind the swap sheet's "smart display" (AGENTS.md §4.6). The target
 * itself is dropped from the result; disabled candidates never appear.
 *
 * The **target** may itself be recommendation-disabled. Similarity still uses its catalog
 * metadata so the user can swap a non-recommended lift for a close enabled match. Only
 * candidate rows require `enabled = true`.
 *
 * Hot stream: re-emits whenever the **enabled** catalog changes. Target metadata is resolved
 * from the enabled stream when present, otherwise via a bounded [LiftRepository.getLiftsByIds]
 * lookup (DATA_LAYER_RULES.md §5 — avoid loading the full catalog only to drop disabled rows).
 *
 * If the target id is not in the catalog at all, the stream still emits — every enabled lift
 * is scored against `null`, so all candidates land at the same baseline and sort by `mainstream`
 * then name. Callers typically open the sheet only with a known-good target id.
 */
