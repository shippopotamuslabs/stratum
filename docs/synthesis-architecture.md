# Program synthesis architecture

Stratum builds weekly training programs with a unified CP-SAT model (structure + volume +
calendar in one solve). This document is the map of layers, the meaning of
`SessionRegionFilter`, and the critical-path files.

## Layers (critical path)

```
slot library  →  universe  →  SessionRegionFilter  →  solve  →  realize  →  polish
```

| Layer | Responsibility | Key types / modules |
|-------|----------------|---------------------|
| **Slot library** | Explicit movement-pattern catalog (anchors, press/pull, isolations) — not a SessionTypeId union | `baseSlotLibrary()`, `SlotLibrary`, `WorkoutSlotTemplate`; legacy `slotsForSessionType` for name/plan paths |
| **Universe** | Relevant, realizable slots for this user (aims, specialization, candidate existence) | `buildSlotUniverse` → `baseSlotLibrary` + specialized variants + cross-domain touches → `List<SynthesisSlot>` |
| **SessionRegionFilter** | Per-session CP-SAT domain: which body-region filter applies to session position *i* | `SessionRegionFilter`, `regionFiltersBySessionPosition`, structure constraints |
| **Solve** | Joint CP-SAT: activation, set counts, weekdays, region filters, recovery, aims | `CpSatProgramSynthesizer`, `ProgramSynthesisModel` |
| **Realize** | Map solver solution → plans/sessions; lift selection; drafts. Region filter is structure; `SessionTypeId` on plans is a legacy display label only | `materializeSynthesisResult`, lift selection, pipeline |
| **Polish** | Naming, swap assist, validation, integration — **names are polish only** (deterministic titles must not change recovery/variety) | `applyHeuristicProgramNames`, optional lift-swap Callable |

Cloud path: Android `:core:inference` encodes request via `ProgramSynthesisWire` → Firebase /
Cloud Run `solver-service` runs OR-Tools → result wire decoded back to domain.

## Slot library → universe

The universe is **not** built by unioning five `SessionTypeId`s. Construction is:

```
baseSlotLibrary()
  + specializedVariants(context)   // promote optionals; sibling-B when multi-lift
  + crossDomainTouches(context)    // domain shells, stable touch-{muscle} ids
  .filter(isSlotRelevant)          // aims, G14, glute policy, candidate existence
  .distinctBy(slotKey)             // pattern|muscle|role|touch|variant
```

`baseSlotLibrary()` is the deduped union of templates from the Upper / Lower / FullBody /
UpperPush / UpperPull catalogs, exposed as logical groups (`LOWER_ANCHORS`,
`LOWER_ISOLATIONS`, `UPPER_PRESS`, `UPPER_PULL`, `UPPER_ISOLATIONS`). No invented slots.

`SessionTypeId` / `slotsForSessionType` remain for legacy plan labels and session-name paths;
they are not the universe source loop.

Golden coverage: `SlotUniverseGoldenTest` snapshots key sets for maintenance, arms, arm+calf,
and sean5 layouts.

## SessionRegionFilter is not a split type

`SessionRegionFilter` is a **search / structure gate** on each session position:

| Value | Ordinal (frozen) | Meaning |
|-------|------------------|---------|
| `UpperOnly` | 0 | Only upper-eligible slots may activate |
| `LowerOnly` | 1 | Only lower-eligible slots may activate |
| `Unrestricted` | 2 | Both regions may activate (true full-body viable) |

**Emergent splits** (Chest/Quads vs Back/Hams, push/pull flavors, etc.) are **not** encoded as
enum values. They arise from:

1. **Slot activation** — which universe slots the solver turns on
2. **Recovery hard/soft constraints** — spacing of muscle exposure across the calendar
3. **Aim / specialization objective** — volume targets and priorities

Post-hoc `SessionTypeId` on plans (Upper / Lower / FullBody) is a **legacy display label**, not
structure truth. Materialization maps it from the solver region filter when present:

| `SessionRegionFilter` | Legacy `SessionTypeId` label |
|-----------------------|------------------------------|
| `UpperOnly` | `Upper` |
| `LowerOnly` | `Lower` |
| `Unrestricted` | `FullBody` |

Fallback (region filter list short/missing — should not happen on the real solver path):
`inferSessionTypeFromActiveSlots`. The **source of truth** after solve is
`regionFilterBySession` on `ProgramSynthesisResult` / `UnifiedSynthesisSolution`.

**Realize vs polish:**

- **Structure** — `regionFilterBySession`, active slots, set counts, weekday indices.
- **Polish** — deterministic workout/program names (local date/time program title; Upper/Lower/
  Full Body session labels from lift-region heuristics), optional display labels. Downstream
  recovery/variety repairs (`CalendarLiftVariety`, schedule muscle exposures) use lift metadata
  and muscle doses — they do **not** parse workout names for structure.

### Calendar policy (per training weekday)

Sessions are ordered by calendar day via CP-SAT symmetry break: position *i* → sorted training
day *i*. Policy:

- **Rest-sandwiched** weekday (rest on both calendar sides, week wrap): **Unrestricted only**
  (prune UpperOnly/LowerOnly for search speed — pure U/L is almost never chosen when mixed
  activation is viable).
- **Not rest-sandwiched**: **Unrestricted + UpperOnly + LowerOnly**. Unrestricted stays so
  mix-full families (Chest/Quads ↔ Back/Hams) remain feasible on dense weeks. True “everything
  on one day” full body is still rare under recovery hard-forbids without a rest sandwich.

Helper: `regionFiltersBySessionPosition(weekdays): List<Set<SessionRegionFilter>>`.

## Anti-glossary: meanings of “Upper”

Do not conflate these:

1. **`SessionRegionFilter.UpperOnly`** — CP-SAT structure filter for a *session position*
   (solver domain). Source of truth for “this day is upper-gated.”
2. **`SessionTypeId.Upper` / `TrainingDomain.Upper`** — catalog and muscle-side labels used in
   slot libraries, volume accounting, and legacy plan labels. Not the solver decision variable.
3. **Natural-language “upper day”** — coaching / UI copy. May describe an emergent session that
   happens to be upper-heavy even under `Unrestricted`, or a post-hoc `SessionTypeId.Upper`.
4. **Workout title region (`Upper A`)** — polish-only heuristic from lift primary muscles +
   pattern fallback (`applyHeuristicProgramNames`). Never used for recovery, variety, or volume.

When debugging synthesis, prefer logging `regionFilterBySession` and active slot ids over
session-type names alone.

## Wire contract

`ProgramSynthesisWire.encodeResult` / `decodeResult` include:

```json
"regionFilterBySession": ["UpperOnly", "LowerOnly", "Unrestricted"]
```

Enum **names** (not ordinals) on the wire. Ordinals stay frozen in Kotlin for CP-SAT domains and
warm-start hints.

## Critical-path file list

### Domain (`:core:domain`)

Package root: `com.stratum.domain.program.generation`

| File | Role |
|------|------|
| `.../slots/SlotLibrary.kt` | **Base slot library**: `baseSlotLibrary()`, logical groups (`LOWER_ANCHORS`, `UPPER_PRESS`, …) |
| `.../slots/SessionTypeCatalog.kt` | Legacy session-type templates, `SessionTypeId`, `slotsForSessionType` (plans/names) |
| `.../slots/SessionComposition.kt` | Organic composition overrides for catalog injectors |
| `.../slots/*` | Specialized / cross-domain / spec-focused slot injectors |
| `.../universe/SynthesisSlot.kt` | Universe slot DTO (`SynthesisSlot`) |
| `.../universe/SlotUniverseBuilder.kt` | `buildSlotUniverse` — library → variants → touches → filter/dedupe |
| `.../structure/SessionRegionFilter.kt` | `SessionRegionFilter` enum (ordinal freeze) |
| `.../structure/SessionRegionFilterPolicy.kt` | Calendar → per-session allowed filters |
| `.../structure/SlotRegionCompatibility.kt` | Slot ↔ filter eligibility helpers |
| `.../synthesis/UnifiedSynthesisSolution.kt` | Solver solution DTO (`regionFilterBySession`) |
| `.../synthesis/ProgramSynthesisPort.kt` | Port + `ProgramSynthesisResult` |
| `.../synthesis/SynthesisMaterializer.kt` | Solution → plans/sessions; label from region filter; pass-through filters |
| `.../synthesis/wire/ProgramSynthesisWire.kt` | Request/result JSON boundary |
| `.../split/` | Schedule leftovers: weekdays, exposures, resolved plans, name→type inference |

### Solver host (`:core:domain-solver`)

| File | Role |
|------|------|
| `.../solver/CpSatProgramSynthesizer.kt` | Entry: universe → model → solve → materialize |
| `.../solver/ProgramSynthesisModel.kt` | CP-SAT model, warm-start, solution read |
| `.../solver/UnifiedStructureConstraints.kt` | Per-session region filter domains + slot gates |
| `.../solver/UnifiedStructureState.kt` | `regionFilterVarByPosition` |
| `.../solver/ConstructiveSynthesisSeed.kt` | Soft warm-start seeds |

### Tests / fakes / cloud

| File | Role |
|------|------|
| `.../structure/SessionRegionFilterPolicyTest.kt` | Calendar policy unit tests |
| `.../universe/SlotUniverseBuilderTest.kt` | Universe build unit tests |
| `.../universe/SlotUniverseGoldenTest.kt` | Golden key sets for library/universe rewrite |
| `.../ConstructiveSynthesisSeedTest.kt` | Seed vs policy |
| `.../ProgramSynthesisWireTest.kt` | Wire round-trip including region filters |
| `.../FakeProgramSynthesisPort.kt` | Native-free fake for JVM unit tests |
| `.../LocalSynthesisLab.kt` | Interactive program dump |
| `solver-service/` | Cloud Run OR-Tools worker |

## Warm-start seed families (opt-in)

Set `stratum.solver.warm_start=true` to apply soft CP-SAT hints from
`ConstructiveSynthesisSeed`:

| Calendar | Region filter seed | Slot family seed |
|----------|--------------------|------------------|
| Rest-sandwiched day | Unrestricted | **Full** (squat+hinge+press+pull) |
| Dense Unrestricted | Unrestricted | Alternate **PressHinge** (chest/ham/tri/delt) ↔ **PullSquat** (back/quad/bi/delt) |
| UpperOnly / LowerOnly | that filter | Region-gated ranking only |

Set allocation prefers lagging antagonist aims (Chest↔Back, Quad↔Ham) so equal-aim
maintenance pairs stay even **without new objective weights**. Dense A/B seeds use at most
one lower anchor.

## Solver test matrix (speed)

Default CP-SAT harnesses use **[CORE_SOLVER_SCENARIOS]** (~10 high-signal cases: sparse/dense
layouts, maintenance, arms, legs, chest, glutes, shoulders). Full 28-scenario cartesian
(`[GENERATION_SCENARIOS]`) and the ~56-case Optuna benchmark are opt-in.

```bash
# PR / local (default core matrix, 15s deadline, no benchmark)
./gradlew :core:domain-solver-tests:test -PrunSolverTests=true

# Nightly cartesian
./gradlew :core:domain-solver-tests:test -PrunSolverTests=true -PfullSolverScenarios=true

# Weight tuning only
./gradlew :core:domain-solver-tests:test -PrunSolverTests=true -PincludeBenchmark=true \
  --tests '*benchmarkTuningSuite*'
```

Property: `stratum.solver.scenarios=core|full` (Gradle maps `-PfullSolverScenarios` → `full`).

## Explicit non-goals (this architecture pass)

- No retune of objective weights or recovery thresholds
- No rewrite of objective logic beyond region-filter rename + per-session domains
- Domain stays pure JVM (no OR-Tools in `:core:domain`)
