# v1 fixtures — provenance and reading guide

## Provenance

No production `*.request.json` / `*.result.json` traces were available (the
`~/workspace/stratum-trace/` directory does not exist), so these pairs were generated from
the production Kotlin code itself:

- **Requests**: encoded by `ProgramSynthesisWire.encodeRequest` from synthetic scenario
  contexts (`scenarioPayload` + `planWeeklyVolume`), then stamped with
  `"modelVersion": "v1"` and pretty-printed. Byte-shape (key presence, nulls, number
  forms) matches what the production producer uploads to
  `synthesis/{uid}/{jobId}/request.json`, except for the added `modelVersion`.
- **Responses**: solved by the production Kotlin `CpSatProgramSynthesizer` (20 s
  deadline) and encoded by `ProgramSynthesisWire.encodeResult`, then stamped and
  pretty-printed the same way.
- Synthetic inputs only (catalog lifts, no user data) — nothing to anonymize.
- Generated 2026-10-03 on `main` at the STR-44 branch point.

If real production traces surface later, anonymize and add them here (re-validate first);
encoder-generated pairs stay as the minimal regression anchors.

## The pairs

| Pair | Days | Specialization | Session min | Cycle space |
|---|---|---|---|---|
| `3day-fullbody-maint` | 3 (MWF) | none (all Maintenance) | 60 | omitted (server defaults) |
| `4day-arms-spec` | 4 (sequential) | Bicep + Tricep Specialize | 75 | explicit, 7-slot |
| `5day-shoulders-spec` | 5 (MTWFS) | LateralDeltoid + AnteriorDeltoid Specialize | 75 | explicit, 7-slot |
| `6day-rotating-cycle8` | 6 (sequential) | Chest + Back Specialize | 90 | explicit, 8-slot rotating |

## How to use them

- **Shape anchors**: every file MUST validate against `schema/v1/*.schema.json` (CI).
  They pin the exact JSON shape — key sets, enum spellings, nullability, slot-id
  formats (`s{session}_{universeId}` in plans, bare universe ids in
  `activeSlotIdsBySession`).
- **NOT numeric oracles**: the `*.result.json` files record the OLD Kotlin solver's
  output, including the STR-39 defect (small-muscle isolation scheduled back-to-back —
  see LateralDeltoid across all 5 sessions of `5day-shoulders-spec`). The Python
  solver's output on these requests MUST differ there: low-tier small-muscle isolation
  requires 48 h before the next meaningful exposure of that muscle. Reproduce the
  structure, not the sets.
- **Round-trip inputs**: feed any `*.request.json` to the Python solver; the response
  MUST validate against `response.schema.json` and decode in Kotlin.
