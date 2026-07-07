# Ticket: workout-screen

## Summary

Design and plan the **runtime architecture** for the live workout experience in
Stratum: a clean three-way separation between (1) workout structure, (2) the
initial progression snapshot computed once from history, and (3) a live workout
overlay that captures runtime edits, logged values, and dynamic
recommendations. This separation is the foundation of the `WorkoutScreen`
architecture and unblocks correct implementations of live recommendation
adjustments, dynamic set creation (Myo-reps, drop sets), and undo behavior.

This ticket is the architectural successor to `workout-logging` (persistence
foundation) and consumes the program templates produced by `program-synthesis` /
`program-generation-v2`.

## Problem

LiftLab (`~/Code/LiftLab`) tightly coupled progression computation with live
workout state. Its flow was:

```
Historical data → Progression calculation → Create new workout state → Emit as Flow
```

This caused concrete, recurring problems:

- Progression was continually recomputed.
- Live logging mutated the same state progression depended on.
- Myo-reps became especially difficult to model.
- Live recommendation updates fought against the original progression output.

Worked example of the failure mode:

- Last workout indicates today's recommendation: `90 lbs × 6 reps`.
- User achieves only `90 lbs × 4 reps`.
- The next-set recommendation should immediately change: estimate current e1RM
  from the performed set and answer "what weight lets the user hit the bottom of
  the target rep range on the next set?"
- That live recommendation must **not** require recomputing the original
  progression state.

Stratum's progression model is simpler than LiftLab's (top set in a prescribed
rep range + RIR target; backoff sets at the same weight for AMRAP-at-RIR), so
the runtime question reduces to: *based on performance so far, hold weight or
reduce it so remaining backoff sets stay meaningful?* — but only if the
architecture keeps the layers independent.

## Agreed direction (from planning notes — to be validated in research)

### Three independent layers

1. **Workout structure** — lifts, exercises, set ordering, dynamically
   added/deleted sets. No progression information.
2. **Initial progression snapshot** — `Set → recommended weight, recommended
   reps`, computed once from historical data. Never mutates during a workout.
3. **Live workout overlay** — logged reps/weights, live recommendation
   adjustments, dynamically created sets, user edits, completion state.

Rendering combines all three: `Structure + Snapshot + Overlay → Rendered Workout`.

### Progression recomputation rules

- Snapshot recomputes only when historical context actually changes (e.g. an
  exercise swap). Adding/deleting sets must **not** trigger recalculation.
- Adding a set: copy previous weight recommendation; estimate expected reps via
  a fatigue model (user-specific eventually, population gradient initially).
- Deleting a set: remove it. No recalculation.

### Live recommendations

- Driven by per-set e1RM estimation from performed sets.
- Independent of the snapshot; overlay-only.

### Future set types (must be supported from the start)

- **Myo-reps** — two variants:
  - *Type A (rep floor):* activation AMRAP, then mini-sets until one hits the
    rep floor.
  - *Type B (rep matching):* activation AMRAP, then mini-sets until cumulative
    mini-set reps equal activation reps.
- **Drop sets** — must immediately follow another set; weight reduction
  estimated via fatigue model (population initially, user-specific eventually).
- Dynamic set creation is required (Myo-reps generate sets during the workout);
  users must be able to delete generated sets, mark them incomplete, and undo.

### Progression algorithms

1. **Double progression** — top set in rep range + RIR target (Stratum's primary
   model).
2. **Linear progression** — research best practices before implementation.
   LiftLab has an unvalidated `LinearProgression`; current recollection is
   "increase weight every session or weekly; after ~2 stall sessions, transition
   into double progression." Research to confirm.

### Historical data priority (for snapshot generation)

1. Same lift, same workout, same position, previous session (n−1).
2. Same lift, same workout, same position, earlier sessions (n−2, n−3, …).
3. Same lift from another workout.

Always use the highest-signal data available; do not require data only from the
immediately previous identical workout.

### EditWorkoutScreen constraints (related slice)

- A Myo-rep block must be either the only set or the final set of the exercise
  (so progression can still be driven from the primary top set).
- A drop set must immediately follow another set.
- Rep range should display only on the top set (progression is driven entirely
  there); backoff sets need no rep range. A header info affordance should open a
  bottom sheet explaining Stratum progression.

### Program generation concern (related slice)

Current rep-range generation can produce unrealistic workouts (e.g. Bench Press
4–12 × 5 sets: if the top set is 4 reps, the 5th set is unlikely to be
meaningful without powerlifting-length rest). Needs investigation during
research/design.

## Project breakdown (implementation slices — to be refined in plan phase)

1. **WorkoutScreen** — live workout architecture, runtime recommendation
   updates, dynamic set creation, rendering overlays.
2. **EditWorkoutScreen** — editing restrictions, rep-range display changes,
   Myo-rep/drop-set authoring constraints.
3. **Backend / Progression** — initial snapshot generation, double + linear
   progression, historical data prioritization, fatigue estimation, live
   recommendation calculations.

## Out of scope (this ticket)

- `:feature:workout` Compose pixel implementation (covered by structure/plan,
  built during implementation slice 1).
- Cloud LLM program generation (already owned by `program-synthesis` /
  `program-generation-v2`).
- Room migrations (pre-release: v1 reinstall policy per AGENTS.md §4.5).

## Constraints

- Read `AGENTS.md`, `contracts/ARCHITECTURE.md`, `contracts/UI_DESIGN.md`,
  `contracts/DATA_LAYER_RULES.md`, `contracts/CODE_STYLE_RULES.md`.
- Clean Architecture: domain owns progression ports and the deterministic
  recommendation engine; UI consumes presentation models mapped from domain —
  never domain models directly in composables (AGENTS.md §4.1).
- No on-device LLM; deterministic core only for runtime recommendations
  (AGENTS.md §4.6).
- Schema lock: no new tables/columns for transient UI state; any persistence
  change requires explicit authorization (AGENTS.md §4.5) and depends on the
  `workout-logging` schema landing first.
- JUnit 5 + Turbine + fakes for tests; MockK only on third-party SDK boundaries
  (AGENTS.md §4.4).

## Dependencies on prior tickets

- `workout-logging` — provides `TrainingSession`, `logged_sets` with lift
  snapshots, program change events, cycle progress pointers. Currently at Final
  Review.
- `program-synthesis` / `program-generation-v2` — produce the program/workout
  templates that WorkoutScreen renders and progresses.
- `response-profile` — response-extraction model feeding snapshot generation.

## Success criteria

- Approved `research.md`, `design.md`, `structure.md`, `spec.md`, `test_spec.md`.
- A documented three-layer domain model (structure / snapshot / overlay) with
  clear ownership, immutability guarantees, and merge rules for rendering.
- Defined ports for: initial snapshot generation, live recommendation engine,
  fatigue estimation, dynamic set lifecycle.
- Concrete decision on linear progression algorithm (validated against research,
  not LiftLab recollection).
- Identified seams for Myo-rep (Type A + B) and drop set support without
  re-architecting later.
