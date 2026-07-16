# Ticket: fatigue-profile-rename

## Summary

Rename and reframe lift **kinematic profile** as **fatigue profile** (High /
Medium / Low), simplify default rest, preserve live app data via a real Room
migration, and align user-facing (and internal slot) "Isolation" language with
**Accessory**. Plan as slices suitable for sequential implementation.

## Problem

1. `KinematicProfile` (AxialCompound / NonAxialCompound / Isolation) is used as a
   **programming / fatigue cost** band, but is named like biomechanics.
2. Default rest uses `Lift.isCompound` (multi-pattern or compound kinematic), so
   NonAxial gets 3 min rest; product wants **High → 180s, Medium+Low → 120s**.
3. "Isolation" overloads true single-joint work, low-fatigue accessory work, and
   `SlotRole.Isolation` programming lanes that already admit NonAxial compounds.
4. Maintainer actively uses the app; schema changes need a **Room migration**,
   not wipe/reinstall.

## Agreed product decisions (from design conversation)

| Topic | Decision |
|-------|----------|
| Rest defaults | High → 180s; Medium + Low → 120s. Override via `defaultRestSeconds` only. Rest must not use `isCompound`. |
| Enum rename | `KinematicProfile` → `FatigueProfile`; values `High` / `Medium` / `Low` (was Axial / NonAxial / Isolation). Property `fatigueProfile`. |
| UI | Label "Fatigue profile"; tooltips with examples per tier. Low is **not** "single-joint only." |
| Room / remote | **Wipe** device + Firestore (approved 2026-07-16). Schema/wire 1:1 new names; no legacy dual-read balloon. |
| Slot / filter language | Switch Isolation → **Accessory** (user-facing categories + `SlotRole.Isolation` → `Accessory`). |
| Golden keys | **Break and update** goldens when role name appears in universe keys (`...\|Isolation\|...` → `...\|Accessory\|...`). No alias. |
| Linear progression | Real rule is increment/working-weight % friendliness — **out of scope for implementation in this ticket** except documenting interim gate. Do not invent full % rule here unless plan phase scopes a thin interim only. |

## Interim Linear note (not the proper fix)

Proper Linear eligibility ≈ small enough `increment / workingWeight`. Compound
was a proxy. This ticket may leave `isCompound` gating for Linear as-is, or
document a temporary High\|Medium heuristic — product to confirm in design.
Full threshold-based LP eligibility is a **follow-on**.

## In scope

1. Rest simplification (High 180 / else 120).
2. Full rename kinematic → fatigue (domain, data, seed-lifts, UI, tooltips, tests).
3. Room/schema rename for enum/column (wipe install; no data migration / legacy maps).
4. Accessory rename: filter chips / `LiftCategory` display (and domain enum if
   structure chooses rename vs display-only); `SlotRole.Isolation` → `Accessory`
   with golden updates.
5. Create-lift defaults / soft invariants where structure says they belong
   (major patterns default Medium not Low, etc.).
6. Sliced implementation plan (synthitect structure + plan phases).

## Out of scope

- Full Linear eligibility by increment/load ratio (follow-on).
- Collapsing `isCompound` / `isCompoundForCategory` beyond what rest/rename force
  (may leave thin adapters).
- Fractional muscle models, CP-SAT math redesign.
- Optional biomechanical facts (`spineLoaded` + joint scope) derived tier model.

## Constraints

- Read `AGENTS.md`, `contracts/ARCHITECTURE.md`, `contracts/DATA_LAYER_RULES.md`,
  `contracts/UI_DESIGN.md` (for Compose/tooltip surfaces).
- Maintainer will wipe local DB + Firestore; prefer clean rename without legacy
  remap layers (reverts earlier "preserve live data" preference).
- No MockK on owned ports; JUnit 5 + Turbine + fakes.
- Detekt `maxIssues: 0`; domain models not passed to composables.

## Success criteria

- Approved research → design → structure → plan (`spec.md` + `test_spec.md`)
  with clear slices (rest, rename+migration, accessory, UI tooltips, defaults).
- Migration strategy preserves existing programs on maintainer device.
- Goldens updated for Accessory role keys (no Isolation alias).
- Linear % rule captured as follow-on, not blocked work.
