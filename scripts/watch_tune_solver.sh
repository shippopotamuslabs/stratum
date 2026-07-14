#!/usr/bin/env bash
# Live monitor for scripts/run_tune_solver.sh / scripts/tune_solver.py
#
# Usage (separate terminal from the tuner):
#   ./scripts/watch_tune_solver.sh
#   ./scripts/watch_tune_solver.sh --once    # print status once and exit
#
# Shows:
#   - latest optuna_status.json (trial #, best loss, components if finished)
#   - last 30 lines of optuna_tune_run.log
#   - whether tune_solver.py is running

set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOG="$ROOT/core/domain-solver/build/optuna_tune_run.log"
STATUS="$ROOT/core/domain-solver/build/optuna_status.json"
BEST="$ROOT/core/domain-solver/build/best_weights.json"
LOSS="$ROOT/core/domain-solver-tests/build/tuning_loss.json"

ONCE=0
if [[ "${1:-}" == "--once" ]]; then
  ONCE=1
fi

print_panel() {
  clear 2>/dev/null || true
  echo "════════════════════════════════════════════════════════════════"
  echo "  Stratum solver Optuna watch  $(date '+%Y-%m-%d %H:%M:%S')"
  echo "════════════════════════════════════════════════════════════════"

  if pgrep -f 'scripts/tune_solver.py' >/dev/null 2>&1; then
    echo "  process: RUNNING (tune_solver.py)"
  else
    echo "  process: not running"
  fi

  if [[ -f "$STATUS" ]]; then
    echo
    echo "── status (optuna_status.json) ──"
    if command -v python3 >/dev/null 2>&1; then
      python3 - <<'PY' "$STATUS"
import json, sys
p = sys.argv[1]
with open(p) as f:
    d = json.load(f)
print(f"  state:        {d.get('state')}")
print(f"  trial:        {d.get('trial', d.get('n_complete', '—'))}")
print(f"  n_complete:   {d.get('n_complete', '—')}")
print(f"  best_value:   {d.get('best_value', d.get('loss', '—'))}")
if d.get("best_params"):
    bp = d["best_params"]
    keys = ["FREQUENCY_SHORTFALL", "SESSION_FILL_SHORTFALL", "SLOT_ACTIVATION_COUNT",
            "RECOVERY_PRIORITY", "MAINTENANCE_AIM_DEVIATION", "SPECIALIZE_AIM_DEVIATION"]
    print("  best (subset):")
    for k in keys:
        if k in bp:
            print(f"    {k}: {bp[k]}")
if d.get("components"):
    print("  last/best components:")
    for k, v in d["components"].items():
        print(f"    {k}: {v}")
print(f"  updated_at:   {d.get('updated_at', d.get('finished_at', '—'))}")
PY
    else
      cat "$STATUS"
    fi
  else
    echo
    echo "  (no status file yet — start ./scripts/run_tune_solver.sh)"
  fi

  if [[ -f "$LOSS" ]]; then
    echo
    echo "── last trial loss file ──"
    if command -v python3 >/dev/null 2>&1; then
      python3 - <<'PY' "$LOSS"
import json, sys
with open(sys.argv[1]) as f:
    d = json.load(f)
print(f"  loss={d.get('loss')}  ok={d.get('n_ok')}  failed={d.get('n_failed')}")
c = d.get("components") or {}
if c:
    print("  " + " ".join(f"{k}={v}" for k, v in c.items()))
PY
    else
      cat "$LOSS"
    fi
  fi

  if [[ -f "$BEST" ]]; then
    echo
    echo "── best_weights.json mtime ──"
    ls -l "$BEST" | awk '{print "  "$0}'
  fi

  echo
  echo "── log tail ($LOG) ──"
  if [[ -f "$LOG" ]]; then
    tail -n 25 "$LOG"
  else
    echo "  (no log yet)"
  fi
  echo
  echo "Ctrl-C to stop watching  |  refresh every 15s"
}

if [[ "$ONCE" -eq 1 ]]; then
  print_panel
  exit 0
fi

while true; do
  print_panel
  sleep 15
done
