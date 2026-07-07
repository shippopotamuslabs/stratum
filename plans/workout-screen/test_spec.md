# Test Spec: workout-screen

Behavioral scenarios for the live workout runtime architecture. Gherkin with
stable scenario IDs. Every `Then` clause names a literal expected value,
observable state, enum case, or specific exception type and exact message.

Reference numeric values are derived independently from the Brzycki
(reps ≤ 10) / Epley (else) formulas:

```
calculate(w, r ≤ 10) = w * (1.0278 / (1.0278 - 0.0278*r))
calculate(w, r > 10) = w * (1 + r/30)
weightForReps(e, r ≤ 10) = e * (1.0278 - 0.0278*r) / 1.0278
weightForReps(e, r > 10)  = e / (1 + r/30)
repsForWeight(e, w) = round( inverse of forward at w )
```

Computed reference values used across scenarios:

| Expression | Value |
|------------|-------|
| `calculate(135.0, 5)` | `≈ 156.113` |
| `calculate(100.0, 8)` | `≈ 127.614` |
| `calculate(90.0, 8)`  | `≈ 114.852` |
| `weightForReps(156.113, 5)` | `≈ 135.0` |
| `weightForReps(156.113, 8)` | `≈ 122.33` |
| `repsForWeight(156.113, 135.0)` | `5` |
| `weightForReps(114.852, 5)` | `≈ 99.32` |
| `weightForReps(114.852, 8)` | `90.0` |
| `repsForWeight(114.852, 100.0)` | `5` |

Weight values are in pounds (lb). Where a scenario asserts a `Double`, the
production code rounds to the nearest `0.5 lb` plate increment; expected values
below are given to two decimals and assertions use `±0.5` tolerance unless the
value is integral.

---

## Test framework and run commands

| Scope | Command |
|-------|---------|
| Domain unit tests | `./gradlew :core:domain:test` |
| Data layer (Room) | `./gradlew :core:data:testDebugUnitTest` |
| Feature workout ViewModel | `./gradlew :feature:workout:testDebugUnitTest` |
| Feature program ViewModel | `./gradlew :feature:program:testDebugUnitTest` |
| Feature onboarding (enum display) | `./gradlew :feature:onboarding:testDebugUnitTest` |
| Static analysis (every slice) | `./gradlew detekt` (`maxIssues: 0`) |

Framework: JUnit 5 (`org.junit.jupiter.api`) + Turbine for `Flow`/`StateFlow` +
`MainDispatcherRule` (`@ExtendWith`). Fakes from `:core:testing`; MockK only on
third-party SDK boundaries with `@file:Suppress("ForbiddenMockKInTests")`. No
`verify()`/`coVerify` in `*ViewModelTest.kt` (Detekt
`ForbiddenVerifyInViewModelTests`).

---

## Scenario index

### Slice 1 — Snapshot + merge skeleton
- `S1-structure-projection-empty-overlay`
- `S1-snapshot-immutable`
- `S1-snapshot-compute-once`
- `S1-history-priority-n-minus-one-first`
- `S1-history-priority-earlier-sessions`
- `S1-history-priority-same-lift-other-workout`
- `S1-history-priority-empty-when-no-history`
- `S1-merge-empty-overlay-renders-snapshot-baseline`
- `S1-mutation-defense-recompute-on-add-set`

### Slice 2 — Double progression + live recommendation
- `S2-weight-for-reps-round-trip`
- `S2-reps-for-weight-round-trip`
- `S2-mutation-defense-weight-for-reps-not-identity`
- `S2-double-increment-when-top-of-range-at-rir`
- `S2-double-hold-otherwise`
- `S2-double-backoff-inherits-top-set-weight`
- `S2-rec-top-set-hold`
- `S2-rec-top-set-reduce`
- `S2-rec-backoff-hold`
- `S2-rec-backoff-reduce-to-floor`
- `S2-rec-null-when-no-performed-set`
- `S2-mutation-defense-always-hold`
- `S2-merge-logged-supersedes-recommendation`
- `S2-merge-recommendation-supersedes-snapshot-upcoming`

### Slice 3 — Dynamic set lifecycle (Standard)
- `S3-fatigue-per-lift-slope-used`
- `S3-fatigue-population-default-when-null`
- `S3-fatigue-rep-projection-monotonic-non-increasing`
- `S3-add-set-copies-weight-estimates-reps`
- `S3-delete-set-removes`
- `S3-undo-add-removes`
- `S3-undo-delete-restores`
- `S3-mutation-defense-snapshot-untouched-on-add-set`

### Slice 4 — Linear progression (Greyskull-style hybrid)
- `S4-last-trained-bucket-3-values`
- `S4-enum-converters-round-trip`
- `S4-old-5-values-no-longer-referenced`
- `S4-scheme-beginner-linear`
- `S4-scheme-non-beginner-within-three-months-double`
- `S4-scheme-non-beginner-more-than-three-months-linear`
- `S4-scheme-isolation-always-double`
- `S4-mutation-defense-scheme-ignores-iscompound`
- `S4-linear-increment-when-bottom-at-rir`
- `S4-linear-hold-when-rir-not-met`
- `S4-linear-2-stalls-10-percent-reset`
- `S4-linear-reset-count-resets-after-reset`
- `S4-linear-2-resets-transition-to-double`
- `S4-linear-missed-sessions-do-not-credit-stall`
- `S4-linear-per-lift-increment-override`
- `S4-linear-stall-strictness-absolute-vs-rir`

### Slice 5 — Myo-reps Type A + Type B
- `S5-myo-rep-type-a-rep-floor-generates-until-floor`
- `S5-myo-rep-type-a-block-ends-on-floor`
- `S5-myo-rep-type-b-rep-matching-generates-until-parity`
- `S5-myo-rep-type-b-block-ends-on-parity`
- `S5-myo-rep-only-set-valid`
- `S5-myo-rep-final-set-valid`
- `S5-myo-rep-mid-lift-invalid`
- `S5-myo-rep-mini-set-deletable-undoable`

### Slice 6 — Drop sets
- `S6-drop-set-follows-another-set-valid`
- `S6-drop-set-first-set-invalid`
- `S6-drop-set-fatigue-reduction-applied`
- `S6-drop-set-per-lift-slope-vs-population`

### Slice 7 — `:feature:workout` presentation skeleton
- `S7-no-navcontroller-in-feature`
- `S7-empty-overlay-renders-snapshot-baseline`
- `S7-log-set-updates-overlay-calls-log-set-use-case`
- `S7-add-delete-undo-mutate-overlay`
- `S7-recommendation-supersedes-snapshot-upcoming`
- `S7-merge-priority-logged-over-recommendation-over-snapshot`
- `S7-mutation-defense-no-verify`

### Slice 8 — EditWorkoutScreen per-lift scheme toggle
- `S8-toggle-compound-updates-state-marks-dirty`
- `S8-toggle-isolation-is-noop`
- `S8-toggle-appends-scheme-changed-event`
- `S8-mutation-defense-toggle-appends-no-event`
- `S8-mutation-defense-toggle-isolation-not-noop`

---

## Slice 1 — Snapshot + merge skeleton

### Scenario: S1-structure-projection-empty-overlay
```gherkin
Feature: Snapshot + merge skeleton
  Scenario: ProjectWorkoutStructureUseCase builds a structure projection with no load
    Given a Workout with one WorkoutLift containing a single Standard top set
      with slotId SetSlotId.Template(liftSetId=ls-1), isTopSet=true, repMin=4, repMax=6, rirGoal=2
    When ProjectWorkoutStructureUseCase.invoke is called with the workout and a liftNames map
    Then the returned WorkoutStructureProjection has lifts of size 1
    And the first WorkoutLiftProjection has slots of size 1
    And the first SetSlot has slotId SetSlotId.Template(LiftSetId("ls-1"))
    And the first SetSlot has setType SetType.Standard
    And the first SetSlot has isTopSet true
    And the first SetSlot has repMin 4 and repMax 6 and rirGoal 2
    And the SetSlot has no recommendedWeight field
```

### Scenario: S1-snapshot-immutable
```gherkin
  Scenario: ProgressionSnapshot is immutable for the session life
    Given a ProgressionSnapshot with bySlot containing one SlotRecommendation for slot ls-1
      with recommendedWeight Weight(135.0) and baselineExpectedReps 5
    Then the ProgressionSnapshot class is a Kotlin data class with all val properties
    And bySlot is of type Map<SetSlotId, SlotRecommendation>
```

### Scenario: S1-snapshot-compute-once
```gherkin
  Scenario: GenerateProgressionSnapshotUseCase computes the snapshot once and does not recompute on overlay mutation
    Given a FakeProgressionHistoryRepository seeded with one SlotHistoryEntry for slot ls-1 with loggedWeight 135.0 and reps 5
    And a SnapshotRequest for the workout containing slot ls-1
    When GenerateProgressionSnapshotUseCase.invoke is called once
    Then the result is Result.success(ProgressionSnapshot)
    And the snapshot bySlot[SetSlotId.Template(LiftSetId("ls-1"))].recommendedWeight is Weight(135.0)
    And the snapshot bySlot[SetSlotId.Template(LiftSetId("ls-1"))].baselineExpectedReps is 5
    When a WorkoutOverlay mutation (add set) is applied and invoke is NOT called again
    Then the held snapshot reference is unchanged (same bySlot values)
    And GenerateProgressionSnapshotUseCase.invoke was called exactly 1 time
```

### Scenario: S1-history-priority-n-minus-one-first
```gherkin
  Scenario: Historical-priority read returns n-1 session first
    Given a FakeProgressionHistoryRepository seeded for slot ls-1 with:
      | session | programId | workoutId | workoutLiftId | setIndex | completedAt     | weight |
      | s-n2    | p-1       | w-1       | wl-1          | 0        | 2026-06-21T10:00 | 130.0  |
      | s-n1    | p-1       | w-1       | wl-1          | 0        | 2026-06-28T10:00 | 135.0  |
    When ProgressionHistoryPort.historyForSlot is called with SlotHistoryRequest(programId=p-1, workoutId=w-1, workoutLiftId=wl-1, liftId=L-1, setIndex=0)
    Then the returned list has size 2
    And the first SlotHistoryEntry.session.id is TrainingSessionId("s-n1")
    And the first SlotHistoryEntry.loggedSet.weight is Weight(135.0)
```

### Scenario: S1-history-priority-earlier-sessions
```gherkin
  Scenario: Historical-priority read orders n-2, n-3 after n-1
    Given three same-workout sessions for slot ls-1 ordered by completedAt ascending (s-n3 oldest, s-n1 newest)
    When historyForSlot is called for that slot
    Then the returned list is ordered by completedAt descending
    And the entries are [s-n1, s-n2, s-n3] in that order
```

### Scenario: S1-history-priority-same-lift-other-workout
```gherkin
  Scenario: Same-lift-other-workout entries appear after same-workout entries
    Given two entries for slot ls-1: one from workout w-1 (same) and one from workout w-2 (other) for the same liftId L-1
    When historyForSlot is called with workoutId=w-1
    Then the same-workout entry (workoutId=w-1) precedes the other-workout entry (workoutId=w-2) in the list
```

### Scenario: S1-history-priority-empty-when-no-history
```gherkin
  Scenario: historyForSlot returns empty list when no history exists
    Given an unseeded FakeProgressionHistoryRepository
    When historyForSlot is called for any slot
    Then the returned list is empty
```

### Scenario: S1-merge-empty-overlay-renders-snapshot-baseline
```gherkin
  Scenario: Merge with empty overlay renders the snapshot baseline
    Given a WorkoutStructureProjection with one slot ls-1
    And a ProgressionSnapshot with bySlot[ls-1] = SlotRecommendation(recommendedWeight=Weight(135.0), baselineExpectedReps=5)
    And a WorkoutOverlay with empty loggedSets, empty recommendations, empty addedSlots, empty deletedSlotIds
    When MergeRenderedWorkoutUseCase.invoke is called with the three
    Then the returned RenderedWorkout has lifts of size 1
    And the first RenderedSet has recommendedWeight Weight(135.0)
    And the first RenderedSet has recommendedReps 5
    And the first RenderedSet has loggedValue null
```

### Scenario: S1-mutation-defense-recompute-on-add-set
```gherkin
  Scenario: Mutation defense — snapshot must not recompute when a set is added
    Given a ProgressionSnapshot computed once for slot ls-1 with recommendedWeight Weight(135.0)
    And a WorkoutOverlay with an AddSet mutation applied after slot ls-1
    When the merge is recomputed (NOT a new snapshot generation)
    Then the snapshot bySlot still contains exactly the original entries
    And the snapshot bySlot[ls-1].recommendedWeight is still Weight(135.0)
    # This scenario FAILS if a lazy implementation recomputes the snapshot on add-set:
    # a recomputed snapshot would either change recommendedWeight or grow bySlot size.
```

---

## Slice 2 — Double progression + live recommendation

### Scenario: S2-weight-for-reps-round-trip
```gherkin
Feature: Inverse e1RM
  Scenario: weightForReps inverts calculate within tolerance
    Given an estimatedOneRm of 156.113 (from calculate(135.0, 5))
    When EstimatedOneRm.weightForReps(156.113, 5) is called
    Then the result is 135.0 within ±0.5
    When EstimatedOneRm.weightForReps(156.113, 8) is called
    Then the result is 122.33 within ±0.5
    When EstimatedOneRm.weightForReps(127.614, 8) is called  # from calculate(100.0,8)
    Then the result is 100.0 within ±0.5
```

### Scenario: S2-reps-for-weight-round-trip
```gherkin
  Scenario: repsForWeight inverts calculate within tolerance
    Given an estimatedOneRm of 156.113
    When EstimatedOneRm.repsForWeight(156.113, 135.0) is called
    Then the result is 5
    When EstimatedOneRm.repsForWeight(114.852, 100.0) is called  # e1rm from calculate(90,8)
    Then the result is 5
```

### Scenario: S2-mutation-defense-weight-for-reps-not-identity
```gherkin
  Scenario: Mutation defense — weightForReps must NOT return the calculate result unmodified
    Given estimatedOneRm = 156.113 and targetReps = 8
    When EstimatedOneRm.weightForReps(156.113, 8) is called
    Then the result is 122.33 within ±0.5
    # And NOT 156.113 (an identity/buggy implementation that returns the e1rm unchanged fails).
    And the result is strictly less than 156.113
```

### Scenario: S2-double-increment-when-top-of-range-at-rir
```gherkin
Feature: Double progression engine
  Scenario: Double increments load when TOP of rep range met at RIR
    Given a SetSlot ls-1 with isTopSet=true, repMin=4, repMax=6, rirGoal=2
    And history where the last session logged reps=6 at rir=2 at weight 135.0
    When DoubleProgressionEngine.recommend is called
    Then the SlotRecommendation.recommendedWeight is greater than Weight(135.0)
    And the increment equals the configured per-lift increment (default 5.0 lb for lower-body)
```

### Scenario: S2-double-hold-otherwise
```gherkin
  Scenario: Double holds load when top of range NOT met at RIR
    Given a SetSlot ls-1 with isTopSet=true, repMin=4, repMax=6, rirGoal=2
    And history where the last session logged reps=5 at rir=1 at weight 135.0
    When DoubleProgressionEngine.recommend is called
    Then the SlotRecommendation.recommendedWeight is Weight(135.0)
```

### Scenario: S2-double-backoff-inherits-top-set-weight
```gherkin
  Scenario: Double backoff slot inherits the top-set weight
    Given a top-set slot ls-1 with recommendedWeight Weight(135.0)
    And a backoff slot ls-2 with isTopSet=false for the same lift
    When DoubleProgressionEngine.recommend is called for slot ls-2
    Then the SlotRecommendation.recommendedWeight is Weight(135.0)
    # backoff sets have no rep range — only a RIR target
    And the baselineExpectedReps is unset/derived (no repMin/repMax applied)
```

### Scenario: S2-rec-top-set-hold
```gherkin
Feature: Recommendation engine (overlay-only, D4)
  Scenario: Top-set recommendation Holds when projected reps >= repMin
    Given a lastPerformed LoggedSetValue with weight 135.0, reps 5, rir 2
    And a targetSlot top set with repMin 4, repMax 6, rirGoal 2
    And a currentWeight Weight(135.0) (snapshot baseline, read-only)
    # e1rm from performed = calculate(135.0, 5+2) = calculate(135.0, 7) ≈ 147.78
    # projected reps at 135.0 = repsForWeight(147.78, 135.0) ≈ 7; minus targetRir 2 = 5 >= repMin 4
    When RecommendationEngine.recommendNextSet is called
    Then the result is RecommendationOutcome.Hold(Weight(135.0))
```

### Scenario: S2-rec-top-set-reduce
```gherkin
  Scenario: Top-set recommendation Reduces when projected reps < repMin
    Given a lastPerformed LoggedSetValue with weight 135.0, reps 4, rir 0
    And a targetSlot top set with repMin 6, repMax 8, rirGoal 2
    And a currentWeight Weight(135.0)
    # e1rm = calculate(135.0, 4+0) = 135.0 * 1.0278 / (1.0278 - 0.0278*4) ≈ 151.385 (Brzycki, reps ≤ 10)
    # projected reps at 135.0 = repsForWeight(151.385,135.0) ≈ 4; minus rirGoal 2 = 2 < repMin 6
    When RecommendationEngine.recommendNextSet is called
    Then the result is RecommendationOutcome.Reduce
    And the Reduce weight equals EstimatedOneRm.weightForReps(e1rm, repMin=6)
    # i.e. weightForReps(151.385, 6) = 151.385 * (1.0278 - 0.0278*6) / 1.0278 ≈ 126.81 within ±0.5
    And the Reduce weight is 126.81 within ±0.5
```

### Scenario: S2-rec-backoff-hold
```gherkin
  Scenario: Backoff recommendation Holds when projected reps >= BACKOFF_REP_FLOOR at target RIR
    Given a lastPerformed LoggedSetValue with weight 100.0, reps 8, rir 2
    And a targetSlot backoff set with isTopSet=false, rirGoal 2 (no rep range)
    And a currentWeight Weight(100.0)
    # e1rm = calculate(100.0, 8+2) = calculate(100.0,10) ≈ 133.5
    # projected reps at 100.0 = repsForWeight(133.5, 100.0) ≈ 10; minus rirGoal 2 = 8 >= BACKOFF_REP_FLOOR 5
    When RecommendationEngine.recommendNextSet is called
    Then the result is RecommendationOutcome.Hold(Weight(100.0))
```

### Scenario: S2-rec-backoff-reduce-to-floor
```gherkin
  Scenario: Backoff recommendation Reduces to BACKOFF_REP_FLOOR when projected reps < 5
    Given a lastPerformed LoggedSetValue with weight 100.0, reps 5, rir 0
    And a targetSlot backoff set with isTopSet=false, rirGoal 2 (no rep range)
    And a currentWeight Weight(100.0)
    # e1rm = calculate(100.0, 5) ≈ 115.64
    # projected reps at 100.0 = repsForWeight(115.64, 100.0) ≈ 6; minus rirGoal 2 = 4 < BACKOFF_REP_FLOOR 5
    When RecommendationEngine.recommendNextSet is called
    Then the result is RecommendationOutcome.Reduce
    And the Reduce weight equals EstimatedOneRm.weightForReps(115.64, BACKOFF_REP_FLOOR=5)
    # weightForReps(115.64, 5) ≈ 99.97 within ±0.5
    And the Reduce weight is 99.97 within ±0.5
    # The targetReps source is BACKOFF_REP_FLOOR (5), NOT repMin (backoff has none).
```

### Scenario: S2-rec-null-when-no-performed-set
```gherkin
  Scenario: Recommendation returns null when no performed set exists for the lift
    Given no LoggedSetValue for the lift (the upcoming set is the first set of the session)
    When RecommendationEngine.recommendNextSet is called with lastPerformed absent
    Then the result is null
```

### Scenario: S2-mutation-defense-always-hold
```gherkin
  Scenario: Mutation defense — engine must NOT always return Hold
    Given a lastPerformed LoggedSetValue with weight 135.0, reps 4, rir 0
    And a targetSlot top set with repMin 6
    # projected reps at 135.0 minus rirGoal 2 = 2 < repMin 6 → must Reduce
    When RecommendationEngine.recommendNextSet is called
    Then the result is RecommendationOutcome.Reduce
    # A lazy implementation returning Hold(...) unconditionally fails this scenario.
```

### Scenario: S2-merge-logged-supersedes-recommendation
```gherkin
Feature: Merge priority (logged > recommendation > snapshot)
  Scenario: A logged value supersedes a recommendation for the same slot
    Given a WorkoutOverlay with loggedSets[ls-1] = LoggedSetValue(weight=130.0, reps=6, rir=2)
    And recommendations[ls-1] = Weight(135.0)
    And a snapshot bySlot[ls-1].recommendedWeight = Weight(140.0)
    When MergeRenderedWorkoutUseCase.invoke is called
    Then the RenderedSet for ls-1 has loggedValue with weight Weight(130.0)
    And the RenderedSet.recommendedWeight is Weight(140.0)  # snapshot baseline preserved for display
    # the recommendation is ignored for this slot because a logged value exists
```

### Scenario: S2-merge-recommendation-supersedes-snapshot-upcoming
```gherkin
  Scenario: A recommendation supersedes the snapshot for an upcoming unlogged set
    Given a WorkoutOverlay with empty loggedSets
    And recommendations[ls-2] = Weight(120.0)
    And a snapshot bySlot[ls-2].recommendedWeight = Weight(135.0)
    When MergeRenderedWorkoutUseCase.invoke is called
    Then the RenderedSet for ls-2 has loggedValue null
    And the RenderedSet.recommendedWeight is Weight(120.0)  # recommendation overlays snapshot
```

---

## Slice 3 — Dynamic set lifecycle (Standard)

### Scenario: S3-fatigue-per-lift-slope-used
```gherkin
Feature: Fatigue model (D5, Q2)
  Scenario: Per-lift slope overrides the population default
    Given a FatigueModel with POPULATION_FATIGUE_DECAY_SLOPE = -2.0 and a per-lift slope of -3.0 for lift L-1
    And a baselineOneRm of 150.0 and targetWeight Weight(100.0)
    When FatigueModel.expectedRepsForAddedSet is called with nextSetIndex=2 and perLiftSlope=-3.0
    Then the result uses slope -3.0 (NOT -2.0)
    # the per-lift value is observable: a steeper negative slope yields fewer reps
    And the result is strictly less than the result computed with slope -2.0
```

### Scenario: S3-fatigue-population-default-when-null
```gherkin
  Scenario: Population default is used when per-lift slope is null
    Given a FatigueModel with POPULATION_FATIGUE_DECAY_SLOPE = -2.0
    When expectedRepsForAddedSet is called with perLiftSlope=null
    Then the result equals the result computed with slope -2.0
```

### Scenario: S3-fatigue-rep-projection-monotonic-non-increasing
```gherkin
  Scenario: Projected reps are monotonically non-increasing across set index
    Given a baselineOneRm of 150.0, targetWeight Weight(100.0), slope -2.0
    When expectedRepsForAddedSet is called for nextSetIndex = 1, 2, 3, 4
    Then the four results satisfy result[1] >= result[2] >= result[3] >= result[4]
```

### Scenario: S3-add-set-copies-weight-estimates-reps
```gherkin
Feature: Dynamic set lifecycle (Standard)
  Scenario: AddSet copies the previous recommended weight and estimates reps via fatigue
    Given a WorkoutOverlay with no addedSlots
    And a snapshot bySlot[ls-1].recommendedWeight = Weight(135.0) and baselineExpectedReps = 5
    When StructuralMutation.AddSet(afterSlotId=ls-1, newSlot) is applied
    Then the overlay addedSlots has size 1
    And the new slot's recommendedWeight is Weight(135.0)  # copied from previous
    And the new slot's recommendedReps is computed by EstimateAddedSetRepsUseCase (an Int > 0)
```

### Scenario: S3-delete-set-removes
```gherkin
  Scenario: DeleteSet removes the slot from the projection
    Given a WorkoutOverlay with three template slots ls-1, ls-2, ls-3
    When StructuralMutation.DeleteSet(slotId=ls-2) is applied
    Then the overlay deletedSlotIds contains SetSlotId.Template(LiftSetId("ls-2"))
    And the structure projection no longer contains a slot for ls-2
```

### Scenario: S3-undo-add-removes
```gherkin
  Scenario: Undo of an AddSet removes the added slot
    Given a WorkoutOverlay after applying AddSet(afterSlotId=ls-1) producing addedSlots of size 1
    When WorkoutOverlay.undo() is called
    Then addedSlots is empty
    And undoStack is empty
```

### Scenario: S3-undo-delete-restores
```gherkin
  Scenario: Undo of a DeleteSet restores the deleted slot
    Given a WorkoutOverlay after applying DeleteSet(slotId=ls-2)
    When WorkoutOverlay.undo() is called
    Then deletedSlotIds no longer contains ls-2
    And the structure projection contains ls-2 again
```

### Scenario: S3-mutation-defense-snapshot-untouched-on-add-set
```gherkin
  Scenario: Mutation defense — add/delete must not touch the snapshot
    Given a ProgressionSnapshot with bySlot of size 1 (ls-1 → 135.0)
    When a StructuralMutation.AddSet is applied to the overlay
    Then the snapshot bySlot still has size 1
    And the snapshot bySlot[ls-1].recommendedWeight is still Weight(135.0)
    # A lazy implementation that mutates the snapshot on add-set fails: bySlot size or value changes.
```

---

## Slice 4 — Linear progression (Greyskull-style hybrid)

### Scenario: S4-last-trained-bucket-3-values
```gherkin
Feature: LastTrainedBucket enum collapse (D8)
  Scenario: LastTrainedBucket has exactly 3 values
    When LastTrainedBucket.entries is read
    Then the entries are [Never, WithinLastThreeMonths, MoreThanThreeMonthsAgo]
    And the size is 3
```

### Scenario: S4-enum-converters-round-trip
```gherkin
  Scenario: The 3 new values round-trip through EnumConverters
    When EnumConverters.fromLastTrainedBucket(LastTrainedBucket.WithinLastThreeMonths) is called
    Then the result is the string "WithinLastThreeMonths"
    When EnumConverters.toLastTrainedBucket("MoreThanThreeMonthsAgo") is called
    Then the result is LastTrainedBucket.MoreThanThreeMonthsAgo
    When EnumConverters.toLastTrainedBucket("WithinLastMonth") is called  # stale old name
    Then the result is null  # stale names decode to null (v1 reinstall policy)
```

### Scenario: S4-old-5-values-no-longer-referenced
```gherkin
  Scenario: No production or test source references the old 5 bucket names
    When the codebase is grepped for "WithinLastMonth", "OneToSixMonthsAgo", "SixToTwelveMonthsAgo", "MoreThanAYearAgo" in *.kt
    Then the only matches are inside comments documenting the migration or the toLastTrainedBucket stale-name decode test
    # (Production source and tests use the 3 new names exclusively.)
```

### Scenario: S4-scheme-beginner-linear
```gherkin
Feature: schemeForTrainingAge (compounds-only, beginner-or-layoff)
  Scenario: Beginner on a compound lift returns Linear
    When schemeForTrainingAge(trainingAge=TrainingAge.Beginner, lastTrained=LastTrainedBucket.WithinLastThreeMonths, isCompound=true) is called
    Then the result is ProgressionScheme.Linear
```

### Scenario: S4-scheme-non-beginner-within-three-months-double
```gherkin
  Scenario: Non-beginner who trained within 3 months returns Double
    When schemeForTrainingAge(trainingAge=TrainingAge.Intermediate, lastTrained=LastTrainedBucket.WithinLastThreeMonths, isCompound=true) is called
    Then the result is ProgressionScheme.Double
```

### Scenario: S4-scheme-non-beginner-more-than-three-months-linear
```gherkin
  Scenario: Non-beginner returning from a >3 month layoff returns Linear for compounds
    When schemeForTrainingAge(trainingAge=TrainingAge.LateIntermediate, lastTrained=LastTrainedBucket.MoreThanThreeMonthsAgo, isCompound=true) is called
    Then the result is ProgressionScheme.Linear
```

### Scenario: S4-scheme-isolation-always-double
```gherkin
  Scenario: Isolation lift returns Double regardless of training age or last-trained
    When schemeForTrainingAge(trainingAge=TrainingAge.Beginner, lastTrained=LastTrainedBucket.MoreThanThreeMonthsAgo, isCompound=false) is called
    Then the result is ProgressionScheme.Double
```

### Scenario: S4-mutation-defense-scheme-ignores-iscompound
```gherkin
  Scenario: Mutation defense — schemeForTrainingAge must consult isCompound
    When schemeForTrainingAge(trainingAge=TrainingAge.Beginner, lastTrained=LastTrainedBucket.MoreThanThreeMonthsAgo, isCompound=false) is called
    Then the result is ProgressionScheme.Double
    # A lazy implementation that ignores isCompound returns Linear here and fails.
```

### Scenario: S4-linear-increment-when-bottom-at-rir
```gherkin
Feature: LinearProgressionEngine (Greyskull-style hybrid, D8)
  Scenario: Linear increments when BOTTOM of rep range met at RIR
    Given a LinearProgressionConfig with lowerBodyIncrement Weight(5.0), stallThresholdSessions 2, resetFraction 0.10
    And a SetSlot ls-1 (compound lower-body) with repMin 5, repMax 8, rirGoal 2
    And history where the last session logged reps=5 (bottom of range) at rir=2 at weight 200.0
    When LinearProgressionEngine.recommend is called
    Then the SlotRecommendation.recommendedWeight is Weight(205.0)  # 200.0 + 5.0 increment
```

### Scenario: S4-linear-hold-when-rir-not-met
```gherkin
  Scenario: Linear holds load when bottom of range met but RIR NOT met
    Given a LinearProgressionConfig (defaults)
    And a SetSlot ls-1 with repMin 5, repMax 8, rirGoal 2
    And history where the last session logged reps=5 at rir=0 at weight 200.0
    When LinearProgressionEngine.recommend is called
    Then the SlotRecommendation.recommendedWeight is Weight(200.0)  # hold
```

### Scenario: S4-linear-2-stalls-10-percent-reset
```gherkin
  Scenario: 2 consecutive stalls trigger a 10% reset
    Given a LinearProgressionConfig with resetFraction 0.10, stallThresholdSessions 2
    And history showing 2 consecutive sessions where reps fell below repMin at the target RIR at weight 200.0
    When LinearProgressionEngine.diagnose is called
    Then LinearProgressionState.shouldReset is true
    And the next recommended weight is Weight(180.0)  # 200.0 * (1 - 0.10)
```

### Scenario: S4-linear-reset-count-resets-after-reset
```gherkin
  Scenario: Stall counter resets to 0 after a reset
    Given history showing 2 stalls (reset triggered) followed by a session at the reduced load meeting bottom-of-range-at-RIR
    When LinearProgressionEngine.diagnose is called
    Then LinearProgressionState.currentStallCount is 0
    And LinearProgressionState.currentResetCount is 1
```

### Scenario: S4-linear-2-resets-transition-to-double
```gherkin
  Scenario: 2 resets trigger auto-transition to Double (no user prompt)
    Given history showing 2 full reset cycles (2 resets) each followed by renewed stalls
    When LinearProgressionEngine.diagnose is called
    Then LinearProgressionState.shouldTransitionToDouble is true
    And a SchemeTransitionEvent is emitted with reason SchemeTransitionReason.ResetCyclesExhausted
    And the event has from=ProgressionScheme.Linear and to=ProgressionScheme.Double
    # No PromptSchemeTransition UI event is emitted — D8 locks auto-transition.
```

### Scenario: S4-linear-missed-sessions-do-not-credit-stall
```gherkin
  Scenario: Missed sessions do not increment the stall count
    Given history with 1 stall session, then a gap of 2 missed sessions, then a session meeting bottom-of-range-at-RIR
    When LinearProgressionEngine.diagnose is called
    Then LinearProgressionState.currentStallCount is 0  # the gap does not count as stalls
    And LinearProgressionState.shouldReset is false
```

### Scenario: S4-linear-per-lift-increment-override
```gherkin
  Scenario: Per-lift increment override is applied
    Given a LinearProgressionConfig with perLiftIncrementOverride[L-1] = Weight(2.5)
    And a SetSlot for lift L-1 (upper-body) meeting bottom-of-range-at-RIR at weight 100.0
    When LinearProgressionEngine.recommend is called
    Then the SlotRecommendation.recommendedWeight is Weight(102.5)  # 100.0 + 2.5 override, not the default upper-body increment
```

### Scenario: S4-linear-stall-strictness-absolute-vs-rir
```gherkin
  Scenario: StallStrictness.AbsoluteFailure vs RirBased diverge on a ground-out set
    Given a session logging reps=5 (bottom of range) at rir=0 (grinded) at weight 200.0
    When LinearProgressionEngine is configured with StallStrictness.RirBased
    Then diagnose reports the session as a NON-stall (RIR not met → hold, not stall)
    When LinearProgressionEngine is configured with StallStrictness.AbsoluteFailure
    Then diagnose reports the session as a stall (could not complete at any RIR beyond bottom)
    # The two configurations produce different stall counts for the same history.
```

---

## Slice 5 — Myo-reps Type A + Type B

### Scenario: S5-myo-rep-type-a-rep-floor-generates-until-floor
```gherkin
Feature: Myo-rep lifecycle (D6)
  Scenario: Type A (rep floor) generates a mini-set when the last mini-set did NOT hit the floor
    Given a MyoRepBlockState with kind=MyoRepKind.RepFloor, repFloor=5, lastMiniSetReps=4
    When MyoRepLifecycle.nextMiniSetRepFloor is called
    Then the result is a non-null SetSlot  # generate next mini-set
```

### Scenario: S5-myo-rep-type-a-block-ends-on-floor
```gherkin
  Scenario: Type A block ends when a mini-set hits the rep floor
    Given a MyoRepBlockState with kind=MyoRepKind.RepFloor, repFloor=5, lastMiniSetReps=5
    When MyoRepLifecycle.nextMiniSetRepFloor is called
    Then the result is null  # block complete
```

### Scenario: S5-myo-rep-type-b-rep-matching-generates-until-parity
```gherkin
  Scenario: Type B (rep matching) generates while cumulative mini-set reps < activation reps
    Given a MyoRepBlockState with kind=MyoRepKind.RepMatching, activationReps=12, cumulativeMiniSetReps=8
    When MyoRepLifecycle.nextMiniSetRepMatching is called
    Then the result is a non-null SetSlot
```

### Scenario: S5-myo-rep-type-b-block-ends-on-parity
```gherkin
  Scenario: Type B block ends when cumulative mini-set reps equal activation reps
    Given a MyoRepBlockState with kind=MyoRepKind.RepMatching, activationReps=12, cumulativeMiniSetReps=12
    When MyoRepLifecycle.nextMiniSetRepMatching is called
    Then the result is null
```

### Scenario: S5-myo-rep-only-set-valid
```gherkin
Feature: Myo-rep constraint (only-set or final-set, D6)
  Scenario: A Myo-rep block that is the only set of its lift is valid
    Given a WorkoutLiftProjection with a single slot of setType SetType.MyoRep
    When MyoRepConstraintValidator.invoke is called
    Then the result is MyoRepConstraintResult.Valid
```

### Scenario: S5-myo-rep-final-set-valid
```gherkin
  Scenario: A Myo-rep block as the final set (preceded by a top set) is valid
    Given a WorkoutLiftProjection with two slots: slot 0 is a Standard top set, slot 1 is MyoRep
    When MyoRepConstraintValidator.invoke is called
    Then the result is MyoRepConstraintResult.Valid
```

### Scenario: S5-myo-rep-mid-lift-invalid
```gherkin
  Scenario: A Myo-rep block in the middle of a lift is invalid
    Given a WorkoutLiftProjection with three slots: slot 0 Standard top set, slot 1 MyoRep, slot 2 Standard backoff
    When MyoRepConstraintValidator.invoke is called
    Then the result is MyoRepConstraintResult.Invalid
    And reasons contains MyoRepConstraintViolation.MyoRepBlockNotOnlyOrFinalSet
```

### Scenario: S5-myo-rep-mini-set-deletable-undoable
```gherkin
  Scenario: A generated Myo-rep mini-set is deletable and undoable (reuses Slice 3 undo)
    Given a WorkoutOverlay after applying StructuralMutation.GenerateMyoRepMiniSet
    When StructuralMutation.DeleteSet is applied to the generated mini-set
    Then the mini-set is removed from the structure projection
    When WorkoutOverlay.undo() is called
    Then the mini-set is restored
```

---

## Slice 6 — Drop sets

### Scenario: S6-drop-set-follows-another-set-valid
```gherkin
Feature: Drop set constraint (D7)
  Scenario: A drop set immediately following another set of the same lift is valid
    Given a WorkoutLiftProjection with two slots: slot 0 Standard, slot 1 DropSet
    When DropSetConstraintValidator.invoke is called
    Then the result is DropSetConstraintResult.Valid
```

### Scenario: S6-drop-set-first-set-invalid
```gherkin
  Scenario: A drop set as the first set of a lift is invalid
    Given a WorkoutLiftProjection with one slot of setType SetType.DropSet at position 0
    When DropSetConstraintValidator.invoke is called
    Then the result is DropSetConstraintResult.Invalid
    And reasons contains DropSetConstraintViolation.DropSetNotFollowingAnotherSet
```

### Scenario: S6-drop-set-fatigue-reduction-applied
```gherkin
Feature: Drop-set weight estimation (D7)
  Scenario: Drop-set recommended weight is the preceding set's weight reduced by the fatigue model
    Given a precedingWeight Weight(100.0) and a FakeFatigueModel that projects a 10% reduction at the next set index
    When EstimateDropSetWeightUseCase.invoke is called with nextSetIndex=1 and perLiftSlope=null
    Then the result is Weight(90.0)  # 100.0 reduced by population fatigue projection
```

### Scenario: S6-drop-set-per-lift-slope-vs-population
```gherkin
  Scenario: Per-lift fatigue slope produces a different reduction than the population default
    Given a FakeFatigueModel with population reduction 10% and a per-lift reduction of 15% for lift L-1
    When EstimateDropSetWeightUseCase.invoke is called with perLiftSlope=-3.0 (per-lift) for precedingWeight Weight(100.0)
    Then the result is Weight(85.0)  # 15% reduction, not 10%
```

---

## Slice 7 — `:feature:workout` presentation skeleton

### Scenario: S7-no-navcontroller-in-feature
```gherkin
Feature: :feature:workout presentation skeleton
  Scenario: WorkoutRoute exposes navigation via callbacks, not NavController
    When the source of feature/workout is grepped for "NavController"
    Then there are zero matches
    And WorkoutRoute has parameters onNavigateBack: () -> Unit and onNavigateToFinalize: (sessionId: String) -> Unit
```

### Scenario: S7-empty-overlay-renders-snapshot-baseline
```gherkin
  Scenario: With an empty overlay, the ViewModel renders the snapshot baseline
    Given a WorkoutViewModel with a FakeProgressionHistoryRepository seeded for slot ls-1 (135.0 × 5)
    And an empty WorkoutOverlay
    When startSession(workoutId) is called and uiState is observed via Turbine
    Then uiState reaches WorkoutUiState.Loaded
    And the first RenderedSetUi has recommendedWeightLabel "135.0" and recommendedRepsLabel "5"
    And loggedWeightLabel is null
```

### Scenario: S7-log-set-updates-overlay-calls-log-set-use-case
```gherkin
  Scenario: Logging a set updates the overlay and persists via LogSetUseCase
    Given a WorkoutViewModel in Loaded state for slot ls-1
    When logSet(slotId=ls-1, weight=Weight(135.0), reps=5, rir=2) is called
    Then the FakeTrainingLogRepository.insertSetCallCount is 1
    And the next uiState emission shows ls-1 with loggedWeightLabel "135.0" and loggedRepsLabel "5" and isComplete true
```

### Scenario: S7-add-delete-undo-mutate-overlay
```gherkin
  Scenario: addSet/deleteSet/undo mutate the overlay and re-render
    Given a WorkoutViewModel in Loaded state with one slot ls-1
    When addSet(afterSlotId=ls-1) is called
    Then the next uiState emission shows two sets for the lift
    When deleteSet(slotId=ls-1) is called
    Then the next uiState emission shows one set for the lift (ls-1 removed)
    When undo() is called
    Then the next uiState emission shows two sets again (ls-1 restored)
```

### Scenario: S7-recommendation-supersedes-snapshot-upcoming
```gherkin
  Scenario: For an upcoming unlogged set, the recommendation overlays the snapshot
    Given a WorkoutViewModel with a recommendation of Weight(120.0) for slot ls-2 (upcoming, unlogged)
    And a snapshot baseline of Weight(135.0) for ls-2
    When uiState is observed
    Then the RenderedSetUi for ls-2 has recommendedWeightLabel "120.0"  # recommendation
    And loggedWeightLabel is null
```

### Scenario: S7-merge-priority-logged-over-recommendation-over-snapshot
```gherkin
  Scenario: Merge priority is logged > recommendation > snapshot
    Given a WorkoutViewModel where slot ls-1 has a logged value 130.0, a recommendation 120.0, and a snapshot 135.0
    When uiState is observed
    Then the RenderedSetUi for ls-1 has loggedWeightLabel "130.0"
    And the RenderedSetUi for ls-1 has isComplete true
    # the logged value wins for the logged field; recommendation is ignored for this slot
```

### Scenario: S7-mutation-defense-no-verify
```gherkin
  Scenario: Mutation defense — ViewModel test must not use MockK verify
    When the source of WorkoutViewModelTest.kt is grepped for "verify(" and "coVerify("
    Then there are zero matches
    # State-based assertions on uiState only (UNIT_TESTING.md §1, Detekt ForbiddenVerifyInViewModelTests).
```

---

## Slice 8 — EditWorkoutScreen per-lift scheme toggle

### Scenario: S8-toggle-compound-updates-state-marks-dirty
```gherkin
Feature: EditWorkoutScreen per-lift scheme toggle (D11)
  Scenario: Toggling a compound lift's scheme updates state and marks the program dirty
    Given an EditWorkoutViewModel in Loaded state with a compound lift row (isCompound=true) whose progressionScheme is Linear
    When setProgressionScheme(workoutLiftId=wl-1, scheme=ProgressionScheme.Double) is called
    Then the EditLiftState for wl-1 has progressionScheme ProgressionScheme.Double
    And EditWorkoutUiState.Loaded.isDirty is true
```

### Scenario: S8-toggle-isolation-is-noop
```gherkin
  Scenario: setProgressionScheme for an isolation lift is a no-op (defensive)
    Given an EditWorkoutViewModel in Loaded state with an isolation lift row (isCompound=false) whose progressionScheme is Double
    When setProgressionScheme(workoutLiftId=wl-2, scheme=ProgressionScheme.Linear) is called
    Then the EditLiftState for wl-2 still has progressionScheme ProgressionScheme.Double
    And EditWorkoutUiState.Loaded.isDirty is false  # no change recorded
```

### Scenario: S8-toggle-appends-scheme-changed-event
```gherkin
  Scenario: Saving after a scheme toggle appends a ProgramChangeEvent with SchemeChanged
    Given an EditWorkoutViewModel where wl-1 was toggled from Linear to Double (isDirty=true)
    And a FakeProgramChangeEventRepository
    When saveWorkout() is called and completes successfully
    Then the FakeProgramChangeEventRepository has 1 appended event
    And the event has eventType ProgramChangeEventType.SCHEME_CHANGED
    And the event.payload is ProgramChangeEventPayload.SchemeChanged
    And the payload has workoutLiftId=WorkoutLiftId("wl-1") and previous=ProgressionScheme.Linear and current=ProgressionScheme.Double
```

### Scenario: S8-mutation-defense-toggle-appends-no-event
```gherkin
  Scenario: Mutation defense — saving without a scheme change appends NO SchemeChanged event
    Given an EditWorkoutViewModel where only a set's repMin was edited (no scheme change) (isDirty=true)
    And a FakeProgramChangeEventRepository
    When saveWorkout() is called and completes successfully
    Then the FakeProgramChangeEventRepository has 0 events with eventType SCHEME_CHANGED
    # A lazy implementation that always appends a SchemeChanged event on save fails this scenario.
```

### Scenario: S8-mutation-defense-toggle-isolation-not-noop
```gherkin
  Scenario: Mutation defense — setProgressionScheme must respect isCompound
    Given an EditWorkoutViewModel with a compound lift wl-1 (Linear) and an isolation lift wl-2 (Double)
    When setProgressionScheme(wl-2, Linear) is called  # isolation → must be no-op
    Then EditLiftState[wl-2].progressionScheme is ProgressionScheme.Double
    When setProgressionScheme(wl-1, Double) is called  # compound → must change
    Then EditLiftState[wl-1].progressionScheme is ProgressionScheme.Double
    # A lazy implementation that ignores isCompound would change wl-2 to Linear and fail this scenario.
```

---

## Mutation-defense summary

Each slice includes at least one scenario that fails against the most plausible
lazy or hardcoded implementation:

| Slice | Scenario | Lazy implementation it defeats |
|-------|----------|--------------------------------|
| 1 | `S1-mutation-defense-recompute-on-add-set` | Snapshot recomputes on add-set (breaks D3 invariant) |
| 2 | `S2-mutation-defense-weight-for-reps-not-identity` | `weightForReps` returns the e1RM unmodified |
| 2 | `S2-mutation-defense-always-hold` | Recommendation engine always returns `Hold` |
| 3 | `S3-mutation-defense-snapshot-untouched-on-add-set` | Add-set mutates the snapshot |
| 4 | `S4-mutation-defense-scheme-ignores-iscompound` | `schemeForTrainingAge` ignores `isCompound` |
| 5 | `S5-myo-rep-mid-lift-invalid` | Constraint validator always returns `Valid` |
| 6 | `S6-drop-set-first-set-invalid` | Drop-set validator always returns `Valid` |
| 7 | `S7-mutation-defense-no-verify` | ViewModel test uses MockK `verify` |
| 8 | `S8-mutation-defense-toggle-appends-no-event` | Save always appends a `SchemeChanged` event |
| 8 | `S8-mutation-defense-toggle-isolation-not-noop` | `setProgressionScheme` ignores `isCompound` |
