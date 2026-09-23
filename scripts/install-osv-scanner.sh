#!/usr/bin/env bash
# Download the pinned osv-scanner release binary to a caller-chosen path.
#
# Always downloads fresh — never reuse a host-shared copy. The shell
# executor's /tmp is world-writable and persists across pipelines, so
# executing a reused binary from there is a cache-poisoning / RCE vector.
# Callers must pass a job-private directory (e.g. "$CI_PROJECT_DIR/.tmp")
# so concurrent jobs cannot race on the same path either.
#
# Usage: ./scripts/install-osv-scanner.sh <version> <output-path>

set -euo pipefail

VERSION="${1:?usage: install-osv-scanner.sh <version> <output-path>}"
OUT="${2:?usage: install-osv-scanner.sh <version> <output-path>}"

case "$(uname -m)" in
  x86_64) ARCH=amd64 ;;
  aarch64|arm64) ARCH=arm64 ;;
  *) echo "Unsupported architecture: $(uname -m)" >&2; exit 1 ;;
esac

URL="https://github.com/google/osv-scanner/releases/download/v${VERSION}/osv-scanner_linux_${ARCH}"
echo "Downloading osv-scanner ${VERSION} (${ARCH})"
curl -sSL --retry 3 -o "$OUT" "$URL"
chmod +x "$OUT"
"$OUT" --version
