#!/usr/bin/env python3
"""
Optuna TPE weight tuning for CP-SAT program synthesis.

Each trial runs:
  :core:domain-solver-tests:test --tests '*benchmarkTuningSuite*'
with -Dstratum.solver.weights=KEY:val,... and writes
  core/domain-solver-tests/build/tuning_loss.json

Progress files (for scripts/watch_tune_solver.sh):
  core/domain-solver/build/optuna_tune_run.log
  core/domain-solver/build/optuna_status.json
  core/domain-solver/build/optuna_study.db   (SQLite — resume-safe)
  core/domain-solver/build/best_weights.json

Usage:
  ./scripts/run_tune_solver.sh 20
  # or:
  python scripts/tune_solver.py --trials 20 --deadline-ms 10000
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import time
from datetime import datetime, timezone

import optuna

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BUILD_DIR = os.path.join(REPO_ROOT, "core", "domain-solver", "build")
LOSS_PATH = os.path.join(REPO_ROOT, "core", "domain-solver-tests", "build", "tuning_loss.json")
BEST_WEIGHTS_PATH = os.path.join(BUILD_DIR, "best_weights.json")
STATUS_PATH = os.path.join(BUILD_DIR, "optuna_status.json")
LOG_PATH = os.path.join(BUILD_DIR, "optuna_tune_run.log")
STUDY_DB = os.path.join(BUILD_DIR, "optuna_study.db")
# Bump when BENCHMARK_SPECS / loss layers change so SQLite studies don't mix surfaces.
STUDY_NAME = "stratum_solver_weights_v3_day_prior"

# Production seed: v3 day-prior study trial 5 (stopped early; best of 12).
DEFAULT_WEIGHTS = {
    "RECOVERY_PRIORITY": 3_300_000,
    "RECOVERY_GENERAL": 300_000,
    "SPECIALIZE_AIM_DEVIATION": 4_900,
    "MAINTENANCE_AIM_DEVIATION": 3_700,
    "FREQUENCY_SHORTFALL": 12_000,
    "SLOT_ACTIVATION_COUNT": 650,
    "LOWER_ANCHOR_ACTIVATION_BONUS": -550,
    "SESSION_FILL_SHORTFALL": 6_500,
    "SESSION_SET_IMBALANCE": 1_900,
    "UPPER_BODY_MAINTENANCE_PARITY": 800,
    "LOWER_BODY_MAINTENANCE_PARITY": 500,
}


def log(msg: str) -> None:
    # Print only — shell/nohup redirects capture the log file (avoid double lines).
    line = f"[{datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')}] {msg}"
    print(line, flush=True)


def write_status(payload: dict) -> None:
    os.makedirs(BUILD_DIR, exist_ok=True)
    tmp = STATUS_PATH + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(payload, f, indent=2)
        f.write("\n")
    os.replace(tmp, STATUS_PATH)


def suggest_weights(trial: optuna.Trial) -> dict[str, int]:
    return {
        "RECOVERY_PRIORITY": trial.suggest_int("RECOVERY_PRIORITY", 500_000, 5_000_000, step=100_000),
        "RECOVERY_GENERAL": trial.suggest_int("RECOVERY_GENERAL", 50_000, 500_000, step=10_000),
        "SPECIALIZE_AIM_DEVIATION": trial.suggest_int("SPECIALIZE_AIM_DEVIATION", 1000, 5000, step=100),
        "MAINTENANCE_AIM_DEVIATION": trial.suggest_int("MAINTENANCE_AIM_DEVIATION", 500, 4000, step=100),
        "FREQUENCY_SHORTFALL": trial.suggest_int("FREQUENCY_SHORTFALL", 1_000, 20_000, step=500),
        "SLOT_ACTIVATION_COUNT": trial.suggest_int("SLOT_ACTIVATION_COUNT", 50, 1000, step=50),
        "LOWER_ANCHOR_ACTIVATION_BONUS": trial.suggest_int(
            "LOWER_ANCHOR_ACTIVATION_BONUS", -1000, -100, step=50
        ),
        "SESSION_FILL_SHORTFALL": trial.suggest_int("SESSION_FILL_SHORTFALL", 1000, 10_000, step=500),
        "SESSION_SET_IMBALANCE": trial.suggest_int("SESSION_SET_IMBALANCE", 500, 5000, step=100),
        "UPPER_BODY_MAINTENANCE_PARITY": trial.suggest_int(
            "UPPER_BODY_MAINTENANCE_PARITY", 500, 5000, step=100
        ),
        "LOWER_BODY_MAINTENANCE_PARITY": trial.suggest_int(
            "LOWER_BODY_MAINTENANCE_PARITY", 500, 5000, step=100
        ),
    }


def run_benchmark(weights: dict[str, int], deadline_ms: int) -> tuple[float, dict]:
    override_str = ",".join(f"{k}:{v}" for k, v in weights.items())
    cmd = [
        "./gradlew",
        ":core:domain-solver-tests:test",
        "-PrunSolverTests=true",
        "-PincludeBenchmark=true",
        "--tests",
        "com.stratum.domain.solver.ProgramSynthesisAcceptanceTest.benchmarkTuningSuite",
        f"-Dstratum.solver.weights={override_str}",
        f"-Dstratum.solver.deadline={deadline_ms}",
    ]
    if os.path.exists(LOSS_PATH):
        os.remove(LOSS_PATH)

    t0 = time.time()
    result = subprocess.run(
        cmd,
        cwd=REPO_ROOT,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    elapsed = time.time() - t0

    if not os.path.exists(LOSS_PATH):
        log(
            f"Loss file missing (exit={result.returncode}). "
            f"Gradle tail:\n{(result.stdout or '')[-4000:]}\n{(result.stderr or '')[-2000:]}"
        )
        return 1_000_000.0, {"error": "no_loss_file", "elapsed_s": elapsed}

    with open(LOSS_PATH, "r", encoding="utf-8") as f:
        data = json.load(f)
    loss = float(data.get("loss", 1_000_000.0))
    data["elapsed_s"] = elapsed
    data["gradle_exit"] = result.returncode
    return loss, data


def best_snapshot(study: optuna.Study) -> tuple[float | None, dict | None]:
    """Safe best lookup — empty studies raise ValueError on study.best_trial."""
    try:
        t = study.best_trial
        return t.value, dict(t.params)
    except ValueError:
        return None, None


def make_objective(deadline_ms: int, study: optuna.Study):
    def objective(trial: optuna.Trial) -> float:
        weights = suggest_weights(trial)
        log(f"--- Trial {trial.number} starting ---")
        log(f"Weights: {weights}")
        best_val, best_params = best_snapshot(study)
        write_status(
            {
                "state": "running_trial",
                "trial": trial.number,
                "weights": weights,
                "best_value": best_val,
                "best_params": best_params,
                "n_complete": len(
                    [t for t in study.trials if t.state == optuna.trial.TrialState.COMPLETE]
                ),
                "updated_at": datetime.now(timezone.utc).isoformat(),
            }
        )

        try:
            loss, payload = run_benchmark(weights, deadline_ms)
        except Exception as e:
            log(f"Trial {trial.number} errored: {e}")
            return 1_000_000.0

        components = payload.get("components") or {}
        log(
            f"Trial {trial.number} finished. Loss: {loss}  "
            f"elapsed={payload.get('elapsed_s', 0):.1f}s  "
            f"ok={payload.get('n_ok')} failed={payload.get('n_failed')}  "
            f"components={components}"
        )
        # Attach user attrs for Optuna dashboard / post-hoc.
        trial.set_user_attr("components", components)
        trial.set_user_attr("n_ok", payload.get("n_ok"))
        trial.set_user_attr("n_failed", payload.get("n_failed"))
        return loss

    return objective


def run_study(n_trials: int, deadline_ms: int, fresh: bool) -> None:
    os.makedirs(BUILD_DIR, exist_ok=True)
    if fresh and os.path.exists(STUDY_DB):
        os.remove(STUDY_DB)
        log(f"Removed prior study DB {STUDY_DB}")

    storage = f"sqlite:///{STUDY_DB}"
    study = optuna.create_study(
        study_name=STUDY_NAME,
        storage=storage,
        load_if_exists=not fresh,
        direction="minimize",
    )

    # Seed production defaults once per study (skip if already present).
    has_seed = any(
        t.params.get("FREQUENCY_SHORTFALL") == DEFAULT_WEIGHTS["FREQUENCY_SHORTFALL"]
        and t.params.get("RECOVERY_PRIORITY") == DEFAULT_WEIGHTS["RECOVERY_PRIORITY"]
        and t.params.get("SLOT_ACTIVATION_COUNT") == DEFAULT_WEIGHTS["SLOT_ACTIVATION_COUNT"]
        for t in study.trials
        if t.state in (optuna.trial.TrialState.COMPLETE, optuna.trial.TrialState.WAITING)
    )
    if not has_seed:
        study.enqueue_trial(DEFAULT_WEIGHTS)
        log("Enqueued production DEFAULT_WEIGHTS as first trial seed")

    already = len([t for t in study.trials if t.state == optuna.trial.TrialState.COMPLETE])
    remaining = max(0, n_trials - already)
    log(
        f"Study {STUDY_NAME}: complete={already} requested_total={n_trials} "
        f"will_run={remaining} deadline_ms={deadline_ms}"
    )
    write_status(
        {
            "state": "starting",
            "study": STUDY_NAME,
            "n_trials_target": n_trials,
            "n_complete": already,
            "deadline_ms": deadline_ms,
            "updated_at": datetime.now(timezone.utc).isoformat(),
        }
    )

    if remaining == 0:
        log("Nothing to run — study already has enough complete trials")
    else:
        study.optimize(make_objective(deadline_ms, study), n_trials=remaining)

    best = study.best_trial
    log("=" * 40)
    log("Optimization finished!")
    log(f"Best Trial: #{best.number} Loss: {best.value}")
    log(f"Best Weights: {best.params}")
    log("=" * 40)

    best_payload = {
        "loss": best.value,
        "trial": best.number,
        "weights": best.params,
        "components": best.user_attrs.get("components"),
        "study": STUDY_NAME,
        "n_complete": len(
            [t for t in study.trials if t.state == optuna.trial.TrialState.COMPLETE]
        ),
        "finished_at": datetime.now(timezone.utc).isoformat(),
    }
    with open(BEST_WEIGHTS_PATH, "w", encoding="utf-8") as f:
        json.dump(best_payload, f, indent=2)
        f.write("\n")
    log(f"Best weights saved to {BEST_WEIGHTS_PATH}")
    write_status({"state": "finished", **best_payload})


def main() -> None:
    ap = argparse.ArgumentParser(description="Optuna solver weight tuning")
    ap.add_argument(
        "trials",
        nargs="?",
        type=int,
        default=None,
        help="Total complete trials desired (legacy positional)",
    )
    ap.add_argument("--trials", dest="trials_flag", type=int, default=20)
    ap.add_argument(
        "--deadline-ms",
        type=int,
        default=10_000,
        help="CP-SAT deadline per scenario (ms). Default 10000.",
    )
    ap.add_argument(
        "--fresh",
        action="store_true",
        help="Delete study DB and start a new study",
    )
    args = ap.parse_args()
    n_trials = args.trials if args.trials is not None else args.trials_flag
    run_study(n_trials=n_trials, deadline_ms=args.deadline_ms, fresh=args.fresh)


if __name__ == "__main__":
    main()
