# Solver Contract — Versioning Policy

Contract-first boundary between the Kotlin request producer and the program-synthesis
solver. The boundary is a **JSON payload contract**, not shared code: either side may be
reimplemented in any language as long as it honors the schemas and this policy.

- Schemas: `solver-contract/schema/<major>/request.schema.json`,
  `solver-contract/schema/<major>/response.schema.json` (JSON Schema draft 2020-12).
- Regression anchors: `solver-contract/fixtures/<major>/` — anonymized request/response
  pairs. Every fixture MUST validate against its major's schemas.
- This file is normative for producer, solver, dispatcher, and CI changes.

## 1. modelVersion semantics

- Every solver request and solver response payload carries `modelVersion`, formatted
  `v<major>` or `v<major>.<minor>` (e.g. `v1`, `v2`, `v1.1`).
- **Producers MUST emit `modelVersion`** on every request. The solver MUST emit it on
  every response.
- **Missing means v1.** Pre-versioned Kotlin wire payloads have no `modelVersion` field;
  servers MUST treat a missing field as `v1`, and clients MUST tolerate a missing field
  in responses (pre-versioned solver results during rollout).
- **Dispatch is on the major.** `v1` and `v1.1` route to the same v1 handler; the minor
  only records backward-compatible additions (see §5).
- **Unknown majors are rejected, not guessed.** A server receiving a request whose major
  it does not support MUST fail the job with a user-safe error (never attempt a
  cross-major solve). A client receiving a response whose major it cannot decode MUST
  surface generation failure (never render a half-parsed program).

## 2. Decoder leniency (v1)

Schemas are strict (`additionalProperties: false`) so CI catches drift, but **decoders
MUST be lenient** so minor additions and rollout skew never break the other side:

| Situation | Required behavior |
|---|---|
| Unknown top-level or nested field | Ignore it. |
| Request/response without `modelVersion` | Treat as `v1` (§1). |
| `modelVersion` with unknown minor (`v1.9`) | Accept as v1; ignore unknown fields. |
| `modelVersion` with unknown major (`v3`) | Reject (§1). |
| Response without `regionFilterBySession` | Infer from `sessionType` labels (legacy map in `ProgramSynthesisWire`). |
| `role: "Isolation"` on the wire | Map to `Accessory` (legacy alias). Producers MUST emit canonical names only. |
| Unknown `allowedFatigueProfiles` names | Drop the unknown names; empty result means null (pattern match only). |
| Missing `fatigueProfile` / `incrementTiers` | Default to `Low` / `[]`. Producers MUST emit them. |
| Missing `restSlotIndices` / `cycleLength` | Apply server cycle-space defaults. |
| Free-form strings (`trainingAge`, `lastTrained`, `posture`, `evidence`, …) with unknown values | Tolerate; never crash. Known v1 values are listed in the request schema descriptions. |

Python and Kotlin decoders MUST implement every row. Contract tests cover each row with
a dedicated fixture or case (see §6).

## 3. Server dispatch and endpoints

- The authoritative version signal is the payload's `modelVersion`, not the URL. The
  job-based transport (`synthesis/{uid}/{jobId}/request.json` → `/process {uid, jobId}`
  → `synthesis/{uid}/{jobId}/result.json`) carries any supported major; the worker
  reads `modelVersion` from the downloaded `request.json` and dispatches to the matching
  handler.
- Direct HTTP endpoints SHOULD be versioned per major (`POST /solve/v1`, `POST /solve/v2`)
  and MUST validate that the body's `modelVersion` major matches the URL major
  (missing counts as `v1` and matches `/solve/v1` only).
- Handlers are additive: adding v2 support MUST NOT change v1 handler behavior. Shared
  code between handlers MUST be version-agnostic (pure helpers) or explicitly branched.
- The dispatcher (`startProgramSynthesis` / `processProgramSynthesisJob`) is
  version-transparent: it forwards job metadata and never parses the payload, except it
  MAY record `modelVersion` on the job doc for observability.

## 4. Support window and deprecation

- The server MUST support the current major **N** and the previous major **N-1** at all
  times. Supporting older majors is allowed but not required.
- A major version MAY be removed only when ALL of these hold:
  1. No supported client build emits it by default (the Remote Config rollout in §5 is
     at 100% on the next major, plus one full release cycle).
  2. Server logs show < 1% of successful solves on that major over the trailing 30 days.
  3. The removal is announced in the parent migration/tracking issue and the schemas
     directory keeps the retired schema files (read-only, for forensics) while fixtures
     for the retired major move to `fixtures/_retired/<major>/`.
- Dropping N-1 support is a major, explicitly reviewed change — never drive-by.

## 5. Rolling out a new schema version

Client builds update slowly (Play rollout), so new versions roll out behind Remote Config.
Conventions follow `AppFeatureFlag` (boolean flags, `solver_*` key prefix, fail closed).

To introduce major N+1:

1. **Land the contract first.** Add `schema/v<N+1>/` + `fixtures/v<N+1>/`, extend this
   file's leniency table if needed. Do NOT change `schema/v<N>/` behavior.
2. **Ship server support.** Deploy the solver with N and N+1 handlers. CI must be green
   on both majors (see §6) before this deploys.
3. **Ship a client that can emit N+1 but defaults to N.** Gate emission on a launch flag
   `solver_emit_model_v<N+1>` (default `false`). When the flag is off, the client emits
   N exactly as before. The client MUST still decode N responses (server may serve N
   during incidents via the kill-switch below).
4. **Ramp the flag** in the Firebase console (internal → 1% → 10% → 50% → 100%),
   watching solve success rate and `modelVersion` distribution per major.
5. **Keep a kill-switch.** A flag `solver_allow_model_v<N+1>` (default `true`) lets
   on-call force the fleet back to N without an app release: when off, clients emit N
   even if the launch flag is on. An unconfigured kill-switch MUST NOT disable its path.
6. **Deprecate N** per §4 once the window criteria hold.

**Minor revisions** (`v1` → `v1.1`): allowed only for backward-compatible changes —
new optional fields, relaxed constraints, new enum values that old readers can safely
ignore or default. Minor bumps update the v1 schema files in place, add the new value
to the `modelVersion` pattern implicitly (`^v1(\.\d+)?$` already accepts it), and note
the change in the contract changelog (append below). They MUST NOT require a Remote
Config rollout, and old binaries MUST keep working. Anything else is a new major.

## 6. What CI enforces (STR-47)

The contract test suite fails the pipeline on any of these:

1. **Fixture validity.** Every `fixtures/<major>/*.request.json` validates against
   `schema/<major>/request.schema.json`; every `*.response.json` against the response
   schema. Checked for EVERY supported major, not just the latest.
2. **Producer conformance.** The Kotlin producer's `encodeRequest` output for a fixed
   seed context validates against the request schema, byte-shape included (required
   keys present, `modelVersion` emitted).
3. **Round-trip.** Kotlin producer output → Python solver → response validates against
   the response schema AND decodes in Kotlin (`ProgramSynthesisWire.decodeResult`)
   without loss of required fields.
4. **Leniency rows.** One test per §2 row (unknown fields ignored, missing version
   defaults, legacy aliases mapped, unknown major rejected).
5. **Version matrix.** The harness enumerates supported majors from the
   `solver-contract/schema/` directory: adding `schema/v2/` with no test changes MUST
   automatically extend the matrix (tests fail if fixtures or handlers are missing for
   a discovered major).
6. **Drift detection.** Schemas use `additionalProperties: false`: if either side's
   encoder emits a field the schema does not allow, the round-trip tests fail until the
   contract is amended (§5) — silent drift is impossible.

## 7. Coordination rule

`solver-contract/` is frozen during parallel implementation. If an implementer finds the
contract insufficient, they STOP and report the gap to the orchestrator instead of
working around it. The orchestrator amends the contract (minor bump or new major per
§5), and all implementers rebase onto the amended contract. No implementation PR may
change `solver-contract/` and implementation code in the same merge — contract changes
land first, alone.

## 8. Transport reference (informative)

Current production flow (v1 payloads; unchanged by the Python migration except for the
worker internals):

1. Android `FirebaseProgramSynthesisAdapter.synthesize()` encodes the request
   (`ProgramSynthesisWire.encodeRequest`) and uploads
   `synthesis/{uid}/{jobId}/request.json` to Cloud Storage.
2. The app calls `startProgramSynthesis(jobId, requestStoragePath, clientStartMs)`;
   the Node dispatcher POSTs `/process {uid, jobId}` to the Cloud Run worker.
3. The worker downloads `request.json`, solves, uploads
   `synthesis/{uid}/{jobId}/result.json`, and marks the job `ready`.
4. The app polls `getProgramSynthesisStatus` until terminal, downloads `result.json`,
   and decodes it (`ProgramSynthesisWire.decodeResult`).

Auth (Google Sign-In), App Check, per-user rate limits, and the monthly
program-generation cap stay as-is; they are orthogonal to payload versioning.

## 9. Changelog

- 2026-10-03 — v1 initial contract (STR-44). Freezes the pre-versioned Kotlin wire
  shape (`ProgramSynthesisWire`) as v1 and adds `modelVersion`. Fixtures are
  encoder-generated from synthetic scenario contexts (no production traces were
  available); see `fixtures/v1/README.md` for provenance. No production trace dir
  (`~/workspace/stratum-trace/`) exists — if real traces surface later, add them as
  fixtures after anonymization and re-validate.
