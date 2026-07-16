# Design: fatigue-profile-rename

Reframe lift loading/recovery cost as **Fatigue profile** (High / Medium / Low),
simplify default rest off that tier, migrate live Room data, and rename Isolation →
Accessory in filters and session slots. Linear eligibility by increment/load % is a
follow-on; this design only freezes an interim gate.

Grounded in `plans/fatigue-profile-rename/research.md` and re-checked call sites under
`core/domain`, `core/data`, create-lift UI, and generation materialize.

---

## Current state

### Domain classification

- `KinematicProfile` has three values: `AxialCompound`, `NonAxialCompound`, `Isolation`
  (`KinematicProfile.kt`). Documented as a catalog loading category for recovery mapping
  into `LiftStressClass` (HeavyAxial / UpperCompound / Isolation).
- Domain `Lift.kinematicProfile` defaults to `Isolation`; optional `defaultRestSeconds`
  override (`Lift.kt`).
- `inferKinematicProfile` fills gaps for custom/import: empty or isolation patterns →
  Isolation; free-weight squat/hinge names → Axial; else NonAxial (`InferKinematicProfile.kt`).
- Generation carries the same enum on `LiftContext`, synthesis wire, and slot
  `allowedKinematicProfiles` as enum `.name` strings (research F5).

### Rest

- Constants: compound 180s, isolation 120s (`DEFAULT_COMPOUND_REST_SECONDS` /
  `DEFAULT_ISOLATION_REST_SECONDS`).
- `resolvedDefaultRestSeconds()` = override **or** `isCompound` → 180 else 120.
- `Lift.isCompound` = multi-pattern **or** Axial/NonAxial kinematic (research F7).
- Add-lift / live-overlay / create-program editor paths use the resolver; **program
  generation materialize always writes `DEFAULT_LIFT_SET_REST_SECONDS` (120)** and never
  consults the lift resolver (`ProgramDomainMapper.kt`). Seed catalog has no
  `defaultRestSeconds` overrides (research F8–F9).

### Dual “compound” semantics

| API | Purpose today |
|-----|----------------|
| `Lift.isCompound` | Rest + Linear UI toggle + programming that must match logged rest |
| `isCompoundForCategory` | Broader Gym/swap filters (`MAJOR_COMPOUND_PATTERNS`) |
| Local kinematic-only flags | Assembler progression, slot selector bonuses, catalog quality |

Linear uses boolean `isCompound` only (`schemeForTrainingAge` in `ProgramRules.kt`); no
increment/load % rule exists (research F28).

### Persistence and sync

- Room `StratumDatabase` version **1**, `exportSchema = true`, **no** production
  `Migration` classes; `fallbackToDestructiveMigration(dropAllTables = true)` if version
  bumps without a migration (research F10, F14).
- Columns: `lifts.kinematicProfile` TEXT NOT NULL (entity default Isolation);
  `logged_sets.kinematicProfile` TEXT NOT NULL (entity default NonAxialCompound).
- Converters: store enum `.name`; unknown → null via `runCatching` (research F12). Legacy
  alias pattern exists only for onboarding step `Generate` → `Review`.
- Sync codecs encode/decode `kinematic_profile`; unknown remote names fall back to
  Isolation (lifts) or NonAxialCompound (logged sets) (research F33).
- Schema contract asserts `1.json` exists and **forbids** `2.json`/`3.json` (research F13).

### Seed catalog

- 266 seed lifts: NonAxial 131 / Isolation 125 / Axial 10. Seeder defaults missing profile
  to Isolation. Re-seed only when seed count is 0 (research F15–F17) — upgrades with a
  populated catalog **do not** rewrite profiles from JSON alone.

### Isolation language

- `LiftCategory` variants `IsolationUpper` / `IsolationLower` with display “Upper/Lower
  Isolation” (research F18–F20).
- `SlotRole.Isolation` in session templates, solver role priority, and universe keys
  `pattern|muscle|role|…` — goldens pin many `|Isolation|` keys (research F21–F23).
- Isolation slots already allow-list Isolation **and** NonAxialCompound (research F24).
- Create-lift labels: “Compound -- Spine Loaded”, “Compound -- No Spine Loading”,
  “Isolation”; field title “Kinematic profile” (research F29–F30). Draft defaults to
  Isolation + 120s rest.

### LiftStressClass / recovery math

- Internal recovery enum still named `Isolation` / `UpperCompound` / `HeavyAxial`
  (`MuscleRecoveryReference.kt`). Not user-facing. Mapping is kinematic → stress class only
  on the direct path (research F4).

---

## Desired end state

Observable after all slices land:

1. **Fatigue profile** — Domain type `FatigueProfile` with values `High`, `Medium`, `Low`
   (was Axial / NonAxial / Isolation). Property `Lift.fatigueProfile` (and the same name on
   context, entities, seed JSON field, wire, snapshots). No remaining production references
   to `KinematicProfile` / `kinematicProfile`.
2. **Default rest** — With no `defaultRestSeconds` override: **High → 180s**; **Medium and
   Low → 120s**. Rest resolution **does not** call `isCompound`. Override path
   (`SetLiftDefaultRestUseCase`, 0–600s) unchanged in behavior.
3. **Catalog + generation rest** — Paths that create sets from a known lift use
   `resolvedDefaultRestSeconds()` (add/swap/edit/live). **Materialize of generated programs
   also applies per-lift resolved rest** when the lift catalog is available (High compounds
   get 180 on template sets, not a blanket 120).
4. **Local + remote reset (approved wipe)** — Maintainer clears device app data and
   Firestore. No production legacy dual-read, no value-remap migration balloon. Schema
   and wire are **1:1 with new names only** after wipe + reseed.
5. **Remote sync** — Encode/decode `fatigue_profile` with `High`/`Medium`/`Low` only.
   No `kinematic_profile` dual-key support.
6. **Accessory language** — User-facing category chips/labels say “Upper Accessory” /
   “Lower Accessory”. `SlotRole.Accessory` replaces `Isolation`; universe keys and goldens
   use `|Accessory|` with **no** Isolation alias. Library group identifiers renamed
   accordingly (e.g. `LOWER_ACCESSORIES`).
7. **UI** — Field label “Fatigue profile”; options High / Medium / Low with short tooltips
   (examples; Low is not “single-joint only”).
8. **Create / infer defaults** — Empty patterns → Low; true isolation-style patterns → Low;
   free-weight axial squat/hinge names → High; other multi-joint / major patterns →
   **Medium** (not Low). Soft re-inference on name/pattern change remains.
9. **Linear (interim)** — Still gated by a compound-style boolean derived from fatigue +
   multi-pattern (see D6). Full increment/working-weight % rule is **follow-on**, not
   implemented here.
10. **Tests / contracts** — Domain and data tests updated; goldens broken and rewritten;
    schema contract keeps wipe-friendly `1.json` with new column/value shapes (structure);
    detekt clean.

---

## Patterns to follow

| Pattern | Source | Apply to |
|---------|--------|----------|
| Enum `.name` as Room TEXT + `EnumConverters` | `EnumConverters.kt`, `DATA_LAYER_RULES.md` | `FatigueProfile` converters (new names only) |
| Domain property defaults + seed seeder fallback | `Lift.kt`, `LiftCatalogSeeder` | Default `Low`; missing seed field → `Low` |
| Pure rest resolver on `Lift` | `resolvedDefaultRestSeconds()` | Fatigue-tier rest; keep override first |
| Presentation labels off domain enums | `LiftDetailsPresentation`, `LiftCategory.displayName` | Fatigue labels + Accessory display |
| Universe key = `role.name` segment | `SlotLibrary` / `SlotUniverseBuilder` | Accessory keys; update goldens in place |
| Help copy in string resources | `lift_increments_help`, `manage_equipment_help` | Inline fatigue field help |
| JUnit 5 + fakes; no MockK on owned ports | `UNIT_TESTING.md`, research §J | Rest, category, goldens, converters |
| Wipe-friendly schema rename | Pre-release wipe culture; design D4 revised | Clean `fatigueProfile` in `1.json`; no data Migration |

---

## Patterns to avoid

| Anti-pattern | Why | Instead |
|--------------|-----|---------|
| Legacy dual-read / alias tables for old kinematic names | Code balloon; maintainer wiping | New names only (D4/D5) |
| Rely on seed re-run to rewrite profiles on upgrade | Seeder gate skips when any seed rows exist (research F17) | Wipe + reseed; rewrite seed asset for fresh installs |
| Keep rest on `isCompound` | Product: High 180 / else 120 only (ticket) | Rest from `fatigueProfile` (+ override) |
| Leave materialize at fixed 120 forever | Generated programs ignore High rest; dual rest systems | Pass catalog (or rest map) into materialize |
| Isolation alias for `SlotRole` / universe keys | Ticket: break goldens, no alias | Hard rename to `Accessory` |
| Rename `LiftStressClass.Isolation` in this ticket | Internal recovery math; not user language; large tangential blast radius | Keep stress-class names; only remap from new fatigue enum |
| Full Linear % rule in this ticket | Explicit follow-on | Interim compound gate (D6) |
| Invent `spineLoaded` / joint-scope derived tier | Out of scope (ticket) | Authored catalog fatigue + existing infer heuristics |
| Copy pre-release “stay v1 / uninstall” comments as the policy for this change | Ticket authorizes real migration | Update schema contract + DI comments to v2 + migration |

**Legacy copy risk:** Agents must not reintroduce `resolvedDefaultRestSeconds` based on
`isCompound`, seed defaults of Isolation for major-pattern create-lift drafts after
inference, or schema tests that forbid `2.json`.

---

## Design decisions

### D1 — Enum and property rename (value map)

**Choice:** Replace `KinematicProfile` with `FatigueProfile`:

| Legacy | New |
|--------|-----|
| `AxialCompound` | `High` |
| `NonAxialCompound` | `Medium` |
| `Isolation` | `Low` |

Property and field names: `fatigueProfile` / `fatigue_profile` (Room/sync) consistently.
Stress mapping stays: High → HeavyAxial, Medium → UpperCompound, Low → Isolation
(`LiftStressClass` **unchanged** names). Inference API renames to `inferFatigueProfile`
with the same rule shape, outputs new enum.

**Rejected:** Keep type name `KinematicProfile` with only renamed values (still biomechanics
wording). Rejected: four-tier or continuous fatigue score. Rejected: renaming
`LiftStressClass` variants in this ticket.

### D2 — Rest resolution

**Choice:**

```text
resolvedDefaultRestSeconds =
  defaultRestSeconds ?: (if fatigueProfile == High then 180 else 120)
```

Rename constants to fatigue-aligned names (e.g. `DEFAULT_HIGH_FATIGUE_REST_SECONDS` /
`DEFAULT_STANDARD_REST_SECONDS`). `DEFAULT_LIFT_SET_REST_SECONDS` (120) remains the
fallback when **no lift** is known (unknown catalog id).

**Rejected:** Rest from `isCompound` (current). Rejected: Medium → 180 (ticket: only High).
Rejected: per-set rest migration rewriting existing template `rest_seconds` rows (preserve
logged/template integers; only **future** resolution changes).

### D3 — Generation materializer rest (in scope)

**Choice:** Fix materialize so template sets get per-lift resolved rest when lifts are
available. Signature of the domain map step gains a lift lookup (id → `Lift` or id → rest
seconds); each set uses `resolvedDefaultRestSeconds()` for that `liftId`, else 120.

**Rationale:** Research F8 shows generation always stamps 120. Leaving that would make the
product rest rule invisible on the primary “generate program” path while add-lift paths
obey it.

**Rejected:** Leave hardcode 120 as intentional generation default (creates two rest
policies). Rejected: bulk UPDATE of existing program set rest on migration (user may have
edited rest; only new materialize / new adds change).

### D4 — Room schema (wipe; no legacy migration balloon) — **revised on design approval**

**Choice:** Rename columns and enum values to `fatigueProfile` / High|Medium|Low in
entities, converters, and exported schema. **Do not** ship SQL value remaps, dual-name
TypeConverters, or dual JSON keys for kinematic legacy.

Persistence approach (structure picks one; both are wipe-compatible):

- Prefer **keep version 1** with rewritten `1.json` + destructive reinstall (matches current
  pre-release wipe culture and avoids forbidding-`2.json` churn), **or**
- Bump to **2** with `fallbackToDestructiveMigration` only and a clean `2.json` — **no**
  `Migration(1, 2)` data rewrite.

Domain defaults after wipe: `FatigueProfile.Low` on `Lift`; logged snapshot default
**Medium** (OQ2).

**Rejected (superseded):** Real `Migration(1, 2)` with CASE remaps and dual-read converters
— maintainer chose wipe of device + Firestore to avoid legacy code bloat (2026-07-16).

### D5 — Firestore / remote (wipe; 1:1 new schema) — **revised on design approval**

**Choice:** Wire codecs use key `fatigue_profile` and values `High`/`Medium`/`Low` only.
No `kinematic_profile` dual-read, no legacy enum name maps. Maintainer clears remote data
so remote stays 1:1 with new code.

Unknown/null decode fallbacks remain simple: lift → Low; logged set → Medium (OQ2) —
normal defensive defaults, not a legacy alias table.

**Rejected (superseded):** Shared pure remap of AxialCompound/… and dual-key decode for
one release.

### D6 — Interim Linear / `isCompound` gate

**Choice:** Keep a `Lift.isCompound` (or identically named) boolean for Linear toggle and
`schemeForTrainingAge(..., isCompound)` with semantics:

```text
isCompound = movementPatterns.size > 1
  || fatigueProfile == High
  || fatigueProfile == Medium
```

That preserves pre-rename behavior (Axial|NonAxial → compound) after the enum swap.
`isCompoundForCategory` still ORs `MAJOR_COMPOUND_PATTERNS`. Rest **does not** use this flag
(D2). Document follow-on: Linear when `increment / workingWeight` is small enough.

**Rejected:** Interim Linear only for High (would strip Medium/NonAxial compounds from Linear
vs today). Rejected: implement full % rule in this ticket. Rejected: gate Linear on
`isCompoundForCategory` (broader filter hardening must not expand Linear eligibility).

### D7 — Accessory rename (SlotRole + LiftCategory + goldens)

**Choice:**

- `SlotRole.Isolation` → `SlotRole.Accessory` everywhere (templates, solver, promotion
  Optional→Accessory, allow-lists, identifiers).
- Universe keys change role segment; **update golden sets** (break CI intentionally; no
  dual-accept Isolation keys).
- `LiftCategory.IsolationUpper` / `IsolationLower` → `AccessoryUpper` / `AccessoryLower`;
  `displayName` → “Upper Accessory” / “Lower Accessory”.
- Rename library groups `LOWER_ISOLATIONS` / `UPPER_ISOLATIONS` → Accessory equivalents.
- Slot allow-lists: Low + Medium (was Isolation + NonAxial). Ranking that preferred true
  Isolation prefers Low first.

**Rejected:** Display-only string change without renaming `SlotRole` (ticket requires role
rename). Rejected: alias Isolation≡Accessory in goldens or solvers.

### D8 — Create-lift defaults and inference

**Choice:**

- Draft initial profile: **Low** only until patterns/name drive inference (same empty-start
  as today, new name).
- On pattern/name re-inference: isolation pattern set → Low; free-weight axial squat/hinge
  → High; **any other non-empty pattern set including major compounds → Medium** (so major
  patterns never stick on Low after commit). Empty patterns stay Low.
- Rest draft tracks resolver (High 180 / else 120) unless user override.
- Presentation: High / Medium / Low labels + tooltips (string resources; examples that Low
  includes light accessories and is **not** “single-joint only”).

**Rejected:** Default draft Medium always (wrong for curls/extensions). Rejected: Low =
  single-joint invariant enforced in domain (product explicitly rejects that framing).

### D9 — Schema testing approach — **revised on design approval**

**Choice:** No MigrationTestHelper and **no** legacy remap unit matrix. Verify with:

1. Updated schema contract / exported schema JSON for the chosen version strategy (D4).
2. Converter tests for **new names only**.
3. Domain rest / category / golden tests for new enums and Accessory keys.
4. Maintainer smoke: wipe app + Firestore, reinstall, reseed, regenerate or re-import program.

**Rejected:** Legacy alias test matrix (no legacy path). Rejected: MigrationTestHelper for
this wipe-based ticket.

### D10 — Seed asset and seeder

**Choice:** Rewrite `seed-lifts.json` field and values to `fatigueProfile` High/Medium/Low
(bijective map of current distribution). Seeder maps missing → Low. Fresh install after
wipe reads new JSON only.

**Rejected:** Leaving seed asset on old field names (fresh install would write wrong schema).

### D11 — Scope of `isCompound` collapse

**Choice:** Minimal adapters only: rest leaves `isCompound`; fatigue renames update the
kinematic branches inside `isCompound` and local High|Medium checks. No broader unification
of assembler / quality / category APIs beyond rename compilation.

**Rejected:** Delete `isCompound` in this ticket.

---

## Slice framing (for later structure — not a task DAG)

Logical product slices (structure will order TDD):

| Slice | Intent |
|-------|--------|
| Rest | Resolver + constants + tests; stop using `isCompound` for rest |
| Rename + schema | Domain/data/seed/wire rename; Room entities/converters; sync 1:1 new names; wipe install |
| Materialize rest | Generation path uses resolver when catalog present |
| Accessory | SlotRole, LiftCategory, library groups, goldens, allow-lists |
| UI + defaults | Labels, tooltips, create-lift/infer Medium for majors |
| Interim Linear note | Keep compound gate; document follow-on % rule in plan only |

---

## Open questions — resolved (2026-07-16)

### OQ1 — Dual-read remote key name → **wipe; no dual-read**

Maintainer will wipe device app data **and** Firestore. No dual-key decode, no legacy
value maps. Wire and Room are 1:1 with `fatigue_profile` / High|Medium|Low (D4/D5 revised).

### OQ2 — Logged-set unknown default → **Medium**

Prefer Medium for defensive decode of garbage/null logged-set fatigue. With wipe, impact is
low; still the locked default for converters.

### OQ3 — Existing template set rest → **irrelevant under wipe**

No migration rewrites `rest_seconds`. After wipe, new programs get resolver + materialize
rest (D2/D3). No bulk UPDATE of old template rest.

### OQ4 — Tooltip chrome → **inline help if not bloated**

Use short **inline supporting text** under Fatigue profile (equipment-help style) only if
it fits without crowding the create/edit surface. No new info-icon tooltip component in
this ticket. If space is tight, one compact multi-line help string is enough.

---

## Follow-on (explicitly not this ticket)

- Linear eligibility by `increment / workingWeight` (replace interim D6 gate).
- Optional deeper collapse of `isCompound` / category / assembler flags.
- `LiftStressClass` user-language cleanup if ever exposed.
- Room data migrations / dual-read if a future release cannot wipe.
- MigrationTestHelper harness for multi-version chains.
- Biomechanical fact model (`spineLoaded`, joint scope) replacing authored tiers.

---

## Self-review

- Current state matches research F1–F41 and re-checked materialize/rest paths.
- OQ1–OQ4 resolved 2026-07-16; D4/D5/D9 revised for wipe (no legacy balloon).
- Rejected alternatives named on D1–D11.
- No runnable implementation code; signatures described at decision level only.
