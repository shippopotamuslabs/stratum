# Structure: workout-screen

Vertical-slice execution outline for the live workout runtime architecture.
This is the "C header" for the feature: signatures, types, phases, and
checkpoints — not implementation bodies. It honors the human-approved
`design.md` decisions D1–D11 (2026-07-05) and the linear-progression research
in `research-linear-progression.md`.

Grounded in `research.md` (verified against code 2026-07-05). Builds on the
`workout-logging` persistence foundation (TrainingSession, LoggedSet with
snapshotted LoggedLiftContext, cycle pointers, ProgramChangeEvent,
ObserveCycleProgressUseCase) and consumes templates from
`program-synthesis` / `program-generation-v2`.

---

## Layer recap (D1, D2)

| Layer | Owns | Lives in | Mutability |
|-------|------|----------|------------|
| **Structure projection** | Runtime view of `WorkoutLift`/`LiftSet` ordering + dynamic adds/deletes/generations; carries set type, rep range, RIR, top-set marking — **no load** | `:core:domain` (read model) | Re-derived by the merge use case on overlay structural change; template rows are read-only |
| **Snapshot** | `SetSlot → recommended weight + baseline expected reps`, computed once from history | `:core:domain` (immutable value) | Frozen for session life; recomputed only on exercise swap (D3) |
| **Overlay** | Logged values, live recommendations, dynamic set entries, completion state, undo stack | `:feature:workout` (ViewModel `MutableStateFlow`) | The only mutable layer (D9 — in-memory only) |
| **Rendered workout** | Merge result of the three above into per-set rows for display | `:core:domain` (read model produced by a use case) | Pure function of its inputs |

Merge rule (D1): structure decides row existence/position/type/target band;
snapshot supplies the baseline recommended weight; overlay supersedes in
priority order — (a) logged value, (b) live recommendation for an upcoming
set when a newer per-set e1RM exists, (c) dynamic-set estimate. Overlay
**never writes back** into the snapshot.

---

## Slice index

Eight vertical slices, each independently verifiable end-to-end before the
next begins. Order is chosen so each slice exercises one new contract while
reusing the merged-rendered-workout seam introduced in Slice 1.

| # | Slice | Goal (one line) |
|---|-------|-----------------|
| 1 | **Snapshot + merge skeleton** | Compute-once initial progression snapshot for a single Standard top set from the historical-priority read, merged with an empty overlay into a rendered-workout read model behind a use case. No overlay mutation yet. |
| 2 | **Double progression + live recommendation** | Double-progression snapshot generation for full lifts from history, plus the overlay-only recommendation engine (`weightForReps` / `repsForWeight` inverses; top-set `repMin` vs backoff `BACKOFF_REP_FLOOR` target) for the next upcoming set. |
| 3 | **Dynamic set lifecycle (Standard)** | Add / delete / undo for Standard sets with fatigue-model rep estimation (population-gradient default + per-lift override). |
| 4 | **Linear progression (Greyskull-style hybrid)** | Per-session increment gating, 2-stall reset, 10% reset, **auto-transition to Double after 2 resets** (D8 locked). Eligibility: compounds-only for beginner-or-layoff users (`schemeForTrainingAge` extended to consult `lastTrained`). **Also collapses `LastTrainedBucket` 5→3** to make the 3-month layoff threshold structural. Remaining Q1a knobs surfaced as parameters. |
| 5 | **Myo-reps Type A + Type B** | Rep-floor and rep-matching lifecycles, generated mini-sets, only-set/final-set constraint validation. |
| 6 | **Drop sets** | Must-follow constraint validation + fatigue-estimated weight reduction from the preceding set. |
| 7 | **`:feature:workout` presentation skeleton** | ViewModel owning overlay state + presentation model; Compose `WorkoutRoute` consuming the merged rendered workout (skeleton — pixel polish out of scope per ticket). |
| 8 | **EditWorkoutScreen per-lift scheme toggle** | `Linear`/`Double` affordance on compound `WorkoutLift` rows only (`isCompound == true`); hidden for isolation lifts; explainer bottom sheet; toggle writes `progressionScheme` + appends a `ProgramChangeEvent`. Myo-rep/drop-set authoring and rep-range-display change remain out of scope (separate slice per D11). |

---

## Per-slice checklist

### Slice 1 — Snapshot + merge skeleton

**Components touched**

- New: snapshot value types, structure-projection read model, rendered-workout read model, snapshot-generation use case + port, historical-priority repository read, merge use case, merge function (pure), `SetSlot` identity.
- New fake in `:core:testing` for the historical-priority port.

**New types / signatures**

```kotlin
// :core:domain — workout/WorkoutSlot.kt
@JvmInline value class OverlaySetInstanceId(val value: String)

// Identity for a slot that is stable across snapshot/overlay merge.
// Template slots key on LiftSetId; dynamic/generated slots key on OverlaySetInstanceId.
sealed interface SetSlotId {
    data class Template(val liftSetId: LiftSetId) : SetSlotId
    data class Overlay(val instanceId: OverlaySetInstanceId) : SetSlotId
}

// :core:domain — workout/SetSlot.kt
data class SetSlot(
    val slotId: SetSlotId,
    val workoutLiftId: WorkoutLiftId,
    val liftId: LiftId,
    val position: Int,
    val setType: SetType,
    val isTopSet: Boolean,
    val repMin: Int,
    val repMax: Int,
    val rirGoal: Int,
    val restSeconds: Int,
)  // structure projection — no load

// :core:domain — workout/ProgressionSnapshot.kt
data class SlotRecommendation(
    val slotId: SetSlotId,
    val recommendedWeight: Weight,
    val baselineExpectedReps: Int,
)
data class ProgressionSnapshot(
    val workoutId: WorkoutId,
    val bySlot: Map<SetSlotId, SlotRecommendation>,
)  // immutable for session life (D3)

// :core:domain — workout/WorkoutOverlay.kt
// Slice 1 ships an EMPTY overlay value (no mutation API yet — added in Slice 3).
data class LoggedSetValue(
    val slotId: SetSlotId,
    val weight: Weight,
    val reps: Int,
    val rir: Int?,
)
data class WorkoutOverlay(
    val loggedSets: Map<SetSlotId, LoggedSetValue> = emptyMap(),
    // Slice 2 adds: recommendations: Map<SetSlotId, Weight>
    // Slice 3 adds: structural mutations + undo stack
)  // owned by the ViewModel (D2, D9)

// :core:domain — workout/RenderedWorkout.kt
data class RenderedSet(
    val slotId: SetSlotId,
    val workoutLiftId: WorkoutLiftId,
    val liftId: LiftId,
    val position: Int,
    val setType: SetType,
    val isTopSet: Boolean,
    val repMin: Int,
    val repMax: Int,
    val rirGoal: Int,
    val restSeconds: Int,
    val recommendedWeight: Weight,     // snapshot baseline OR overlay-derived
    val recommendedReps: Int,           // snapshot baseline OR overlay-derived
    val loggedValue: LoggedSetValue?,   // overlay (a)
)
data class RenderedWorkout(
    val workoutId: WorkoutId,
    val workoutName: String,
    val lifts: List<RenderedWorkoutLift>,
)
data class RenderedWorkoutLift(
    val workoutLiftId: WorkoutLiftId,
    val liftId: LiftId,
    val liftName: String,
    val progressionScheme: ProgressionScheme,
    val sets: List<RenderedSet>,
)

// :core:domain — workout/ProgressionHistoryPort.kt
interface ProgressionHistoryPort {
    // Historical-priority read (research Gap 4). Returns sets for a slot across
    // sessions, ordered n−1, n−2, … (same lift + same workout + same position),
    // then same lift from other workouts. Empty if no history.
    suspend fun historyForSlot(request: SlotHistoryRequest): List<SlotHistoryEntry>
}
data class SlotHistoryRequest(
    val programId: ProgramId,
    val workoutId: WorkoutId,
    val workoutLiftId: WorkoutLiftId,
    val liftId: LiftId,
    val setIndex: Int,
)
data class SlotHistoryEntry(
    val session: TrainingSession,
    val loggedSet: LoggedSet,
)

// :core:domain — workout/GenerateProgressionSnapshotUseCase.kt
class GenerateProgressionSnapshotUseCase @Inject constructor(
    private val progressionHistoryPort: ProgressionHistoryPort,
    // double/linear engines injected here are added in Slices 2/4; Slice 1 uses
    // a single Standard top-set placeholder computation behind the same call.
) {
    // Compute-once snapshot (D3). Called at session start (after
    // StartTrainingSessionUseCase resolves the workout) or on exercise swap.
    suspend operator fun invoke(request: SnapshotRequest): Result<ProgressionSnapshot>
}
data class SnapshotRequest(
    val programId: ProgramId,
    val workoutId: WorkoutId,
    val lifts: List<WorkoutLift>,
)

// :core:domain — workout/MergeRenderedWorkoutUseCase.kt
class MergeRenderedWorkoutUseCase @Inject constructor() {
    // Pure. Combines structure projection + snapshot + overlay → rendered workout.
    operator fun invoke(
        structure: WorkoutStructureProjection,
        snapshot: ProgressionSnapshot,
        overlay: WorkoutOverlay,
    ): RenderedWorkout
}
data class WorkoutStructureProjection(
    val workoutId: WorkoutId,
    val workoutName: String,
    val lifts: List<WorkoutLiftProjection>,
)
data class WorkoutLiftProjection(
    val workoutLiftId: WorkoutLiftId,
    val liftId: LiftId,
    val liftName: String,
    val progressionScheme: ProgressionScheme,
    val slots: List<SetSlot>,
)

// :core:domain — workout/ProjectWorkoutStructureUseCase.kt
class ProjectWorkoutStructureUseCase @Inject constructor() {
    // Pure. Builds the structure projection from the template Workout (no load).
    // Slice 3 extends this to fold in overlay structural mutations.
    operator fun invoke(workout: Workout, liftNames: Map<LiftId, String>): WorkoutStructureProjection
}
```

**File map (Create)**

| File | Responsibility |
|------|----------------|
| `core/domain/.../workout/SetSlot.kt` | `SetSlot`, `SetSlotId`, `OverlaySetInstanceId` — structure identity |
| `core/domain/.../workout/ProgressionSnapshot.kt` | Snapshot value types (`SlotRecommendation`, `ProgressionSnapshot`) |
| `core/domain/.../workout/WorkoutOverlay.kt` | Overlay value (`WorkoutOverlay`, `LoggedSetValue`) — Slice 1 shape only |
| `core/domain/.../workout/RenderedWorkout.kt` | Merge-result read model (`RenderedWorkout`, `RenderedWorkoutLift`, `RenderedSet`) |
| `core/domain/.../workout/WorkoutStructureProjection.kt` | Structure projection read model |
| `core/domain/.../workout/ProgressionHistoryPort.kt` | Historical-priority port + request/entry types |
| `core/domain/.../workout/GenerateProgressionSnapshotUseCase.kt` | Snapshot-generation use case (Slice 1: Standard top set only) |
| `core/domain/.../workout/MergeRenderedWorkoutUseCase.kt` | Pure merge use case |
| `core/domain/.../workout/ProjectWorkoutStructureUseCase.kt` | Pure structure projection use case |
| `core/data/.../repository/ProgressionHistoryRepository.kt` | Implements `ProgressionHistoryPort` over `TrainingLogRepository` DAOs (research Gap 4) |
| `core/testing/.../workout/FakeProgressionHistoryRepository.kt` | In-memory fake (UNIT_TESTING.md §6) |

**File map (Modify)**

| File | Change |
|------|--------|
| `core/data/.../db/TrainingLogDaos.kt` | Add indexed query joining `(programId, workoutId, workoutLiftId, setIndex)` across sessions (research Gap 4) |
| `settings.gradle.kts` | No change in Slice 1 (no new module yet) |

**Checkpoint**

- `./gradlew :core:domain:test` — `GenerateProgressionSnapshotUseCaseTest` (Standard top set, happy path), `MergeRenderedWorkoutUseCaseTest` (empty overlay → snapshot baseline rendered), `ProjectWorkoutStructureUseCaseTest`.
- `./gradlew :core:data:testDebugUnitTest` — `ProgressionHistoryRepositoryTest` (historical-priority ordering: n−1 first, then n−2, then same-lift-other-workout).

---

### Slice 2 — Double progression + live recommendation

**Components touched**

- New: double-progression engine (pure), recommendation engine (pure), `EstimatedOneRm.weightForReps` inverse, recommendation overlay value, snapshot engine wired into `GenerateProgressionSnapshotUseCase`.

**New types / signatures**

```kotlin
// :core:domain — workout/progression/DoubleProgressionEngine.kt
class DoubleProgressionEngine @Inject constructor() {
    // Per-slot recommendation. Increments load when the TOP of the rep range was
    // achieved at the RIR target in history; holds load otherwise. Backoff slots
    // inherit the top-set weight (AMRAP-at-RIR). Pure.
    fun recommend(request: SlotProgressionRequest): SlotRecommendation
}
data class SlotProgressionRequest(
    val slot: SetSlot,
    val history: List<SlotHistoryEntry>,
    val fallbackOneRm: Weight?,  // from LiftTrainingInsights when available
)

// :core:domain — workout/progression/ProgressionEngine.kt
// Dispatch interface consumed by GenerateProgressionSnapshotUseCase.
interface ProgressionEngine {
    fun recommend(request: SlotProgressionRequest): SlotRecommendation
    val scheme: ProgressionScheme
}

// :core:domain — workout/RecommendationEngine.kt
class RecommendationEngine @Inject constructor() {
    // Overlay-only (D4). Hold-vs-reduce decision for the next upcoming set,
    // computed from the most recent performed set.
    //
    // targetReps source depends on set role:
    //   - Top set (has rep range):  slot.repMin   (bottom of prescribed range)
    //   - Backoff set (RIR-only):   BACKOFF_REP_FLOOR (default 5; no rep range)
    //
    // Decision: project reps achievable at currentWeight (inverse forward
    // formula minus targetRir). HOLD if projected ≥ targetReps; REDUCE to
    // weightForReps(e1rm, targetReps) if projected < targetReps.
    // Returns null when no performed set exists yet for the lift.
    fun recommendNextSet(
        lastPerformed: LoggedSetValue,
        targetSlot: SetSlot,
        currentWeight: Weight,        // snapshot baseline (read-only)
    ): RecommendationOutcome?
}

// NEW — :core:domain — workout/RecommendationContracts.kt
// "Meaningful backoff set" = at least BACKOFF_REP_FLOOR reps at target RIR.
// Lifts the "reasonable rep count by the last backoff set" intent into a
// named, testable constant (D4). Tunable.
const val BACKOFF_REP_FLOOR: Int = 5

sealed interface RecommendationOutcome {
    data class Hold(val weight: Weight) : RecommendationOutcome
    data class Reduce(val weight: Weight) : RecommendationOutcome  // weightForReps(e1rm, targetReps)
}

// :core:domain — importing/LiftNameResolver.kt  (MODIFY — extend EstimatedOneRm)
object EstimatedOneRm {
    fun calculate(weight: Double, reps: Int): Double  // existing, forward
    // NEW: inverse — "what weight lets the lifter hit targetReps at the given e1RM".
    // Inverts the same Brzycki (reps ≤ 10) / Epley (else) formula as calculate().
    fun weightForReps(estimatedOneRm: Double, targetReps: Int): Double
    // NEW: second inverse — "how many reps is the lifter good for at this weight
    // at this e1RM". Used by RecommendationEngine to project achievable reps at
    // currentWeight before applying the target RIR subtraction.
    fun repsForWeight(estimatedOneRm: Double, weight: Double): Int
}

// :core:domain — workout/WorkoutOverlay.kt  (EXTEND)
data class WorkoutOverlay(
    val loggedSets: Map<SetSlotId, LoggedSetValue> = emptyMap(),
    val recommendations: Map<SetSlotId, Weight> = emptyMap(),  // NEW — live rec (D4)
)
```

**File map (Create)**

| File | Responsibility |
|------|----------------|
| `core/domain/.../workout/progression/ProgressionEngine.kt` | Dispatch interface + `SlotProgressionRequest` |
| `core/domain/.../workout/progression/DoubleProgressionEngine.kt` | Double-progression math (D8 primary) |
| `core/domain/.../workout/RecommendationEngine.kt` | Overlay-only next-set recommendation (D4) |
| `core/domain/.../workout/RecommendationContracts.kt` | `BACKOFF_REP_FLOOR`, `RecommendationOutcome` (Hold/Reduce) |

**File map (Modify)**

| File | Change |
|------|--------|
| `core/domain/.../importing/LiftNameResolver.kt` | Add `EstimatedOneRm.weightForReps` inverse (Brzycki/Epley inversion) |
| `core/domain/.../workout/GenerateProgressionSnapshotUseCase.kt` | Wire `DoubleProgressionEngine` for `Double` lifts (Slice 1 placeholder replaced) |
| `core/domain/.../workout/WorkoutOverlay.kt` | Add `recommendations` field |
| `core/domain/.../workout/MergeRenderedWorkoutUseCase.kt` | Overlay priority (b): recommendation supersedes snapshot for upcoming unlogged set |
| `core/testing/.../workout/FakeProgressionHistoryRepository.kt` | Extend seed to drive double-progression increment cases |

**Checkpoint**

- `./gradlew :core:domain:test` —
  - `DoubleProgressionEngineTest` (increment when top-of-range at RIR hit; hold otherwise; backoff inherits top-set weight).
  - `RecommendationEngineTest`:
    - Top set: performed set → e1RM → `weightForReps(repMin)`; `Hold` when projected ≥ `repMin`, `Reduce` when projected < `repMin`.
    - Backoff set: `targetReps = BACKOFF_REP_FLOOR (5)`; `Hold` when projected ≥ 5 at target RIR, `Reduce` to `weightForReps(e1rm, 5)` when projected < 5.
    - Returns null when no performed set exists yet for the lift.
  - `EstimatedOneRmWeightForRepsTest` (round-trip with `calculate`); `EstimatedOneRmRepsForWeightTest` (round-trip with `calculate`).
- Merge test extended: logged value present → recommendation ignored; upcoming set with recommendation → recommendation (`Hold` or `Reduce` weight) rendered over snapshot baseline.

---

### Slice 3 — Dynamic set lifecycle (Standard)

**Components touched**

- New: fatigue-model contract + population-gradient default constant, overlay mutation API (add/delete/undo), add-set rep estimation via fatigue projection.

**New types / signatures**

```kotlin
// :core:domain — response/ResponseConstants.kt  (MODIFY — add population default)
// Population-gradient default for within-session e1RM decay per set index.
// Used when a lift has no per-lift LiftTrainingInsights.fatigueDecaySlope (Q2 resolution).
const val POPULATION_FATIGUE_DECAY_SLOPE: Double
const val POPULATION_FATIGUE_DECAY_INTERCEPT: Double

// :core:domain — workout/fatigue/FatigueModel.kt
interface FatigueModel {
    // Projects expected e1RM (and thus expected reps at a given weight) one
    // set-index forward from the last performed/top-set e1RM. Per-lift values
    // override the population default (D5/D7, Q2).
    fun expectedRepsForAddedSet(
        baselineOneRm: Double,
        targetWeight: Weight,
        nextSetIndex: Int,
        perLiftSlope: Double?,      // from LiftTrainingInsights; null → population default
        perLiftIntercept: Double?,
    ): Int
}

// :core/domain — workout/WorkoutOverlay.kt  (EXTEND)
sealed interface StructuralMutation {
    data class AddSet(val afterSlotId: SetSlotId, val newSlot: SetSlot) : StructuralMutation
    data class DeleteSet(val slotId: SetSlotId) : StructuralMutation
    // Slice 5 adds: GenerateMyoRepMiniSet; Slice 6: AddDropSet
}
data class WorkoutOverlay(
    val loggedSets: Map<SetSlotId, LoggedSetValue> = emptyMap(),
    val recommendations: Map<SetSlotId, Weight> = emptyMap(),
    val addedSlots: List<SetSlot> = emptyList(),          // NEW — dynamic structural entries
    val deletedSlotIds: Set<SetSlotId> = emptySet(),      // NEW
    val undoStack: List<StructuralMutation> = emptyList(), // NEW (D5)
) {
    fun apply(mutation: StructuralMutation): WorkoutOverlay  // returns new overlay + pushes undo
    fun undo(): WorkoutOverlay                                // pops last mutation
}

// :core/domain — workout/EstimateAddedSetRepsUseCase.kt
class EstimateAddedSetRepsUseCase @Inject constructor(
    private val fatigueModel: FatigueModel,
) {
    // Add-set rep estimation (D5): copy previous recommended weight; estimate
    // expected reps via fatigue model projected one set-index forward.
    operator fun invoke(
        previousRecommendation: SlotRecommendation,
        nextSetIndex: Int,
        perLiftSlope: Double?,
        perLiftIntercept: Double?,
    ): Int
}
```

**File map (Create)**

| File | Responsibility |
|------|----------------|
| `core/domain/.../workout/fatigue/FatigueModel.kt` | Fatigue-model port + population default usage (D5/D7, Q2) |
| `core/domain/.../workout/fatigue/PopulationFatigueModel.kt` | Default impl using `POPULATION_FATIGUE_DECAY_*` constants |
| `core/domain/.../workout/EstimateAddedSetRepsUseCase.kt` | Add-set rep estimation (D5) |
| `core/testing/.../workout/FakeFatigueModel.kt` | In-memory fatigue fake |

**File map (Modify)**

| File | Change |
|------|--------|
| `core/domain/.../response/ResponseConstants.kt` | Add `POPULATION_FATIGUE_DECAY_SLOPE`/`INTERCEPT` (Q2 resolution) |
| `core/domain/.../workout/WorkoutOverlay.kt` | Add `StructuralMutation`, `addedSlots`, `deletedSlotIds`, `undoStack`, `apply`/`undo` |
| `core/domain/.../workout/ProjectWorkoutStructureUseCase.kt` | Fold overlay structural mutations into the projection (adds appended, deletes removed) |
| `core/domain/.../workout/MergeRenderedWorkoutUseCase.kt` | Overlay priority (c): added-set estimate from fatigue model; undo reverts cleanly |

**Checkpoint**

- `./gradlew :core:domain:test` — `EstimateAddedSetRepsUseCaseTest` (per-lift slope present → uses it; absent → population default; rep projection monotonic non-increasing across set index), `WorkoutOverlayMutationTest` (add/delete/undo; undo of add removes the slot; undo of delete restores it; snapshot untouched — D3/D9 invariant).
- Merge test extended: added set renders with copied weight + fatigue-estimated reps; deleted set absent.

---

### Slice 4 — Linear progression (Greyskull-style hybrid)

**Components touched**

- New: linear-progression engine (pure) with all Q1a tuning knobs surfaced as explicit parameters, snapshot dispatch for `Linear` lifts, scheme-transition event. **Modifies**: `LastTrainedBucket` enum (5→3 collapse, D8 Eligibility), `schemeForTrainingAge` (now consults `lastTrained` + `isCompound`), onboarding display strings, and the volume-planner profile-override tests that reference old bucket names.

**New types / signatures**

```kotlin
// :core/domain — workout/progression/LinearProgressionConfig.kt
// Remaining Q1a tuning knobs (D8 locks transition to Automatic + eligibility to compounds-only).
data class LinearProgressionConfig(
    val lowerBodyIncrement: Weight,             // default 5 lb (Greyskull/SS consensus)
    val upperBodyIncrement: Weight,             // default 2.5 lb
    val minimumIncrement: Weight,               // Q1a microplate/minimum-increment assumption
    val perLiftIncrementOverride: Map<LiftId, Weight>,  // Q1a per-lift increment config
    val stallThresholdSessions: Int,            // 2 consecutive sub-range sessions (Greyskull)
    val resetFraction: Double,                  // 0.10 (10% reset consensus); per-lift overridable
    val perLiftResetFractionOverride: Map<LiftId, Double>,  // e.g. upper-body 0.05
    val resetsBeforeTransition: Int,            // 2 resets → AUTO-transition to Double (D8 locked)
    val stallStrictness: StallStrictness,       // Q1a RIR-based vs absolute
    // No transitionMode field — D8 locks auto-transition, no user confirm.
)
enum class StallStrictness {
    RirBased,        // stall = top-set RIR met but bottom of rep range missed (recommended)
    AbsoluteFailure, // stall = could not complete bottom of range at any RIR
}

// NEW — :core/domain — program/generation/reference/ProgramRules.kt  (MODIFY)
// `lastTrained` is now consulted alongside `trainingAge` (D8 Eligibility).
// Linear is only seeded for COMPOUND lifts of beginner-or-layoff users.
// After the enum collapse, "layoff" is exactly one bucket — no constant needed.
fun schemeForTrainingAge(
    trainingAge: TrainingAge?,
    lastTrained: LastTrainedBucket?,
    isCompound: Boolean,
): ProgressionScheme = when {
    !isCompound -> ProgressionScheme.Double                                  // isolation never Linear (D8)
    trainingAge == TrainingAge.Beginner -> ProgressionScheme.Linear
    lastTrained == LastTrainedBucket.MoreThanThreeMonthsAgo -> ProgressionScheme.Linear
    else -> ProgressionScheme.Double
}

// NEW — :core:domain — onboarding/TrainingProfileEnums.kt  (MODIFY — enum collapse)
// 5 values -> 3 per D8 Eligibility. Layoff threshold (3 months) is now structural.
enum class LastTrainedBucket {
    Never,
    WithinLastThreeMonths,        // was WithinLastMonth (relabeled) — not Linear-eligible
    MoreThanThreeMonthsAgo,       // Linear-eligible for non-beginners on compounds
    ;
}

// :core/domain — workout/progression/LinearProgressionEngine.kt
class LinearProgressionEngine @Inject constructor(
    private val config: LinearProgressionConfig,
) : ProgressionEngine {
    override val scheme: ProgressionScheme = ProgressionScheme.Linear

    // Per-session increment GATED by RIR achievement on the top set (D8).
    // Linear increments when BOTTOM of rep range met at RIR (load-first);
    // Double (Slice 2) increments when TOP met at RIR (reps-first).
    // Tracks per-lift stall count + reset count across history entries.
    override fun recommend(request: SlotProgressionRequest): SlotRecommendation

    // Stall/reset/transition state derived from history (pure; no persistence —
    // computed from SlotHistoryEntry sequence each snapshot generation).
    fun diagnose(liftId: LiftId, history: List<SlotHistoryEntry>): LinearProgressionState
}
data class LinearProgressionState(
    val liftId: LiftId,
    val currentStallCount: Int,
    val currentResetCount: Int,
    val shouldReset: Boolean,
    val shouldTransitionToDouble: Boolean,
)

// :core/domain — workout/progression/SchemeTransitionEvent.kt
// Emitted when a Linear lift exhausts `resetsBeforeTransition`. D8 locks the
// transition to Automatic — the snapshot applies it (mutates the lift's
// progressionScheme in-memory and appends a ProgramChangeEvent via the existing
// AppendProgramChangeEventUseCase); no UI prompt. User can flip back manually
// in EditWorkoutScreen (Slice 8). No new persistence for transient state (D9).
data class SchemeTransitionEvent(
    val workoutLiftId: WorkoutLiftId,
    val liftId: LiftId,
    val from: ProgressionScheme,
    val to: ProgressionScheme,
    val reason: SchemeTransitionReason,
)
enum class SchemeTransitionReason { ResetCyclesExhausted }
```

**File map (Create)**

| File | Responsibility |
|------|----------------|
| `core/domain/.../workout/progression/LinearProgressionConfig.kt` | Remaining Q1a tuning knobs as explicit parameters (no `transitionMode`) |
| `core/domain/.../workout/progression/LinearProgressionEngine.kt` | Greyskull-style hybrid math (D8) |
| `core/domain/.../workout/progression/SchemeTransitionEvent.kt` | Auto-transition event type |

**File map (Modify)**

| File | Change |
|------|--------|
| `core/domain/.../program/generation/reference/ProgramRules.kt` | `schemeForTrainingAge` now takes `trainingAge` + `lastTrained` + `isCompound`; no constant needed after enum collapse (D8 Eligibility) |
| `core/domain/.../onboarding/TrainingProfileEnums.kt` | `LastTrainedBucket` collapsed 5→3: `Never` / `WithinLastThreeMonths` / `MoreThanThreeMonthsAgo` (D8 Eligibility — v1 reinstall) |
| `feature/onboarding/.../ProfileDraftMapping.kt` | Update `lastTrainedLabel(...)` display strings for the 3 new buckets (lines 83-87) |
| `feature/onboarding/.../ui/*` | Update onboarding screen copy / selector to 3 options |
| `core/domain/.../program/generation/schema/ProgramNormalizer.kt` | Pass `lastTrained` + `isCompound` to `schemeForTrainingAge` |
| `core/domain/.../program/generation/build/WorkoutAssembler.kt` | Same — pass new args through |
| `core/domain/.../workout/GenerateProgressionSnapshotUseCase.kt` | Dispatch `LinearProgressionEngine` for `Linear` lifts alongside `DoubleProgressionEngine`; on `SchemeTransitionEvent` mutate scheme + append `ProgramChangeEvent` |
| `core/testing/.../workout/FakeProgressionHistoryRepository.kt` | Seed multi-session history sequences to drive stall/reset/transition cases |

**Checkpoint**

- `./gradlew :core:domain:test` —
  - `LinearProgressionEngineTest`: (a) increment proceeds when bottom-of-range met at RIR; (b) increment held when RIR not met; (c) 2 consecutive stalls → 10% reset; (d) reset counter resets after a reset; (e) 2 resets → `shouldTransitionToDouble` and `SchemeTransitionEvent` emitted with `ResetCyclesExhausted`; (f) missed sessions do not credit a stall; (g) per-lift increment override applied; (h) `StallStrictness.AbsoluteFailure` vs `RirBased` divergence.
  - `SchemeForTrainingAgeTest` (modified): Beginner → Linear; non-Beginner `WithinLastThreeMonths` → Double; non-Beginner `MoreThanThreeMonthsAgo` → Linear; isolation lift (`isCompound == false`) → Double regardless of user state.
  - `LastTrainedBucketTest` / onboarding tests: 3-value enum round-trips through `EnumConverters`; volume-planner string-keyed profile overrides updated to new names.
  - Snapshot test: a `Linear` lift and a `Double` lift in the same workout dispatch to the correct engine; on `SchemeTransitionEvent` the lift's scheme flips and a `ProgramChangeEvent` is appended.

---

### Slice 5 — Myo-reps Type A + Type B

**Components touched**

- New: Myo-rep kind type, Myo-rep lifecycle (generate mini-set on demand), only-set/final-set constraint validation.

**New types / signatures**

```kotlin
// :core:domain — program/ProgramEnums.kt  (EXTEND — sub-type, NOT persisted on SetType per D6)
// Myo-rep sub-type lives in the structure/overlay model, not the bare SetType enum.
enum class MyoRepKind {
    RepFloor,     // Type A: mini-sets until one hits the rep floor
    RepMatching,  // Type B: mini-sets until cumulative reps equal activation reps
}

// :core/domain — workout/myorep/MyoRepLifecycle.kt
interface MyoRepLifecycle {
    // Type A: returns the next mini-set slot when the previous mini-set did NOT
    // hit the rep floor; returns null (block complete) when the floor was hit.
    fun nextMiniSetRepFloor(state: MyoRepBlockState): SetSlot?

    // Type B: returns the next mini-set slot while cumulative mini-set reps <
    // activation reps; returns null at parity.
    fun nextMiniSetRepMatching(state: MyoRepBlockState): SetSlot?
}
data class MyoRepBlockState(
    val workoutLiftId: WorkoutLiftId,
    val activationSlotId: SetSlotId,
    val kind: MyoRepKind,
    val activationReps: Int,           // from the activation AMRAP
    val repFloor: Int,                 // Type A threshold
    val cumulativeMiniSetReps: Int,    // Type B running total
    val lastMiniSetReps: Int?,         // Type A last result
    val generatedSlots: List<SetSlot>,
)

// :core/domain — workout/myorep/MyoRepConstraintValidator.kt
class MyoRepConstraintValidator @Inject constructor() {
    // D6: a Myo-rep block must be the ONLY set or the FINAL set of its lift.
    // Returns failure reasons (authoring-time + snapshot-time validation).
    operator fun invoke(lift: WorkoutLiftProjection): MyoRepConstraintResult
}
sealed interface MyoRepConstraintResult {
    data object Valid : MyoRepConstraintResult
    data class Invalid(val reasons: Set<MyoRepConstraintViolation>) : MyoRepConstraintResult
}
enum class MyoRepConstraintViolation { MyoRepBlockNotOnlyOrFinalSet }
```

**File map (Create)**

| File | Responsibility |
|------|----------------|
| `core/domain/.../workout/myorep/MyoRepKind.kt` | `MyoRepKind` enum (Type A / Type B) — D6 |
| `core/domain/.../workout/myorep/MyoRepLifecycle.kt` | Mini-set generation for both kinds |
| `core/domain/.../workout/myorep/MyoRepConstraintValidator.kt` | Only-set/final-set constraint (D6) |

**File map (Modify)**

| File | Change |
|------|--------|
| `core/domain/.../workout/SetSlot.kt` | Carry `myoRepKind: MyoRepKind?` on slots (runtime/authoring concept; not persisted on `SetType`) |
| `core/domain/.../workout/WorkoutOverlay.kt` | `StructuralMutation.GenerateMyoRepMiniSet` added to the sealed interface |
| `core/domain/.../workout/GenerateProgressionSnapshotUseCase.kt` | Validate Myo-rep constraint at snapshot time; reject invalid |

**Checkpoint**

- `./gradlew :core:domain:test` — `MyoRepRepFloorLifecycleTest` (generate until floor hit; block ends), `MyoRepRepMatchingLifecycleTest` (generate until cumulative parity), `MyoRepConstraintValidatorTest` (only-set valid; final-set valid; mid-lift block invalid).
- Overlay test: generated mini-set is deletable/undoable (reuses Slice 3 undo).

---

### Slice 6 — Drop sets

**Components touched**

- New: drop-set constraint validator, drop-set weight-reduction estimation via fatigue model.

**New types / signatures**

```kotlin
// :core/domain — workout/dropset/DropSetConstraintValidator.kt
class DropSetConstraintValidator @Inject constructor() {
    // D7: a drop set MUST immediately follow another set of the same lift.
    operator fun invoke(lift: WorkoutLiftProjection): DropSetConstraintResult
}
sealed interface DropSetConstraintResult {
    data object Valid : DropSetConstraintResult
    data class Invalid(val reasons: Set<DropSetConstraintViolation>) : DropSetConstraintResult
}
enum class DropSetConstraintViolation { DropSetNotFollowingAnotherSet }

// :core/domain — workout/dropset/EstimateDropSetWeightUseCase.kt
class EstimateDropSetWeightUseCase @Inject constructor(
    private val fatigueModel: FatigueModel,
) {
    // D7: preceding set's weight reduced by the fatigue-model projection
    // (population-gradient default fallback per Q2 when no per-lift value).
    operator fun invoke(
        precedingWeight: Weight,
        nextSetIndex: Int,
        perLiftSlope: Double?,
        perLiftIntercept: Double?,
    ): Weight
}
```

**File map (Create)**

| File | Responsibility |
|------|----------------|
| `core/domain/.../workout/dropset/DropSetConstraintValidator.kt` | Must-follow constraint (D7) |
| `core/domain/.../workout/dropset/EstimateDropSetWeightUseCase.kt` | Fatigue-estimated weight reduction (D7) |

**File map (Modify)**

| File | Change |
|------|--------|
| `core/domain/.../workout/WorkoutOverlay.kt` | `StructuralMutation.AddDropSet` added |
| `core/domain/.../workout/GenerateProgressionSnapshotUseCase.kt` | Validate drop-set constraint at snapshot time |
| `core/testing/.../workout/FakeFatigueModel.kt` | Extend to drive drop-set reduction cases |

**Checkpoint**

- `./gradlew :core:domain:test` — `DropSetConstraintValidatorTest` (follows another set → valid; first set of lift → invalid), `EstimateDropSetWeightUseCaseTest` (reduction applied; per-lift slope present vs population default).
- Overlay test: drop set renders with fatigue-reduced weight; undo removes it.

---

### Slice 7 — `:feature:workout` presentation skeleton

**Components touched**

- New Gradle module `:feature:workout`; ViewModel owning overlay state; presentation model; `WorkoutRoute` composable skeleton; navigation wiring in `:app`.

**New types / signatures**

```kotlin
// :feature:workout — WorkoutViewModel.kt
@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val observeCycleProgress: ObserveCycleProgressUseCase,
    private val generateSnapshot: GenerateProgressionSnapshotUseCase,
    private val mergeRenderedWorkout: MergeRenderedWorkoutUseCase,
    private val projectStructure: ProjectWorkoutStructureUseCase,
    private val recommendationEngine: RecommendationEngine,
    private val estimateAddedSetReps: EstimateAddedSetRepsUseCase,
    private val logSetUseCase: LogSetUseCase,
    private val fatigueModel: FatigueModel,
) : ViewModel() {
    val uiState: StateFlow<WorkoutUiState>
    val events: Flow<WorkoutUiEvent>  // one-shot via Channel<Event>(BUFFERED).receiveAsFlow()

    fun startSession(workoutId: WorkoutId)
    fun logSet(slotId: SetSlotId, weight: Weight, reps: Int, rir: Int?)  // → LogSetUseCase
    fun addSet(afterSlotId: SetSlotId)
    fun deleteSet(slotId: SetSlotId)
    fun undo()
    fun generateMyoRepMiniSet(activationSlotId: SetSlotId)
    fun addDropSet(afterSlotId: SetSlotId)
    fun swapLift(workoutLiftId: WorkoutLiftId, newLiftId: LiftId)  // triggers snapshot recompute (D3)
    fun finalizeSession()
    fun abandonSession()
}

// :feature:workout — ui/WorkoutUiState.kt
sealed interface WorkoutUiState {
    data object Loading : WorkoutUiState
    data object NoActiveSession : WorkoutUiState
    data class Loaded(
        val workoutName: String,
        val lifts: List<RenderedLiftUi>,
        val isFinalizing: Boolean,
    ) : WorkoutUiState
    data class Error(val message: String) : WorkoutUiState
}
data class RenderedLiftUi(
    val liftName: String,
    val progressionSchemeLabel: String,
    val sets: List<RenderedSetUi>,
)
data class RenderedSetUi(
    val slotKey: String,              // SetSlotId mapped to a stable String for Compose
    val positionLabel: String,
    val setTypeLabel: String,
    val repRangeLabel: String?,       // top-set only per ticket (full display rule is EditWorkoutScreen, D11)
    val rirLabel: String,
    val recommendedWeightLabel: String,
    val recommendedRepsLabel: String,
    val loggedWeightLabel: String?,
    val loggedRepsLabel: String?,
    val isComplete: Boolean,
)
sealed interface WorkoutUiEvent {
    data class ShowMessage(val message: String) : WorkoutUiEvent
    // Note: no PromptSchemeTransition event — D8 locks auto-transition with no confirm.
    // SchemeTransitionEvent is consumed by the snapshot/scheme store, not surfaced as a UI prompt.
    data class NavigateToFinalize(val sessionId: String) : WorkoutUiEvent
}

// :feature:workout — ui/WorkoutRoute.kt
@Composable
fun WorkoutRoute(
    onNavigateBack: () -> Unit,
    onNavigateToFinalize: (sessionId: String) -> Unit,
    viewModel: WorkoutViewModel = hiltViewModel(),
)
```

**File map (Create)**

| File | Responsibility |
|------|----------------|
| `feature/workout/build.gradle.kts` | New module — depends on `:core:domain`, `:core:ui` (not `:core:data` directly per ARCHITECTURE.md §4.4) |
| `feature/workout/.../WorkoutViewModel.kt` | Owns overlay (`MutableStateFlow<WorkoutOverlay>`); maps rendered workout → presentation model |
| `feature/workout/.../ui/WorkoutUiState.kt` | Presentation model (`WorkoutUiState`, `RenderedLiftUi`, `RenderedSetUi`) — no domain types embedded (Detekt `ForbiddenDomainTypeInUiState`) |
| `feature/workout/.../ui/WorkoutRoute.kt` | `@Composable` route; consumes `WorkoutUiState`; navigation via callbacks (no `NavController` in feature) |
| `feature/workout/.../ui/WorkoutUiEvent.kt` | One-shot events |
| `feature/workout/.../ui/WorkoutUiMapper.kt` | Domain `RenderedWorkout` → `WorkoutUiState.Loaded` |

**File map (Modify)**

| File | Change |
|------|--------|
| `settings.gradle.kts` | `include(":feature:workout")` |
| `app/build.gradle.kts` | `implementation(project(":feature:workout"))` |
| `app/.../navigation/*` | Add `WorkoutRoute` to the `NavHost` with callback wiring |

**Checkpoint**

- `./gradlew :feature:workout:testDebugUnitTest` — `WorkoutViewModelTest` (JUnit 5 + Turbine + `MainDispatcherRule` + fakes): empty overlay renders snapshot baseline; logging a set updates the overlay + calls `LogSetUseCase`; add/delete/undo mutate overlay; recommendation supersedes snapshot for upcoming set; no `verify()` (Detekt enforced).
- `./gradlew detekt` — `ForbiddenDomainTypeInUiState` / `ForbiddenDomainTypeInComposable` pass.

---

### Slice 8 — EditWorkoutScreen per-lift progression-scheme toggle

**Components touched**

- Modify: `EditWorkoutViewModel` exposes per-lift scheme state + a toggle command; `EditWorkoutScreen` renders the toggle on compound rows only; new bottom-sheet explainer content. Reads/writes `WorkoutLift.progressionScheme` (already round-tripped by `EditWorkoutDomainMapper:31,36`); appends a `ProgramChangeEvent` on change.

**New types / signatures**

```kotlin
// :feature:program — EditWorkoutViewModel.kt  (MODIFY)
// Per-lift scheme toggle surfaced only when lift.isCompound == true (D8/D11).
fun setProgressionScheme(workoutLiftId: WorkoutLiftId, scheme: ProgressionScheme)
// → updates EditWorkoutState for that lift, marks the program dirty, and on
//   save appends a ProgramChangeEvent({type = SchemeChanged, ...}) via the
//   existing AppendProgramChangeEventUseCase.

// :feature:program — ui/EditWorkoutScreen.kt  (MODIFY)
// On each WorkoutLift row: if lift.isCompound, render a Linear/Double toggle
// (e.g. StratumSegmentedButton or equivalent Stratum* component per UI_DESIGN);
// if !isCompound, render nothing (Linear is not offered — D8 Rejected e/f).
// An info affordance on the section header opens ProgressionSchemeExplainerSheet.

// :feature:program — ui/ProgressionSchemeExplainerSheet.kt  (NEW)
@Composable
fun ProgressionSchemeExplainerSheet(onDismiss: () -> Unit)
// StratumBottomSheet content explaining: what each scheme does, who is eligible
// (compounds only; beginner-gains or returning from layoff → Linear), and the
// auto-graduation rule (after 2 reset cycles Stratum moves the lift to Double).

// :core:domain — program/ProgramChangeEvent.kt  (EXTEND if needed)
// Reuse the existing ProgramChangeEvent type from workout-logging; if a
// SchemeChanged variant is not already in the enum, add it. Append-only.
```

**File map (Create)**

| File | Responsibility |
|------|----------------|
| `feature/program/.../ui/ProgressionSchemeExplainerSheet.kt` | Bottom-sheet content explaining both schemes + eligibility + auto-graduation |

**File map (Modify)**

| File | Change |
|------|--------|
| `feature/program/.../EditWorkoutViewModel.kt` | `setProgressionScheme(workoutLiftId, scheme)`; gate UI exposure on `isCompound` |
| `feature/program/.../ui/EditWorkoutScreen.kt` | Render toggle on compound rows only; info affordance → explainer sheet |
| `feature/program/.../ui/EditWorkoutUiState.kt` | Add `isCompound` + current `progressionScheme` per lift row |
| `core/domain/.../program/ProgramChangeEvent.kt` | If needed, add `SchemeChanged` variant (append-only; from workout-logging) |

**Checkpoint**

- `./gradlew :feature:program:testDebugUnitTest` — `EditWorkoutViewModelTest`: (a) toggling a compound lift's scheme updates state + marks program dirty; (b) on save, a `ProgramChangeEvent` with `SchemeChanged` is appended; (c) `setProgressionScheme` for an isolation lift is a no-op (defensive — UI hides the toggle).
- `./gradlew detekt` — toggle uses `Stratum*` components only (UI_DESIGN); no raw Material chrome.
- Out of scope for this slice (separate slice per D11): Myo-rep/drop-set authoring UI, "rep range on top set only" display change, structural constraint authoring.

---

## Public contracts summary

### Three-layer types (D1)

| Type | Layer | Module | Notes |
|------|-------|--------|-------|
| `SetSlot`, `SetSlotId`, `OverlaySetInstanceId` | Structure projection | `:core:domain` | Runtime identity; template slots key on `LiftSetId`, dynamic on `OverlaySetInstanceId` |
| `WorkoutStructureProjection`, `WorkoutLiftProjection` | Structure projection | `:core:domain` | Built by `ProjectWorkoutStructureUseCase`; folds overlay structural mutations |
| `ProgressionSnapshot`, `SlotRecommendation` | Snapshot value | `:core:domain` | Immutable for session life (D3) |
| `WorkoutOverlay`, `LoggedSetValue`, `StructuralMutation` | Overlay value | `:core:domain` (type) / `:feature:workout` (instance) | Only mutable layer; in-memory only (D9) |
| `RenderedWorkout`, `RenderedWorkoutLift`, `RenderedSet` | Merge result | `:core:domain` | Pure output of `MergeRenderedWorkoutUseCase` |

### Snapshot generation + historical-priority (D3, research Gap 4)

| Contract | Signature |
|----------|-----------|
| `GenerateProgressionSnapshotUseCase.invoke(request: SnapshotRequest): Result<ProgressionSnapshot>` | Compute-once snapshot; recompute on exercise swap only |
| `ProgressionHistoryPort.historyForSlot(request: SlotHistoryRequest): List<SlotHistoryEntry>` | Historical-priority read: n−1, n−2, … then same-lift-other-workout |
| `ProgressionHistoryRepository` (`:core:data`) | Implements the port over `TrainingLogRepository` DAOs with a new indexed `(programId, workoutId, workoutLiftId, setIndex)` join |

### Progression engine API (double + linear, D8)

| Contract | Signature |
|----------|-----------|
| `ProgressionEngine.recommend(request: SlotProgressionRequest): SlotRecommendation` + `scheme: ProgressionScheme` | Dispatch interface |
| `DoubleProgressionEngine.recommend(...)` | Increment when TOP of range met at RIR; backoff inherits top-set weight |
| `LinearProgressionEngine.recommend(...)` + `diagnose(...)` | Greyskull-style hybrid; increment when BOTTOM met at RIR |
| `LinearProgressionConfig` | Q1a tuning knobs (transition is locked, not a knob): `lowerBodyIncrement`, `upperBodyIncrement`, `minimumIncrement`, `perLiftIncrementOverride`, `stallThresholdSessions` (2), `resetFraction` (0.10), `perLiftResetFractionOverride`, `resetsBeforeTransition` (2), `stallStrictness` (`RirBased`/`AbsoluteFailure`). **`transitionMode` removed** — D8 locks auto-transition, no confirm. |
| `SchemeTransitionEvent` + `SchemeTransitionReason.ResetCyclesExhausted` | Emitted after 2 resets; **always applied automatically** to flip the lift's `progressionScheme` to `Double` and append a `ProgramChangeEvent` |
| `schemeForTrainingAge(trainingAge, lastTrained, isCompound)` (`ProgramRules.kt`) | MODIFIED: consults `lastTrained` + `isCompound`. Returns `Linear` iff `isCompound && (trainingAge == Beginner OR lastTrained == MoreThanThreeMonthsAgo)`. Else `Double`. |
| `LastTrainedBucket` (`TrainingProfileEnums.kt`) | MODIFIED — collapsed 5→3: `Never` / `WithinLastThreeMonths` / `MoreThanThreeMonthsAgo`. Schema-adjacent (v1 reinstall). |

### Recommendation engine API (D4)

| Contract | Signature |
|----------|-----------|
| `RecommendationEngine.recommendNextSet(lastPerformed: LoggedSetValue, targetSlot: SetSlot, currentWeight: Weight): RecommendationOutcome?` | Overlay-only hold-vs-reduce; null when no performed set. Top set targets `repMin`; backoff set targets `BACKOFF_REP_FLOOR` |
| `RecommendationOutcome` (sealed) | `Hold(weight)` / `Reduce(weight)` |
| `BACKOFF_REP_FLOOR: Int = 5` (`RecommendationContracts.kt`) | "Meaningful backoff set" floor (D4); tunable |
| `EstimatedOneRm.weightForReps(estimatedOneRm: Double, targetReps: Int): Double` | NEW inverse (Brzycki/Epley inversion); `calculate(...)` unchanged |
| `EstimatedOneRm.repsForWeight(estimatedOneRm: Double, weight: Double): Int` | NEW second inverse; used to project achievable reps at `currentWeight` before RIR subtraction |

### Fatigue-model contract (D5/D7, Q2 resolution)

| Contract | Signature |
|----------|-----------|
| `FatigueModel.expectedRepsForAddedSet(baselineOneRm, targetWeight, nextSetIndex, perLiftSlope?, perLiftIntercept?): Int` | Per-lift source from `LiftTrainingInsights`; null → population default |
| `POPULATION_FATIGUE_DECAY_SLOPE` / `POPULATION_FATIGUE_DECAY_INTERCEPT` (`ResponseConstants.kt`) | Population-gradient default constants (Q2) |
| `EstimateAddedSetRepsUseCase.invoke(...)` | Add-set rep estimation (D5) |
| `EstimateDropSetWeightUseCase.invoke(...)` | Drop-set weight reduction (D7) |

### Myo-rep kind (D6) + drop-set constraint (D7)

| Contract | Signature |
|----------|-----------|
| `MyoRepKind { RepFloor, RepMatching }` | Type A / Type B sub-type (NOT persisted on `SetType`) |
| `MyoRepLifecycle.nextMiniSetRepFloor(state)` / `.nextMiniSetRepMatching(state)` | Adaptive end-condition mini-set generation |
| `MyoRepConstraintValidator.invoke(lift): MyoRepConstraintResult` | Only-set / final-set enforcement |
| `DropSetConstraintValidator.invoke(lift): DropSetConstraintResult` | Must-follow enforcement |
| `DropSetConstraintViolation.DropSetNotFollowingAnotherSet` | Failure reason |

### `:feature:workout` presentation skeleton (slices 7)

| Contract | Signature |
|----------|-----------|
| `WorkoutViewModel` (Hilt) | Owns overlay; consumes use cases only (no repository ports directly — ARCHITECTURE.md §4.4) |
| `WorkoutUiState` sealed | `Loading` / `NoActiveSession` / `Loaded` / `Error` |
| `RenderedLiftUi`, `RenderedSetUi` | Presentation model; no embedded domain types |
| `WorkoutUiEvent` sealed | One-shot events incl. `PromptSchemeTransition` |
| `WorkoutRoute(onNavigateBack, onNavigateToFinalize, viewModel)` | Compose route; callbacks, no `NavController` |

---

## Test checkpoints (cumulative)

| After slice | Runnable tests |
|-------------|----------------|
| 1 | `GenerateProgressionSnapshotUseCaseTest` (Standard top set), `MergeRenderedWorkoutUseCaseTest` (empty overlay), `ProjectWorkoutStructureUseCaseTest`, `ProgressionHistoryRepositoryTest` (priority ordering) |
| 2 | + `DoubleProgressionEngineTest`, `RecommendationEngineTest` (top-set `repMin` + backoff `BACKOFF_REP_FLOOR`; Hold/Reduce), `EstimatedOneRmWeightForRepsTest`, `EstimatedOneRmRepsForWeightTest`, merge tests for logged/recommendation priority |
| 3 | + `EstimateAddedSetRepsUseCaseTest` (per-lift vs population), `WorkoutOverlayMutationTest` (add/delete/undo, snapshot-untouched invariant) |
| 4 | + `LinearProgressionEngineTest` (increment gating, 2-stall reset, 10% reset, 2-resets → auto-transition, missed sessions, per-lift override, strictness), `SchemeForTrainingAgeTest` (Beginner / layoff / compounds-only) |
| 5 | + `MyoRepRepFloorLifecycleTest`, `MyoRepRepMatchingLifecycleTest`, `MyoRepConstraintValidatorTest` |
| 6 | + `DropSetConstraintValidatorTest`, `EstimateDropSetWeightUseCaseTest` |
| 7 | + `WorkoutViewModelTest` (Turbine + fakes; overlay rendering; no `verify`) |
| 8 | + `EditWorkoutViewModelTest` (compound toggle updates state + appends `ProgramChangeEvent`; isolation lift toggle is no-op) |

All domain tests run via `./gradlew :core:domain:test`; data-layer via
`./gradlew :core:data:testDebugUnitTest`; feature via
`./gradlew :feature:workout:testDebugUnitTest` and
`./gradlew :feature:program:testDebugUnitTest`. `./gradlew detekt`
(`maxIssues: 0`) gates every slice.

---

## Dependencies between slices

```text
Slice 1 (snapshot + merge skeleton)
   │  ── establishes SetSlot / ProgressionSnapshot / WorkoutOverlay /
   │     RenderedWorkout / ProgressionHistoryPort / merge use case
   ▼
Slice 2 (double + recommendation)
   │  ── depends on Slice 1 snapshot/merge; adds engines + inverse e1RM
   ▼
Slice 3 (dynamic lifecycle + fatigue model)
   │  ── depends on Slice 1 overlay + Slice 2 recommendation priority;
   │     adds StructuralMutation API + FatigueModel + population default
   ├─ Slice 4 (linear progression) depends on Slice 2 engine dispatch
   │     (independent of Slice 3, but same snapshot-generation wiring)
   │     AND modifies schemeForTrainingAge in :core:domain
   ├─ Slice 5 (Myo-reps) depends on Slice 3 undo/mutation API
   └─ Slice 6 (drop sets) depends on Slice 3 FatigueModel
       │
       ▼
Slice 7 (feature module) depends on ALL prior slices' domain contracts
   ── the ViewModel composes every use case; presentation model maps the
      fully-featured RenderedWorkout
   │
   ▼
Slice 8 (EditWorkoutScreen scheme toggle) depends on Slice 4's
   schemeForTrainingAge changes + existing EditWorkoutScreen surface.
   Independent of Slice 7 — touches :feature:program, not :feature:workout.
```

Hard ordering: **1 → 2 → 3**. Slices **4, 5, 6** may proceed in any order
after 3 (they are independent domain extensions), but all must precede
**7** (the presentation skeleton consumes the complete rendered-workout
shape). **Slice 8** depends on Slice 4 (for the `schemeForTrainingAge`
eligibility changes) but is otherwise independent of Slice 7 — it touches
`:feature:program`, not `:feature:workout`, so it can run in parallel with
7 once 4 lands. Recommended order for the Plan phase: 4 → 5 → 6 → 7 → 8
(linear first since it shares the engine-dispatch seam with Slice 2 and
unblocks Slice 8; Myo-reps and drop sets then layer onto the mutation API
from Slice 3).

---

## Self-review

- [x] Every slice is vertically integrated — each has a testable end-to-end checkpoint (use case / engine / merge / ViewModel), not a horizontal-layer-only phase.
- [x] Checkpoints exist after EACH slice (cumulative test table), not only at the end.
- [x] File map entries align with D2 module placement: domain types in `:core:domain`, overlay instance in `:feature:workout`, history-read impl in `:core:data`, fakes in `:core:testing`.
- [x] No horizontal-layer-only phase remains (each slice touches types + use case + test; Slice 7 adds presentation on top of the full domain surface; Slice 8 adds the EditWorkoutScreen scheme toggle that consumes the D8 eligibility work end-to-end).
- [x] No new Room tables/columns for transient overlay state (D9) — overlay is in-memory ViewModel state; persistence boundary is the existing `LogSetUseCase`. The only new `:core:data` work is a read query (Slice 1, research Gap 4), not a schema change.
- [x] Domain purity preserved: no Compose/Android in `:core:domain`; the ViewModel maps `RenderedWorkout` → presentation model; no repository port injected into the ViewModel (ARCHITECTURE.md §4.4 — use cases only).
- [x] D1–D11 honored: three-layer separation (D1), module placement (D2), compute-once/recompute-on-swap (D3), overlay-only recommendations (D4), dynamic lifecycle + undo (D5), Myo-rep Type A/B + constraint (D6), drop-set must-follow + fatigue reduction (D7), Greyskull-style linear (D8), no transient persistence (D9), rep-range guard out of scope (D10), EditWorkoutScreen out of scope (D11).
- [x] Q1a tuning parameters surfaced as explicit parameters on `LinearProgressionConfig` (not hardcoded): increment schedule, stall threshold, reset percentage, transition threshold, microplate/minimum increment, automatic-vs-confirmed transition, RIR-vs-absolute stall strictness, per-lift increment override.
- [x] Q2 population-gradient default constant placed in `ResponseConstants.kt`, per-lift override honored.
- [x] No placeholders (`TBD`, `TODO`, "implement later").
- [x] Signatures explicit; algorithmic detail omitted.
