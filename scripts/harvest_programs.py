#!/usr/bin/env python3
"""
Stage 1 — Program Harvesting.

Produces `core/domain-solver/build/candidate_manifest.json`: a list of candidate
benchmark specs (no solving yet — offline/fast) drawn from three sources:

  (a) ANCHORS  — the 8 existing Benchmark_A–H contexts (verbatim specs) so the
                  diversity selector seeds with the current anti-overfit baseline.
  (b) WEB      — canonical published splits (PHUL, PPL, nSuns, SBS, 5/3/1, Madcow,
                  Nuckols, Mountain Dog, etc.). Signatures sourced from Boostcamp,
                  LiftVault, the r/weightroom wiki, and each program's official page.
  (c) XLSX     — splits mined from sample_programs/*.xlsx (Jeff Nippard Powerbuilding,
                  Pure Bodybuilding Upper/Lower, SBS Hypertrophy 2x–6x frequency variants).
  (d) SYNTHETIC — combinatorial cross-product of days × specialization presets ×
                  weekday layouts × session lengths, plus curated multi-muscle
                  specialization clusters.

Each candidate carries the exact axes a `ProgramGenerationContext` varies on:
  days (3-6), weekdayLayout, sessionLengthMinutes, specialization (Muscle → "Specialize").

The manifest is consumed by:
  - `ProgramCandidateAuditTest.kt` (Stage 2b solver cleaning)
  - `clean_and_diversify.py`        (Stage 2a static filter + Stage 4 selection)

Usage:
    .venv-bench/bin/python scripts/harvest_programs.py
"""

from __future__ import annotations

import json
import os
from collections import OrderedDict

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BUILD_DIR = os.path.join(REPO_ROOT, "core", "domain-solver", "build")
MANIFEST_PATH = os.path.join(BUILD_DIR, "candidate_manifest.json")

# The 11 muscles Stratum tracks volume against (SCENARIO_MUSCLE_NAMES order — must match
# ProgramGenerationScenarioFixtures.kt so the 11-dim volume vector is canonical).
MUSCLE_VOCAB = [
    "Chest", "Back", "Quad", "Hamstring", "Glute", "Calf",
    "Tricep", "Bicep", "LateralDeltoid", "PosteriorDeltoid", "AnteriorDeltoid",
]

VALID_LAYOUTS = ["sequential", "mwf", "spread5", "mtwfs", "sean5", "weird4"]
VALID_DAYS = [3, 4, 5, 6]
VALID_SESSION_LENGTHS = [60, 75, 90]

SPECIALIZATION_PRESETS = {
    "maintenance": {},
    "arms":        {"Bicep": "Specialize", "Tricep": "Specialize"},
    "chest":       {"Chest": "Specialize"},
    "back":        {"Back": "Specialize"},
    "glutes":      {"Glute": "Specialize"},
    "shoulders":   {"LateralDeltoid": "Specialize", "AnteriorDeltoid": "Specialize"},
    "legs":        {"Quad": "Specialize", "Glute": "Specialize"},
    "calves":      {"Calf": "Specialize"},
    "triceps":     {"Tricep": "Specialize"},
    "biceps":      {"Bicep": "Specialize"},
    "lateral":     {"LateralDeltoid": "Specialize"},
    "posterior":   {"PosteriorDeltoid": "Specialize"},
    "hamstrings":  {"Hamstring": "Specialize"},
}


def spec(label, source, days, layout, specialization, session_length=75, session_focus=""):
    """Build a single candidate spec dict, validating against the fixed vocabularies."""
    assert days in VALID_DAYS, f"{label}: days={days} not in {VALID_DAYS}"
    assert layout in VALID_LAYOUTS, f"{label}: layout={layout!r} not in {VALID_LAYOUTS}"
    assert session_length in VALID_SESSION_LENGTHS, f"{label}: session_length={session_length}"
    for m, lvl in specialization.items():
        assert m in MUSCLE_VOCAB, f"{label}: unknown muscle {m!r}"
        assert lvl == "Specialize", f"{label}: only 'Specialize' supported, got {lvl!r} for {m}"
    return OrderedDict([
        ("label", label),
        ("source", source),
        ("days", days),
        ("layout", layout),
        ("session_length", session_length),
        ("specialization", OrderedDict(sorted(specialization.items()))),
        ("session_focus", session_focus),
    ])


# ─────────────────────────────────────────────────────────────────────────────
# (a) ANCHORS — the 8 existing Benchmark_A–H (verbatim). Mined from
#     ProgramSynthesisAcceptanceTest.kt benchmarkContextB..H + richArmCalfCatalog.
#     These seed the diversity selector so the new weights can't regress the
#     current anti-overfit baseline.
# ─────────────────────────────────────────────────────────────────────────────
ANCHORS = [
    spec("Benchmark_A_ArmCalf5Day",   "anchor", 5, "mtwfs",     {"Calf": "Specialize", "Bicep": "Specialize", "Tricep": "Specialize"}),
    spec("Benchmark_B_FullBody4Day",  "anchor", 4, "spread5",   {}),
    spec("Benchmark_C_ULPPL5Day",     "anchor", 5, "spread5",   {}),
    spec("Benchmark_D_ArmTricepSpec5Day60Min", "anchor", 5, "mtwfs", {"Bicep": "Specialize", "Tricep": "Specialize"}, session_length=60),
    spec("Benchmark_E_ChestBackSpec3Day75Min", "anchor", 3, "mwf",   {"Chest": "Specialize", "Back": "Specialize"}),
    spec("Benchmark_F_QuadHamSpec4Day75Min",   "anchor", 4, "spread5", {"Quad": "Specialize", "Hamstring": "Specialize"}),
    spec("Benchmark_G_ShoulderSpec5Day75Min",  "anchor", 5, "mtwfs", {"LateralDeltoid": "Specialize", "PosteriorDeltoid": "Specialize"}),
    spec("Benchmark_H_ChestTricepSpec6Day75Min","anchor", 6, "sequential", {"Chest": "Specialize", "Tricep": "Specialize"}),
]

# ─────────────────────────────────────────────────────────────────────────────
# (b) WEB — canonical published splits. Source signatures researched from
#     Boostcamp / LiftVault / r/weightroom wiki / official program pages.
#     `specialize` reflects the program's implicit emphasis (empty = balanced).
#     `session_focus` is metadata for human review (Stage 3 curation).
# ─────────────────────────────────────────────────────────────────────────────
WEB_SPLITS = [
    # PHUL — 4-day power/hypertrophy upper/lower (Muscle&Strength)
    spec("PHUL_4day",            "web", 4, "sequential", {}, session_focus="Upper;Lower;Upper;Lower"),
    # PHAT — Layne Norton 5-day power/hypertrophy (SimplyShredded)
    spec("PHAT_5day",            "web", 5, "spread5",    {}, session_focus="UpperPower;LowerPower;Back/Shoulders;LowerHypertrophy;Chest/Arms"),
    # PPL — 6-day and 3-day variants
    spec("PPL_6day",             "web", 6, "sequential", {}, session_focus="Push;Pull;Legs;Push;Pull;Legs"),
    spec("PPL_3day",             "web", 3, "mwf",        {}, session_focus="Push;Pull;Legs"),
    # nSuns — CAP3 (6-day) and LP (5-day)
    spec("nSuns_CAP3_6day",      "web", 6, "sequential", {}, session_focus="Chest/Squat;Deadlift/Back;OHP/Squat;Chest/Ham;OHP/Squat;Deadlift/Back"),
    spec("nSuns_LP_5day",        "web", 5, "sequential", {}, session_focus="Bench/OHP;Squat/RDL;Bench/Incline;Deadlift/FrontSquat;Chest/Tricep"),
    # Upper/Lower 4-day
    spec("UpperLower_4day",      "web", 4, "sequential", {}, session_focus="Upper;Lower;Upper;Lower"),
    # 5/3/1 Boring But Big (Wendler) — 4-day
    spec("531_BBB_4day",         "web", 4, "sequential", {}, session_focus="OHP;Deadlift;Bench;Squat"),
    # Madcow 5x5 — 3-day full body linear
    spec("Madcow_5x5_3day",      "web", 3, "mwf",        {}, session_focus="Volume;Light;Heavy"),
    # Greyskull LP — 3-day full body
    spec("Greyskull_LP_3day",    "web", 3, "mwf",        {}, session_focus="FullBody A;FullBody B;FullBody A"),
    # Texas Method — 3-day full body
    spec("TexasMethod_3day",     "web", 3, "mwf",        {}, session_focus="Volume;Light;Heavy"),
    # Starting Strength / Metallicadpa — 3-day full body
    spec("FullBody_3day_SS",     "web", 3, "mwf",        {}, session_focus="FullBody A;FullBody B;FullBody A"),
    # Nuckols 28 — beginner 4-day and intermediate 5-day combined templates
    spec("Nuckols_Beginner_4day","web", 4, "sequential", {}, session_focus="Squat;Bench;Deadlift;Squat/Bench"),
    spec("Nuckols_Intermediate_5day","web", 5, "sequential", {}, session_focus="Squat;Bench;Deadlift;Squat;Bench/OHP"),
    # Bro split — 5-day, 1 muscle/day (implicitly bodybuilding-focused)
    spec("BroSplit_5day",        "web", 5, "sequential", {"Chest": "Specialize", "Back": "Specialize", "Bicep": "Specialize", "Tricep": "Specialize"}, session_focus="Chest;Back;Legs;Shoulders;Arms"),
    # DUP full body 3-day
    spec("DUP_FullBody_3day",    "web", 3, "mwf",        {}, session_focus="Hypertrophy;Strength;Power"),
    # PPL + Upper/Lower hybrid — 5-day
    spec("PPLUL_Hybrid_5day",    "web", 5, "spread5",    {}, session_focus="Upper;Lower;Push;Pull;Legs"),
    # Smolov base/junior — 4-day squat specialization
    spec("Smolov_Base_4day",     "web", 4, "sequential", {"Quad": "Specialize", "Glute": "Specialize", "Hamstring": "Specialize"}, session_focus="Squat;Squat;Squat;Squat"),
    # Mountain Dog (John Meadows) — 5-day bodybuilding
    spec("MountainDog_5day",     "web", 5, "sequential", {}, session_focus="Chest/Tri;Back/Bi/RearDelt;Quads/Calves;Shoulders/Tris;Hams/Glutes/Back"),
]

# ─────────────────────────────────────────────────────────────────────────────
# (c) XLSX — splits mined from sample_programs/*.xlsx
#       Jeff Nippard Powerbuilding 4x        → 4-day full body
#       Pure Bodybuilding Upper/Lower        → 4-day UL bodybuilding
#       SBS Hypertrophy Template             → 2x/3x/4x/5x/6x DUP variants
#     These mirror the frequency sheets parsed from the spreadsheets. All are
#     "balanced" (no specialization) since the source templates distribute main
#     lifts evenly across the week.
# ─────────────────────────────────────────────────────────────────────────────
XLSX_SPLITS = [
    # Jeff Nippard Powerbuilding 4x — full-body 4x/week (Mon/Tue/Thu/Fri)
    spec("Nippard_Powerbuilding_4x",  "xlsx", 4, "spread5", {}, session_length=90, session_focus="FB Squat/OHP;FB Deadlift/Bench;FB Squat/Dip;FB Bench/Row"),
    # Pure Bodybuilding Upper/Lower — 4-day bodybuilding UL
    spec("PureBodybuilding_UL_4x",    "xlsx", 4, "spread5", {}, session_length=75, session_focus="Upper;Lower;Upper;Lower"),
    # SBS Hypertrophy — each frequency sheet is a DUP full-body rotation.
    # (The template's "2x" sheet is omitted: Stratum's solver floor is 3 days/week.)
    spec("SBS_Hypertrophy_3x", "xlsx", 3, "mwf",        {}, session_length=75, session_focus="Squat/RDL/DBBench;Bench/OHP/LegPress;BlockPull/Incline/HackSquat"),
    spec("SBS_Hypertrophy_4x", "xlsx", 4, "spread5",    {}, session_length=75, session_focus="Squat/DBBench/RDL;Bench/LegPress/DBOHP;BlockPull/Incline;OHP/HackSquat"),
    spec("SBS_Hypertrophy_5x", "xlsx", 5, "mtwfs",      {}, session_length=75, session_focus="Squat/DBOHP;Bench/LegPress;BlockPull/Incline;OHP/HackSquat;Squat/RDL"),
    spec("SBS_Hypertrophy_6x", "xlsx", 6, "sequential", {}, session_length=75, session_focus="Squat/Incline;DBOHP/RDL;Bench/LegPress;DBBench/HackSquat;BlockPull/LegPress;OHP/FrontSquat"),
]
# Note: the template's 2x sheet is skipped — Stratum's solver floor is 3 days/week.

# ─────────────────────────────────────────────────────────────────────────────
# (d) SYNTHETIC — combinatorial generation over Stratum's axes.
#     (i)   days × preset × layout × session-length cross-products (downsampled)
#     (ii)  curated multi-muscle specialization clusters
# ─────────────────────────────────────────────────────────────────────────────

# (i) Cross-product, prioritizing 4/5-day variants (the user's stated focus).
# We sample to keep the manifest bounded (~100-150 candidates), letting the
# diversity selector find the most dissimilar subset.
CROSS_PRODUCT_DAYS = [3, 4, 5, 6]
CROSS_PRODUCT_LAYOUTS_BY_DAYS = {
    3: ["mwf", "sequential", "weird4"],
    4: ["spread5", "sequential", "sean5", "weird4"],
    5: ["mtwfs", "spread5", "sean5", "sequential"],
    6: ["sequential", "mtwfs"],
}
CROSS_PRODUCT_PRESETS = ["maintenance", "arms", "chest", "back", "glutes", "shoulders", "legs", "calves"]
CROSS_PRODUCT_SESSIONS = [60, 75, 90]


def synth_cross_product():
    out = []
    for days in CROSS_PRODUCT_DAYS:
        layouts = CROSS_PRODUCT_LAYOUTS_BY_DAYS[days]
        for preset_name, spec_map in SPECIALIZATION_PRESETS.items():
            for layout in layouts:
                # Vary session length: 75 always, 60 only on 4/5-day, 90 only on 3-day
                sessions = [75]
                if days in (3, 4, 5):
                    sessions.append(60)
                if days == 3:
                    sessions.append(90)
                for sess in sessions:
                    label = f"Synth_{days}d_{layout}_{preset_name}_{sess}min"
                    out.append(spec(label, "synthetic", days, layout, dict(spec_map), session_length=sess))
    return out


# (ii) Curated multi-muscle specialization clusters — realistic bodybuilding
# weak-point focuses that single-muscle presets can't express.
CURATED_CLUSTERS = [
    ("ChestBack_Antagonist",  {"Chest": "Specialize", "Back": "Specialize"}),
    ("QuadHamstring_Power",   {"Quad": "Specialize", "Hamstring": "Specialize"}),
    ("Arms_Only",             {"Bicep": "Specialize", "Tricep": "Specialize"}),
    ("Shoulder_Head_Triplex", {"LateralDeltoid": "Specialize", "PosteriorDeltoid": "Specialize", "AnteriorDeltoid": "Specialize"}),
    ("Push_Emphasis",         {"Chest": "Specialize", "Tricep": "Specialize", "AnteriorDeltoid": "Specialize"}),
    ("Pull_Emphasis",         {"Back": "Specialize", "Bicep": "Specialize", "PosteriorDeltoid": "Specialize"}),
    ("ChestTricep_Synergy",   {"Chest": "Specialize", "Tricep": "Specialize"}),
    ("BackBicep_Synergy",     {"Back": "Specialize", "Bicep": "Specialize"}),
    ("Legs_Quad_Glute",       {"Quad": "Specialize", "Glute": "Specialize"}),
    ("Legs_Posterior_Chain",  {"Hamstring": "Specialize", "Glute": "Specialize"}),
    ("GluteCalf_Lower",       {"Glute": "Specialize", "Calf": "Specialize"}),
    ("FullUpper",             {"Chest": "Specialize", "Back": "Specialize", "Bicep": "Specialize", "Tricep": "Specialize"}),
    ("FullLower",             {"Quad": "Specialize", "Hamstring": "Specialize", "Glute": "Specialize", "Calf": "Specialize"}),
    ("ChestArms_Bro",         {"Chest": "Specialize", "Bicep": "Specialize", "Tricep": "Specialize"}),
    ("BackShoulders_VTaper",  {"Back": "Specialize", "LateralDeltoid": "Specialize"}),
    ("LateralCalf_Isolation", {"LateralDeltoid": "Specialize", "Calf": "Specialize"}),
    ("ChestGlute_Powerbuilding", {"Chest": "Specialize", "Glute": "Specialize"}),
    ("QuadBicep_Odd Couple",  {"Quad": "Specialize", "Bicep": "Specialize"}),
    ("HamstringTricep",       {"Hamstring": "Specialize", "Tricep": "Specialize"}),
    ("Single_Calf",           {"Calf": "Specialize"}),
    ("Single_LateralDelt",    {"LateralDeltoid": "Specialize"}),
    ("Single_AnteriorDelt",   {"AnteriorDeltoid": "Specialize"}),
    ("Single_Hamstring",      {"Hamstring": "Specialize"}),
    ("Single_Bicep",          {"Bicep": "Specialize"}),
    ("Single_Tricep",         {"Tricep": "Specialize"}),
]


def synth_curated_clusters():
    out = []
    # Each cluster at 2 representative day/layout combos so the diversity selector
    # has both compact and spread variants to choose from.
    cluster_day_layouts = [
        (4, "spread5", 75),
        (5, "mtwfs", 75),
    ]
    for name, cluster in CURATED_CLUSTERS:
        for days, layout, sess in cluster_day_layouts:
            label = f"Synth_{name}_{days}d_{layout}"
            out.append(spec(label, "synthetic", days, layout, dict(cluster), session_length=sess))
    return out


def main():
    os.makedirs(BUILD_DIR, exist_ok=True)
    candidates = []
    candidates.extend(ANCHORS)
    candidates.extend(WEB_SPLITS)
    candidates.extend(XLSX_SPLITS)
    candidates.extend(synth_cross_product())
    candidates.extend(synth_curated_clusters())

    # De-duplicate on the full spec signature (everything except label), keeping the
    # highest-priority source: anchor > web > xlsx > synthetic. A published split
    # (e.g. PHAT_5day) is a more meaningful label than a synthetic with the same
    # (days, layout, session, specialization) signature, so it wins the slot.
    source_priority = {"anchor": 0, "web": 1, "xlsx": 2, "synthetic": 3}
    by_key = {}
    for c in candidates:
        key = (c["days"], c["layout"], c["session_length"],
               tuple(sorted(c["specialization"].items())))
        prior = by_key.get(key)
        if prior is None or source_priority[c["source"]] < source_priority[prior["source"]]:
            by_key[key] = c
    deduped = list(by_key.values())
    # Stable-order: anchors first, then by source priority, then label.
    deduped.sort(key=lambda c: (source_priority[c["source"]], c["label"]))

    manifest = {"candidates": deduped, "muscle_vocab": MUSCLE_VOCAB}
    with open(MANIFEST_PATH, "w") as f:
        json.dump(manifest, f, indent=2)

    # Source breakdown
    by_source = {}
    for c in deduped:
        by_source[c["source"]] = by_source.get(c["source"], 0) + 1
    by_days = {}
    for c in deduped:
        by_days[c["days"]] = by_days.get(c["days"], 0) + 1

    print(f"Wrote {len(deduped)} candidates → {MANIFEST_PATH}")
    print(f"By source: {by_source}")
    print(f"By days:   {dict(sorted(by_days.items()))}")


if __name__ == "__main__":
    main()
