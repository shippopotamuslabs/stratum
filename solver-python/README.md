# Stratum program-synthesis solver (Python, STR-45)

Cloud Run HTTP service that solves program-synthesis requests with OR-Tools
CP-SAT (Python API) and answers the v1 JSON contract in
`solver-contract/` (which this service treats as read-only).

## Layout

- `solver/domain.py` — frozen domain constants/tables ported from Kotlin
  (recovery math, cycle space, timeline, region filters, slot catalog,
  universe builder) plus the STR-39 small-muscle rules.
- `solver/wire.py` — lenient v1 request decode (VERSIONING.md §2) + canonical
  v1 response encode.
- `solver/model.py` — full CP-SAT model (faithful port of
  `ProgramSynthesisModel.kt` at `ef470aa3`) + STR-39; `solve_payload()` is the
  single entry point.
- `solver/app.py` — stdlib HTTP service (`POST /solve/v1`, `GET /health`).
- `tests/` — pytest suite (`test_wire_leniency`, `test_str39`,
  `test_fixtures`, `test_app`).

## Local run

```bash
cd solver-python
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt        # ortools==9.15.6755, jsonschema, pytest
python -m solver.app                   # listens on $PORT (default 8080)
```

Solve a contract fixture through the running service:

```bash
curl -s -X POST localhost:8080/solve/v1 \
  -H 'Content-Type: application/json' \
  --data @../solver-contract/fixtures/v1/3day-fullbody-maint.request.json | python -m json.tool
curl -s localhost:8080/health
```

Knobs (environment):

- `SOLVER_MAX_TIME_SECONDS` (default `120`, matches the Kotlin
  `CpSatProgramSynthesizer` deadline),
- `SOLVER_NUM_SEARCH_WORKERS` (unset = OR-Tools default),
- `PORT` / `SOLVER_PORT` / `SOLVER_HOST`.

Tests:

```bash
python -m pytest tests/ -q                      # full suite (slow: solves fixtures)
SOLVER_TEST_DEADLINE_SECONDS=45 python -m pytest tests/ -q   # shorter deadlines
```

## Fallback ladder (STR-54)

Narrow catalog + back-to-back training days can make the full model infeasible
under STR-39 (measured: proofs land in ~0.1s). `solve_payload` tries L0 (full
model) and, on a proven `INFEASIBLE` only — never on timeouts — retries once
at L1, which softens the min-active-lifts floor to a heavy-slack penalty
(`W_L1_ACTIVE_LIFTS_SHORTFALL`). Recovery forbids stay hard at every level, so
L1 output is safe-but-thin (fewer lifts per session; volume preserved where
possible). Engagement is logged to stdout (`solver: L0 infeasible ... retrying
at L1`). Server-internal: no contract change; the L1 penalty feeds `total` but
has no v1 breakdown field (a v1.1 `fallbackLevel` field can expose it).

## Cloud Run notes (do NOT deploy from here)

Build and deploy are separate, maintainer-run steps:

```bash
gcloud builds submit --tag gcr.io/PROJECT/solver-python .
gcloud run deploy stratum-solver --image gcr.io/PROJECT/solver-python \
  --platform managed --region REGION --no-allow-unauthenticated \
  --set-env-vars SOLVER_MAX_TIME_SECONDS=120 --min-instances 0 --max-instances 4
```

The production transport is job-based (`synthesis/{uid}/{jobId}/request.json`
→ worker solves → `result.json`); `POST /solve/v1` is the direct-HTTP
equivalent used for contract tests. Auth (Google Sign-In), App Check, rate
limits, and the monthly generation cap live outside this service (dispatcher
layer) and are unchanged by the Python migration.
