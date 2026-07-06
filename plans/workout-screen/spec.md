# Spec: workout-screen

Tactical implementation plan for Stratum's live workout runtime architecture.
Translates the human-approved `design.md` (2026-07-05, including post-approval
corrections to D4, D8, D11) and `structure.md` (2026-07-05) into ordered
implementation tasks and the file map. Behavioral scenarios live in
`test_spec.md`; each task references the scenario IDs it satisfies.

All signatures here are contracts only — no implementation bodies. Existing
file paths and signatures were verified against the codebase 2026-07-05.

---

## Feature summary

A three-layer runtime model feeds `WorkoutScreen` rendering:

```
Structure projection + Progression snapshot + Workout overlay → Rendered workout
```

- **Structure projection** — runtime view of `WorkoutLift`/`LiftSet` ordering plus
  dynamic adds/deletes/generations; carries set type, rep range, RIR, top-set
  marking; **no load**. Lives in `:core:domain` as a read model.
- **Snapshot** — `SetSlot → recommended weight + baseline expected reps`, computed
  **once** per session from history. Immutable for the session life; recomputes
  only on exercise swap (D3). Lives in `:core:domain`.
- **Overlay** — logged values, live recommendations, dynamic set entries,
  completion state, undo stack. The only mutable layer; owned by
  `WorkoutViewModel` as in-memory session state (D9 — never persisted to Room
  except via existing `LogSetUseCase` on finalize).

A snapshot-generation use case computes the snapshot from history through the
historical-priority rules (same lift / same workout / same position / previous
session first, then earlier sessions, then same-lift-other-workout). A pure
merge use case combines the three layers into a `RenderedWorkout`. A pure
recommendation engine derives per-set e1RM from performed sets and decides
hold-vs-reduce for the next upcoming set — overlay-only, never writes back to
the snapshot (D4). A Greyskull-style linear-progression engine handles
`Linear` lifts (compounds only, beginner-or-layoff users) with RIR-gated
increment, 2-stall reset, 10% reset drop, and auto-transition to Double after
2 resets (D8). Myo-rep Type A (rep floor) and Type B (rep matching) and drop
sets are first-class structural shapes with authoring/snapshot-time
constraints (D6, D7). A new `:feature:workout` module hosts the ViewModel and
Compose route (skeleton). EditWorkoutScreen gains a per-lift scheme toggle on
compound rows only, appending a `ProgramChangeEvent` on save (D11).

---

## Vertical slice table

From `structure.md`. Hard ordering: **1 → 2 → 3 → {4, 5, 6} → 7 → 8**.
Slices 4, 5, 6 are independent domain extensions after 3; recommended order
4 → 5 → 6. Slice 8 depends on Slice 4's `schemeForTrainingAge` changes but is
independent of Slice 7 (touches `:feature:program`, not `:feature:workout`).

| # | Slice | Goal (one line) | Satisfies |
|---|-------|-----------------|-----------|
| 1 | Snapshot + merge skeleton | Compute-once initial progression snapshot for a single Standard top set from the historical-priority read, merged with an empty overlay into a rendered-workout read model behind a use case. No overlay mutation yet. | D1, D2, D3 |
| 2 | Double progression + live recommendation | Double-progression snapshot generation for full lifts from history, plus the overlay-only recommendation engine (`weightForReps` / `repsForWeight` inverses; top-set `repMin` vs backoff `BACKOFF_REP_FLOOR` target) for the next upcoming set. | D4 |
| 3 | Dynamic set lifecycle (Standard) | Add / delete / undo for Standard sets with fatigue-model rep estimation (population-gradient default + per-lift override). | D5, D9 |
| 4 | Linear progression (Greyskull-style hybrid) | Per-session increment gating, 2-stall reset, 10% reset, auto-transition to Double after 2 resets. Eligibility: compounds-only for beginner-or-layoff users. Collapses `LastTrainedBucket` 5→3. | D8 |
| 5 | Myo-reps Type A + Type B | Rep-floor and rep-matching lifecycles, generated mini-sets, only-set/final-set constraint validation. | D6 |
| 6 | Drop sets | Must-follow constraint validation + fatigue-estimated weight reduction from the preceding set. | D7 |
| 7 | `:feature:workout` presentation skeleton | ViewModel owning overlay state + presentation model; Compose `WorkoutRoute` consuming the merged rendered workout (skeleton). | D1, D2, D9 |
| 8 | EditWorkoutScreen per-lift scheme toggle | `Linear`/`Double` affordance on compound `WorkoutLift` rows only; hidden for isolation lifts; explainer bottom sheet; toggle writes `progressionScheme` + appends a `ProgramChangeEvent` with `SchemeChanged`. | D11 |

D10 (program-generation rep-range × set-count guard) is out of scope — guard
belongs in `program-generation-v2`. EditWorkoutScreen Myo-rep/drop-set
authoring and the "rep range on top set only" display change remain a separate
slice (D11).

---

## File structure table

Every file that will be created or modified. `Create` = new file; `Modify` =
existing file edited. Paths are relative to repo root. Existing-file paths
were verified against the codebase 2026-07-05.

### Slice 1 — Snapshot + merge skeleton

| Action | File | Responsibility |
|--------|------|----------------|
| Create | `core/domain/src/main/kotlin/com/stratum/domain/workout/SetSlot.kt` | `SetSlot`, `SetSlotId` (sealed: `Template(LiftSetId)` / `Overlay(OverlaySetInstanceId)`), `@JvmInline value class OverlaySetInstanceId(val value: String)` — structure identity stable across snapshot/overlay merge |
| Create | `core/domain/src/main/kotlin/com/stratum/domain/workout/ProgressionSnapshot.kt` | `SlotRecommendation(slotId, recommendedWeight: Weight, baselineExpectedReps: Int)`, `ProgressionSnapshot(workoutId, bySlot: Map<SetSlotId, SlotRecommendation>)` — immutable for session life (D3) |
| Create | `core/domain/src/main/kotlin/com/stratum/domain/workout/WorkoutOverlay.kt` | `LoggedSetValue(slotId, weight: Weight, reps: Int, rir: Int?)`, `WorkoutOverlay(loggedSets: Map<SetSlotId, LoggedSetValue> = emptyMap())` — Slice 1 shape only (no mutation API yet) |
| Create | `core/domain/src/main/kotlin/com/stratum/domain/workout/RenderedWorkout.kt` | Merge-result read model: `RenderedSet`, `RenderedWorkoutLift`, `RenderedWorkout` |
| Create | `core/domain/src/main/kotlin/com/stratum/domain/workout/WorkoutStructureProjection.kt` | `WorkoutStructureProjection(workoutId, workoutName, lifts: List<WorkoutLiftProjection>)`, `WorkoutLiftProjection(workoutLiftId, liftId, liftName, progressionScheme, slots: List<SetSlot>)` |
| Create | `core/domain/src/main/kotlin/com/stratum/domain/workout/ProgressionHistoryPort.kt` | `ProgressionHistoryPort.historyForSlot(request): List<SlotHistoryEntry>` + `SlotHistoryRequest(programId, workoutId, workoutLiftId, liftId, setIndex)` + `SlotHistoryEntry(session: TrainingSession, loggedSet: LoggedSet)` (research Gap 4) |
| Create | `core/domain/src/main/kotlin/com/stratum/domain/workout/GenerateProgressionSnapshotUseCase.kt` | `GenerateProgressionSnapshotUseCase(progressionHistoryPort).invoke(request: SnapshotRequest): Result<ProgressionSnapshot>` — compute-once snapshot; Slice 1 uses a single Standard top-set placeholder computation |
| Create | `core/domain/src/main/kotlin/com/stratum/domain/workout/MergeRenderedWorkoutUseCase.kt` | `MergeRenderedWorkoutUseCase().invoke(structure, snapshot, overlay): RenderedWorkout` — pure merge |
| Create | `core/domain/src/main/kotlin/com/stratum/domain/workout/ProjectWorkoutStructureUseCase.kt` | `ProjectWorkoutStructureUseCase().invoke(workout: Workout, liftNames: Map<LiftId, String>): WorkoutStructureProjection` — pure projection from template (no load) |
| Create | `core/data/src/main/kotlin/com/stratum/core/data/repository/ProgressionHistoryRepository.kt` | Implements `ProgressionHistoryPort` over `TrainingLogRepository` DAOs with a new indexed `(programId, workoutId, workoutLiftId, setIndex)` join (research Gap 4) |
| Create | `core/testing/src/main/kotlin/com/stratum/core/testing/workout/FakeProgressionHistoryRepository.kt` | In-memory fake (UNIT_TESTING.md §6) with seed + ordering |
| Modify | `core/data/src/main/kotlin/com/stratum/core/data/db/TrainingLogDaos.kt` | Add indexed query joining `(programId, workoutId, workoutLiftId, setIndex)` across sessions (research Gap 4) |

### Slice 2 — Double progression + live recommendation

| Action | File | Responsibility |
|--------|------|----------------|
| Create | `core/domain/src/main/kotlin/com/stratum/domain/workout/progression/ProgressionEngine.kt` | Dispatch interface `ProgressionEngine { fun recommend(request: SlotProgressionRequest): SlotRecommendation; val scheme: ProgressionScheme }` + `SlotProgressionRequest(slot, history, fallbackOneRm: Weight?)` |
| Create | `core/domain/src/main/kotlin/com/stratum/domain/workout/progression/DoubleProgressionEngine.kt` | Double-progression math (D8 primary): increment load when TOP of rep range met at RIR; hold otherwise; backoff inherits top-set weight. Pure. |
| Create | `core/domain/src/main/kotlin/com/stratum/domain/workout/RecommendationEngine.kt` | `RecommendationEngine().recommendNextSet(lastPerformed, targetSlot, currentWeight): RecommendationOutcome?` — overlay-only hold-vs-reduce (D4) |
| Create | `core/domain/src/main/kotlin/com/stratum/domain/workout/RecommendationContracts.kt` | `const val BACKOFF_REP_FLOOR: Int = 5` + sealed `RecommendationOutcome { Hold(weight); Reduce(weight) }` |
| Modify | `core/domain/src/main/kotlin/com/stratum/domain/importing/LiftNameResolver.kt` | Add `EstimatedOneRm.weightForReps(estimatedOneRm: Double, targetReps: Int): Double` and `EstimatedOneRm.repsForWeight(estimatedOneRm: Double, weight: Double): Int` inverses (Brzycki ≤10 reps / Epley else, matching `calculate`). Existing `calculate` unchanged. |
| Modify | `core/domain/src/main/kotlin/com/stratum/domain/workout/GenerateProgressionSnapshotUseCase.kt` | Wire `DoubleProgressionEngine` for `Double` lifts (Slice 1 placeholder replaced). Engine injected. |
| Modify | `core/domain/src/main/kotlin/com/stratum/domain/workout/WorkoutOverlay.kt` | Add `recommendations: Map<SetSlotId, Weight> = emptyMap()` |
| Modify | `core/domain/src/main/kotlin/com/stratum/domain/workout/MergeRenderedWorkoutUseCase.kt` | Overlay priority (b): recommendation supersedes snapshot for upcoming unlogged set |
| Modify | `core/testing/src/main/kotlin/com/stratum/core/testing/workout/FakeProgressionHistoryRepository.kt` | Extend seed to drive double-progression increment cases |

### Slice 3 — Dynamic set lifecycle (Standard)

| Action | File | Responsibility |
|--------|------|----------------|
| Create | `core/domain/src/main/kotlin/com/stratum/domain/workout/fatigue/FatigueModel.kt` | `FatigueModel.expectedRepsForAddedSet(baselineOneRm, targetWeight: Weight, nextSetIndex: Int, perLiftSlope: Double?, perLiftIntercept: Double?): Int` — per-lift source from `LiftTrainingInsights`; null → population default |
| Create | `core/domain/src/main/kotlin/com/stratum/domain/workout/fatigue/PopulationFatigueModel.kt` | Default impl using `POPULATION_FATIGUE_DECAY_SLOPE`/`INTERCEPT` constants |
| Create | `core/domain/src/main/kotlin/com/stratum/domain/workout/EstimateAddedSetRepsUseCase.kt` | `EstimateAddedSetRepsUseCase(fatigueModel).invoke(previousRecommendation, nextSetIndex, perLiftSlope?, perLiftIntercept?): Int` (D5) |
| Create | `core/testing/src/main/kotlin/com/stratum/core/testing/workout/FakeFatigueModel.kt` | In-memory fatigue fake |
| Modify | `core/domain/src/main/kotlin/com/stratum/domain/response/ResponseConstants.kt` | Add `POPULATION_FATIGUE_DECAY_SLOPE: Double` and `POPULATION_FATIGUE_DECAY_INTERCEPT: Double` (Q2 resolution) |
| Modify | `core/domain/src/main/kotlin/com/stratum/domain/workout/WorkoutOverlay.kt` | Add `StructuralMutation` sealed interface (`AddSet`, `DeleteSet`), `addedSlots`, `deletedSlotIds`, `undoStack`, `apply(mutation)`, `undo()` |
| Modify | `core/domain/src/main/kotlin/com/stratum/domain/workout/ProjectWorkoutStructureUseCase.kt` | Fold overlay structural mutations into the projection (adds appended, deletes removed) |
| Modify | `core/domain/src/main/kotlin/com/stratum/domain/workout/MergeRenderedWorkoutUseCase.kt` | Overlay priority (c): added-set estimate from fatigue model; undo reverts cleanly |

### Slice 4 — Linear progression (Greyskull-style hybrid)

| Action | File | Responsibility |
|--------|------|----------------|
| Create | `core/domain/src/main/kotlin/com/stratum/domain/workout/progression/LinearProgressionConfig.kt` | Q1a tuning knobs as explicit parameters: `lowerBodyIncrement`, `upperBodyIncrement`, `minimumIncrement`, `perLiftIncrementOverride: Map<LiftId, Weight>`, `stallThresholdSessions` (=2), `resetFraction` (=0.10), `perLiftResetFractionOverride`, `resetsBeforeTransition` (=2), `stallStrictness: StallStrictness`. No `transitionMode` field — auto-transition locked. |
| Create | `core/domain/src/main/kotlin/com/stratum/domain/workout/progression/LinearProgressionEngine.kt` | `LinearProgressionEngine(config) : ProgressionEngine` — Greyskull-style hybrid math (D8): increment when BOTTOM of rep range met at RIR; 2-stall reset; 10% reset; auto-transition after 2 resets. `diagnose(liftId, history): LinearProgressionState`. |
| Create | `core/domain/src/main/kotlin/com/stratum/domain/workout/progression/SchemeTransitionEvent.kt` | `SchemeTransitionEvent(workoutLiftId, liftId, from, to, reason: SchemeTransitionReason)` + `enum class SchemeTransitionReason { ResetCyclesExhausted }` + `LinearProgressionState(liftId, currentStallCount, currentResetCount, shouldReset, shouldTransitionToDouble)` |
| Modify | `core/domain/src/main/kotlin/com/stratum/domain/program/generation/reference/ProgramRules.kt` | `schemeForTrainingAge` signature changes to `fun schemeForTrainingAge(trainingAge: TrainingAge?, lastTrained: LastTrainedBucket?, isCompound: Boolean): ProgressionScheme`. Returns `Linear` iff `isCompound && (trainingAge == Beginner OR lastTrained == MoreThanThreeMonthsAgo)`; else `Double`. Generation-package enum stays; no constant needed after enum collapse. |
| Modify | `core/domain/src/main/kotlin/com/stratum/domain/onboarding/TrainingProfileEnums.kt` | Collapse `LastTrainedBucket` 5→3: `Never` / `WithinLastThreeMonths` / `MoreThanThreeMonthsAgo`. Schema-adjacent (v1 reinstall, AGENTS.md §4.5). |
| Modify | `feature/onboarding/src/main/kotlin/com/stratum/feature/onboarding/ProfileDraftMapping.kt` | Update `lastTrainedLabel(...)` display strings for the 3 new buckets (lines 83-87: `WithinLastMonth`→`WithinLastThreeMonths`, `OneToSixMonthsAgo`+`SixToTwelveMonthsAgo`+`MoreThanAYearAgo` collapsed → `MoreThanThreeMonthsAgo`) |
| Modify | `feature/onboarding/.../ui/*` (onboarding screen copy / selector) | Update onboarding screen copy / selector to 3 options |
| Modify | `core/domain/src/main/kotlin/com/stratum/domain/program/generation/schema/ProgramNormalizer.kt` | Pass `lastTrained` + `isCompound` (from lift catalog) to `schemeForTrainingAge` |
| Modify | `core/domain/src/main/kotlin/com/stratum/domain/program/generation/build/WorkoutAssembler.kt` | Same — pass new args through where `schemeForTrainingAge` is consulted |
| Modify | `core/domain/src/main/kotlin/com/stratum/domain/workout/GenerateProgressionSnapshotUseCase.kt` | Dispatch `LinearProgressionEngine` for `Linear` lifts; on `SchemeTransitionEvent` mutate scheme (in-memory) + append `ProgramChangeEvent` via `ProgramChangeEventRepository.append` |
| Modify | `core/testing/src/main/kotlin/com/stratum/core/testing/workout/FakeProgressionHistoryRepository.kt` | Seed multi-session history sequences to drive stall/reset/transition cases |
| Modify | `core/domain/src/test/kotlin/com/stratum/domain/program/generation/volume/VolumeTargetPlannerTest.kt` | Update string-keyed profile overrides referencing old bucket names to the 3 new names |
| Modify | `core/domain/src/test/kotlin/com/stratum/domain/program/generation/volume/VolumePlannerTest.kt` | Same |
| Modify | onboarding/data tests referencing old bucket names (see EnumConverters verification) | Update to the 3 new names. Affected files: `OnboardingReviewStepViewModelTest.kt`, `ReviewDraftMappingTest.kt`, `OnboardingProfileStepViewModelTest.kt`, `GetOnboardingStateUseCaseTest.kt`, `ValidateCurrentOnboardingStepUseCaseTest.kt`, `ValidateProfileStepUseCaseTest.kt`, `AdvanceOnboardingStepUseCaseTest.kt`, `ValidateScheduleStepUseCaseTest.kt`, `CompleteOnboardingUseCaseTest.kt`, `UserProfileMapperTest.kt`, `RoomUserTrainingProfileRepositoryTest.kt`, `UserProfileDaoTest.kt` |

### Slice 5 — Myo-reps Type A + Type B

| Action | File | Responsibility |
|--------|------|----------------|
| Create | `core/domain/src/main/kotlin/com/stratum/domain/workout/myorep/MyoRepKind.kt` | `enum class MyoRepKind { RepFloor, RepMatching }` (Type A / Type B) — NOT persisted on `SetType` per D6 |
| Create | `core/domain/src/main/kotlin/com/stratum/domain/workout/myorep/MyoRepLifecycle.kt` | `MyoRepLifecycle` interface: `nextMiniSetRepFloor(state): SetSlot?`, `nextMiniSetRepMatching(state): SetSlot?` + `MyoRepBlockState(workoutLiftId, activationSlotId, kind, activationReps, repFloor, cumulativeMiniSetReps, lastMiniSetReps, generatedSlots)` |
| Create | `core/domain/src/main/kotlin/com/stratum/domain/workout/myorep/MyoRepConstraintValidator.kt` | `MyoRepConstraintValidator().invoke(lift: WorkoutLiftProjection): MyoRepConstraintResult` — only-set/final-set enforcement (D6). `MyoRepConstraintResult { Valid; Invalid(reasons) }` + `MyoRepConstraintViolation { MyoRepBlockNotOnlyOrFinalSet }` |
| Modify | `core/domain/src/main/kotlin/com/stratum/domain/workout/SetSlot.kt` | Carry `myoRepKind: MyoRepKind?` on slots (runtime/authoring concept; not persisted on `SetType`) |
| Modify | `core/domain/src/main/kotlin/com/stratum/domain/workout/WorkoutOverlay.kt` | Add `StructuralMutation.GenerateMyoRepMiniSet` to the sealed interface |
| Modify | `core/domain/src/main/kotlin/com/stratum/domain/workout/GenerateProgressionSnapshotUseCase.kt` | Validate Myo-rep constraint at snapshot time; reject invalid |

### Slice 6 — Drop sets

| Action | File | Responsibility |
|--------|------|----------------|
| Create | `core/domain/src/main/kotlin/com/stratum/domain/workout/dropset/DropSetConstraintValidator.kt` | `DropSetConstraintValidator().invoke(lift: WorkoutLiftProjection): DropSetConstraintResult` — must-follow enforcement (D7). `DropSetConstraintResult { Valid; Invalid(reasons) }` + `DropSetConstraintViolation { DropSetNotFollowingAnotherSet }` |
| Create | `core/domain/src/main/kotlin/com/stratum/domain/workout/dropset/EstimateDropSetWeightUseCase.kt` | `EstimateDropSetWeightUseCase(fatigueModel).invoke(precedingWeight: Weight, nextSetIndex: Int, perLiftSlope?, perLiftIntercept?): Weight` (D7) |
| Modify | `core/domain/src/main/kotlin/com/stratum/domain/workout/WorkoutOverlay.kt` | Add `StructuralMutation.AddDropSet` |
| Modify | `core/domain/src/main/kotlin/com/stratum/domain/workout/GenerateProgressionSnapshotUseCase.kt` | Validate drop-set constraint at snapshot time |
| Modify | `core/testing/src/main/kotlin/com/stratum/core/testing/workout/FakeFatigueModel.kt` | Extend to drive drop-set reduction cases |

### Slice 7 — `:feature:workout` presentation skeleton

| Action | File | Responsibility |
|--------|------|----------------|
| Create | `feature/workout/build.gradle.kts` | New module — depends on `:core:domain`, `:core:ui` (not `:core:data` directly per ARCHITECTURE.md §4.4) |
| Create | `feature/workout/src/main/kotlin/com/stratum/feature/workout/WorkoutViewModel.kt` | `@HiltViewModel WorkoutViewModel` owning overlay (`MutableStateFlow<WorkoutOverlay>`); consumes use cases only; maps rendered workout → presentation model |
| Create | `feature/workout/src/main/kotlin/com/stratum/feature/workout/ui/WorkoutUiState.kt` | `WorkoutUiState` sealed (`Loading` / `NoActiveSession` / `Loaded` / `Error`) + `RenderedLiftUi` + `RenderedSetUi` — no domain types embedded (Detekt `ForbiddenDomainTypeInUiState`) |
| Create | `feature/workout/src/main/kotlin/com/stratum/feature/workout/ui/WorkoutRoute.kt` | `@Composable fun WorkoutRoute(onNavigateBack: () -> Unit, onNavigateToFinalize: (sessionId: String) -> Unit, viewModel: WorkoutViewModel = hiltViewModel())` — consumes `WorkoutUiState`; navigation via callbacks (no `NavController` in feature) |
| Create | `feature/workout/src/main/kotlin/com/stratum/feature/workout/ui/WorkoutUiEvent.kt` | One-shot events: `ShowMessage(message)`, `NavigateToFinalize(sessionId)`. No `PromptSchemeTransition` — D8 locks auto-transition with no confirm. |
| Create | `feature/workout/src/main/kotlin/com/stratum/feature/workout/ui/WorkoutUiMapper.kt` | Domain `RenderedWorkout` → `WorkoutUiState.Loaded` |
| Modify | `settings.gradle.kts` | `include(":feature:workout")` |
| Modify | `app/build.gradle.kts` | `implementation(project(":feature:workout"))` |
| Modify | `app/src/main/java/com/stratum/navigation/StratumNavHost.kt` | Add `WorkoutRoute` to the `NavHost` with callback wiring |

### Slice 8 — EditWorkoutScreen per-lift scheme toggle

| Action | File | Responsibility |
|--------|------|----------------|
| Create | `feature/program/src/main/kotlin/com/stratum/feature/program/ui/ProgressionSchemeExplainerSheet.kt` | `@Composable fun ProgressionSchemeExplainerSheet(onDismiss: () -> Unit)` — `StratumBottomSheet` content explaining both schemes + eligibility (compounds only; beginner-gains or returning from layoff → Linear) + auto-graduation rule (after 2 reset cycles → Double) |
| Modify | `feature/program/src/main/kotlin/com/stratum/feature/program/EditWorkoutViewModel.kt` | Add `setProgressionScheme(workoutLiftId: WorkoutLiftId, scheme: ProgressionScheme)`; gate UI exposure on `isCompound`; on save, append a `ProgramChangeEvent` with `SchemeChanged` payload when scheme changed. Inject `ProgramChangeEventRepository` (via a use case if a wrapper exists; otherwise the repository port — see API contracts note). |
| Modify | `feature/program/src/main/kotlin/com/stratum/feature/program/ui/EditWorkoutScreen.kt` | Render toggle on compound rows only; info affordance → explainer sheet |
| Modify | `feature/program/src/main/kotlin/com/stratum/feature/program/ui/EditWorkoutUiState.kt` | Add `isCompound: Boolean` + current `progressionScheme: ProgressionScheme` per `EditLiftState` |
| Modify | `feature/program/src/main/kotlin/com/stratum/feature/program/EditWorkoutDomainMapper.kt` | Read `progressionScheme` from baseline (already round-tripped at lines 31/36) into `EditLiftState`; on save, write the toggled scheme back through `toWorkout` |
| Modify | `core/domain/src/main/kotlin/com/stratum/domain/training/ProgramChangeEvent.kt` | Add `SCHEME_CHANGED` to `ProgramChangeEventType` enum + `data class SchemeChanged(val workoutLiftId: WorkoutLiftId, val liftId: LiftId, val previous: ProgressionScheme, val current: ProgressionScheme) : ProgramChangeEventPayload`. Append-only. |

---

## API contracts and interfaces

Signatures only — no bodies. Names must match `test_spec.md` exactly.

### Value types (D1)

```kotlin
// :core:domain — workout/SetSlot.kt
@JvmInline value class OverlaySetInstanceId(val value: String)

sealed interface SetSlotId {
    data class Template(val liftSetId: LiftSetId) : SetSlotId
    data class Overlay(val instanceId: OverlaySetInstanceId) : SetSlotId
}

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
    val myoRepKind: MyoRepKind? = null,   // added in Slice 5
)

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
```

```kotlin
// :core:domain — workout/ProgressionSnapshot.kt
data class SlotRecommendation(
    val slotId: SetSlotId,
    val recommendedWeight: Weight,
    val baselineExpectedReps: Int,
)
data class ProgressionSnapshot(
    val workoutId: WorkoutId,
    val bySlot: Map<SetSlotId, SlotRecommendation>,
)
```

```kotlin
// :core:domain — workout/WorkoutOverlay.kt
data class LoggedSetValue(
    val slotId: SetSlotId,
    val weight: Weight,
    val reps: Int,
    val rir: Int?,
)

sealed interface StructuralMutation {
    data class AddSet(val afterSlotId: SetSlotId, val newSlot: SetSlot) : StructuralMutation
    data class DeleteSet(val slotId: SetSlotId) : StructuralMutation
    data class GenerateMyoRepMiniSet(val activationSlotId: SetSlotId, val newSlot: SetSlot) : StructuralMutation  // Slice 5
    data class AddDropSet(val afterSlotId: SetSlotId, val newSlot: SetSlot) : StructuralMutation                  // Slice 6
}

data class WorkoutOverlay(
    val loggedSets: Map<SetSlotId, LoggedSetValue> = emptyMap(),
    val recommendations: Map<SetSlotId, Weight> = emptyMap(),
    val addedSlots: List<SetSlot> = emptyList(),
    val deletedSlotIds: Set<SetSlotId> = emptySet(),
    val undoStack: List<StructuralMutation> = emptyList(),
) {
    fun apply(mutation: StructuralMutation): WorkoutOverlay
    fun undo(): WorkoutOverlay
}
```

```kotlin
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
    val recommendedWeight: Weight,
    val recommendedReps: Int,
    val loggedValue: LoggedSetValue?,
)
data class RenderedWorkoutLift(
    val workoutLiftId: WorkoutLiftId,
    val liftId: LiftId,
    val liftName: String,
    val progressionScheme: ProgressionScheme,
    val sets: List<RenderedSet>,
)
data class RenderedWorkout(
    val workoutId: WorkoutId,
    val workoutName: String,
    val lifts: List<RenderedWorkoutLift>,
)
```

### Snapshot generation + historical-priority (D3, research Gap 4)

```kotlin
// :core:domain — workout/ProgressionHistoryPort.kt
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
interface ProgressionHistoryPort {
    suspend fun historyForSlot(request: SlotHistoryRequest): List<SlotHistoryEntry>
}

data class SnapshotRequest(
    val programId: ProgramId,
    val workoutId: WorkoutId,
    val lifts: List<WorkoutLift>,
)
class GenerateProgressionSnapshotUseCase @Inject constructor(
    private val progressionHistoryPort: ProgressionHistoryPort,
    // double/linear engines injected; added in Slices 2/4
) {
    suspend operator fun invoke(request: SnapshotRequest): Result<ProgressionSnapshot>
}
```

### Merge + projection (D1)

```kotlin
class MergeRenderedWorkoutUseCase @Inject constructor() {
    operator fun invoke(
        structure: WorkoutStructureProjection,
        snapshot: ProgressionSnapshot,
        overlay: WorkoutOverlay,
    ): RenderedWorkout
}
class ProjectWorkoutStructureUseCase @Inject constructor() {
    operator fun invoke(workout: Workout, liftNames: Map<LiftId, String>): WorkoutStructureProjection
}
```

### Progression engines (D8)

```kotlin
// :core:domain — workout/progression/ProgressionEngine.kt
data class SlotProgressionRequest(
    val slot: SetSlot,
    val history: List<SlotHistoryEntry>,
    val fallbackOneRm: Weight?,
)
interface ProgressionEngine {
    val scheme: ProgressionScheme
    fun recommend(request: SlotProgressionRequest): SlotRecommendation
}

// Slice 2
class DoubleProgressionEngine @Inject constructor() : ProgressionEngine {
    override val scheme: ProgressionScheme = ProgressionScheme.Double
    override fun recommend(request: SlotProgressionRequest): SlotRecommendation
}

// Slice 4
enum class StallStrictness { RirBased, AbsoluteFailure }
data class LinearProgressionConfig(
    val lowerBodyIncrement: Weight,
    val upperBodyIncrement: Weight,
    val minimumIncrement: Weight,
    val perLiftIncrementOverride: Map<LiftId, Weight>,
    val stallThresholdSessions: Int,            // 2
    val resetFraction: Double,                  // 0.10
    val perLiftResetFractionOverride: Map<LiftId, Double>,
    val resetsBeforeTransition: Int,            // 2
    val stallStrictness: StallStrictness,
)
data class LinearProgressionState(
    val liftId: LiftId,
    val currentStallCount: Int,
    val currentResetCount: Int,
    val shouldReset: Boolean,
    val shouldTransitionToDouble: Boolean,
    val emittedEvent: SchemeTransitionEvent? = null,   // non-null iff shouldTransitionToDouble (D8)
)
class LinearProgressionEngine @Inject constructor(
    private val config: LinearProgressionConfig,
) : ProgressionEngine {
    override val scheme: ProgressionScheme = ProgressionScheme.Linear
    override fun recommend(request: SlotProgressionRequest): SlotRecommendation
    fun diagnose(liftId: LiftId, history: List<SlotHistoryEntry>): LinearProgressionState
}
enum class SchemeTransitionReason { ResetCyclesExhausted }
data class SchemeTransitionEvent(
    val workoutLiftId: WorkoutLiftId,
    val liftId: LiftId,
    val from: ProgressionScheme,
    val to: ProgressionScheme,
    val reason: SchemeTransitionReason,
)
```

### Recommendation engine (D4)

```kotlin
// :core:domain — workout/RecommendationContracts.kt
const val BACKOFF_REP_FLOOR: Int = 5
sealed interface RecommendationOutcome {
    data class Hold(val weight: Weight) : RecommendationOutcome
    data class Reduce(val weight: Weight) : RecommendationOutcome
}

// :core:domain — workout/RecommendationEngine.kt
class RecommendationEngine @Inject constructor() {
    fun recommendNextSet(
        lastPerformed: LoggedSetValue?,   // nullable: returns null when no performed set yet (first set of session)
        targetSlot: SetSlot,
        currentWeight: Weight,
    ): RecommendationOutcome?
}
```

### Inverse e1RM (D4) — MODIFY `EstimatedOneRm`

```kotlin
// :core:domain — importing/LiftNameResolver.kt
object EstimatedOneRm {
    fun calculate(weight: Double, reps: Int): Double              // existing, unchanged
    fun weightForReps(estimatedOneRm: Double, targetReps: Int): Double  // NEW
    fun repsForWeight(estimatedOneRm: Double, weight: Double): Int     // NEW
}
```

`weightForReps` and `repsForWeight` invert the same Brzycki (reps ≤ 10) /
Epley (else) formulas as `calculate`, so the three are mathematical inverses
(round-trip within rounding tolerance).

### Fatigue model (D5/D7, Q2)

```kotlin
// :core:domain — response/ResponseConstants.kt  (MODIFY)
const val POPULATION_FATIGUE_DECAY_SLOPE: Double
const val POPULATION_FATIGUE_DECAY_INTERCEPT: Double

// :core:domain — workout/fatigue/FatigueModel.kt
interface FatigueModel {
    fun expectedRepsForAddedSet(
        baselineOneRm: Double,
        targetWeight: Weight,
        nextSetIndex: Int,
        perLiftSlope: Double?,
        perLiftIntercept: Double?,
    ): Int
}
class EstimateAddedSetRepsUseCase @Inject constructor(private val fatigueModel: FatigueModel) {
    operator fun invoke(
        previousRecommendation: SlotRecommendation,
        nextSetIndex: Int,
        perLiftSlope: Double?,
        perLiftIntercept: Double?,
    ): Int
}
class EstimateDropSetWeightUseCase @Inject constructor(private val fatigueModel: FatigueModel) {
    operator fun invoke(
        precedingWeight: Weight,
        nextSetIndex: Int,
        perLiftSlope: Double?,
        perLiftIntercept: Double?,
    ): Weight
}
```

### Myo-rep (D6) + drop set (D7)

```kotlin
enum class MyoRepKind { RepFloor, RepMatching }
data class MyoRepBlockState(
    val workoutLiftId: WorkoutLiftId,
    val activationSlotId: SetSlotId,
    val kind: MyoRepKind,
    val activationReps: Int,
    val repFloor: Int,
    val cumulativeMiniSetReps: Int,
    val lastMiniSetReps: Int?,
    val generatedSlots: List<SetSlot>,
)
interface MyoRepLifecycle {
    fun nextMiniSetRepFloor(state: MyoRepBlockState): SetSlot?
    fun nextMiniSetRepMatching(state: MyoRepBlockState): SetSlot?
}
enum class MyoRepConstraintViolation { MyoRepBlockNotOnlyOrFinalSet }
sealed interface MyoRepConstraintResult {
    data object Valid : MyoRepConstraintResult
    data class Invalid(val reasons: Set<MyoRepConstraintViolation>) : MyoRepConstraintResult
}
class MyoRepConstraintValidator @Inject constructor() {
    operator fun invoke(lift: WorkoutLiftProjection): MyoRepConstraintResult
}

enum class DropSetConstraintViolation { DropSetNotFollowingAnotherSet }
sealed interface DropSetConstraintResult {
    data object Valid : DropSetConstraintResult
    data class Invalid(val reasons: Set<DropSetConstraintViolation>) : DropSetConstraintResult
}
class DropSetConstraintValidator @Inject constructor() {
    operator fun invoke(lift: WorkoutLiftProjection): DropSetConstraintResult
}
```

### Scheme eligibility (D8) — MODIFY `ProgramRules.kt`

```kotlin
// :core:domain — program/generation/reference/ProgramRules.kt
fun schemeForTrainingAge(
    trainingAge: TrainingAge?,
    lastTrained: LastTrainedBucket?,
    isCompound: Boolean,
): ProgressionScheme  // generation-package enum; mapped to domain via existing .toDomain()
```

Returns `Linear` iff `isCompound && (trainingAge == Beginner || lastTrained == MoreThanThreeMonthsAgo)`; else `Double`.

### Enum collapse (D8) — MODIFY `LastTrainedBucket`

```kotlin
// :core:domain — onboarding/TrainingProfileEnums.kt
enum class LastTrainedBucket {
    Never,
    WithinLastThreeMonths,
    MoreThanThreeMonthsAgo,
}
```

Round-trips through `EnumConverters.toLastTrainedBucket` / `fromLastTrainedBucket` (unchanged converter mechanism; the `runCatching { valueOf(it) }` decode means stale persisted old names resolve to `null` on existing dev installs — v1 reinstall policy applies).

### ProgramChangeEvent extension (Slice 8)

```kotlin
// :core:domain — training/ProgramChangeEvent.kt  (MODIFY)
enum class ProgramChangeEventType {
    LIFT_SWAPPED,
    WORKOUT_EDITED,
    PROGRAM_REGENERATED,
    CYCLE_COMPLETED,
    SCHEME_CHANGED,                                  // NEW
}
sealed interface ProgramChangeEventPayload {
    data class LiftSwapped(...) : ProgramChangeEventPayload
    data class WorkoutEdited(val workoutId: WorkoutId) : ProgramChangeEventPayload
    data object ProgramRegenerated : ProgramChangeEventPayload
    data class CycleCompleted(val completedCycleNumber: Int) : ProgramChangeEventPayload
    data class SchemeChanged(                         // NEW
        val workoutLiftId: WorkoutLiftId,
        val liftId: LiftId,
        val previous: ProgressionScheme,
        val current: ProgressionScheme,
    ) : ProgramChangeEventPayload
}
```

**Note on `AppendProgramChangeEventUseCase`:** the ticket/design reference this
conceptually, but the codebase has no such use case class — the port is
`ProgramChangeEventRepository.append(event: ProgramChangeEvent)`
(`ProgramChangeEventRepository.kt:7`). Slice 8 and the Slice 4 auto-transition
both append events by constructing a `ProgramChangeEvent` and calling the
repository. Slice 8 should inject the repository (or a thin wrapper use case if
the detekt `ForbiddenDirectRepositoryInjection` rule requires it — verify in
TDD Red).

### Presentation skeleton (Slice 7)

```kotlin
// :feature:workout
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
    val events: Flow<WorkoutUiEvent>
    fun startSession(workoutId: WorkoutId)
    fun logSet(slotId: SetSlotId, weight: Weight, reps: Int, rir: Int?)
    fun addSet(afterSlotId: SetSlotId)
    fun deleteSet(slotId: SetSlotId)
    fun undo()
    fun generateMyoRepMiniSet(activationSlotId: SetSlotId)
    fun addDropSet(afterSlotId: SetSlotId)
    fun swapLift(workoutLiftId: WorkoutLiftId, newLiftId: LiftId)  // triggers snapshot recompute (D3)
    fun finalizeSession()
    fun abandonSession()
}
sealed interface WorkoutUiState {
    data object Loading : WorkoutUiState
    data object NoActiveSession : WorkoutUiState
    data class Loaded(val workoutName: String, val lifts: List<RenderedLiftUi>, val isFinalizing: Boolean) : WorkoutUiState
    data class Error(val message: String) : WorkoutUiState
}
data class RenderedLiftUi(val liftName: String, val progressionSchemeLabel: String, val sets: List<RenderedSetUi>)
data class RenderedSetUi(
    val slotKey: String,
    val positionLabel: String,
    val setTypeLabel: String,
    val repRangeLabel: String?,
    val rirLabel: String,
    val recommendedWeightLabel: String,
    val recommendedRepsLabel: String,
    val loggedWeightLabel: String?,
    val loggedRepsLabel: String?,
    val isComplete: Boolean,
)
sealed interface WorkoutUiEvent {
    data class ShowMessage(val message: String) : WorkoutUiEvent
    data class NavigateToFinalize(val sessionId: String) : WorkoutUiEvent
}
@Composable
fun WorkoutRoute(
    onNavigateBack: () -> Unit,
    onNavigateToFinalize: (sessionId: String) -> Unit,
    viewModel: WorkoutViewModel = hiltViewModel(),
)
```

### EditWorkoutScreen toggle (Slice 8)

```kotlin
// :feature:program — EditWorkoutViewModel.kt  (MODIFY)
fun setProgressionScheme(workoutLiftId: WorkoutLiftId, scheme: ProgressionScheme)
// EditLiftState gains: isCompound: Boolean, progressionScheme: ProgressionScheme
```

---

## Data model / schema changes

- **`LastTrainedBucket` enum collapse (5→3):** `Never` / `WithinLastMonth` /
  `OneToSixMonthsAgo` / `SixToTwelveMonthsAgo` / `MoreThanAYearAgo` → `Never` /
  `WithinLastThreeMonths` / `MoreThanThreeMonthsAgo`. Persisted via
  `EnumConverters` as `.name`; stale old names decode to `null` on existing
  dev installs. **Schema-adjacent → v1 reinstall policy** (AGENTS.md §4.5,
  DATA_LAYER_RULES.md §7): keep `StratumDatabase` at version 1; apply by
  uninstall/reinstall during development. No migration file.
- **No new Room tables/columns for transient overlay state (D9).** Overlay is
  in-memory ViewModel session state. The only new `:core:data` work is a read
  query (Slice 1, research Gap 4) and the existing append-only
  `ProgramChangeEvent` table gets two new enum values (`SCHEME_CHANGED`) — both
  are additive enum values stored as `.name` strings, not column changes.
- **`ProgramChangeEvent` extension:** add `SCHEME_CHANGED` to
  `ProgramChangeEventType` and `SchemeChanged` to `ProgramChangeEventPayload`.
  Append-only; no column change; converter already round-trips via `.name`.

---

## Drift and escalation rules

- **Schema drift:** if implementation discovers the overlay or snapshot needs a
  persisted column, **stop and escalate to human** per AGENTS.md §4.5 /
  DATA_LAYER_RULES.md §7. Do not add a Room column without explicit
  authorization.
- **D8 tuning knobs:** `POPULATION_FATIGUE_DECAY_SLOPE`/`INTERCEPT`,
  `BACKOFF_REP_FLOOR`, and the `LinearProgressionConfig` defaults are
  parameters. If a value becomes blocking (e.g. the population fatigue constant
  produces nonsensical rep estimates for a test fixture), surface it as an
  escalation with a recommended numeric value rather than guessing silently.
- **`schemeForTrainingAge` signature change:** it is currently
  `fun schemeForTrainingAge(trainingAge: String): ProgressionScheme`
  (`ProgramRules.kt:30-31`) and is called by `ProgramNormalizer.kt:25` and
  `WorkoutAssembler`. Slice 4 changes the signature and must update both
  call sites plus thread `lastTrained` + `isCompound` through
  `ProgramNormalizationRequest` / `ProgramGenerationContext`. If a call site
  cannot supply `lastTrained` without a generation-pipeline refactor, escalate
  — do not pass `null` silently.
- **`AppendProgramChangeEventUseCase` naming:** the ticket references it but
  the codebase has only `ProgramChangeEventRepository.append`. Slice 8 must
  use the repository (or add a wrapper use case if detekt
  `ForbiddenDirectRepositoryInjection` forbids ViewModel→repository). If the
  detekt rule blocks direct injection and adding a wrapper use case is the
  cleaner fix, do that without escalating (it is a mechanical, contract-aligned
  change).
- **Feature-module detekt:** `:feature:workout` must pass `ForbiddenDomainTypeInUiState`,
  `ForbiddenDomainTypeInComposable`, `ForbiddenMockKInTests`, and
  `ForbiddenVerifyInViewModelTests`. If a presentation mapping cannot avoid a
  domain type without a contract waiver, escalate.
- **`MinDispatcherRule`:** all ViewModel tests use
  `com.stratum.core.testing.MainDispatcherRule` (`@ExtendWith`) and
  `StandardTestDispatcher`. No `delay()` polls for IO.

---

## Ordered implementation tasks

Each task references the scenario IDs in `test_spec.md` it satisfies. Run the
slice's full test command after each task within the slice.

### Slice 1 — Snapshot + merge skeleton

**Task 1.1 — Structure identity + read models** (Create: `SetSlot.kt`,
`ProgressionSnapshot.kt`, `WorkoutOverlay.kt` (Slice 1 shape),
`RenderedWorkout.kt`, `WorkoutStructureProjection.kt`)
Satisfies: `S1-structure-projection-empty-overlay`, `S1-snapshot-immutable`.

**Task 1.2 — Historical-priority port + repository** (Create:
`ProgressionHistoryPort.kt`, `ProgressionHistoryRepository.kt`,
`FakeProgressionHistoryRepository.kt`; Modify: `TrainingLogDaos.kt`)
Satisfies: `S1-history-priority-n-minus-one-first`,
`S1-history-priority-earlier-sessions`,
`S1-history-priority-same-lift-other-workout`,
`S1-history-priority-empty-when-no-history`.

**Task 1.3 — Snapshot-generation use case (Standard top set placeholder)** (Create:
`GenerateProgressionSnapshotUseCase.kt` with a single Standard top-set
computation behind the eventual engine dispatch)
Satisfies: `S1-snapshot-compute-once`,
`S1-mutation-defense-recompute-on-add-set`.

**Task 1.4 — Structure projection + merge use cases (pure)** (Create:
`ProjectWorkoutStructureUseCase.kt`, `MergeRenderedWorkoutUseCase.kt`)
Satisfies: `S1-merge-empty-overlay-renders-snapshot-baseline`.

### Slice 2 — Double progression + live recommendation

**Task 2.1 — Inverse e1RM** (Modify: `LiftNameResolver.kt` — add
`weightForReps` + `repsForWeight`)
Satisfies: `S2-weight-for-reps-round-trip`,
`S2-reps-for-weight-round-trip`,
`S2-mutation-defense-weight-for-reps-not-identity`.

**Task 2.2 — Double-progression engine + dispatch** (Create:
`ProgressionEngine.kt`, `DoubleProgressionEngine.kt`; Modify:
`GenerateProgressionSnapshotUseCase.kt` to dispatch the Double engine)
Satisfies: `S2-double-increment-when-top-of-range-at-rir`,
`S2-double-hold-otherwise`,
`S2-double-backoff-inherits-top-set-weight`.

**Task 2.3 — Recommendation engine (D4)** (Create:
`RecommendationContracts.kt`, `RecommendationEngine.kt`)
Satisfies: `S2-rec-top-set-hold`,
`S2-rec-top-set-reduce`,
`S2-rec-backoff-hold`,
`S2-rec-backoff-reduce-to-floor`,
`S2-rec-null-when-no-performed-set`,
`S2-mutation-defense-always-hold`.

**Task 2.4 — Overlay recommendation field + merge priority** (Modify:
`WorkoutOverlay.kt`, `MergeRenderedWorkoutUseCase.kt`)
Satisfies: `S2-merge-logged-supersedes-recommendation`,
`S2-merge-recommendation-supersedes-snapshot-upcoming`.

### Slice 3 — Dynamic set lifecycle (Standard)

**Task 3.1 — Fatigue model + population constants** (Create: `FatigueModel.kt`,
`PopulationFatigueModel.kt`, `FakeFatigueModel.kt`; Modify:
`ResponseConstants.kt`)
Satisfies: `S3-fatigue-per-lift-slope-used`,
`S3-fatigue-population-default-when-null`,
`S3-fatigue-rep-projection-monotonic-non-increasing`.

**Task 3.2 — Overlay mutation API (add/delete/undo)** (Modify: `WorkoutOverlay.kt`,
`ProjectWorkoutStructureUseCase.kt`, `MergeRenderedWorkoutUseCase.kt`)
Satisfies: `S3-add-set-copies-weight-estimates-reps`,
`S3-delete-set-removes`,
`S3-undo-add-removes`,
`S3-undo-delete-restores`,
`S3-mutation-defense-snapshot-untouched-on-add-set`.

**Task 3.3 — EstimateAddedSetRepsUseCase** (Create:
`EstimateAddedSetRepsUseCase.kt`)
Satisfies: `S3-add-set-copies-weight-estimates-reps`.

### Slice 4 — Linear progression (Greyskull-style hybrid)

**Task 4.1 — Enum collapse (5→3)** (Modify: `TrainingProfileEnums.kt`,
`ProfileDraftMapping.kt`, onboarding screen copy, onboarding/data tests,
`VolumeTargetPlannerTest.kt`, `VolumePlannerTest.kt`)
Satisfies: `S4-last-trained-bucket-3-values`,
`S4-enum-converters-round-trip`,
`S4-old-5-values-no-longer-referenced`.

**Task 4.2 — schemeForTrainingAge (compounds-only, beginner-or-layoff)** (Modify:
`ProgramRules.kt`, `ProgramNormalizer.kt`, `WorkoutAssembler.kt`)
Satisfies: `S4-scheme-beginner-linear`,
`S4-scheme-non-beginner-within-three-months-double`,
`S4-scheme-non-beginner-more-than-three-months-linear`,
`S4-scheme-isolation-always-double`,
`S4-mutation-defense-scheme-ignores-iscompound`.

**Task 4.3 — LinearProgressionConfig + LinearProgressionEngine** (Create:
`LinearProgressionConfig.kt`, `LinearProgressionEngine.kt`,
`SchemeTransitionEvent.kt`; Modify: `GenerateProgressionSnapshotUseCase.kt`
to dispatch the Linear engine and apply auto-transition)
Satisfies: `S4-linear-increment-when-bottom-at-rir`,
`S4-linear-hold-when-rir-not-met`,
`S4-linear-2-stalls-10-percent-reset`,
`S4-linear-reset-count-resets-after-reset`,
`S4-linear-2-resets-transition-to-double`,
`S4-linear-missed-sessions-do-not-credit-stall`,
`S4-linear-per-lift-increment-override`,
`S4-linear-stall-strictness-absolute-vs-rir`.

### Slice 5 — Myo-reps Type A + Type B

**Task 5.1 — MyoRepKind + lifecycle** (Create: `MyoRepKind.kt`,
`MyoRepLifecycle.kt`; Modify: `SetSlot.kt` to carry `myoRepKind`)
Satisfies: `S5-myo-rep-type-a-rep-floor-generates-until-floor`,
`S5-myo-rep-type-a-block-ends-on-floor`,
`S5-myo-rep-type-b-rep-matching-generates-until-parity`,
`S5-myo-rep-type-b-block-ends-on-parity`.

**Task 5.2 — Constraint validator + snapshot-time validation** (Create:
`MyoRepConstraintValidator.kt`; Modify: `WorkoutOverlay.kt`
(`GenerateMyoRepMiniSet` mutation), `GenerateProgressionSnapshotUseCase.kt`)
Satisfies: `S5-myo-rep-only-set-valid`,
`S5-myo-rep-final-set-valid`,
`S5-myo-rep-mid-lift-invalid`,
`S5-myo-rep-mini-set-deletable-undoable`.

### Slice 6 — Drop sets

**Task 6.1 — Drop-set constraint validator** (Create:
`DropSetConstraintValidator.kt`)
Satisfies: `S6-drop-set-follows-another-set-valid`,
`S6-drop-set-first-set-invalid`.

**Task 6.2 — Drop-set weight estimation + overlay wiring** (Create:
`EstimateDropSetWeightUseCase.kt`; Modify: `WorkoutOverlay.kt`
(`AddDropSet` mutation), `GenerateProgressionSnapshotUseCase.kt`,
`FakeFatigueModel.kt`)
Satisfies: `S6-drop-set-fatigue-reduction-applied`,
`S6-drop-set-per-lift-slope-vs-population`.

### Slice 7 — `:feature:workout` presentation skeleton

**Task 7.1 — Module + Gradle wiring** (Create: `feature/workout/build.gradle.kts`;
Modify: `settings.gradle.kts`, `app/build.gradle.kts`,
`StratumNavHost.kt`)
Satisfies: `S7-no-navcontroller-in-feature`.

**Task 7.2 — WorkoutViewModel (overlay owner)** (Create: `WorkoutViewModel.kt`)
Satisfies: `S7-empty-overlay-renders-snapshot-baseline`,
`S7-log-set-updates-overlay-calls-log-set-use-case`,
`S7-add-delete-undo-mutate-overlay`,
`S7-recommendation-supersedes-snapshot-upcoming`,
`S7-merge-priority-logged-over-recommendation-over-snapshot`,
`S7-mutation-defense-no-verify`.

**Task 7.3 — Presentation model + mapper** (Create: `WorkoutUiState.kt`,
`WorkoutUiEvent.kt`, `WorkoutUiMapper.kt`)
Satisfies: `S7-empty-overlay-renders-snapshot-baseline`,
`S7-merge-priority-logged-over-recommendation-over-snapshot`.

**Task 7.4 — WorkoutRoute composable** (Create: `WorkoutRoute.kt`)
Satisfies: `S7-no-navcontroller-in-feature`.

### Slice 8 — EditWorkoutScreen per-lift scheme toggle

**Task 8.1 — ProgramChangeEvent extension** (Modify: `ProgramChangeEvent.kt` —
add `SCHEME_CHANGED` + `SchemeChanged` payload)
Satisfies: `S8-toggle-appends-scheme-changed-event`,
`S8-mutation-defense-toggle-appends-no-event`.

**Task 8.2 — EditLiftState + mapper + ViewModel toggle** (Modify:
`EditWorkoutUiState.kt` (`isCompound` + `progressionScheme`),
`EditWorkoutDomainMapper.kt`, `EditWorkoutViewModel.kt`
(`setProgressionScheme`))
Satisfies: `S8-toggle-compound-updates-state-marks-dirty`,
`S8-toggle-isolation-is-noop`,
`S8-toggle-appends-scheme-changed-event`,
`S8-mutation-defense-toggle-appends-no-event`,
`S8-mutation-defense-toggle-isolation-not-noop`.

**Task 8.3 — EditWorkoutScreen UI + explainer sheet** (Modify:
`EditWorkoutScreen.kt`; Create: `ProgressionSchemeExplainerSheet.kt`)
Satisfies: `S8-toggle-compound-updates-state-marks-dirty`,
`S8-toggle-isolation-is-noop`.

---

## Test commands (per slice)

| Slice | Command |
|-------|---------|
| 1 | `./gradlew :core:domain:test` + `./gradlew :core:data:testDebugUnitTest` |
| 2 | `./gradlew :core:domain:test` |
| 3 | `./gradlew :core:domain:test` |
| 4 | `./gradlew :core:domain:test` + `./gradlew :feature:onboarding:testDebugUnitTest` (enum/display) + `./gradlew :core:data:testDebugUnitTest` (mapper/dao tests) |
| 5 | `./gradlew :core:domain:test` |
| 6 | `./gradlew :core:domain:test` |
| 7 | `./gradlew :feature:workout:testDebugUnitTest` |
| 8 | `./gradlew :feature:program:testDebugUnitTest` |

Every slice gates on `./gradlew detekt` with `maxIssues: 0`.

---

## Self-review

- Every approved design decision (D1–D11) appears in tasks or is explicitly out
  of scope (D10 out of scope; D11 split — toggle in scope, Myo-rep/drop-set
  authoring + rep-range display out of scope).
- Slice order matches `structure.md` checkpoints: 1 → 2 → 3 → {4,5,6} → 7 → 8.
- Every requirement has both an implementation task and a scenario (see
  `test_spec.md`).
- Every behavior-changing task references at least one scenario ID.
- Names and signatures match exactly between `spec.md` and `test_spec.md`.
- All listed existing files were verified in the codebase (paths confirmed
  2026-07-05): `Program.kt`, `ProgramEnums.kt`, `ProgramRules.kt:30-31`,
  `ProgramNormalizer.kt:25`, `WorkoutAssembler.kt`, `LiftNameResolver.kt:272-286`,
  `TrainingProfileEnums.kt:34-41`, `ProfileDraftMapping.kt:83-87`,
  `EnumConverters.kt:84-89`, `UserTrainingProfile.kt`, `ResponseConstants.kt`,
  `TrainingInsightsExtractor.kt`, `EditWorkoutDomainMapper.kt:31,36`,
  `EditWorkoutViewModel.kt`, `EditWorkoutScreen.kt`, `LogSetUseCase.kt`,
  `CycleProgressResolver.kt`, `ObserveCycleProgressUseCase.kt`,
  `ProgramChangeEvent.kt`, `ProgramChangeEventRepository.kt:7`,
  `RoomTrainingLogRepository.kt`, `Lift.kt:50` (`isCompound`).
- Post-approval corrections reflected: D4 backoff `BACKOFF_REP_FLOOR = 5`
  Hold/Reduce + second inverse `repsForWeight`; D8 `LastTrainedBucket` 5→3
  collapse, compounds-only eligibility, auto-transition locked (no
  `transitionMode` field, no `PromptSchemeTransition` UI event); D11 Slice 8
  toggle (compound rows only, isolation no-op, appends `SchemeChanged`).
- Verified gaps addressed: `ProgramChangeEventPayload` has no `SchemeChanged`
  today → Task 8.1 adds it; `AppendProgramChangeEventUseCase` does not exist
  as a class → Slice 8/4 use `ProgramChangeEventRepository.append` (noted in
  API contracts); `schemeForTrainingAge` currently takes `String` → Task 4.2
  changes signature and updates `ProgramNormalizer.kt:25` + `WorkoutAssembler`.
- No placeholders (`TBD`, `TODO`, "similar to", "as above", "implement later").
