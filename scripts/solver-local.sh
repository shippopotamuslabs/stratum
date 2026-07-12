#!/usr/bin/env bash
# Local CP-SAT synthesis lab runner.
#
# Uses the host JVM when Docker is not available (linux/darwin with ortools-java
# natives). Prefer Docker for a clean JDK 17 + libgomp environment that matches
# Cloud Run.
#
# Usage:
#   ./scripts/solver-local.sh lab [args…]
#   ./scripts/solver-local.sh test [TestClassOrPattern]
#   ./scripts/solver-local.sh smoke          # core matrix solver suite (default PR gate)
#   ./scripts/solver-local.sh shell          # docker only
#   ./scripts/solver-local.sh docker lab …  # force Docker even if host works
#
# Lab system properties (via -D or env):
#   stratum.lab.days=4
#   stratum.lab.layout=sequential|sean5|…
#   stratum.lab.session_minutes=75
#   stratum.lab.spec=Chest:Specialize,Back:Maintenance
#   stratum.solver.deadline=30000
#   stratum.solver.log_search=true
#   stratum.solver.warm_start=false
#   stratum.solver.scenarios=core|full

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

FORCE_DOCKER=0
if [[ "${1:-}" == "docker" ]]; then
  FORCE_DOCKER=1
  shift
fi

CMD="${1:-lab}"
shift || true

have_docker() {
  command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1
}

run_host() {
  local gradle_args=("$@")
  echo "solver-local: host JVM ($(uname -s)/$(uname -m))"
  exec ./gradlew "${gradle_args[@]}"
}

run_docker() {
  local docker_args=("$@")
  if ! have_docker; then
    echo "solver-local: Docker is not available." >&2
    echo "  Install: sudo apt install docker.io   (or Docker Desktop)" >&2
    echo "  Then:    sudo usermod -aG docker \"\$USER\" && newgrp docker" >&2
    echo "  Or run without Docker (host OR-Tools works on this machine)." >&2
    exit 1
  fi
  echo "solver-local: Docker (stratum-solver-lab)"
  if ! docker image inspect stratum-solver-lab:local >/dev/null 2>&1; then
    docker build -f solver-service/Dockerfile.local -t stratum-solver-lab:local .
  fi
  exec docker run --rm -it \
    -v "$ROOT:/workspace" \
    -v stratum-solver-gradle-cache:/home/solver/.gradle \
    -w /workspace \
    -e GRADLE_USER_HOME=/home/solver/.gradle \
    -e JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:--XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0}" \
    stratum-solver-lab:local \
    "${docker_args[@]}"
}

gradle_lab() {
  # Forward common -D props from the environment for convenience.
  local props=()
  [[ -n "${STRATUM_LAB_DAYS:-}" ]] && props+=("-Dstratum.lab.days=$STRATUM_LAB_DAYS")
  [[ -n "${STRATUM_LAB_LAYOUT:-}" ]] && props+=("-Dstratum.lab.layout=$STRATUM_LAB_LAYOUT")
  [[ -n "${STRATUM_LAB_SESSION_MINUTES:-}" ]] && props+=("-Dstratum.lab.session_minutes=$STRATUM_LAB_SESSION_MINUTES")
  [[ -n "${STRATUM_LAB_SPEC:-}" ]] && props+=("-Dstratum.lab.spec=$STRATUM_LAB_SPEC")
  [[ -n "${STRATUM_SOLVER_DEADLINE:-}" ]] && props+=("-Dstratum.solver.deadline=$STRATUM_SOLVER_DEADLINE")
  [[ -n "${STRATUM_SOLVER_LOG_SEARCH:-}" ]] && props+=("-Dstratum.solver.log_search=$STRATUM_SOLVER_LOG_SEARCH")
  [[ -n "${STRATUM_SOLVER_WARM_START:-}" ]] && props+=("-Dstratum.solver.warm_start=$STRATUM_SOLVER_WARM_START")

  # Also accept bare -D… args after `lab`.
  local extra=()
  while [[ $# -gt 0 ]]; do
    case "$1" in
      -D*) extra+=("$1"); shift ;;
      *) extra+=("$1"); shift ;;
    esac
  done

  echo ./gradlew :core:domain-solver-tests:test \
    -PrunSolverTests=true \
    --tests 'com.stratum.domain.solver.LocalSynthesisLab' \
    "${props[@]}" "${extra[@]}"
  ./gradlew :core:domain-solver-tests:test \
    -PrunSolverTests=true \
    --tests 'com.stratum.domain.solver.LocalSynthesisLab' \
    "${props[@]}" "${extra[@]}"
}

gradle_test() {
  local pattern="${1:-OrToolsLoaderSmokeTest}"
  shift || true
  ./gradlew :core:domain-solver-tests:test \
    -PrunSolverTests=true \
    --tests "*${pattern}*" \
    "$@"
}

gradle_smoke() {
  # Intentionally narrow: cartesian harnesses on CORE_SOLVER_SCENARIOS + unit smokes.
  # Full module still includes many one-off acceptance solves — use:
  #   ./gradlew :core:domain-solver-tests:test -PrunSolverTests=true
  # Nightly cartesian:
  #   -PfullSolverScenarios=true
  echo "solver-local: smoke = core matrix harnesses + OrTools/seed unit smokes"
  ./gradlew :core:domain-solver-tests:test \
    -PrunSolverTests=true \
    -Dstratum.solver.scenarios=core \
    --tests 'com.stratum.domain.solver.OrToolsLoaderSmokeTest' \
    --tests 'com.stratum.domain.solver.OrToolsImplicationSmokeTest' \
    --tests 'com.stratum.domain.solver.OrToolsChannelDayPairSmokeTest' \
    --tests 'com.stratum.domain.solver.ConstructiveSynthesisSeedTest' \
    --tests 'com.stratum.domain.solver.ProgramSynthesisPropertyTest' \
    --tests 'com.stratum.domain.solver.CombinatorialSpecializationRegressionTest' \
    --tests 'com.stratum.domain.program.generation.ProgramGenerationPipelineTest' \
    --tests 'com.stratum.domain.solver.WeirdCalendarFourDaySynthesisTest' \
    "$@"
}

case "$CMD" in
  lab)
    if [[ "$FORCE_DOCKER" -eq 1 ]]; then
      run_docker ./scripts/solver-local.sh lab "$@"
    else
      gradle_lab "$@"
    fi
    ;;
  test)
    if [[ "$FORCE_DOCKER" -eq 1 ]]; then
      run_docker ./scripts/solver-local.sh test "$@"
    else
      gradle_test "$@"
    fi
    ;;
  smoke)
    if [[ "$FORCE_DOCKER" -eq 1 ]]; then
      run_docker ./scripts/solver-local.sh smoke "$@"
    else
      gradle_smoke "$@"
    fi
    ;;
  shell)
    run_docker bash
    ;;
  help|-h|--help)
    sed -n '2,35p' "$0"
    ;;
  *)
    echo "Unknown command: $CMD (try lab | smoke | test | shell | help)" >&2
    exit 2
    ;;
esac
