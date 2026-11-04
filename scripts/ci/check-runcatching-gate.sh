#!/usr/bin/env bash
# STR-239 gate (v2): thin wrapper — the scanner lives in
# scripts/ci/check_runcatching_gate.py (see its docstring for the rules).
# This path stays the CI entry point (.github/workflows/ci.yml calls it).
set -euo pipefail

exec python3 "$(dirname "$0")/check_runcatching_gate.py" "$@"
