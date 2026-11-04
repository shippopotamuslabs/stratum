#!/usr/bin/env bash
# STR-239 gate: plain `runCatching` absorbs CancellationException into a Result,
# which suspend code must never do (CODE-STYLE-GOSPEL §11: cancellation is not a
# failure). This project's detekt has no type resolution, so a grep gate carries
# the rule instead of a detekt rule.
#
# Rules:
#   - core/application/src/main: zero tolerance. Every Result-envelope producer
#     in a suspend context is runCatchingCancellable (or runCatchingLogged).
#   - core/domain/src/main and core/infrastructure/src/main: plain runCatching
#     is allowed only in the files listed in scripts/ci/runcatching-allowlist.txt
#     (non-suspend parse/decode/control-flow sites owned by later stack tickets
#     or permanently out of scope). Each allowlist entry carries its reason, and
#     an entry whose file no longer contains a plain call fails the gate so the
#     list keeps shrinking as later tickets convert sites.
#
# The pattern requires "{" or "<" right after the token, so runCatchingLogged
# and runCatchingCancellable call sites never match.
set -euo pipefail

cd "$(dirname "$0")/../.."

PATTERN='runCatching[[:space:]]*(\{|<)'
ALLOWLIST='scripts/ci/runcatching-allowlist.txt'
fail=0

# --- application: zero tolerance -------------------------------------------
app_hits="$(grep -rnE --include='*.kt' "$PATTERN" core/application/src/main || true)"
if [ -n "$app_hits" ]; then
    echo "FAIL: plain runCatching in core/application/src/main (use runCatchingCancellable):"
    echo "$app_hits"
    fail=1
fi

# --- domain + infrastructure: allowlisted files only ------------------------
allowed="$(grep -vE '^[[:space:]]*(#|$)' "$ALLOWLIST" | awk '{print $1}' | sort -u)"

hits="$(grep -rnE --include='*.kt' "$PATTERN" core/domain/src/main core/infrastructure/src/main || true)"
if [ -n "$hits" ]; then
    while IFS= read -r line; do
        file="${line%%:*}"
        if ! printf '%s\n' "$allowed" | grep -qxF "$file"; then
            echo "FAIL: plain runCatching outside the allowlist: $line"
            fail=1
        fi
    done <<< "$hits"
fi

# --- allowlist hygiene: no stale entries ------------------------------------
while IFS= read -r file; do
    [ -n "$file" ] || continue
    if ! grep -qE "$PATTERN" "$file"; then
        echo "FAIL: stale allowlist entry (no plain runCatching left): $file — remove it from $ALLOWLIST"
        fail=1
    fi
done <<< "$allowed"

if [ "$fail" -ne 0 ]; then
    exit 1
fi
echo "runCatching gate: OK"
