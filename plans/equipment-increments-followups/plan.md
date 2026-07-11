# Equipment increments follow-ups — implementation plan

**Status:** implemented on branch (2026-07-11) — PR-A through PR-F landed; product decisions locked  
**Branch context:** builds on `feature/equipment-level-increments` (equipment catalog, dual-parent `increment_tiers`, Manage Equipment UI)  
**Date:** 2026-07-11

### Product decisions (locked)

| # | Decision | Choice |
|---|----------|--------|
| 1 | Seed “few lifts” vs “many lifts” cutoff (**K**) | **3** — if more than 3 lifts inherit this equipment’s defaults, prefer forking a new custom piece rather than quietly rewriting the shared default. Confirmed good. |
| 2 | Max proposal cards per workout complete | **3** |
| 3 | Two “yes” moments | **Fork / create custom / amend own custom:** one Accept is enough (only signal lifts move). **Amend seed (or any shared default that would hit many inheritors):** one extra confirmation that states blast radius, e.g. “This’ll alter defaults for 12 lifts.” Never silent multi-lift seed change. |
| 4 | “Affects N lifts” includes disabled? | **Yes** — disabled lifts still inherit when re-enabled |
| 5 | Fork-from parent | **Any equipment** (seed or custom) — copy tiers only; **no lineage** back to seed (a custom “Life Fitness Single Cable Stack” is not linked to seed Cables). Zoo risk accepted as low (infrequent gym switches); **archive** deferred as a later mitigation (out of this wave). |

---

## 1. Product narrative (#1–#3 as one wave)

Seed equipment (`cable`, `pin-loaded-machine`, …) is a **starting lattice**, not the user’s gym. Real stacks jump by brand-specific steps (5 lb, 10 lb, 12.5, half-plates, pin + add-on). Lifters already type what they used; Stratum already has:

- shared **equipment** defaults + **lift overrides**
- **custom equipment** create/rename/delete (unreferenced custom only)
- **workout complete** summary (`WorkoutCompleteScreen` / `GetWorkoutSummaryUseCase`)
- progression that **rounds to** effective tiers (`roundToIncrement`)

This wave closes the loop:

1. **Detect** when logged weights disagree with the lattice the app thinks the lift is on.
2. **Propose** the cheapest correct fix: **add/adjust a tier band** on existing equipment **or** **fork a per-machine custom equipment** and reassign only the lifts that produced the signal.
3. **Confirm in seconds** on workout complete (and optionally from Manage Equipment later)—never mid-set, never a multi-screen form chore.
4. **Show blast radius** when editing equipment increments (“Affects N lifts”) so shared defaults stay intentional.
5. **Productize** per-machine customs (“Life Fitness lat stack”) as the natural home for brand-specific lattices—manual + inferred paths, not a new entity type.

**Guiding UX principle:** post-workout proposals are **pre-filled, one-tap, reversible-ish decisions**, not a second onboarding. The user should be able to accept a proposal without typing a name, inventing bands, or walking the full Manage Equipment flow—unless they choose “customize.”

---

## 2. Domain detection design (answers product questions 1–3)

### 2.1 Question 1 — When is a logged weight “impossible” on current increments?

**Definition (on-lattice):** For effective tiers `T` and logged weight `w` (same unit as tiers; bodyweight load type skipped; assisted uses `|amount|` for band lookup like `roundToIncrement`):

```text
residual(w, T) = |w.amount − T.roundToIncrement(w).amount|
onLattice(w, T)  ⇔ residual(w, T) ≤ ε
```

- **ε (v1):** `1e-6` relative to unit, or fixed **0.01** on amount after normalizing display rounding (weights are stored as doubles; UI often shows 1 decimal). Prefer absolute ε = **0.05** lb/kg-equivalent of the weight’s unit so 62.5 vs 5-step is clearly off, while floating noise from 100/3 is not.
- Use **effective** tiers for the lift (`Lift.incrementTiers` after `resolveEffectiveIncrementTiers`), not raw equipment rows alone—overrides already “solved” that lift.
- **Empty tiers** (bodyweight seed, cleared equipment): no lattice signal (skip).
- **Zero weight** on weighted lifts: skip (not a stack signal).

**Session anomaly (primary signal for #1):**

- Collect distinct positive work weights per `liftId` in the completed session (`TrainingLogRepository.getSetsForSession`).
- A lift is **anomalous** if ≥1 logged weight is off-lattice against that lift’s **current** effective tiers.
- Optional strength: off-lattice weight appears on **≥2 sets** or matches a **repeated residual pattern** across the session (reduces one-off typos).

**History optional (v1.1, non-goal for first PR):** same residual check on last N sessions for that lift to promote weak session-only signals. Keep v1 **session-scoped** so analysis stays cheap and offline.

**Do not** treat “user rounded differently than recommendation engine” mid-workout as a prompt. Logging remains free-form; detection is **post-hoc**.

### 2.2 Question 2 — New tier vs new custom equipment?

For each anomaly cluster, domain produces **ranked remedy candidates**. Clustering key:

| Cluster key | When |
|-------------|------|
| `equipmentId` of the lift | Prefer when lift has equipment assigned |
| `unassigned` / seed-fallback | When no equipmentId; treat as “create custom + assign” |

**Within a cluster (same equipment, or multi-lift same seed):**

Gather all off-lattice weights from the session that map to that cluster (and optionally all weights for those lifts so proposed tiers cover the whole range used).

#### Candidate A — **Amend tiers** (same equipment parent)

Build a proposed `List<IncrementTier>` that:

1. Starts from **current equipment default tiers** (not lift override)—we never silently rewrite seed *in place* if the fix would harm other inheriting lifts (see decision below).
2. Infers a **step size** `s` from GCD-like analysis of residual weights and consecutive jumps among logged amounts (see §2.4).
3. Inserts or splits bands so every observed weight is on-lattice and `ValidateIncrementTiersUseCase` accepts the list (contiguous, open final band, non-decreasing increments).

**Safe to apply as “edit this equipment” only when:**

- Equipment is **already custom** (`isCustom == true`), **or**
- Equipment is seed **but** `affectedInheritingLiftCount ≤ K` (recommend **K = 3** for v1) **and** all session anomalies for that equipment come from lifts that already share it, **and** user is not creating a brand-specific machine story (heuristic: only one lift anomalous on a seed shared by many catalog lifts → prefer fork).

**Unsafe / prefer fork for seed:**

- Seed `cable` / `pin-loaded-machine` / `dumbbell` with **many** inheriting lifts (`count > K`)—editing seed defaults would move progression for the whole gym catalog.

#### Candidate B — **Fork custom equipment** (Feature 3 path)

- `addCustomEquipment(name, proposedTiers)` then `setLiftEquipment` for the **signal lifts only** (not all lifts on the seed parent).
- Name prefill heuristics (§3.3): e.g. `"{liftName} stack"`, `"Custom cable"`, or seed name + short suffix when multi-lift: `"Cables (gym)"`.
- Tiers = inferred lattice (or copy of seed tiers then apply residual step refinement).

#### Candidate C — **Lift-only override** (secondary, often hidden)

- `setIncrementTiers(liftId, …)` without new equipment.
- Use when: **single lift**, user already has weird per-lift setup, or anomaly is one-off isolation move.
- **v1 UI default:** prefer B over C for seed parents (product wants per-machine equipment zoo done *intentionally*, not silent lift overrides). Surface C as “Only this lift” advanced chip if needed.

#### Ranking rule (deterministic)

```text
if equipment.isCustom && amendTiers covers all session weights for cluster
  → primary = Amend(equipment), secondary = Fork(new copy)
else if seed && (inheritingCount > K || multi-lift brand split likely)
  → primary = Fork(custom), secondary = Amend(seed) with strong “Affects N lifts” warning
else if single lift anomaly && residual step divides cleanly into one band
  → primary = Fork or Amend(seed) with N=1; tertiary = LiftOverride
else
  → primary = Fork with inferred tiers
```

**“Adding a level would solve 62.5 with only 5-lb open band”:**  
If all anomalous weights fit a finer step `s` (e.g. 2.5) up to `maxObserved + s` and coarser step above, propose multi-band tiers:

```text
0 – U  → s
U+     → oldIncrement   (or inferred coarser)
```

where `U` is chosen as smallest tidy boundary ≥ max fine weight (round to 5/10/25). That is **Amend** when safe, else **Fork** with those tiers pre-filled.

### 2.3 Question 3 — Surface for rapid approve / ignore

**Primary surface: Workout complete** (`feature/workout/.../WorkoutComplete*`), after summary loads—not a blocking gate on Close.

```text
┌ Workout complete ─────────────────────┐
│ duration / PRs / best sets            │
│                                       │
│ ⚙ Equipment updates (optional)        │
│ ┌ proposal card ───────────────────┐  │
│ │ “Lat pulldown looks like 5 lb    │  │
│ │  steps, not generic Cables.”     │  │
│ │ Tiers: 0+ → 5                    │  │
│ │ Lifts: Lat pulldown              │  │
│ │ [Accept] [Only this lift] [Skip] │  │
│ └──────────────────────────────────┘  │
│ (more cards stacked, max 3)           │
│ [Review later]                        │
└───────────────────────────────────────┘
```

**Interaction budget (target &lt; 15s total):**

| Action | Effect |
|--------|--------|
| **Accept** | Apply primary candidate (usually fork + reassign signal lifts, or amend custom tiers). No navigation. Snackbar “Created …”. |
| **Skip** | Dismiss for this session only. |
| **Not now / Review later** | Persist deferral fingerprint (DataStore); hide until new stronger signal or N days. |
| **Expand / Edit** | Optional bottom sheet: rename, toggle which lifts reassign, switch candidate (Fork vs Amend vs Lift-only). **Not** full tier editor unless user taps “Edit bands”. |
| Mid-set / live workout | **Never** show. |

**No new Room tables/columns for UI state.** Deferrals and “don’t ask again for this fingerprint” live in **DataStore** (same pattern as theme prefs / workout overlay), keyed by stable fingerprint of `(equipmentId|null, inferredStep, sortedLiftIds)`—not by proposal content strings.

**Secondary surfaces (Feature 3):**

- Manage Equipment: manual create (already exists; enhance with **fork-from-seed**).
- Equipment increments editor: **Affects N lifts** (Feature 2)—supports intentional Amend path.

### 2.4 Increment inference heuristics (deterministic; no LLM in v1)

**Inputs:** list of positive amounts `W` (session, same unit), current tiers `T`.

1. **Residuals vs T:** `R = { w ∈ W | !onLattice(w,T) }`. If `R` empty → no proposal.
2. **Candidate steps:** for each residual weight, factors from a small allowlist of gym-realistic steps:  
   `{1, 2, 2.5, 5, 10, 12.5, 15, 20, 25}` (lb) and metric twin `{1, 2, 2.5, 5, 10, …}` if unit is kg. Prefer largest `s` that places **all** of `R` (and preferably all of `W`) on a lattice of step `s` relative to a base of 0 **or** relative to nearest on-lattice plate under current T (stack offsets).
3. **Jump GCD (when ≥2 distinct weights):** positive pairwise diffs; approximate GCD with ε; snap to nearest allowlist step.
4. **Band structure:** if a single open band of `oldInc` and inferred `s < oldInc` and max weight suggests mixed stack, split at tidy upper bound; else single open band with `s`.
5. **Validate** with `ValidateIncrementTiersUseCase`; if invalid, fall back to single open band `s`.
6. **False-positive guards (must pass to emit proposal):**
   - ≥1 residual weight (required).
   - Prefer ≥2 sets or ≥2 distinct residual weights when only one lift and seed parent has high inheriting count.
   - Ignore if residual looks like **typo** (e.g. 625 instead of 62.5)—amount &gt; 3× session max of other sets for same lift.
   - Ignore if load type is Bodyweight.
   - Cap **3 proposals** per completed session (merge lifts into clusters first).
   - Do not propose if primary candidate would create equipment with same normalized name as existing custom (`EquipmentNameMatching.normalizeKey`)—offer reassignment to existing custom instead.

**LLM:** out of v1. If later needed for naming only (`"Life Fitness…"`), use existing Firebase callable + App Check pattern—never for lattice math.

---

## 3. Dependency order & PR slices

| Order | PR | Ships | Depends on |
|------:|----|--------|------------|
| 1 | **PR-A — Lattice fit + remedy pure domain** | `onLattice`, residual, step inference, candidate ranking, proposal models; unit tests only | none |
| 2 | **PR-B — Affects N lifts (Feature 2)** | Count/names of inheriting lifts on equipment increments UI | none (parallelizable with A) |
| 3 | **PR-C — Apply remedies use case** | Transactional accept: add custom / set equipment tiers / reassign lifts; optional batch apply | PR-A |
| 4 | **PR-D — Analyze session use case + DataStore deferrals** | `AnalyzeSessionEquipmentProposalsUseCase`; dismiss/defer prefs | PR-A, PR-C |
| 5 | **PR-E — Workout complete batch UX (Feature 1)** | Presentation on complete screen; Accept/Skip/Not now | PR-D |
| 6 | **PR-F — Per-machine productization (Feature 3)** | Fork-from-seed in Manage Equipment; better create naming; guidance copy; optional proposal deep-link to Manage | PR-B, PR-C (E optional) |

**Recommended merge order:** A → B (can land anytime) → C → D → E → F.

**Why Feature 2 early:** cheap, independent, educates blast radius before users accept seed Amend from complete flow.

---

## 4. Feature 1 — Infer increments → post-workout batch

### 4.1 User stories

1. As a lifter who just finished cable work at 62.5 / 67.5 on a 5-lb seed lattice, I see one card after the workout offering custom equipment (or tier fix) **pre-filled**, and Accept creates it and reassigns only those lifts.
2. As a lifter who mistyped one set, I rarely see a proposal (guards), and Skip / Not now never blocks leaving the screen.
3. As a lifter offline, proposals still appear (pure local analysis).
4. As a lifter who already fixed equipment, I do not get the same fingerprint again until signals change.

### 4.2 UI surfaces

| Surface | Module | Notes |
|---------|--------|-------|
| `WorkoutCompleteScreen` / `WorkoutCompleteUiState` / `WorkoutCompleteViewModel` | `:feature:workout` | Optional section; map domain proposals → UI models only |
| Optional `Stratum*` sheet for “Edit proposal” | `:feature:workout` + `:core:ui` | Name field, lift chips, candidate switch—still Stratum components |
| **Not** live `WorkoutScreen` | — | Hard non-goal |

### 4.3 Domain / data changes

**New (domain, pure):**

- `core/domain/.../lift/IncrementLattice.kt` (name flexible): `residual`, `isOnLattice`, ε policy.
- `core/domain/.../lift/InferIncrementSteps.kt` or `EquipmentIncrementInference.kt`: step allowlist, GCD, band builder.
- `core/domain/.../lift/EquipmentIncrementProposal.kt`: sealed remedy + proposal aggregate:

```text
EquipmentIncrementProposal(
  id: stable fingerprint string,
  clusterEquipmentId: EquipmentId?,
  liftIds: List<LiftId>,
  sampleWeights: List<Weight>,
  proposedTiers: List<IncrementTier>,
  primary: Remedy,
  alternatives: List<Remedy>,
)

sealed Remedy {
  AmendEquipmentTiers(equipmentId, tiers)
  ForkCustomEquipment(suggestedName, tiers, assignLiftIds)
  LiftOverride(liftId, tiers)
}
```

- `AnalyzeSessionEquipmentProposalsUseCase(sessionId)`:
  - load sets (`TrainingLogRepository.getSetsForSession`)
  - load lifts (`LiftRepository.observeLifts().first()` / catalog)
  - load equipment (`EquipmentRepository.observeEquipment().first()`)
  - filter deferred fingerprints
  - return `List&lt;EquipmentIncrementProposal&gt;`

- `ApplyEquipmentIncrementProposalUseCase(proposal, chosenRemedy)`:
  - validate tiers
  - **Fork:** `addCustomEquipment` + N× `setLiftEquipment` (same write lock if we add a repository batch API; else sequential under documentation that partial failure surfaces Result)
  - **Amend:** `setIncrementTiers` on equipment
  - **LiftOverride:** `setIncrementTiers` on lift
  - Prefer extending `EquipmentRepository` / `LiftRepository` with **one transactional batch** in `:core:data` if multi-write (e.g. `createEquipmentAndAssignLifts`) to avoid orphans—mirror custom lift + equipment transaction pattern already used on import/create.

**DataStore (not Room):**

- Port e.g. `EquipmentProposalPreferences` / methods on a small prefs repository: `dismiss(fingerprint)`, `isDismissed`, optional snooze until epoch.
- Impl next to existing DataStore repos under `core/data/.../repository/`.

**No schema change. No Firestore collection. Sync:** only resulting `equipment`, `increment_tiers`, `lifts.equipmentId` dirtied via existing `SyncTouch` paths.

### 4.4 Risks

| Risk | Mitigation |
|------|------------|
| False positives from typos / unit mix | Guards; unit consistency check; Skip easy |
| Editing seed cable harms catalog | Rank fork first when inheritingCount &gt; K |
| Orphan custom equipment on partial apply | Single transaction batch API |
| Proposal spam | Cap 3; cluster; DataStore dismiss |
| Floating point | Shared ε with tests on 62.5 / 2.5 / 12.5 |
| User accepts then regrets | Equipment still editable; reassign lifts in Lift Information; delete custom if unreferenced |

### 4.5 Test plan

| Layer | Tests |
|-------|-------|
| Domain | Lattice residual table-driven; inference for 5→2.5 split; GCD jumps; ranking seed vs custom; empty/bodyweight; validation failure fallback |
| Domain UC | Analyze session fixtures (fake repos); Apply fork/amend/override; dismissed filter |
| Data | Batch create+assign transaction; no orphan on failure (if batch API) |
| VM | `WorkoutCompleteViewModel`: maps proposals; accept calls apply; skip/not now; complete still works with zero proposals |
| UI | Preview light/dark for proposal card; Detekt stratum imports |

Golden structure: follow `contracts/UNIT_TESTING.md`; fakes from `:core:testing` (`FakeEquipmentRepository`, `FakeLiftRepository`, training log fakes).

### 4.6 Product decisions (locked) + remaining open

**Locked:** K=3 · max 3 cards · seed amend allowed with one blast-radius confirmation · disabled lifts count · fork from any equipment.

**Still open / deferred to impl polish (non-blocking):**

1. Include **historical import** sessions in analysis? (Plan v1: **live completed sessions only**.)
2. Exact confirmation copy tone (short vs explanatory).

---

## 5. Feature 2 — “Affects N lifts” on equipment increment edit

### 5.1 User stories

1. On Equipment Increments, I see “Affects **N** lifts using these defaults” before Save.
2. Optional expand/list shows lift names (capped) so I know who inherits.
3. Lifts with **lift override tiers** are **not** counted as affected (they ignore equipment defaults for progression).
4. Disabled lifts: **include** in the affected count (product decision). They still point at this equipment and will pick up new defaults if re-enabled. UI copy: “Affects N lifts” (not “N enabled lifts”).

### 5.2 Exact definition of “affected”

```text
affected(equipmentId) =
  lifts where equipmentId == this
    AND usesEquipmentDefaultIncrements == true
```

`usesEquipmentDefaultIncrements` already means **no lift-scoped override rows** (`EffectiveIncrementTiers` / lift mapper). This matches progression reality: Save on equipment tiers changes effective tiers only for inheritors.

**Not the same as** `EquipmentDao.liftReferenceCount` (all FKs including overrides)—keep both concepts:

| Metric | Use |
|--------|-----|
| Inheriting count (Feature 2) | Blast radius for tier edits |
| Reference count (delete custom) | Already blocks delete if any FK |

### 5.3 UI

- Extend `EquipmentIncrementsUiState` with `affectedLiftCount: Int`, `affectedLiftNames: List&lt;String&gt;` (presentation strings).
- Show subtitle/banner above tier editor in `EquipmentIncrementsRoute` → pass into `LiftIncrementsScreen` via new optional banner field **or** equipment-specific header (avoid overloading lift inheritance banner semantics).
- Stratum components only (`StratumText`, existing banner style from lift inheritance).
- Live: combine `observeEquipmentCatalog` + `observeLiftCatalog` (or dedicated use case) in `EquipmentIncrementsViewModel`—catalog already hot; N is small.

### 5.4 Domain / data

- Prefer **pure filter in use case**: `ObserveEquipmentAffectedLiftsUseCase(equipmentId): Flow&lt;AffectedLifts&gt;` over `observeLifts()`—no new DAO required for v1.
- Optional later: `LiftDao` query `WHERE equipmentId = :id` if profiling demands (index `index_lifts_equipmentId` already exists). **No schema change.**

### 5.5 Risks / tests

- Stale count while draft editing: count is about **persisted** inheritance, not draft—OK.
- VM tests: seed catalog with mix of override/inherit; assert N.
- No Detekt issues; previews if new composable chrome.

### 5.6 Presentation detail (default)

- Subtitle with **N**, first 3 names, “+M more” if needed. Disabled lifts included in N (no separate badge required in v1).

---

## 6. Feature 3 — Per-machine custom equipment (productization of #1)

### 6.1 User stories

1. I can create “Hammer Strength row” from Manage Equipment with tiers forked from Pin-Loaded Machine in one short sheet.
2. After accepting a post-workout fork, the new equipment appears in Choose Equipment and Manage list with correct tiers.
3. I understand when to **fork** (different machine) vs **lift override** (one weird lift) vs **edit shared custom** (whole gym stack).
4. I can delete custom equipment only when unreferenced (existing rule).

### 6.2 Creation paths (no new entity)

| Path | Entry | Behavior |
|------|-------|----------|
| Manual blank | Manage Equipment create sheet (exists) | Name + default single 5 lb band today; keep |
| **Fork from seed** | Manage create / row overflow | Pick seed parent → copy tiers → new custom id + name |
| Post-workout Accept | Feature 1 | Fork or amend as ranked |
| Import `findOrCreateByName` | Existing | Unchanged; still not GymEquipment tags |

### 6.3 Assignment & discovery

- Lift Information / Create Lift **Choose Equipment** already lists catalog via `ManageEquipmentKey(selectionMode=true)`.
- Ensure new customs sort with user customs (name order already `ORDER BY name`).
- Optional: section headers “Your equipment” vs “Standards” (`isCustom`)—nice-to-have in PR-F.

### 6.4 Naming guidance

- Prefill: user-editable; avoid forcing brand entry.
- Suggest patterns in empty-state copy: “Name the machine, not the lift (e.g. Life Fitness lat stack).”
- Dedup: warn if `normalizeKey(name)` collides with existing row.

### 6.5 Delete / rename / sync

- **Rename:** already allowed for seed + custom (`RenameEquipmentUseCase`)—document that renaming seed is cosmetic local label.
- **Delete:** custom + unreferenced only (`DeleteCustomEquipmentUseCase`).
- **Sync:** existing `SyncCollection.Equipment` + `IncrementTiers` + lift FK; no new collections; keep `firestore.rules` allowlist aligned (no change if no new collection).

### 6.6 Equipment zoo control

In-product guidance (copy only, v1):

- Prefer **one custom per physical stack/machine** shared by lifts that use it.
- Prefer **lift override** only when one lift disagrees with a machine you share.
- Prefer **do not** edit seed cable/machine defaults if you train on brand-specific stacks—fork instead (Feature 1 default).

### 6.7 Test plan

- Fork use case: tiers deep-copied, new id, seed untouched.
- ManageEquipmentViewModel: fork flow messages.
- Sync tests unchanged unless new write batch.

### 6.8 Fork-from-any — decision and ramifications

**Decision:** fork may start from **any** equipment row (seed or custom). Implementation: copy current default tiers → `addCustomEquipment(newName, copiedTiers)` → user assigns lifts as they choose (or proposal assigns signal lifts only).

**What “fork” does *not* do:**

- Does not delete or alter the parent.
- Does not auto-move every lift on the parent—only the lifts the user accepts (proposal signal set or manual reassignment).
- Does not create a live link or **lineage** to seed: there is no `forkedFromEquipmentId` (or similar). A custom “Life Fitness Single Cable Stack” cannot later be resolved back to seed Cables for “clone from Cable again.” Each custom is a standalone row with its own tiers.
- Does not imply “linked to Cables” in UI copy after create.

**Ramifications (good):**

- User can clone a tuned stack under a new name without rebuilding bands from scratch.
- Works the same whether the parent is seed Cables or an earlier custom.
- Matches mental model: “copy this machine’s steps under a new name.”
- No schema work for parent pointers; sync stays simple.

**Ramifications (accepted for v1):**

- **No re-seed from clone:** wanting seed Cable bands again means forking seed Cables (or picking it in Manage Equipment)—not walking lineage from a custom. Inference can still propose tiers from logged weights without lineage.
- **Equipment zoo:** product judgment is gym switches are rare enough that big piles are unlikely soon. In-wave mitigations: max 3 post-workout cards, name collision warn, delete when unreferenced, naming guidance.
- **Archive (later, out of this wave):** hide/retire unused customs without fighting hard-delete rules—explicit non-goal now; follow-up when zoo pain appears.
- **Stale copies:** improving parent tiers does not update children (intentional).
- **Fork of a fork:** allowed; no depth limit; no family tree in DB.
- **Sync:** new `equipment` + `increment_tiers` + optional lift FKs only.
- **Seed still special for *amend*:** forking is low-risk; *editing* seed requires blast-radius confirmation (decision #3).

---

## 7. Explicit non-goals (v1 of this wave)

- No mid-set / in-workout prompts.
- No on-device or cloud LLM for lattice inference.
- No Room version bump / migrations; no tables for proposal UI state.
- No silent rewrite of seed equipment without user Accept.
- No automatic reassignment of **all** lifts on a seed parent—only signal lifts (or user-toggled set).
- No changing progression math beyond better tiers after user accepts.
- No merging GymEquipment import tags into Equipment catalog.
- No multi-gym profiles / “which gym am I in today.”
- No kg/lb dual lattices on one equipment row (existing mixed-unit validation stays).
- No historical bulk “scan all my logs and propose 20 machines” wizard (session-scoped first).
- No force-push of equipment changes to other devices beyond existing sync of resulting rows.
- No equipment **archive** / soft-hide (deferred follow-up; delete-unreferenced remains the only retirement path).
- No `forkedFrom` / seed-lineage field on equipment.

---

## 8. File / module touch list (from current repo)

### PR-A — Domain lattice + inference

| Path | Role |
|------|------|
| `core/domain/src/main/kotlin/com/stratum/domain/lift/IncrementTier.kt` | Reuse `roundToIncrement` / `incrementForAmount`; add lattice helpers or sibling file |
| `core/domain/src/main/kotlin/com/stratum/domain/lift/EffectiveIncrementTiers.kt` | Reference only |
| `core/domain/src/main/kotlin/com/stratum/domain/lift/ValidateIncrementTiersUseCase.kt` | Validate proposed tiers |
| `core/domain/src/main/kotlin/com/stratum/domain/lift/EquipmentIncrementInference.kt` *(new)* | Step inference + band builder |
| `core/domain/src/main/kotlin/com/stratum/domain/lift/EquipmentIncrementProposal.kt` *(new)* | Models |
| `core/domain/src/test/kotlin/com/stratum/domain/lift/*Inference*Test.kt` *(new)* | Unit tests |
| `core/domain/src/test/kotlin/com/stratum/domain/lift/ResolveEffectiveIncrementTiersTest.kt` | Pattern reference |

### PR-B — Affects N lifts

| Path | Role |
|------|------|
| `core/domain/.../lift/ObserveEquipmentAffectedLiftsUseCase.kt` *(new)* | Flow of count + lift ids/names |
| `core/domain/.../lift/LiftRepository.kt` / `ObserveLiftCatalogUseCase.kt` | Consume existing observe |
| `app/.../liftconfig/EquipmentIncrementsViewModel.kt` | State: affected count/names |
| `app/.../liftconfig/EquipmentIncrementsScreen.kt` | Banner/subtitle |
| `app/.../liftconfig/LiftIncrementsScreen.kt` | Optional shared header slot |
| `app/src/test/kotlin/.../EquipmentIncrementsViewModelTest.kt` *(new/extend)* | VM tests |
| `core/ui` strings if centralized | `core/ui/src/main/res` equipment strings |

### PR-C — Apply remedies

| Path | Role |
|------|------|
| `core/domain/.../lift/ApplyEquipmentIncrementProposalUseCase.kt` *(new)* | Accept path |
| `core/domain/.../lift/AddCustomEquipmentUseCase.kt` | Reuse |
| `core/domain/.../lift/SetEquipmentIncrementTiersUseCase.kt` | Reuse |
| `core/domain/.../lift/SetLiftEquipmentUseCase.kt` | Reuse |
| `core/domain/.../lift/SetLiftIncrementTiersUseCase.kt` | Lift override path |
| `core/domain/.../lift/EquipmentRepository.kt` | Optional `createAndAssign` batch |
| `core/data/.../repository/RoomEquipmentRepository.kt` | Batch txn + sync touch |
| `core/data/.../repository/RoomLiftRepository.kt` | Assign within txn if batch lives in equipment repo with liftDao |
| `core/testing/.../FakeEquipmentRepository.kt` | Fake batch |
| Domain/data tests | Apply + txn |

### PR-D — Analyze + deferrals

| Path | Role |
|------|------|
| `core/domain/.../lift/AnalyzeSessionEquipmentProposalsUseCase.kt` *(new)* | Orchestrate |
| `core/domain/.../training/TrainingLogRepository.kt` | `getSetsForSession` |
| `core/domain/.../training/GetLoggedSetsForSessionUseCase.kt` | Optional reuse |
| `core/domain/.../theme` or new `prefs` package | Port for dismissals *(prefer small `equipment` prefs port)* |
| `core/data/.../repository/DataStore*EquipmentProposal*` *(new)* | Persist dismiss/snooze |
| Domain tests with fakes | |

### PR-E — Workout complete UX

| Path | Role |
|------|------|
| `feature/workout/.../WorkoutCompleteViewModel.kt` | Load proposals; accept/skip/defer |
| `feature/workout/.../ui/WorkoutCompleteUiState.kt` | Proposal UI models |
| `feature/workout/.../ui/WorkoutCompleteScreen.kt` | Cards |
| `feature/workout/.../ui/WorkoutCompleteRoute.kt` | Wire callbacks |
| `feature/workout/.../navigation/WorkoutCompleteKey.kt` | Unchanged key |
| `feature/workout/src/test/.../WorkoutCompleteViewModelTest.kt` | Extend |
| `app/.../navigation/StratumNavHost.kt` | Only if new routes (prefer none) |
| `feature/workout/src/main/res` | Strings |

### PR-F — Manage Equipment productization

| Path | Role |
|------|------|
| `app/.../liftconfig/ManageEquipmentScreen.kt` | Fork-from-seed sheet/copy |
| `app/.../liftconfig/ManageEquipmentViewModel.kt` | Fork action |
| `core/domain/.../lift/ForkEquipmentUseCase.kt` *(new)* | Copy tiers → custom |
| `app/.../liftconfig/LiftConfigNavigation.kt` | Only if new keys (prefer sheet) |
| `app/src/test/.../ManageEquipmentViewModelTest.kt` | Extend |
| `core/domain/.../lift/RenameEquipmentUseCase.kt` / `DeleteCustomEquipmentUseCase.kt` | Unchanged rules |

### Reference / unchanged core (read, do not redesign)

| Path | Why |
|------|-----|
| `core/data/src/main/assets/lifts/seed-equipment.json` | Seed lattice |
| `core/data/schemas/.../1.json` | equipment + increment_tiers dual parent |
| `core/data/.../db/EquipmentDao.kt` | `liftReferenceCount` |
| `core/data/.../db/IncrementTierDao.kt` | Equipment/lift tiers |
| `core/data/.../sync/SyncCollection` + `firestore.rules` | equipment / increment_tiers allowlist |
| `core/domain/.../importing/GymEquipment.kt` | Import-only; keep separate |
| `docs/onboarding-vertical-slices.md` §2.1–2.2 | Product model |

---

## 9. Key decisions (summary)

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Detection | Residual vs `roundToIncrement` + ε | Reuses production lattice math |
| Scope | Session-first, offline deterministic | Fast, testable, no secrets |
| Seed defaults | Prefer **fork custom** when many inheritors (K=3); seed amend only with one “This’ll alter N lifts” confirmation | Never unwitting multi-lift nuke of shared defaults |
| Fork parent | Any equipment; copy tiers only; **no lineage** to seed | Simple model; rare gym switches; archive later if zoo hurts |
| UX home | Workout complete cards | Zero mid-set friction; natural review moment |
| Persistence of dismiss | DataStore fingerprints | Schema lock compliance |
| Feature 2 metric | Inheriting only (`usesEquipmentDefaultIncrements`) | Matches real blast radius |
| Feature 3 entity | Existing custom equipment | No schema invention |
| LLM | Not in v1 | AGENTS.md; lattice is deterministic |

---

## 10. Implementation readiness checklist (post-approval)

1. Confirm open questions (§4.6, §5.6, §6.8)—especially **K** and seed-amend confirmation.
2. Land PR-A with exhaustive unit tests before any UI.
3. Ship PR-B for immediate UX value and to reuse “Affects N” copy on amend confirmations in PR-E.
4. PR-C batch write under `syncTouch.withWriteLock` + Room transaction.
5. PR-D/E wire complete screen; verify Detekt + `:feature:workout` tests + `:core:domain:test`.
6. PR-F polish Manage Equipment; manual QA on emulator: log off-lattice cable set → complete → Accept → equipment list + lift details equipment id.

---

## 11. PR Plan (shorthand)

1. **PR-A:** Domain lattice fit + increment inference + proposal models + tests.  
2. **PR-B:** Observe affected inheriting lifts + Equipment Increments “Affects N” UI + tests.  
3. **PR-C:** Apply proposal use case (+ optional repo batch create/assign) + tests.  
4. **PR-D:** Analyze session use case + DataStore deferral prefs + tests.  
5. **PR-E:** Workout complete proposal cards + VM + previews + tests.  
6. **PR-F:** Fork-from-seed Manage Equipment + naming/guidance copy + tests.

Each PR is independently reviewable; B can merge without A; E is the user-visible Feature 1 milestone; F completes Feature 3 productization.
