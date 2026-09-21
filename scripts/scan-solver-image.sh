#!/usr/bin/env bash
# Scan the solver-service Cloud Run container image for known CVEs.
#
# Builds the production image from solver-service/Dockerfile (monorepo root
# must be the build context) and runs `osv-scanner scan image` against it,
# covering the OS/base-image layer (eclipse-temurin:17-jre-jammy plus
# libgomp1 from apt) that the SBOM-based `osv-scan` job cannot see.
#
# Prerequisites:
#   - Docker daemon reachable (`docker info` succeeds).
#   - Fat jar built: ./gradlew :solver-service:shadowJar
#     (the Dockerfile COPYs solver-service/build/libs/solver-service-all.jar).
#   - osv-scanner v2 binary on PATH or via OSV_SCANNER_BIN.
#
# Usage:
#   ./gradlew :solver-service:shadowJar
#   ./scripts/scan-solver-image.sh [image-tag]
#
# Env overrides:
#   OSV_SCANNER_BIN  path to the osv-scanner binary (default: osv-scanner)
#   OSV_CONFIG       extra config flag, e.g. --config=.osv-scanner.toml
#                    (default: --config=.osv-scanner.toml when that file exists)
#
# Exit code 1 = CVEs found (same failure policy as the osv-scan CI job).
# See docs/security-scanning.md and work item #11.

set -euo pipefail

IMAGE="${1:-stratum-solver:ci}"
OSV_SCANNER_BIN="${OSV_SCANNER_BIN:-osv-scanner}"

if ! command -v docker >/dev/null 2>&1; then
  echo "scan-solver-image: Docker is not available on this machine." >&2
  echo "  Install: sudo apt install docker.io   (or Docker Desktop)" >&2
  echo "  Then:    sudo usermod -aG docker \"\$USER\" && newgrp docker" >&2
  echo "  Tracked in #11 (home-server runner prerequisite)." >&2
  exit 1
fi
if ! docker info >/dev/null 2>&1; then
  echo "scan-solver-image: Docker daemon is not reachable (docker info failed)." >&2
  echo "  Start the daemon or fix DOCKER_HOST, then retry." >&2
  exit 1
fi

if [ ! -f solver-service/build/libs/solver-service-all.jar ]; then
  echo "scan-solver-image: fat jar missing." >&2
  echo "  Build it first: ./gradlew :solver-service:shadowJar" >&2
  exit 1
fi

if [ -z "${OSV_CONFIG:-}" ]; then
  if [ -f .osv-scanner.toml ]; then
    OSV_CONFIG="--config=.osv-scanner.toml"
  fi
fi

echo "scan-solver-image: building ${IMAGE} (context: repo root)"
docker build -f solver-service/Dockerfile -t "$IMAGE" .

echo "scan-solver-image: image provenance"
docker inspect --format='Id={{.Id}} RepoDigests={{.RepoDigests}}' "$IMAGE" || true

echo "scan-solver-image: scanning ${IMAGE}"
# shellcheck disable=SC2086
"$OSV_SCANNER_BIN" scan image $OSV_CONFIG --format=table "$IMAGE"
