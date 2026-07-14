#!/usr/bin/env bash
# Start (or resume) Optuna solver weight tuning.
#
# Usage:
#   ./scripts/run_tune_solver.sh              # 20 trials, 10s deadline, resume study if present
#   ./scripts/run_tune_solver.sh 30           # 30 total complete trials
#   ./scripts/run_tune_solver.sh 20 --fresh   # wipe study DB and start clean
#   ./scripts/run_tune_solver.sh 20 --deadline-ms 7000
#
# Watch in another terminal:
#   ./scripts/watch_tune_solver.sh

set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

mkdir -p core/domain-solver/build

PYTHON="${ROOT}/venv/bin/python"
if [[ ! -x "$PYTHON" ]]; then
  PYTHON="${PYTHON3:-python3}"
fi

# Default trials from first numeric arg if present
ARGS=("$@")
if [[ ${#ARGS[@]} -eq 0 ]]; then
  ARGS=(20)
fi

echo "run_tune_solver: python=$PYTHON"
echo "run_tune_solver: log=core/domain-solver/build/optuna_tune_run.log"
echo "run_tune_solver: status=core/domain-solver/build/optuna_status.json"
echo "run_tune_solver: watch with: ./scripts/watch_tune_solver.sh"
echo

# Unbuffered so the log updates live for watch_tune_solver.sh
export PYTHONUNBUFFERED=1
exec "$PYTHON" -u scripts/tune_solver.py "${ARGS[@]}"
