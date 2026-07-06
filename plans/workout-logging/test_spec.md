# Test Spec: workout-logging

Gherkin scenarios for TDD Red. Stable IDs (`WL-###`) map to implementation tasks in
`spec.md`. Tests use JUnit 5, fakes from `:core:testing`, Turbine for Flows, Robolectric
for Room contracts per `contracts/UNIT_TESTING.md`.

---

## Fixture conventions

| Symbol | Value |
|--------|-------|
| `programId` | `"prog-1"` |
| `workoutAId` | `"wkt-a"` (position 0) |
| `workoutBId` | `"wkt-b"` (position 1) |
| `workoutCId` | `"wkt-c"` (position 2) |
| `liftBenchId` | `"lift-bench"` |
| `liftSquatId` | `"lift-squat"` |
| `cycleNumber` | `1` |
| `benchHorizontalBitmask` | `32` (bit for `MovementPattern.HorizontalPress`) |
| `chestBitmask` | `1` (bit for `MuscleGroup.Chest`) |
| `tricepsBitmask` | `256` (bit for `MuscleGroup.Tricep`) |

Import sessions use `origin = IMPORT`, `status = COMPLETED`. Live sessions use
`origin = LIVE` unless noted.

---

## Slice 1 — Schema rename + extended columns

### WL-001 — Training session entity round-trips all columns

```gherkin
Feature: Training session persistence

  Scenario: WL-001 TrainingSessionEntity persists renamed table with extended columns
    Given a TrainingSessionEntity with:
      | id                    | ts-001           |
      | program_id            | prog-1           |
      | workout_id            | wkt-a            |
      | status                | COMPLETED        |
      | kind                  | PROGRAM_WORKOUT  |
      | origin                | IMPORT           |
      | cycle_number_snapshot | 1                |
      | weekday_snapshot      | Monday           |
      | started_at_epoch_millis | 1700000000000  |
      | ended_at_epoch_millis | 1700003600000    |
      | source_app            | liftlab          |
    When the entity is inserted and read back via TrainingSessionDao
    Then the read row equals the inserted row
    And the table name is "training_sessions"
```

### WL-002 — Logged set entity persists snapshot columns

```gherkin
  Scenario: WL-002 LoggedSetEntity persists lift snapshot columns
    Given a LoggedSetEntity with:
      | lift_name                   | Barbell Bench Press |
      | movement_patterns_bitmask   | 32                  |
      | primary_muscles_bitmask     | 1                   |
      | secondary_muscles_bitmask   | 256                 |
      | kinematic_profile           | Compound            |
      | muscle_length_bias          | MidRange            |
      | workout_lift_id             | null                |
      | progression_scheme          | null                |
    When the entity is inserted and read back via LoggedSetDao
    Then lift_name equals "Barbell Bench Press"
    And movement_patterns_bitmask equals 32
    And primary_muscles_bitmask equals 1
    And secondary_muscles_bitmask equals 256
    And workout_lift_id is null
```

### WL-003 — EnumConverters round-trip training enums

```gherkin
  Scenario: WL-003 EnumConverters round-trip TrainingSessionStatus Kind and Origin
    Given TrainingSessionStatus.IN_PROGRESS
    When encoded to storage and decoded
    Then the result equals TrainingSessionStatus.IN_PROGRESS
    And TrainingSessionKind.REST_DAY round-trips to "REST_DAY"
    And TrainingSessionOrigin.LIVE round-trips to "LIVE"
    And unknown status string "UNKNOWN" decodes to null
```

---

## Slice 2 — Import snapshots + baseline volume credit

### WL-004 — Import persist attaches lift-only snapshot

```gherkin
Feature: Import snapshot attachment

  Scenario: WL-004 PersistImportedHistoryUseCase writes LoggedLiftContext on import sets
    Given a resolved import set with lift_id "lift-bench" and exercise_name_raw "Bench Press"
    And catalog lift "lift-bench" has movement_patterns_bitmask 32 and primary_muscles_bitmask 1
    When PersistImportedHistoryUseCase persists the import batch
    Then the stored LoggedSet.liftContext.liftName equals "Barbell Bench Press"
    And liftContext.movement_patterns_bitmask equals 32
    And liftContext.primary_muscles_bitmask equals 1
```

### WL-005 — Import snapshot has null template fields

```gherkin
  Scenario: WL-005 Import LoggedLiftContext has null template progression fields
    Given an import set persisted via PersistImportedHistoryUseCase
    When the stored liftContext is read
    Then workout_lift_id is null
    And progression_scheme is null
    And set_type is null
    And rep_min is null
    And rep_max is null
    And rir_goal is null
```

### WL-006 — MuscleVolumeCredit uses snapshot bitmasks

```gherkin
Feature: Snapshot-based volume credit

  Scenario: WL-006 MuscleVolumeCredit credits muscles from LoggedLiftContext bitmasks
    Given a LoggedLiftContext with primary_muscles_bitmask 1 and secondary_muscles_bitmask 256
    And reps 8 and rir 2
    When accumulateSetCredits is called with the liftContext
    Then Chest credit equals 1.0
    And Triceps credit equals 0.5
```

### WL-007 — Catalog mutation does not change import volume credit (mutation defense)

```gherkin
  Scenario: WL-007 Renaming lift in catalog after import does not change credited volume
    Given import sets persisted with liftContext primary_muscles_bitmask 1 for Chest
    And volume baselines computed from snapshotted sets
    When the catalog lift is updated so primary_muscles is Back only
    And volume baselines are recomputed from the same stored sets without catalog join
    Then Chest baseline credit remains 1.0 per set
    And Back baseline credit equals 0.0
```

### WL-044 — Reconcile and analytics ignore stale catalog (mutation defense)

```gherkin
  Scenario: WL-044 ExtractResponseProfile volume path ignores mutated catalog when snapshots present
    Given persisted sets with liftContext movement_patterns_bitmask 32
    And catalog lift movement_patterns changed to VerticalPull only
    When ExtractResponseProfileUseCase runs stimulus extraction
    Then horizontal-push stimulus is credited from snapshot bitmask 32
    And no vertical-pull stimulus is credited from the mutated catalog
```

---

## Slice 3 — Incremental repository CRUD

### WL-008 — Insert session persists

```gherkin
Feature: Training log repository CRUD

  Scenario: WL-008 insertSession persists an IN_PROGRESS training session
    Given a TrainingSession with status IN_PROGRESS and id "ts-live-1"
    When insertSession is called
    Then getSession("ts-live-1") returns status IN_PROGRESS
```

### WL-009 — Update session changes status

```gherkin
  Scenario: WL-009 updateSession changes session status to COMPLETED
    Given an IN_PROGRESS session "ts-live-1"
    When updateSession sets status COMPLETED and endedAt
    Then getSession("ts-live-1").status equals COMPLETED
    And endedAt is not null
```

### WL-010 — Delete session cascades sets

```gherkin
  Scenario: WL-010 deleteSession removes session and cascaded sets
    Given session "ts-live-1" with one logged set "set-1"
    When deleteSession("ts-live-1") is called
    Then getSession("ts-live-1") is null
    And getSetsForSession("ts-live-1") is empty
```

### WL-011 — observeActiveSession emits IN_PROGRESS

```gherkin
  Scenario: WL-011 observeActiveSession emits the IN_PROGRESS session
    Given an IN_PROGRESS session "ts-active"
    When observeActiveSession is collected
    Then the emitted value id equals "ts-active"
```

### WL-012 — observeActiveSession emits null when none active

```gherkin
  Scenario: WL-012 observeActiveSession emits null when no IN_PROGRESS session exists
    Given only COMPLETED sessions exist
    When observeActiveSession is collected
    Then the emitted value is null
```

### WL-013 — getCompletedSessionsForCycle filters by cycle

```gherkin
  Scenario: WL-013 getCompletedSessionsForCycle returns sessions for program and cycle only
    Given COMPLETED session for program "prog-1" cycle_number_snapshot 1 and workout "wkt-a"
    And COMPLETED session for program "prog-1" cycle_number_snapshot 2
    When getCompletedSessionsForCycle("prog-1", 1) is called
    Then the result size equals 1
    And the session workout_id equals "wkt-a"
```

---

## Slice 4 — Cycle pointer + ledger resolver

### WL-014 — resolveNextWorkout returns first incomplete by position

```gherkin
Feature: Cycle progress resolver

  Scenario: WL-014 resolveNextWorkout returns first workout without COMPLETED session this cycle
    Given program with workouts wkt-a position 0 and wkt-b position 1
    And COMPLETED session for wkt-a in cycle 1
    When resolveNextWorkout is called
    Then next workout id equals "wkt-b"
```

### WL-015 — isWorkoutCompletedThisCycle true when session exists

```gherkin
  Scenario: WL-015 isWorkoutCompletedThisCycle is true when COMPLETED session exists
    Given COMPLETED PROGRAM_WORKOUT session for workout "wkt-a" cycle_number_snapshot 1
    When isWorkoutCompletedThisCycle is called for "wkt-a"
    Then the result is true
```

### WL-016 — isCycleComplete when all workouts done

```gherkin
  Scenario: WL-016 isCycleComplete is true when every template workout has COMPLETED session
    Given program with workouts wkt-a and wkt-b
    And COMPLETED sessions for wkt-a and wkt-b in cycle 1
    When isCycleComplete is called
    Then the result is true
```

### WL-017 — reconcileCyclePosition counts ledger sessions

```gherkin
  Scenario: WL-017 reconcileCyclePosition counts distinct completed workouts in ledger
    Given program cycle_position cache 0
    And COMPLETED sessions for wkt-a and wkt-b in cycle 1
    When reconcileCyclePosition is called
    Then the result equals 2
```

### WL-018 — Delete workout mid-cycle does not force re-skip

```gherkin
  Scenario: WL-018 Removing completed workout from template does not invalidate other completions
    Given COMPLETED sessions for wkt-a and wkt-b in cycle 1
    And workout wkt-b is removed from program template
    When resolveNextWorkout is called
    Then next workout is null
    And isWorkoutCompletedThisCycle for wkt-a remains true
```

### WL-019 — Add workout mid-cycle appears as pending

```gherkin
  Scenario: WL-019 New workout added mid-cycle appears as next pending
    Given COMPLETED session for wkt-a in cycle 1
    And new workout wkt-c position 2 added to template
    When resolveNextWorkout is called
    Then next workout id equals "wkt-c"
```

---

## Slice 5 — Session lifecycle

### WL-020 — Start program workout creates IN_PROGRESS

```gherkin
Feature: Training session lifecycle

  Scenario: WL-020 StartTrainingSessionUseCase ProgramWorkout creates IN_PROGRESS session
    Given no active session
    When StartTrainingSessionUseCase is invoked with ProgramWorkout "wkt-a"
    Then the result session status equals IN_PROGRESS
    And workout_id equals "wkt-a"
    And kind equals PROGRAM_WORKOUT
    And cycle_number_snapshot equals 1
```

### WL-021 — Start ad-hoc creates AD_HOC null workout_id

```gherkin
  Scenario: WL-021 StartTrainingSessionUseCase AdHocEmpty creates AD_HOC session
    Given no active session
    When StartTrainingSessionUseCase is invoked with AdHocEmpty
    Then the result session kind equals AD_HOC
    And workout_id is null
    And status equals IN_PROGRESS
```

### WL-022 — Second start throws conflict

```gherkin
  Scenario: WL-022 Second start throws ActiveTrainingSessionExistsException
    Given an IN_PROGRESS session "ts-active"
    When StartTrainingSessionUseCase is invoked again
    Then the result is failure ActiveTrainingSessionExistsException
    And activeSessionId equals "ts-active"
```

### WL-023 — Finalize sets COMPLETED and endedAt

```gherkin
  Scenario: WL-023 FinalizeTrainingSessionUseCase marks session COMPLETED
    Given an IN_PROGRESS PROGRAM_WORKOUT session "ts-live-1"
    When FinalizeTrainingSessionUseCase is invoked
    Then session status equals COMPLETED
    And endedAt is not null
```

### WL-024 — Finalize program workout advances cycle_position

```gherkin
  Scenario: WL-024 Finalize program workout increments cycle_position cache
    Given program cycle_position 0
    And IN_PROGRESS PROGRAM_WORKOUT session for wkt-a
    When FinalizeTrainingSessionUseCase is invoked
    Then program cycle_position equals 1
```

### WL-025 — Finalize ad-hoc does not advance cycle

```gherkin
  Scenario: WL-025 Finalize AD_HOC session does not change cycle pointer
    Given program cycle_position 0 and current_cycle_number 1
    And IN_PROGRESS AD_HOC session
    When FinalizeTrainingSessionUseCase is invoked
    Then program cycle_position equals 0
    And current_cycle_number equals 1
```

### WL-026 — Abandon deletes session and sets

```gherkin
  Scenario: WL-026 AbandonTrainingSessionUseCase deletes session and sets
    Given IN_PROGRESS session "ts-live-1" with one logged set
    When AbandonTrainingSessionUseCase is invoked
    Then getSession("ts-live-1") is null
    And getSetsForSession("ts-live-1") is empty
```

### WL-039 — Workout finalize does not emit change event

```gherkin
  Scenario: WL-039 Finalizing a single workout does not append program_change_events
    Given empty program_change_events
    When FinalizeTrainingSessionUseCase completes one PROGRAM_WORKOUT
    Then program_change_events count equals 0
```

---

## Slice 6 — Zero-set skip / rest

### WL-027 — Workout skip creates zero-set COMPLETED session

```gherkin
Feature: Skip and rest day sessions

  Scenario: WL-027 RecordWorkoutSkipUseCase creates COMPLETED zero-set session
    When RecordWorkoutSkipUseCase is invoked for workout "wkt-a"
    Then session status equals COMPLETED
    And workout_id equals "wkt-a"
    And kind equals PROGRAM_WORKOUT
    And getSetsForSession count equals 0
```

### WL-028 — Rest day skip creates REST_DAY session

```gherkin
  Scenario: WL-028 RecordRestDaySkipUseCase creates REST_DAY zero-set session
    When RecordRestDaySkipUseCase is invoked for weekday Monday
    Then session kind equals REST_DAY
    And workout_id is null
    And weekday_snapshot equals Monday
    And getSetsForSession count equals 0
```

### WL-029 — Skip advances cycle_position

```gherkin
  Scenario: WL-029 Workout skip increments cycle_position
    Given program cycle_position 0
    When RecordWorkoutSkipUseCase is invoked for "wkt-a"
    Then program cycle_position equals 1
```

### WL-030 — Full cycle wrap increments current_cycle_number

```gherkin
  Scenario: WL-030 Completing last workout in cycle wraps cycle number
    Given program with workouts wkt-a and wkt-b and cycle_position 1
    And COMPLETED session already exists for wkt-a in cycle 1
    When RecordWorkoutSkipUseCase is invoked for wkt-b
    Then program current_cycle_number equals 2
    And cycle_position equals 0
```

---

## Slice 7 — Live set logging

### WL-031 — LogSet allowed only IN_PROGRESS

```gherkin
Feature: Live set logging

  Scenario: WL-031 LogSetUseCase persists set for IN_PROGRESS session
    Given IN_PROGRESS PROGRAM_WORKOUT session "ts-live-1"
    When LogSetUseCase is invoked with valid LogSetCommand
    Then insertSet is called once
    And returned set sessionId equals "ts-live-1"
```

### WL-032 — LogSet rejects COMPLETED session

```gherkin
  Scenario: WL-032 LogSetUseCase fails when session is COMPLETED
    Given COMPLETED session "ts-done"
    When LogSetUseCase is invoked
    Then the result is failure
    And no set is inserted
```

### WL-033 — Live snapshot includes template fields

```gherkin
  Scenario: WL-033 Live LoggedLiftContext includes template progression fields
    Given IN_PROGRESS session linked to workout lift "wl-1" with progression_scheme DoubleProgression
    When LogSetUseCase logs a set with workoutLiftId "wl-1"
    Then liftContext.workout_lift_id equals "wl-1"
    And liftContext.progression_scheme equals DoubleProgression
```

### WL-034 — Live snapshot bitmasks match catalog at write time

```gherkin
  Scenario: WL-034 Live snapshot bitmasks match catalog at write time
    Given catalog lift "lift-bench" with primary_muscles_bitmask 1
    When LogSetUseCase logs a set for lift-bench
    Then liftContext.primary_muscles_bitmask equals 1
    And liftContext.liftName equals "Barbell Bench Press"
```

---

## Slice 8 — Program change events

### WL-035 — Swap emits LIFT_SWAPPED

```gherkin
Feature: Program change events

  Scenario: WL-035 SwapWorkoutLiftUseCase appends LIFT_SWAPPED event
    When SwapWorkoutLiftUseCase swaps workout lift from lift-bench to lift-squat
    Then one event with eventType LIFT_SWAPPED is appended
    And payload previousLiftId equals "lift-bench"
    And payload newLiftId equals "lift-squat"
```

### WL-036 — SaveWorkout emits WORKOUT_EDITED

```gherkin
  Scenario: WL-036 SaveWorkoutUseCase appends WORKOUT_EDITED event
    When SaveWorkoutUseCase saves workout "wkt-a"
    Then one event with eventType WORKOUT_EDITED is appended
    And payload workoutId equals "wkt-a"
```

### WL-037 — GenerateProgram emits PROGRAM_REGENERATED

```gherkin
  Scenario: WL-037 GenerateProgramUseCase appends PROGRAM_REGENERATED event
    When GenerateProgramUseCase saves a new program
    Then one event with eventType PROGRAM_REGENERATED is appended
```

### WL-038 — Cycle wrap emits CYCLE_COMPLETED

```gherkin
  Scenario: WL-038 Cycle wrap appends CYCLE_COMPLETED event
    Given final workout in cycle 1 is being completed
    When cycle wrap occurs
    Then one event with eventType CYCLE_COMPLETED is appended
    And payload completedCycleNumber equals 1
```

### WL-040 — Events are append-only

```gherkin
  Scenario: WL-040 Program change events are append-only
    Given existing LIFT_SWAPPED event "evt-1"
    When SaveWorkoutUseCase saves a workout
    Then getEventsForProgram returns 2 events
    And the first event id equals "evt-1"
```

---

## Slice 9 — Response analytics

### WL-041 — StimulusSeriesBuilder uses snapshot

```gherkin
Feature: Response analytics snapshot path

  Scenario: WL-041 StimulusSeriesBuilder credits volume from LoggedLiftContext without Lift join
    Given a logged set with liftContext primary_muscles_bitmask 1
    And no Lift object supplied for that set
    When StimulusSeriesBuilder accumulates credits
    Then Chest weekly credit equals 1.0
```

### WL-042 — ExtractResponseProfile without catalog for volume

```gherkin
  Scenario: WL-042 ExtractResponseProfileUseCase computes volume baselines without live Lift list
    Given persisted import sets with non-zero snapshot bitmasks
    And lifts list is empty
    When ExtractResponseProfileUseCase extracts response profile
    Then volume baselines are non-empty
    And Chest baseline count is greater than 0
```

### WL-043 — LiftEffectivenessAnalyzer uses liftId where appropriate

```gherkin
  Scenario: WL-043 LiftEffectivenessAnalyzer filters by liftId using snapshot movement pattern
    Given sets with liftContext movement_patterns_bitmask 32 and liftId lift-bench
    When effectiveness is computed for horizontal push pattern
    Then lift-bench effectiveness score is greater than 0
```

---

## Scenario index

| ID | Slice | Summary |
|----|-------|---------|
| WL-001 | 1 | Session entity round-trip |
| WL-002 | 1 | Set snapshot columns |
| WL-003 | 1 | Enum converter round-trip |
| WL-004 | 2 | Import snapshot attachment |
| WL-005 | 2 | Import null template fields |
| WL-006 | 2 | Snapshot volume credit |
| WL-007 | 2 | Mutation defense — catalog rename |
| WL-008 | 3 | insertSession |
| WL-009 | 3 | updateSession |
| WL-010 | 3 | deleteSession cascade |
| WL-011 | 3 | observeActiveSession emits |
| WL-012 | 3 | observeActiveSession null |
| WL-013 | 3 | getCompletedSessionsForCycle |
| WL-014 | 4 | resolveNextWorkout |
| WL-015 | 4 | isWorkoutCompletedThisCycle |
| WL-016 | 4 | isCycleComplete |
| WL-017 | 4 | reconcileCyclePosition |
| WL-018 | 4 | Mid-cycle delete workout |
| WL-019 | 4 | Mid-cycle add workout |
| WL-020 | 5 | Start program workout |
| WL-021 | 5 | Start ad-hoc |
| WL-022 | 5 | Block second active |
| WL-023 | 5 | Finalize COMPLETED |
| WL-024 | 5 | Finalize advances pointer |
| WL-025 | 5 | Ad-hoc finalize no advance |
| WL-026 | 5 | Abandon cascade |
| WL-027 | 6 | Workout skip zero-set |
| WL-028 | 6 | Rest day skip |
| WL-029 | 6 | Skip advances pointer |
| WL-030 | 6 | Cycle wrap |
| WL-031 | 7 | LogSet IN_PROGRESS |
| WL-032 | 7 | LogSet rejects COMPLETED |
| WL-033 | 7 | Live template snapshot |
| WL-034 | 7 | Live bitmask snapshot |
| WL-035 | 8 | LIFT_SWAPPED event |
| WL-036 | 8 | WORKOUT_EDITED event |
| WL-037 | 8 | PROGRAM_REGENERATED event |
| WL-038 | 8 | CYCLE_COMPLETED event |
| WL-039 | 5/8 | No event on single finalize |
| WL-040 | 8 | Append-only events |
| WL-041 | 9 | Stimulus from snapshot |
| WL-042 | 9 | Extract without catalog |
| WL-043 | 9 | Effectiveness liftId path |
| WL-044 | 2/4/9 | Mutation defense — analytics ignore catalog |

**Total scenarios: 44**

---

## Self-review

- [x] One scenario per behavior including edge cases and error paths.
- [x] Mutation-defense scenarios: WL-007, WL-044.
- [x] Literal expected values in Then clauses.
- [x] Every scenario ID referenced from `spec.md` tasks.
- [x] No placeholders (`TBD`, `TODO`, `similar to`).
