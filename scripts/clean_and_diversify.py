#!/usr/bin/env python3
"""
Stage 2a / 3 / 4 — Cleaning, Curation, and Diversity Selection.

Consumes `core/domain-solver/build/candidate_manifest.json` (Stage 1) plus the
solver-emitted `core/domain-solver/build/candidate_metrics.json` (Stage 2b) and:

  Stage 2a (static feasibility filter):
      Prune specs that cannot possibly satisfy Stratum's volume/session budget
      arithmetic *before* spending solver time on them. Pure-python, fast.

  Stage 3 (manual curation review):
      Print a summary table of passing candidates (label, split topology, weekly
      muscle volumes, source) for human sanity-checking.

  Stage 4 (mathematical diversity selection):
      Greedy farthest-first selection on a dissimilarity metric combining:
        - cosine similarity on the 11-dim weekly muscle volume vector
        - Jaccard similarity on the weekly movement-pattern set
      → emits `core/domain-solver/build/benchmark_manifest.json`, the final 50+
      specs that get encoded into ProgramSynthesisAcceptanceTest.kt.

Usage:
    # After harvest_programs.py AND ProgramCandidateAuditTest have both run:
    .venv-bench/bin/python scripts/clean_and_diversify.py [--target N] [--curate-only]
"""

from __future__ import annotations

import argparse
import json
import os
import sys

import numpy as np
from sklearn.metrics.pairwise import cosine_similarity

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BUILD_DIR = os.path.join(REPO_ROOT, "core", "domain-solver", "build")
MANIFEST_PATH = os.path.join(BUILD_DIR, "candidate_manifest.json")
METRICS_PATH = os.path.join(BUILD_DIR, "candidate_metrics.json")
BENCHMARK_PATH = os.path.join(BUILD_DIR, "benchmark_manifest.json")

# Canonical 11-muscle order (must match SCENARIO_MUSCLE_NAMES in
# ProgramGenerationScenarioFixtures.kt and harvest_programs.py MUSCLE_VOCAB).
MUSCLE_VOCAB = [
    "Chest", "Back", "Quad", "Hamstring", "Glute", "Calf",
    "Tricep", "Bicep", "LateralDeltoid", "PosteriorDeltoid", "AnteriorDeltoid",
]

# ─────────────────────────────────────────────────────────────────────────────
# Stratum volume landmarks (Advanced training age — what scenarioPayload uses).
# One global prior applies to all muscles (GlobalVolumePriors.kt).
# ─────────────────────────────────────────────────────────────────────────────
MV_ADVANCED = 6.0     # retentionFloorMv for Advanced
PV = 13.0             # paretoVolumePv (Specialize → RC aim; Maintenance → PV aim)
RC = 20.0             # recoverabilityCeilingRc
MAINT_CEILING = (PV + RC) / 2.0  # 16.5 — hard max for Maintenance
MINUTES_PER_HARD_SET = 3.0
SESSION_MUSCLE_SET_CAP = 11


# ═════════════════════════════════════════════════════════════════════════════
# Stage 2a — Static feasibility filter (pure-python, runs before solving)
# ═════════════════════════════════════════════════════════════════════════════

def static_filter(spec: dict) -> tuple[bool, str]:
    """
    Return (passes, reason). Prunes specs that are obviously infeasible *before*
    spending solver time. Calibrated against the 8 known-passing anchors (which
    specialize ≤3 muscles and run at aim/cap = 1.05–2.09 — sum-of-aims is an
    overbound because secondary credits double-count and not every muscle is hit
    at full aim, so a pure volume-budget cut is too blunt).

    Two checks that DO hold:
      (1) Specialization count ceiling: a 3-day/60-min split can't honor 4+
          specialized muscles (each needs ≥2 sessions × multiple sets); a 3-day
          split maxes at ~2 specialized muscles, 4-day ~3, 5-day ~4, 6-day ~5.
      (2) Per-session ceiling: a specialized muscle (RC=20) needs
          ceil(20 / SESSION_MUSCLE_SET_CAP=11) = 2 sessions to carry its volume,
          so any single-muscle specialization needs ≥ 2 training days.
    """
    days = spec["days"]
    specialization = spec["specialization"]
    specialized = {m for m in specialization if specialization[m] == "Specialize"}

    # (1) Specialization-count ceiling by training-day budget.
    spec_count_ceiling = {3: 3, 4: 4, 5: 5, 6: 5}
    if len(specialized) > spec_count_ceiling[days]:
        return False, f"over_specialized: {len(specialized)} muscles > {spec_count_ceiling[days]} for {days}d"

    # (2) Per-muscle per-session ceiling (only triggers for <2-day, which can't
    #     happen given VALID_DAYS=[3,4,5,6], but kept defensively).
    for m in specialized:
        sessions_needed = -(-int(RC) // SESSION_MUSCLE_SET_CAP)  # ceil
        if days < sessions_needed:
            return False, f"{m}_cap_infeasible: needs {sessions_needed} sessions, has {days}"

    return True, ""


def run_static_filter(candidates: list[dict]) -> tuple[list[dict], list[dict]]:
    passed, pruned = [], []
    for c in candidates:
        ok, reason = static_filter(c)
        if ok:
            passed.append(c)
        else:
            c_pruned = dict(c)
            c_pruned["_prune_reason"] = reason
            pruned.append(c_pruned)
    return passed, pruned


# ═════════════════════════════════════════════════════════════════════════════
# Stage 3 — Curation summary
# ═════════════════════════════════════════════════════════════════════════════

def print_curation_table(candidates: list[dict], metrics: dict | None) -> None:
    """Print a human-reviewable summary of solver-passing candidates."""
    print(f"\n{'='*120}")
    print(f"STAGE 3 — Curation review: {len(candidates)} solver-passing candidates")
    print(f"{'='*120}")
    metrics_by_label = {m["label"]: m for m in (metrics or {}).get("candidates", [])} if metrics else {}
    hdr = f"{'Label':<46} {'src':<5} {'d':<2} {'layout':<10} {'min':<3} {'specialization':<32} vol_vec[11]"
    print(hdr)
    print("-" * 120)
    for c in candidates:
        spec_str = ",".join(sorted(c["specialization"].keys())) or "(maintenance)"
        m = metrics_by_label.get(c["label"], {})
        vol_vec = m.get("weekly_volume_vector")
        vol_str = ""
        if vol_vec:
            # Compact: show non-zero muscles
            nz = [f"{muscle[:3]}={v:.0f}" for muscle, v in zip(MUSCLE_VOCAB, vol_vec) if v > 0.5]
            vol_str = " ".join(nz[:8])
        print(f"{c['label']:<46} {c['source']:<5} {c['days']:<2} {c['layout']:<10} {c['session_length']:<3} {spec_str:<32} {vol_str}")
    # Source breakdown of passing set
    from collections import Counter
    by_src = Counter(c["source"] for c in candidates)
    by_days = Counter(c["days"] for c in candidates)
    print(f"\nPassing by source: {dict(by_src)}")
    print(f"Passing by days:   {dict(sorted(by_days.items()))}")


# ═════════════════════════════════════════════════════════════════════════════
# Stage 4 — Mathematical diversity selection
# ═════════════════════════════════════════════════════════════════════════════

def dissimilarity(vec_a, set_a, vec_b, set_b) -> float:
    """
    Dissimilarity in [0, 2]. Combines:
      - volume:    1 - cosine_similarity(11-dim weekly volume vectors)
      - movement:  1 - jaccard(weekly movement-pattern sets)
    Weighted 50/50. Cosine on volume captures "how the load is distributed across
    muscles"; Jaccard on patterns captures "which movement families appear at all".
    """
    va, vb = np.array(vec_a).reshape(1, -1), np.array(vec_b).reshape(1, -1)
    # Guard against zero-vectors (cosine is undefined); fall back to 0 similarity.
    if va.sum() == 0 or vb.sum() == 0:
        vol_sim = 0.0
    else:
        vol_sim = float(cosine_similarity(va, vb)[0, 0])
    union = set_a | set_b
    mov_sim = len(set_a & set_b) / len(union) if union else 0.0
    return 1.0 - 0.5 * vol_sim - 0.5 * mov_sim


def farthest_first_select(passing: list[dict], metrics_by_label: dict, target: int) -> list[dict]:
    """
    Greedy farthest-first traversal. Seed with the 8 ANCHOR labels (so the
    diversity-selected set always contains the current baseline Benchmark_A–H),
    then iteratively add the candidate maximizing min-distance to the selected set.
    """
    # Only consider candidates that have metrics (solver-validated) AND passed.
    eligible = [c for c in passing
                if c["label"] in metrics_by_label and metrics_by_label[c["label"]].get("passed")]
    if not eligible:
        print("ERROR: no eligible candidates with passing metrics. Run ProgramCandidateAuditTest first.", file=sys.stderr)
        return []

    # Pre-extract feature vectors.
    features = {}
    for c in eligible:
        m = metrics_by_label[c["label"]]
        features[c["label"]] = (
            m["weekly_volume_vector"],
            set(m["movement_pattern_set"]),
        )

    selected = []
    selected_labels = set()

    # Seed: all anchor labels present in eligible.
    anchor_labels = [c["label"] for c in eligible if c["source"] == "anchor"]
    for label in anchor_labels:
        c = next(x for x in eligible if x["label"] == label)
        selected.append(c)
        selected_labels.add(label)

    # If fewer anchors than expected, seed with the highest-volume non-anchor to bootstrap.
    if not selected:
        selected.append(eligible[0])
        selected_labels.add(eligible[0]["label"])

    # Greedy farthest-first.
    while len(selected) < target:
        best_c, best_min_dist = None, -1.0
        for c in eligible:
            if c["label"] in selected_labels:
                continue
            vec_c, set_c = features[c["label"]]
            min_dist = min(
                dissimilarity(vec_c, set_c, features[s["label"]][0], features[s["label"]][1])
                for s in selected
            )
            if min_dist > best_min_dist:
                best_min_dist = min_dist
                best_c = c
        if best_c is None:
            break  # ran out of eligible candidates
        selected.append(best_c)
        selected_labels.add(best_c["label"])

    # Annotate with the diversity score (min-distance to the rest of the selected set).
    annotated = []
    for i, c in enumerate(selected):
        vec_c, set_c = features[c["label"]]
        others = [s for j, s in enumerate(selected) if j != i]
        if others:
            min_d = min(dissimilarity(vec_c, set_c, features[s["label"]][0], features[s["label"]][1]) for s in others)
        else:
            min_d = 0.0
        annotated.append({**c, "_diversity_score": round(min_d, 4), "_selection_order": i})
    return annotated


# ═════════════════════════════════════════════════════════════════════════════
# Main
# ═════════════════════════════════════════════════════════════════════════════

def downsample_synth(candidates: list[dict], cap: int) -> list[dict]:
    """
    Keep all anchor/web/xlsx candidates; stratified-sample `cap` synthetics across
    the (days, specialization-size) strata so the solver audit stays tractable
    (~120 candidates) while preserving coverage of every (days, preset, layout,
    session) cell. Deterministic (sorted sampling, no RNG).
    """
    real = [c for c in candidates if c["source"] != "synthetic"]
    synth = [c for c in candidates if c["source"] == "synthetic"]
    if len(synth) <= cap:
        return candidates
    # Stratify by (days, n_specialized); within each stratum, prefer the 75-min
    # variant, then rotate through layouts/presets for spread.
    by_strata: dict[tuple, list[dict]] = {}
    for c in synth:
        key = (c["days"], len(c["specialization"]))
        by_strata.setdefault(key, []).append(c)
    # Sort within stratum: 75min first, then by label for determinism.
    for k in by_strata:
        by_strata[k].sort(key=lambda c: (0 if c["session_length"] == 75 else 1, c["label"]))
    # Round-robin pull from each stratum until we hit cap.
    out = []
    strata_keys = sorted(by_strata.keys())
    idxs = {k: 0 for k in strata_keys}
    while len(out) < cap:
        progressed = False
        for k in strata_keys:
            if idxs[k] < len(by_strata[k]):
                out.append(by_strata[k][idxs[k]])
                idxs[k] += 1
                progressed = True
                if len(out) >= cap:
                    break
        if not progressed:
            break
    return real + out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--target", type=int, default=56, help="Target benchmark count (default 56)")
    ap.add_argument("--curate-only", action="store_true", help="Skip diversity selection (Stage 3 only)")
    ap.add_argument("--no-static-filter", action="store_true", help="Skip Stage 2a static filter")
    ap.add_argument("--cap-synth", type=int, default=110,
                    help="Cap synthetic candidates fed to the solver audit (default 110); keeps all real candidates")
    ap.add_argument("--emit-audit-manifest", action="store_true",
                    help="Write the downsampled static-passing set back to candidate_manifest.json "
                         "(in place of the full harvest) so ProgramCandidateAuditTest runs on a tractable subset. "
                         "Run BEFORE the gradle audit. The full harvest is preserved as candidate_manifest.full.json.")
    args = ap.parse_args()

    if not os.path.exists(MANIFEST_PATH):
        sys.exit(f"Missing {MANIFEST_PATH} — run harvest_programs.py first.")
    with open(MANIFEST_PATH) as f:
        manifest = json.load(f)
    candidates = manifest["candidates"]
    candidates = downsample_synth(candidates, args.cap_synth)

    # ── Optionally emit the downsampled set as the audit input ──
    if args.emit_audit_manifest:
        import shutil
        if not os.path.exists(MANIFEST_PATH + ".full.json"):
            shutil.copy(MANIFEST_PATH, MANIFEST_PATH + ".full.json")
        out = {"candidates": candidates, "muscle_vocab": manifest.get("muscle_vocab", MUSCLE_VOCAB)}
        with open(MANIFEST_PATH, "w") as f:
            json.dump(out, f, indent=2)
        print(f"Emitted downsampled audit manifest ({len(candidates)} candidates) → {MANIFEST_PATH}")
        print(f"(Full {len(manifest['candidates'])}-candidate harvest preserved at {MANIFEST_PATH}.full.json)")
        return

    # ── Stage 2a: static filter ──
    if args.no_static_filter:
        passing_static, pruned = candidates, []
    else:
        passing_static, pruned = run_static_filter(candidates)
    print(f"\nStage 2a (static filter): {len(candidates)} → {len(passing_static)} passed, {len(pruned)} pruned")
    if pruned:
        from collections import Counter
        reasons = Counter(p["_prune_reason"].split(":")[0] for p in pruned)
        print(f"  Prune reasons: {dict(reasons)}")

    # ── Load solver metrics (Stage 2b output), if present ──
    metrics = None
    if os.path.exists(METRICS_PATH):
        with open(METRICS_PATH) as f:
            metrics = json.load(f)
        solver_passed = [c for c in passing_static
                         if any(m["label"] == c["label"] and m.get("passed") for m in metrics.get("candidates", []))]
        print(f"Stage 2b (solver audit): {len(solver_passed)}/{len(passing_static)} also passed solver validation")
        passing_for_curation = solver_passed
    else:
        print(f"\nStage 2b (solver audit): {METRICS_PATH} not found — curation will use static-passing set only.")
        print("(Run ProgramCandidateAuditTest via gradle to produce solver-validated metrics.)")
        passing_for_curation = passing_static

    # ── Stage 3: curation summary ──
    print_curation_table(passing_for_curation, metrics)

    if args.curate_only:
        print("\n--curate-only: stopping after Stage 3.")
        return

    # ── Stage 4: diversity selection ──
    if metrics is None:
        sys.exit("\nCannot run Stage 4 (diversity) without solver metrics. Run ProgramCandidateAuditTest first.")

    metrics_by_label = {m["label"]: m for m in metrics.get("candidates", [])}
    selected = farthest_first_select(passing_for_curation, metrics_by_label, args.target)
    if not selected:
        sys.exit("Diversity selection produced no candidates.")

    out = {"benchmarks": selected, "muscle_vocab": MUSCLE_VOCAB, "target": args.target}
    with open(BENCHMARK_PATH, "w") as f:
        json.dump(out, f, indent=2)

    from collections import Counter
    sel_src = Counter(c["source"] for c in selected)
    sel_days = Counter(c["days"] for c in selected)
    print(f"\n{'='*80}")
    print(f"Stage 4 (diversity): selected {len(selected)} benchmarks → {BENCHMARK_PATH}")
    print(f"{'='*80}")
    print(f"By source: {dict(sel_src)}")
    print(f"By days:   {dict(sorted(sel_days.items()))}")
    print(f"\nSelection (in farthest-first order):")
    for c in selected:
        spec_str = ",".join(sorted(c["specialization"].keys())) or "(maint)"
        print(f"  [{c['_selection_order']:2}] d={c['days']} {c['layout']:<10} {c['session_length']}min  {spec_str:<28}  div={c['_diversity_score']:.3f}  {c['label']}")


if __name__ == "__main__":
    main()
