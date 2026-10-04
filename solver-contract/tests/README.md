# Solver contract tests (STR-47)

Pytest suite enforcing `solver-contract/VERSIONING.md` §6 at the JSON boundary:
Kotlin-produced request fixtures in, solver response out. Run from the repo root:

```sh
pip install -r solver-contract/tests/requirements.txt
python -m pytest solver-contract/tests/ -q
```

No `__init__.py` here on purpose: tests import siblings (`contract`,
`solver_client`, `stub_solver`) off this directory, which pytest puts on
`sys.path` for a rootdir-relative run.

## Layout

| File | Role |
|---|---|
| `contract.py` | Discovery (`schema/v<N>/` → majors), schema loading, `modelVersion` parsing. Majors are **never** listed in test code. |
| `assertions.py` | Shared response assertions (schema validity + structure) so test modules stay leaves with no test-to-test imports. |
| `solver_client.py` | The solver entrypoint (used by every test that solves). |
| `stub_solver.py` | Canned dev solver: contract-valid v1 response shaped by the request's day count. NOT a real solve. |
| `test_fixture_validity.py` | §6.1 — every fixture validates against its major's schema. |
| `test_roundtrip.py` | §6.3 — fixture → solver → response validates + structural checks. |
| `test_leniency.py` | §6.4 — the language-agnostic §2 rows. |
| `test_version_matrix.py` | §6.5 — auto-extension proofs (incl. simulated v2/v9 on scratch trees). |
| `test_entrypoint.py` | Entrypoint transports against the stub + a stdlib fake HTTP server. |

Out of scope here (other agents): Kotlin producer conformance (§6.2) and the
Kotlin decode half of §6.3/§6.4 (`decodeResult`, `regionFilter` inference,
`Isolation`→`Accessory`, fatigue-name/default rows on the read side). The
request-side default rows are covered only as boundary tolerance
(`test_request_side_defaults_tolerated`); semantic correctness of the defaults
is the real solver's unit-test job.

## Solver entrypoint contract (for integration)

Tests reach the solver through `solver_client.solve_request(request_dict)`:

1. **In-process (default).** Import `$SOLVER_MODULE` (default `stub_solver`),
   call `solve(request_dict) -> response_dict`. A real solver module MUST:
   - accept the request dict; IGNORE unknown fields, treat missing
     `modelVersion` as v1, accept `v<major>.<minor>` as its major;
   - return a response validating against `schema/v<major>/response.schema.json`
     with the request's major in its `modelVersion`;
   - raise `ValueError` mentioning "unsupported major" for unknown majors
     (a subclass is fine — tests match `ValueError` + message).
   - expose no other required API (a `SUPPORTED_MAJORS` list is optional).
2. **HTTP.** When `$SOLVER_URL` is set (it wins over `$SOLVER_MODULE`),
   `POST $SOLVER_URL/solve/v<major>` with the request as JSON body, where
   `<major>` is the request's dispatch major (missing counts as v1 → only
   `/solve/v1`). Servers MUST reject a body whose major differs from the URL
   major and MUST use **4xx** for unsupported majors (client surfaces 4xx as
   `ValueError`, 5xx as `RuntimeError`).

To wire the real solver: `SOLVER_MODULE=path.to.module pytest ...` (importable,
e.g. via `PYTHONPATH`) or `SOLVER_URL=http://host:port pytest ...`. The suite
must stay green against either — that is the integration gate.

## How to add v2 (no test-code changes)

1. Add `solver-contract/schema/v2/request.schema.json` +
   `response.schema.json` (contract change lands alone, per VERSIONING.md §7).
2. Add `solver-contract/fixtures/v2/*.request.json` (+ `*.result.json`).
   Without these the matrix guard fails loudly — that is intentional.
3. Give the solver a v2 handler (stub: add `2: solve_v2` to `HANDLERS`; real: same entrypoint).
   Without it the per-major solve check fails loudly.
4. Run the suite: every parametrized test extends to v2 by discovery.

`test_matrix_auto_extends_without_code_changes` proves this on a scratch
tree (fake v1+v2 schemas); `test_discovered_major_without_fixtures_fails_loudly`
proves the missing-fixture failure with a fake v9.
