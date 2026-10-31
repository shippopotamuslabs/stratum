#!/usr/bin/env bash
# Resolve one tree's Gradle dependency graphs to CycloneDX SBOMs and scan
# them (plus the tree's functions lockfiles) with osv-scanner.
#
# Shared by ci.yml's PR diff scan (run once per tree) and
# osv-nightly.yml's full scan, so the scanned inputs and scanner flags
# live in exactly one place.
#
# Trust note: the SBOM generator is taken from THIS script's own
# directory. ci.yml's osv-scan job invokes the copy of this script from
# the PR's BASE-branch checkout, so a PR cannot weaken the gate that
# judges it by editing the generator (or this script) in its own tree.
#
# Usage:
#   osv-scan-source.sh <scanner-bin> <out-prefix> [work-dir]
#                      [--config=<path>] [--format=<fmt>]
#                      [--output-file=<path>]
#
# Writes <out-prefix>.cdx.json and <out-prefix>-solver.cdx.json.
# Exit code is the scanner's (1 = findings present), so table-format
# callers fail on findings while JSON callers may wrap with `|| true`
# and decide from the result file. A failed Gradle resolution or SBOM
# generation aborts the script nonzero (fail-closed: a partial tree
# never produces a clean-looking scan).

set -euo pipefail

SCANNER="${1:?usage: osv-scan-source.sh <scanner-bin> <out-prefix> [work-dir] [--config=<path>] [--format=<fmt>] [--output-file=<path>]}"
OUT_PREFIX="${2:?usage: osv-scan-source.sh <scanner-bin> <out-prefix> [work-dir] [--config=<path>] [--format=<fmt>] [--output-file=<path>]}"
WORK_DIR="${3:-.}"
CONFIG=""
FORMAT="table"
OUTPUT_FILE=""
for arg in "${@:4}"; do
  case "$arg" in
    --config=*) CONFIG="${arg#--config=}" ;;
    --format=*) FORMAT="${arg#--format=}" ;;
    --output-file=*) OUTPUT_FILE="${arg#--output-file=}" ;;
    *) echo "osv-scan-source: unknown argument: $arg" >&2; exit 2 ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

cd "$WORK_DIR"
TMPD="$(mktemp -d)"
trap 'rm -rf "$TMPD"' EXIT

./gradlew :app:dependencies --configuration releaseRuntimeClasspath -q > "$TMPD/app-deps.txt"
python3 "$SCRIPT_DIR/generate-sbom.py" < "$TMPD/app-deps.txt" > "${OUT_PREFIX}.cdx.json"
./gradlew :solver-service:dependencies --configuration runtimeClasspath -q > "$TMPD/solver-deps.txt"
python3 "$SCRIPT_DIR/generate-sbom.py" --component-name stratum-solver < "$TMPD/solver-deps.txt" > "${OUT_PREFIX}-solver.cdx.json"

SCAN_ARGS=(
  -L "${OUT_PREFIX}.cdx.json"
  -L "${OUT_PREFIX}-solver.cdx.json"
  --lockfile=functions/package-lock.json
  --lockfile=functions-python/requirements.txt
  --format="$FORMAT"
)
if [ -n "$CONFIG" ] && [ -f "$CONFIG" ]; then
  SCAN_ARGS+=(--config="$CONFIG")
fi
if [ -n "$OUTPUT_FILE" ]; then
  SCAN_ARGS+=(--output-file="$OUTPUT_FILE")
fi

"$SCANNER" scan source "${SCAN_ARGS[@]}"
