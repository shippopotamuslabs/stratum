# Design: workout-screen

Runtime architecture for Stratum's live workout experience, built on a clean
three-layer separation: **workout structure**, **initial progression snapshot**,
and **live workout overlay**. This is the foundation for `WorkoutScreen` and the
deterministic recommendation engine it consumes. It builds on the persistence
foundation landed in `workout-logging` and consumes templates from
`program-synthesis` / `program-generation-v2`.

---

## Current state

Grounded in `research.md` (verified against code 2026-07-05):

- **Template hierarchy exists, owns no load.** `Program → Workout → WorkoutLift → LiftSet`
  (`Program.kt`). `LiftSet` carries `setType`, `repMin`/`repMax`, `rirGoal`,
  `isTopSet`, `restSeconds` — but **no weight/load field**. The top/backoff
  distinction is the `isTopSet` boolean plus per-row RIR; there is no separate
  structural type for backoff (`Program.kt:64-74`, research Finding 4).
- **`SetType` enum has `Standard`, `MyoRep`, `DropSet`, but generation produces
  only `Standard`.** `MyoRep`/`DropSet` carry no sub-type (rep-floor vs
  rep-matching) distinction today (`ProgramEnums.kt:7`, research Finding 2/22).
- **`ProgressionScheme` is an assignment label only.** `Linear` (Beginner) vs
  `Double` (all other training ages) is assigned by
  `schemeForTrainingAge` during generation
  (`ProgramRules.kt:30-31`, research Finding 3/24). **No engine consumes it** —
  no double-progression ("bump load when top of rep range hit at RIR target")
  and no linear-progression ("add weight every session/week; transition to
  double after stalls") math exists anywhere in-tree (research Finding 6, Gap 1).
- **No progression snapshot, no recommendation code.** Grep for `Recommendation`,
  `ProgressionSnapshot`, `LiveWorkout`, `recommendedWeight`, `recommendedReps`
  returns zero production matches. `LogSetUseCase` persists exactly what the
  caller passes in `LogSetCommand` (`weight`, `reps`, `rir`); it performs no
  recommendation math (`LogSetUseCase.kt`, research Finding 5).
- **e1RM is forward-only.** `EstimatedOneRm.calculate(weight, reps)` is
  Brzycki for `reps ≤ 10`, Epley otherwise (`LiftNameResolver.kt:272-286`); a
  RIR-adjusted variant exists in `TrainingSignalCleaner`
  (`calculate(weight, reps + rir)`). **No inverse**
  `weightForReps(e1rm, targetReps)` exists (research Finding 7, Gap 2).
- **Fatigue model is per-lift-from-history or null.**
  `LiftTrainingInsights.fatigueDecaySlope`/`fatigueDecayIntercept` are computed
  by a linear regression of per-set e1RM over set-index-within-session,
  averaged across sessions (`TrainingInsightsExtractor.kt:100-118`). No
  population-gradient default constant exists in `ResponseConstants.kt`
  (research Finding 17/18, Gap 3).
- **No historical-priority query.** The repository has no method to find
  "previous sessions for `(programId, workoutId, workoutLiftId, setIndex)`"
  across sessions. The closest primitives are `getSetsForSession`,
  `getCompletedSessionsForCycle`, and the completion predicate in
  `CycleProgressResolver` (research Finding 15, Gap 4).
- **Persistence foundation landed (`workout-logging`).** `TrainingSession`,
  `LoggedSet` with snapshotted `LoggedLiftContext`, cycle pointers
  (`currentCycleNumber`/`cyclePosition`), `ProgramChangeEvent`, and the
  `ObserveCycleProgressUseCase` read model (`CycleProgress`) are available.
  `TrainingLogRepository` is the unified port for import + live history
  (research Finding 12-14).
- **No `:feature:workout` module.** The entire WorkoutScreen UI, ViewModel, and
  presentation layer is greenfield (`settings.gradle.kts`, ARCHITECTURE.md §1.2;
  research Finding 9).
- **EditWorkoutScreen has no set-type or top-set concept.** `EditSetState`
  carries `repMin`/`repMax`/`rirGoal` only; rep range renders on every row;
  no Myo-rep/drop-set authoring UI (research Finding 19/20/21, Gap 6).

---

## Desired end state

After this ticket lands (architecture + deterministic core; UI in a follow-on
slice):

1. **Three independent runtime layers** feed `WorkoutScreen` rendering:
   `Structure + Snapshot + Overlay → Rendered Workout`, each with explicit
   ownership and immutability guarantees (D1).
2. **A progression engine** computes a one-time **initial progression snapshot**
   per `WorkoutLift` slot from history using the historical-data priority rules;
   the snapshot is immutable for the life of the session (D3).
3. **A live recommendation engine** derives per-set e1RM from performed sets
   and decides **hold vs. reduce** the next set's weight. Top sets target the
   bottom of the rep range; backoff sets target a `BACKOFF_REP_FLOOR` of **5**
   (since they have no rep range, only a RIR target) — **overlay-only**, never
   touches the snapshot (D4).
4. **Dynamic set lifecycle** is supported end-to-end: add (copy previous
   weight + estimate reps via fatigue model), delete, generated Myo-rep
   mini-sets, and undo — without snapshot recomputation (D5).
5. **Myo-rep Type A (rep floor) and Type B (rep matching)** are first-class set
   shapes with a structural constraint that a Myo-rep block is the only set or
   the final set of its lift (D6).
6. **Drop sets** must immediately follow another set, with weight reduction
   estimated via the fatigue model (D7).
7. **Double progression is Stratum's primary model; linear progression is
   supported** for eligible lifts (compounds only) for users in their
   beginner-gains phase or returning from a long layoff (D8). Eligible lifts
   can be toggled by the user in EditWorkoutScreen; the engine auto-graduates
   a lift to Double after its second reset cycle (D8, D11).
8. **Historical-data priority** is honored: same lift/same workout/same
   position/previous session first, then earlier sessions, then same lift from
   another workout (D3).
9. **`:feature:workout` is added** with a ViewModel that maps the merged
   rendered workout to a presentation model — never domain models into
   composables (ARCHITECTURE.md §4.3).

---

## Patterns to follow

| Pattern | Source | Apply to |
|---------|--------|----------|
| Use-case orchestration of multi-source `Flow` into one domain read model | `ObserveCycleProgressUseCase` (research Finding 11), ARCHITECTURE.md §4.4 | A `ObserveRenderedWorkoutUseCase` that combines structure + snapshot + overlay |
| Repository returns domain types via mappers; ports in `:core:domain` | `RoomTrainingLogRepository`, `TrainingLogRepository` | New snapshot/history-priority read behind `TrainingLogRepository` (or a dedicated port) |
| Typed value-class ids + `EnumConverters` | `DATA_LAYER_RULES.md` §1-2 | Any new runtime ids (set instance ids for overlay) — but see D9 (no new persistence) |
| Fakes in `:core:testing`; JUnit 5 + Turbine + `MainDispatcherRule` | `UNIT_TESTING.md`, `FakeTrainingLogRepository` | New fakes for snapshot/recommendation/fatigue ports (UNIT_TESTING.md §6) |
| Deterministic core for runtime math, no on-device LLM | AGENTS.md §4.6 | Recommendation engine, e1RM inverse, fatigue model — pure domain |
| Domain never passed to composables; ViewModels map to presentation | ARCHITECTURE.md §4.3, UI_DESIGN.md | `WorkoutViewModel` owns overlay state; renders `*UiState` |
| Reuse existing e1RM primitive | `EstimatedOneRm.calculate` (forward) | Extend with inverse `weightForReps`; RIR-adjusted input mirrors `TrainingSignalCleaner` |
| Reuse existing fatigue regression output | `LiftTrainingInsights.fatigueDecaySlope/Intercept` | Per-lift user-specific fatigue source for add-set rep estimation |
| Pre-release v1 reinstall policy (no migrations) | AGENTS.md §4.5, DATA_LAYER_RULES.md §7 | Overlay/transient state stays out of Room entirely |
| Existing compound/isolation classifier | `Lift.isCompound` (`Lift.kt:50`), derived from `movementPatterns.size > 1` | Linear-progression eligibility gate (D8) and EditWorkoutScreen toggle visibility (D11) |

---

## Patterns to avoid

| Anti-pattern | Why | Instead |
|--------------|-----|---------|
| **LiftLab coupling (canonical anti-pattern)** — `History → Progression → live state mutated by logging → Flow emit` recomputes progression continually; live logging mutates the same state progression depends on; recommendations fight the original output (ticket Problem statement) | Three independent layers; snapshot computed once and frozen; overlay carries all runtime mutation |
| Recomputing the snapshot on add/delete set | Destroys the "compute once" invariant; recreates LiftLab failure mode | Add/delete are overlay mutations; snapshot only recomputes on historical-context change (D3) |
| Recommendation math mutating the snapshot | Conflates "what was prescribed" with "what to do next" | Recommendations are overlay-only (D4) |
| Passing domain models (`LiftSet`, snapshot) into composables | ARCHITECTURE.md §4.3, Detekt enforced | ViewModel merges to a presentation model |
| ViewModel doing multi-source `combine` over repositories | ARCHITECTURE.md §4.4 | A use case owns the composition into one read model |
| Adding Room tables/columns for transient overlay state (in-progress set edits, dynamic mini-sets) | AGENTS.md §4.5 schema lock | Overlay is in-memory ViewModel/Session-state; only finalized sets persist via existing `LogSetUseCase` |
| Porting LiftLab's wider `ProgressionScheme` enum | `ProgramEnums.kt:10-18` KDoc: exactly two variants | Stay on `Linear` / `Double` |
| Generation producing `MyoRep`/`DropSet` without sub-type modeling | research Finding 22 — sub-type (rep-floor vs matching) is a runtime concern | Sub-type lives in the structure/overlay model, not the bare `SetType` enum |
| Reusing the generation-package `ProgressionScheme` enum at runtime | research Finding 24 — it is generation-only | Runtime engine switches on `com.stratum.domain.program.ProgressionScheme` |

---

## Design decisions

### D1 — Three-layer model: structure / snapshot / overlay

**Choice.** The rendered workout is the merge of three independent values:

1. **Structure** — the immutable `WorkoutLift`/`LiftSet` template hierarchy
   (`Program.kt`), extended at runtime with a structural view of dynamic sets
   (added/deleted/generated). It carries set ordering, set type, rep range, RIR,
   and top-set marking — **no load**.
2. **Snapshot** — `SetSlot → recommended weight, recommended reps`, computed
   **once** per session from history (D3). Immutable for the life of the
   session. Lives in `:core:domain` as a domain read model.
3. **Overlay** — logged reps/weights, live recommendation adjustments, dynamic
   set additions/deletions/generations, completion state, and undo. Lives in
   ViewModel-owned session state (in-memory) in `:feature:workout`.

**Merge rules (rendering).** For each rendered set row:

- *Structure* decides the row exists, its position, its type, and its target
  band. Top sets carry a **rep range** + RIR target; backoff sets carry **only a
  RIR target** (no rep range — see D11 and the backoff-rep-floor rule in D4).
- *Snapshot* supplies the **baseline** recommended weight (and baseline expected
  reps) for the row, keyed by slot identity.
- *Overlay* layers, in priority order: (a) a logged value if the set was
  performed; (b) a live recommendation if the set is upcoming and a newer
  per-set e1RM is available (D4); (c) dynamic-set estimates for added/generated
  sets (D5). The overlay value supersedes the snapshot baseline for display,
  **but never writes back into the snapshot**.

**Ownership & immutability.**

- Structure: domain types (template) + a runtime structural projection owned by
  the merge use case. Template rows are read-only; the projection reflects
  dynamic adds/deletes.
- Snapshot: produced by a snapshot-generation use case, returned as an
  immutable value; the ViewModel holds a reference but cannot mutate it.
- Overlay: the only mutable layer, owned by the ViewModel as session state
  (`MutableStateFlow`). Persisted only when a set is finalized via
  `LogSetUseCase`.

**Rejected.** (a) A single mutable "live workout state" that combines structure
+ snapshot + progression (the LiftLab failure mode — ticket Problem). (b) A
two-layer model (structure + live) that recomputes recommendations against
history on every change (continual recompute — ticket Problem). (c) Persisting
the overlay to Room (schema lock; also reintroduces draft-table latency).

### D2 — Where each layer lives in the module graph

**Choice.**

- **`:core:domain`** owns: the rendered-workout read model types, the snapshot
  value types, the merge logic (pure), the progression engine (double + linear),
  the recommendation engine (pure), the e1RM inverse helper, the fatigue-model
  contract, and the ports for snapshot generation + historical-priority reads.
  All pure Kotlin, JVM module, no Android/Compose.
- **`:feature:workout`** (new module) owns: `WorkoutViewModel`, the overlay
  session state, presentation models (`*UiState`), the `WorkoutRoute`
  composable, and Compose UI. It consumes domain via use cases only
  (ARCHITECTURE.md §4.4) — never injects a repository port directly.
- **`:core:data`** owns: any new repository read implementations behind the
  snapshot/history ports (e.g. the historical-priority query), reusing the
  existing `TrainingLogRepository` surface where possible.
- **ViewModel** owns the overlay (transient/presentation state per
  ARCHITECTURE.md §4.4: "Only transient/presentation state lives in the
  ViewModel").

**Rejected.** Putting overlay state in `:core:domain` (would force domain to
hold mutable session state and break purity); putting merge logic in the
ViewModel (ARCHITECTURE.md §4.4 forbids multi-source combine in ViewModels —
a use case owns it).

### D3 — Initial progression snapshot: compute once, recompute on exercise swap only

**Choice.** A snapshot-generation use case produces the snapshot at session
start, after `StartTrainingSessionUseCase` resolves the workout. It reads
history through the historical-priority rules:

1. Same lift, same workout, same position, previous session (n−1).
2. Same lift, same workout, same position, earlier sessions (n−2, n−3, …).
3. Same lift from another workout.

Always use the highest-signal data available; never require data only from the
immediately previous identical workout.

**Recompute trigger.** The snapshot recomputes **only when historical context
actually changes** — concretely, an **exercise swap** within the live session
(changes the `liftId` for a slot, so the historical lookup changes). Adding,
deleting, reordering, or generating sets does **not** recompute the snapshot;
those are overlay mutations.

**Historical-priority read.** A new repository read is required (research Gap
4): find previous sessions/sets for a `(programId, workoutId, workoutLiftId,
setIndex)` slot across sessions, with fallback to same-lift-other-workout. The
join keys (`workoutLiftId` on `LoggedLiftContext`, `workoutId` +
`cycleNumberSnapshot` on `TrainingSession`) already exist from `workout-logging`
(research Finding 15). The read goes behind `TrainingLogRepository` (or a
dedicated `ProgressionHistoryPort`) and returns domain types.

**Rejected.** (a) Recompute on every overlay change (LiftLab continual
recompute). (b) Require n−1 identical-workout data only (loses signal when the
user missed a session). (c) Compute the snapshot lazily per-set at render time
(defeats the "compute once" guarantee and makes the snapshot non-immutable).

### D4 — Live recommendation engine: top-set rep-floor vs backoff-set hold-or-reduce (overlay-only)

**Choice.** A pure domain recommendation engine consumes performed sets from
the overlay and computes the next-set recommendation. The inverse problem
differs by set role, because **top sets have a rep range and backoff sets do
not** (only a RIR target — D11). One unified primitive — a new inverse
`EstimatedOneRm.weightForReps(e1rm, targetReps)` — solves both cases against
different `targetReps` sources:

1. **Estimate per-set e1RM** from the most recent performed set:
   `EstimatedOneRm.calculate(performedWeight, performedReps + performedRir)`
   (mirrors the existing RIR-adjusted e1RM in `TrainingSignalCleaner`).
2. **Pick the target rep count by set role:**
   - **Top set (has a rep range):** `targetReps = repMin` — the bottom of the
     prescribed range. Recommendation is `weightForReps(e1rm, repMin)`.
   - **Backoff set (RIR-only, no rep range):** `targetReps = BACKOFF_REP_FLOOR`
     — a single domain constant, **`5`** by default. This codifies
     "meaningful backoff set": a set projected to land under 5 reps at the
     target RIR is no longer meaningful, so the weight should drop.
3. **Decide hold vs. reduce** by projecting the next set's achievable reps at
   the *current* recommended weight:
   - Project: `projectedReps = inverseEpley(currentWeight, e1rm) - targetRir`
     (solve the forward formula for `reps` at the current weight, then subtract
     the RIR target the user is shooting for).
   - **Hold** if `projectedReps ≥ targetReps` (top set still in range, or
     backoff set still ≥ 5 reps at RIR).
   - **Reduce** to `weightForReps(e1rm, targetReps)` if `projectedReps <
     targetReps` — i.e. at the current weight the user is projected to miss the
     top-set floor or fall under 5 on a backoff set.
4. Emit the recommendation as an overlay value for the upcoming set.

This is the concrete codification of the ticket's open runtime question —
*"based on performance so far, has performance dropped enough that the user
should reduce weight so they can still complete meaningful backoff sets?"* —
with "meaningful" = ≥ 5 reps at the target RIR. **No** recomputation of the
snapshot; the snapshot's only contribution is the read-only `currentWeight`
baseline for the hold/reduce comparison.

**`BACKOFF_REP_FLOOR = 5`.** A single tunable constant in `:core:domain`
(default 5). Lifts the "reasonable number of reps by the last backoff set"
intent into a named, testable rule rather than an implicit per-set judgement.
Chosen because ~5 reps at the target RIR is the lowest count that still lets
the later backoff sets contribute volume without forcing powerlifting-length
rests.

**Overlay-only guarantee.** The recommendation is computed and held in the
overlay. It reads the snapshot's `currentWeight` baseline and (for top sets)
the `repMin` floor — both read-only — but **never writes back**. When the user
logs the set, the performed value becomes the new ground truth in the overlay,
and the next recommendation recomputes from it.

**Rejected.** (a) Storing the recommendation on the snapshot (couples layers).
(b) Recomputing the snapshot to produce a new recommendation (LiftLab failure
mode — ticket Problem worked example). (c) A separate "recommendation state"
Flow that fights the snapshot (ticket Problem). (d) Applying the top-set
inverse ("weight for bottom of rep range") to backoff sets verbatim — backoff
sets have no rep range; without the `BACKOFF_REP_FLOOR` constant the engine has
no `targetReps` and the hold/reduce decision becomes undefined. (e) A fixed
percentage drop on a missed backoff set (ignores the just-performed e1RM; the
inverse formula already encodes the right drop).

### D5 — Dynamic set lifecycle: add / delete / generated / undo

**Choice.** Dynamic sets are overlay entries layered on the structure
projection:

- **Add set:** copy the previous set's recommended weight (from snapshot or
  last logged value); estimate expected reps via the fatigue model
  (`fatigueDecaySlope`/`Intercept` projected one set-index forward; population
  fallback per Open question Q2). Does **not** recompute the snapshot.
- **Delete set:** remove from the overlay structural projection. No
  recalculation.
- **Generated Myo-rep mini-sets:** produced by the Myo-rep lifecycle (D6) as
  overlay entries; each is a normal set row the user can log, mark incomplete,
  or delete.
- **Undo:** the overlay keeps an undo stack of structural mutations (add/delete,
  and per-set field edits). Undo pops the last mutation. Undo of a generated
  mini-set removes it; undo of an add removes the added set.

**Persistence.** A dynamic set is persisted via the existing `LogSetUseCase`
only when the user logs a performed value (weight/reps/rir). Until then it is
transient overlay state — no new Room table (schema lock, D9).

**Fatigue fallback (Q2 — population gradient default).** When a lift has no
per-lift `fatigueDecaySlope`/`Intercept` (no history yet), the engine falls back
to a **population-gradient default constant** added to `ResponseConstants.kt`
(source cited in Structure). Per-lift values override the default when present.
This ships the add-set rep estimation working for every lift from day one.

**Rejected.** (a) Mutating the template `WorkoutLift.sets` to add/remove
(persists transient state into the template hierarchy). (b) Recomputing the
snapshot after add/delete. (c) A separate "draft sets" persistence table
(LiftLab draft → history copy, rejected in `workout-logging` D1).

### D6 — Myo-reps: Type A (rep floor) vs Type B (rep matching); block must be only-set or final-set

**Choice.** Myo-rep support requires a sub-type distinction the bare `SetType`
enum lacks (research Finding 2). The structure/overlay model carries a Myo-rep
**kind**: `RepFloor` (Type A) or `RepMatching` (Type B).

- **Type A (rep floor):** an activation AMRAP, then mini-sets until one mini-set
  hits the rep floor. The engine generates the next mini-set on demand; the
  block ends when a mini-set reaches the floor.
- **Type B (rep matching):** an activation AMRAP, then mini-sets until the
  cumulative mini-set reps equal the activation reps. The engine tracks the
  running total and ends the block at parity.

**Structural constraint.** A Myo-rep block must be either the **only set** of
its lift or the **final set** (so progression is still driven from the primary
top set, which precedes the block). This constraint is enforced at authoring
time (EditWorkoutScreen slice) and validated when the snapshot is generated.

**Rejected.** (a) Encoding Type A/B into the persisted `SetType` enum (bloats
the persisted enum; sub-type is a runtime/authoring concept). (b) Allowing a
Myo-rep block mid-lift (breaks top-set-driven progression). (c) Generating all
mini-sets upfront (forces a fixed count; the whole point of Myo-reps is the
adaptive end-condition).

### D7 — Drop sets: must immediately follow another set; weight-reduction estimation

**Choice.** A drop set is an overlay/structural entry that **must immediately
follow another set** of the same lift. Its recommended weight is the preceding
set's weight reduced by an estimate derived from the fatigue model
(`fatigueDecaySlope` projected one step; **population-gradient default fallback
per Q2 resolution** when no per-lift value exists). The constraint ("must follow
another set") is enforced at authoring time and validated at snapshot
generation.

**Rejected.** (a) A fixed percentage drop hardcoded (ignores per-lift fatigue).
(b) Allowing a drop set as the first set of a lift (no preceding load to drop
from).

### D8 — Progression algorithms: double primary, Greyskull-style linear supported

**Choice.**

- **Double progression** is Stratum's primary model: the top set is prescribed
  a rep range + RIR target; load increases when the top of the rep range is
  achieved at the RIR target; backoff sets are at the same weight for
  AMRAP-at-RIR. This is what the snapshot-generation engine implements for
  `Double` lifts.
- **Linear progression** is the algorithm for `Linear` lifts — i.e. **compound
  lifts only** (`Lift.isCompound`, `Lift.kt:50`) owned by users in their
  beginner-gains phase or returning from a long layoff. Isolation lifts never
  run Linear (they cannot reliably increment session-to-session). Locked to a
  **Greyskull-style hybrid** validated against the literature in
  `plans/workout-screen/research-linear-progression.md`:

  - **Increment unit and schedule.** Fixed per-session increment, **gated by
    RIR achievement on the top set** rather than unconditional. Default
    increment is lower-body 5 lb / upper-body 2.5 lb (Greyskull / Starting
    Strength novice convention), per-lift overridable. The Linear vs Double
    distinction is *only the increment threshold*: **Linear increments when the
    *bottom* of the rep range is met at RIR; Double increments when the *top*
    is met at RIR.** Both reuse Stratum's existing rep-range + RIR top-set
    structure — neither forces fixed-rep 3×5.
  - **Stall trigger.** **2 consecutive sub-range sessions** for a lift (matches
    both the LiftLab recollection's "2 stall sessions" figure and the Greyskull
    reset trigger).
  - **Reset.** On stall trigger, **drop load 10%** and resume the per-session
    increment from there.
  - **Transition to Double.** After **2 resets** on the same lift (i.e. two
    full reset cycles that again stall), the lift transitions to Double
    progression. This corrects the LiftLab recollection: the "2 stall sessions"
    figure triggers a *reset*, not a programme transition; the transition is
    the second-order event after reset cycles fail.
  - **Eligibility (who starts on Linear).** A lift is seeded `Linear` at
    program generation when **both** hold: (i) the user's onboarding signals
    beginner-gains-or-layoff — `TrainingAge == Beginner` **OR**
    `LastTrainedBucket == MoreThanThreeMonthsAgo` (see Enum collapse below);
    and (ii) the lift `isCompound == true`. All other lifts seed `Double`
    unconditionally. A returning Late-Intermediate therefore restarts their
    squat on Linear but their curl on Double from day one.
  - **`LastTrainedBucket` enum collapse (locked).** The current 5-value enum
    (`Never` / `WithinLastMonth` / `OneToSixMonthsAgo` / `SixToTwelveMonthsAgo`
    / `MoreThanAYearAgo`) is collapsed to **3 values** to match the Linear
    eligibility threshold cleanly: **`Never` / `WithinLastThreeMonths` /
    `MoreThanThreeMonthsAgo`**. `MoreThanThreeMonthsAgo` is the single
    layoff bucket that routes non-beginners through Linear. This is a
    schema-adjacent change (the enum is persisted via `EnumConverters`), so
    the v1 reinstall policy applies (AGENTS.md §4.5). Affected surface:
    `TrainingProfileEnums.kt`, `ProfileDraftMapping.kt:83-87` (display
    strings), `EnumConverters.kt`, the volume-planner tests
    (`VolumeTargetPlannerTest.kt`, `VolumePlannerTest.kt` reference old
    bucket names as string-keyed profile overrides), and ~14 onboarding/data
    tests referencing the old buckets.
  - **Auto-graduation (locked).** Per Q1a resolution: the 2-reset transition
    fires **automatically**, no user prompt. The program's per-lift scheme
    silently updates; the user can always flip it back manually in
    EditWorkoutScreen (D11). This matches Stratum's quiet-intelligence ethos
    and removes a decision the user is ill-equipped to make.
  - **Edge cases the engine must handle.** Deloads (the 10% reset covers this);
    missed sessions (do not increment, do not credit a stall); per-lift stall
    sensitivity (a lift may stall independently of the others).

**Deferred to Open question Q1a.** Microplate/minimum-increment assumption,
RIR-based vs. absolute-rep-failure stall strictness, and per-lift increment
configuration support in the current domain model. None of these block
Structure; the engine is structured so each becomes a tuning parameter.
**Resolved Q1a items** (locked here): automatic vs. user-confirmed scheme
transition → **automatic, no confirm** (see Auto-graduation above); eligibility
→ compounds-only, beginner-or-layoff (see Eligibility above). Remaining
open: layoff-bucket threshold for Linear seeding (Q1b).

**Rejected.** (a) Adopting LiftLab's unvalidated `LinearProgression` verbatim
(research Gap 1 — no engine exists in-tree; LiftLab is external and unvalidated;
literature review corrected its trigger semantics). (b) Treating
`ProgressionScheme` as a no-op label forever (the enum exists to be consumed;
this ticket consumes it). (c) Forcing a fixed-rep 3×5 model for Linear (breaks
the rep-range + RIR model that unifies Linear and Double). (d) Daily undulating
periodization or weekly-periodization alternatives — more complex, not justified
for the Beginner cohort `Linear` targets. (e) Applying Linear to isolation
lifts (cannot increment session-to-session reliably; `isCompound == false`
disqualifies). (f) Surfacing Linear on a non-qualifying lift in
EditWorkoutScreen (toggle is hidden when `isCompound == false`).

### D9 — No new persistence for transient state

**Choice.** The overlay (in-progress edits, dynamic mini-sets, recommendations,
undo stack) is **in-memory ViewModel/Session state**. The only persistence
boundary is the existing `LogSetUseCase` when a set is performed/finalized, and
the existing session lifecycle (`StartTrainingSessionUseCase`,
`FinalizeTrainingSessionUseCase`, `AbandonTrainingSessionUseCase`). No new Room
tables or columns for transient UI state (AGENTS.md §4.5).

**Rejected.** Draft/in-progress tables (LiftLab pattern, rejected in
`workout-logging`); a "current workout" column on `training_sessions` for
overlay state (schema lock).

### D10 — "Unrealistic rep-range × set-count" program-generation concern: out of scope for this ticket

**Choice.** The concern (e.g. Bench Press 4–12 × 5 sets, where the 5th set is
unlikely to be meaningful) is a **program-generation** problem, not a
runtime-architecture problem. Research confirms generation applies one rep
range to all sets of a lift and no validation ties set-count to the rep band
(research Finding 22/23). This ticket's runtime architecture **renders and
progresses whatever structure it is given**; it does not fix generation. The
guard belongs in `program-generation-v2` / generation validation
(`ProgramValidation.kt`). See Open question Q3 for slicing.

**Rejected.** (a) Patching the rep range at runtime in the overlay (masks the
generation bug; the next regeneration reintroduces it). (b) Adding a runtime
validation that blocks the workout (wrong layer — generation owns template
validity).

### D11 — EditWorkoutScreen rep-range-display change: separate slice

**Choice.** Two parts, split by scope:

- **Per-lift progression-scheme toggle — IN SCOPE (this ticket).** Because
  `progressionScheme` lives on `WorkoutLift` (not `Program`) and Linear is
  meaningful only for compound lifts (D8 Eligibility), the user-facing toggle
  belongs on the qualifying `WorkoutLift` rows in **EditWorkoutScreen**, not on
  ProgramsScreen. This ticket delivers: (a) a `Linear`/`Double` affordance on
  each `WorkoutLift` row whose `lift.isCompound == true`; (b) the toggle is
  **hidden** for isolation lifts (Linear is not offered — D8 Rejected (e)/(f));
  (c) an info affordance opening a bottom sheet explaining the two schemes,
  eligibility (compounds only), and when each applies (beginner-gains or
  returning from layoff → Linear; otherwise → Double); (d) EditWorkoutScreen
  reads/writes `WorkoutLift.progressionScheme` (already round-tripped by
  `EditWorkoutDomainMapper:31,36`). The default at first-program generation
  comes from onboarding per D8 Eligibility; the user can override per lift
  thereafter. Toggling persists a `ProgramChangeEvent` (already in schema from
  `workout-logging`) so the weekly check-in can later surface "you switched
  schemes 3 weeks ago."
- **Rep-range display change + Myo-rep/drop-set authoring — separate slice.**
  The "rep range should display only on the top set" change, the Myo-rep/
  drop-set authoring UI, and the "block must be only/final set" / "drop set
  must follow another set" structural authoring constraints remain a separate
  slice (research Finding 19/20/21 — `EditSetState` has no `setType`/
  `isTopSet` today; this ticket does not add them).

**Rejected.** (a) ProgramsScreen program-level toggle — wrong granularity
(`progressionScheme` is per-lift, not per-program; a program legitimately mixes
compound-Linear and isolation-Double lifts). (b) Offering Linear on isolation
lifts (D8 Rejected (e)). (c) Bundling the full EditWorkoutScreen rewrite into
this ticket (the rep-range-display + Myo-rep/drop-set authoring remains a
separate slice).

---

## Open questions

> **Q1–Q4 resolved 2026-07-05** (human approval pass): Q1 → research-then-decide
> (done; D8 locked to Greyskull-style hybrid). Q2 → population-gradient default
> (folded into D5/D7). Q3 → out of scope (D10 stands). Q4 → separate slice
> (D11 stands). The items below are the follow-on questions surfaced by the
> linear-progression literature review (`research-linear-progression.md`); none
> block Structure — each becomes a tuning parameter on the locked D8 algorithm.

### Q1a — Linear progression tuning parameters (post-Structure, non-blocking)

The D8 algorithm is locked; the following are tuning knobs the engine must
expose so the human can finalize without re-architecting:

- **Microplate / minimum-increment assumption.** Are 2.5 lb upper / 5 lb lower
  defaults viable assuming standard plates, or does the model need a
  configurable minimum increment per lift (microplate-friendly)?
- **Stall-definition strictness.** Stall = "top-set RIR met but bottom of rep
  range missed" (RIR-based, recommended) vs. "absolute rep failure to clear
  bottom of range". RIR-based keeps the model coherent with D4 live
  recommendations.
- **Per-lift increment configuration.** Does the current domain model
  (`Lift`/`LiftSet`) carry the right hook for a per-lift increment override, or
  does Structure need to add one?

**Resolved Q1a items (locked in D8):**

- *Automatic vs. user-confirmed scheme transition* → **automatic, no confirm**
  (D8 Auto-graduation).
- *Eligibility* → **compounds-only** (`isCompound == true`) **for beginner-or-
  layoff users** (`TrainingAge == Beginner` OR `LastTrainedBucket ==
  MoreThanThreeMonthsAgo` after the enum collapse) (D8 Eligibility).

### Q1b — Layoff-bucket threshold for Linear seeding (RESOLVED 2026-07-05)

Threshold locked at **3 months**. To express this cleanly the
`LastTrainedBucket` enum is collapsed from 5 values to 3: `Never` /
`WithinLastThreeMonths` / `MoreThanThreeMonthsAgo`. The latter is the single
layoff bucket that routes non-beginners through Linear. No longer a tuning
knob — the threshold is now structural (encoded in the enum itself, not a
runtime constant). See D8 Eligibility + Enum collapse.

---

## Out of scope (this ticket)

- `:feature:workout` Compose pixel implementation (built during implementation
  slice 1, after Structure/Plan).
- Cloud LLM program generation (owned by `program-synthesis` /
  `program-generation-v2`).
- Room migrations (pre-release v1 reinstall policy, AGENTS.md §4.5).
- The program-generation rep-range × set-count guard (D10, Q3).
- EditWorkoutScreen Myo-rep/drop-set authoring UI and rep-range-display change
  (separate slice per D11; the per-lift scheme toggle IS in scope — D11).
- Weekly LLM check-in (design-noted in `workout-logging` D11).
