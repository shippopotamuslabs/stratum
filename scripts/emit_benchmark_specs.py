#!/usr/bin/env python3
"""
Stage 5 helper — emit Kotlin BenchmarkSpec rows from the diversity manifest.

Reads `core/domain-solver/build/benchmark_manifest.json` (Stage 4 output) and prints
Kotlin `BenchmarkSpec(...)` rows ready to paste into the BENCHMARK_SPECS list in
ProgramSynthesisAcceptanceTest.kt. Entries that duplicate an existing Benchmark_A–H
spec (same days/layout/session/specialization) are skipped — the anchors are already
in the table.

Usage:
    .venv-bench/bin/python scripts/emit_benchmark_specs.py
"""

from __future__ import annotations

import json
import os
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MANIFEST_PATH = os.path.join(REPO_ROOT, "core", "domain-solver", "build", "benchmark_manifest.json")

# The 8 anchor specs already in the table (skip these when emitting).
# Format matches sig_key(): (days, layout, session_len, tuple(sorted(muscles))).
ANCHOR_SIGS = {
    (5, "mtwfs", 75, ("Bicep", "Calf", "Tricep")),
    (4, "spread5", 75, ()),
    (5, "spread5", 75, ()),
    (5, "mtwfs", 60, ("Bicep", "Tricep")),
    (3, "mwf", 75, ("Back", "Chest")),
    (4, "spread5", 75, ("Hamstring", "Quad")),
    (5, "mtwfs", 75, ("LateralDeltoid", "PosteriorDeltoid")),
    (6, "sequential", 75, ("Chest", "Tricep")),
}

MUSCLE_ALIASES = {
    # Manifest uses full MuscleGroup names; Kotlin BenchmarkSpec uses the same.
}


def to_kotlin_muscle(m: str) -> str:
    return f"MuscleGroup.{m}"


def sig_key(spec):
    spec_muscles = tuple(sorted(spec["specialization"].keys()))
    return (spec["days"], spec["layout"], spec["session_length"], spec_muscles)


def main():
    if not os.path.exists(MANIFEST_PATH):
        sys.exit(f"Missing {MANIFEST_PATH} — run clean_and_diversify.py (Stage 4) first.")
    with open(MANIFEST_PATH) as f:
        manifest = json.load(f)
    benchmarks = manifest["benchmarks"]

    seen_sigs = set(ANCHOR_SIGS)
    emitted = []
    skipped = 0
    for b in benchmarks:
        key = sig_key(b)
        if key in seen_sigs:
            skipped += 1
            continue
        seen_sigs.add(key)

        muscles = sorted(b["specialization"].keys())
        if not muscles:
            spec_arg = "emptyMap()"
        elif len(muscles) == 1:
            spec_arg = f"specVararg({to_kotlin_muscle(muscles[0])})"
        else:
            spec_arg = "specVararg(" + ", ".join(to_kotlin_muscle(m) for m in muscles) + ")"

        # Sanitize label for Kotlin string literal.
        label = b["label"].replace('"', '\\"')
        row = (
            f'        BenchmarkSpec("{label}", {b["days"]}, "{b["layout"]}", '
            f'{b["session_length"]}, {spec_arg}),'
        )
        emitted.append(row)

    print(f"// Emitted {len(emitted)} BenchmarkSpec rows ({skipped} duplicates/anchors skipped).")
    print(f"// Paste these into BENCHMARK_SPECS after the Benchmark_H entry:\n")
    for row in emitted:
        print(row)


if __name__ == "__main__":
    main()
