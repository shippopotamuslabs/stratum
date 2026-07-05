# Research: workout-logging

Objective codebase map for persistence and domain foundation prerequisites. No design
recommendations.

---

## Research Questions

### Programs screen / cycle completion UI

1. How does the Programs screen wire workout completion, skip/rest-day, start-next-workout, and
   start-empty-workout callbacks from navigation into ViewModels or use cases?
2. What presentation fields represent workout or cycle completion on the Programs screen, and
   where are they populated?
3. Which symbols compute “next workout” selection for UI expansion, and is that selection
   connected to navigation or persistence?
4. Which module owns the Programs screen ViewModel, route, UI state types, and domain→presentation
   mapper?

### Program template persistence

5. What Room entities, tables, and FK delete rules persist the program template hierarchy?
6. What domain types mirror the template hierarchy, and do they include cycle-progress fields?
7. Which DAOs and repository methods read/write program templates, and what is the save
   semantics (replace vs patch)?
8. How does `buildWeeklySchedule` relate program templates to the weekly schedule shown on the
   Programs screen?

### Import / training history persistence

9. What tables and columns persist imported sessions and logged sets?
10. What is the `TrainingHistoryRepository` port API, and which Room implementation backs it?
11. How do import mappers translate `HistoricalSession` / `LoggedSet` domain models to entities?
12. What use cases write training history today, and do they support incremental writes or only
    bulk replace?
13. Is there a separate live-logging persistence path, or does all history flow through the import
    tables?

### Analytics lift-metadata consumption

14. Which domain analytics components consume logged sets together with live `Lift` catalog data?
15. For each such component, what lift metadata fields are read and how are they resolved
    (direct fields vs derived policies)?
16. What inputs does `ResponseExtractionInput` carry, and where is the lift list loaded for
    response-profile extraction?
17. Does `TrainingInsightsExtractor` join sets to the current lift catalog for muscle volume
    baselines?

### LiftEntity and bitmask encoding

18. What fields does `LiftEntity` persist?
19. How are movement-pattern and muscle bitmasks encoded on write and decoded on read?
20. Which other non-bitmask lift metadata fields exist on `LiftEntity` that analytics might need
    at set-write time?

### EnumConverters and schema contract tests

21. Which types does `EnumConverters` persist for import/history and program entities?
22. What schema contract tests guard v1 database shape and converter round-trips?
23. What is the current `StratumDatabase` version and entity set?

### Program mutation and change history

24. What use cases mutate program templates (swap lift, edit workout, regenerate), and how do
    they persist changes?
25. Does the codebase contain any append-only program change event log (entity, table, or
    repository)?

### Test patterns and commands

26. What shared fakes in `:core:testing` cover program and training-history ports?
27. What test framework, patterns, and Gradle commands apply to domain, data, and feature tests
    in this scope?

### External reference (LiftLab)

28. What tables does LiftLab use for in-progress vs finalized set logging, and what lift metadata
    is snapshotted on history rows?

---

## Findings

### 1. Programs screen callback wiring is mostly no-op at the route level

**Route and navigation**

- `ProgramsRoute` (`feature/program/src/main/kotlin/com/stratum/feature/program/ProgramsRoute.kt`)
  collects `ProgramsViewModel.programsUiState` and passes through `ProgramsScreenCallbacks` and
  `onSwapLiftClick` unchanged; it does not bind completion or start-workout actions to a
  ViewModel.
- `MainTabShell` (`app/src/main/java/com/stratum/navigation/MainTabShell.kt:92–101`) supplies only
  `onKeepProgramClick`, `onDiscardProgramClick`, `onEditWorkoutClick`, and `onSwapLiftClick` via
  `onNavigateToSwap`. It does **not** override `onStartNextWorkoutClick`,
  `onStartEmptyWorkoutClick`, `onStartWorkoutClick`, or `onSkipWorkoutCheckedChange`, so those
  retain default empty lambdas from `ProgramsScreenCallbacks`
  (`feature/program/src/main/kotlin/com/stratum/feature/program/ui/ProgramsScreenCallbacks.kt:7–17`).

**UI surfaces**

- `ProgramsScreen` renders “Start next workout” and “Start empty workout” buttons calling
  `callbacks.onStartNextWorkoutClick` / `onStartEmptyWorkoutClick`
  (`feature/program/src/main/kotlin/com/stratum/feature/program/ui/ProgramsScreen.kt:179–187`).
- Workout cards expose a checkbox bound to `workout.isCompleted` and
  `onSkipWorkoutCheckedChange(workout.name, checked)`
  (`feature/program/src/main/kotlin/com/stratum/feature/program/ui/ProgramsScreen.kt:379–381`).
- Rest-day rows call `onSkipWorkoutCheckedChange(restDayLabel, checked)`
  (`feature/program/src/main/kotlin/com/stratum/feature/program/ui/ProgramsScreen.kt:242–245`).

**Verified fact:** No navigation target or use case is wired for start-workout, skip, or
completion actions from the Programs tab today.

### 2. Workout completion is a presentation default, not persisted state

- `WorkoutViewState.isCompleted` defaults to `false`
  (`feature/program/src/main/kotlin/com/stratum/feature/program/ui/ProgramsUiState.kt:44`).
- `ProgramsUiStateMapper` maps `Workout.toViewState` without setting `isCompleted`; only
  `workoutId`, `name`, `weekdayLabel`, `position`, and `lifts` are assigned
  (`feature/program/src/main/kotlin/com/stratum/feature/program/ProgramsUiStateMapper.kt:106–116`).
- **Verified fact:** `isCompleted` is always `false` in production mapping. Checkbox UI is
  present but state does not change from domain data.
- `ProgramsViewModel` only combines `ObserveProgramUseCase`, `ObserveLiftCatalogUseCase`, and
  `ComputeProgramMuscleVolumeUseCase` into `programsUiState`
  (`feature/program/src/main/kotlin/com/stratum/feature/program/ProgramsViewModel.kt:34–51`). It
  exposes no methods for completion, skip, or session start.
- **Inference (from code absence):** Cycle completion progress is not tracked in ViewModel or
  domain types (see Finding 6).

### 3. Next-workout selection affects UI expansion only

- `findNextWorkoutId` returns the first `ScheduleItemState.Workout` whose
  `workout.isCompleted` is false
  (`feature/program/src/main/kotlin/com/stratum/feature/program/ui/NextWorkoutSelection.kt:7–12`).
- `LoadedProgramContent` uses it to set `expandedWorkoutId` for card expansion
  (`feature/program/src/main/kotlin/com/stratum/feature/program/ui/ProgramsScreen.kt:230–236`).
- Unit tests cover skip-rest-day and all-complete cases
  (`feature/program/src/test/kotlin/com/stratum/feature/program/ui/NextWorkoutSelectionTest.kt`).
- **Verified fact:** `findNextWorkoutId` is not passed to navigation callbacks or persistence.

### 4. Programs feature module ownership

| Concern | Location |
|---------|----------|
| ViewModel | `feature/program/.../ProgramsViewModel.kt` |
| Route | `feature/program/.../ProgramsRoute.kt` |
| UI state | `feature/program/.../ui/ProgramsUiState.kt` |
| Callbacks bundle | `feature/program/.../ui/ProgramsScreenCallbacks.kt` |
| Domain→UI mapper | `feature/program/.../ProgramsUiStateMapper.kt` |
| Screen composable | `feature/program/.../ui/ProgramsScreen.kt` |
| ViewModel tests | `feature/program/src/test/.../ProgramsViewModelTest.kt` |

### 5. Program template Room schema

Four normalized tables in `core/data/src/main/kotlin/com/stratum/core/data/db/ProgramEntities.kt`:

| Table | Entity | Notable columns | FK on delete |
|-------|--------|-----------------|--------------|
| `programs` | `ProgramEntity` | `id`, `name`, `created_at`, `cycle_length`, `training_weekdays` | — |
| `workouts` | `WorkoutEntity` | `program_id`, `name`, `weekday`, `position` | CASCADE from `programs` |
| `workout_lifts` | `WorkoutLiftEntity` | `workout_id`, `lift_id`, `progression_scheme`, `position`, superset fields | CASCADE from `workouts`; RESTRICT on `lifts` |
| `sets` | `LiftSetEntity` | `workout_lift_id`, `set_type`, rep/rir/top-set/rest fields | CASCADE from `workout_lifts` |

- Entity KDoc states templates are for generation (OS-7), distinct from logged-set hierarchy
  (`ProgramEntities.kt:19–22`).
- No completion, skip, or cycle-position columns exist on template rows.

### 6. Domain program model has cycle length but no progress pointer

`core/domain/src/main/kotlin/com/stratum/domain/program/Program.kt`:

- `Program` fields: `id`, `name`, `createdAt`, `cycleLength`, `trainingWeekdays`, `workouts`.
- KDoc: template hierarchy, not logged sets (`Program.kt:15–21`).
- `Workout`, `WorkoutLift`, `LiftSet` carry template prescription fields only; no completion or
  cycle-progress properties.
- Grep across `*.kt` finds no `current_cycle`, `cycle_position`, or persisted `isCompleted` outside
  presentation `WorkoutViewState`.

### 7. Program repository save semantics

`RoomProgramRepository` (`core/data/src/main/kotlin/com/stratum/core/data/repository/RoomProgramRepository.kt`):

- `saveProgram`: transaction deletes all rows in `sets`, `workout_lifts`, `workouts`, `programs`,
  then inserts the full hierarchy (`:40–58`).
- `observeProgram` / `getProgram`: read root program row, rejoin children in one read transaction
  (`:33–36`, `:62–69`).
- `ProgramDao` exposes single-program `get`/`observe` with `LIMIT 1`
  (`core/data/src/main/kotlin/com/stratum/core/data/db/ProgramDaos.kt:18–22`).

Domain port: `core/domain/src/main/kotlin/com/stratum/domain/program/ProgramRepository.kt` (not
re-read; consumed by `ObserveProgramUseCase`, `SaveWorkoutUseCase`, `SwapWorkoutLiftUseCase`).

### 8. Weekly schedule construction

- `buildWeeklySchedule(program)` in
  `core/domain/src/main/kotlin/com/stratum/domain/program/ProgramSchedule.kt:132` produces
  `List<ScheduleDayItem>` (workout days and rest days from `trainingWeekdays`).
- `ProgramsUiStateMapper` calls `buildWeeklySchedule(this)` when mapping a loaded program
  (`ProgramsUiStateMapper.kt:36`).
- `Program.cycleLength` is stored on `ProgramEntity` but is not used by
  `ProgramsUiStateMapper` or `findNextWorkoutId` in the traced paths.

### 9. Import history tables and columns

`core/data/src/main/kotlin/com/stratum/core/data/db/ImportEntities.kt`:

**`historical_sessions`** (`HistoricalSessionEntity`):

- `id` (PK), `startedAtEpochMillis`, `endedAtEpochMillis?`, `sourceApp?`

**`logged_sets`** (`LoggedSetEntity`):

- `id` (PK), `sessionId` → `historical_sessions.id` (CASCADE delete)
- `exerciseNameRaw`, `liftId?`, `setIndex`, `weightAmount`, `weightUnit`, `reps`, `rir?`,
  `completedAtEpochMillis`
- Indexes on `sessionId`, `completedAtEpochMillis` (`ImportEntities.kt:44–47`)

**Related import analytics tables (same file):** `import_jobs`, `lift_training_insights`,
`superset_affinities`, `muscle_volume_baselines`, `import_analysis_metadata`.

**Verified fact:** `LoggedSetEntity` stores `liftId` and `exerciseNameRaw` only; no snapshotted
movement pattern, muscle bitmasks, kinematic profile, or muscle-length bias columns.

### 10. TrainingHistoryRepository API and Room implementation

Port (`core/domain/src/main/kotlin/com/stratum/domain/importing/ImportPorts.kt:151–159`):

- `replaceAll(sessions, sets)`
- `getAllSessions()`, `getAllSets()`
- `getSetsInWindow(windowStart, windowEnd)`

`RoomTrainingHistoryRepository`
(`core/data/src/main/kotlin/com/stratum/core/data/repository/RoomImportRepositories.kt:63–90`):

- `replaceAll`: transaction `deleteAll` on sessions and sets, then bulk insert.
- No `insert`/`update`/`delete` per session or set on the port.

### 11. Import mappers

`core/data/src/main/kotlin/com/stratum/core/data/mapper/ImportMapper.kt`:

- `HistoricalSessionEntity` ↔ `HistoricalSession` (epoch millis ↔ `Instant`)
- `LoggedSetEntity` ↔ `LoggedSet` (`Weight` amount/unit split on entity)
- Additional mappers for insights, supersets, baselines, metadata CSV fields.

Domain models (`core/domain/src/main/kotlin/com/stratum/domain/importing/ImportModels.kt:22–39`):

- `HistoricalSession`: `id`, `startedAt`, `endedAt?`, `sourceApp?` — no status field.
- `LoggedSet`: `id`, `sessionId`, `exerciseNameRaw`, `liftId?`, `setIndex`, `weight`, `reps`,
  `rir?`, `completedAt` — no embedded lift metadata snapshot.

### 12. History write paths today

**`PersistImportedHistoryUseCase`**
(`core/domain/src/main/kotlin/com/stratum/domain/importing/ImportUseCases.kt:421–471`):

1. Sanitize sessions/sets
2. `resolveImportLifts` → resolved sets with `liftId`
3. `trainingHistoryRepository.replaceAll(sessions, outcome.resolvedSets)`
4. `extractTrainingInsights` then `extractResponseProfile`
5. On insight/response failure, rolls back history via `replaceAll(emptyList(), emptyList())`

**`MergeImportedHistoryUseCase`**
(`core/domain/src/main/kotlin/com/stratum/domain/importing/MergeImportedHistoryUseCase.kt`):
namespaces session/set ids across multi-file import chunks; does not persist.

**Orchestration:** `RunFullHistoricalImportUseCase` in `:core:inference` chains merge + persist.

**Verified fact:** All traced history writes use bulk `replaceAll`; no incremental append API.

### 13. No live-logging persistence path in Stratum

- `settings.gradle.kts` includes `:feature:onboarding` and `:feature:program` only; no
  `:feature:workout` module (`settings.gradle.kts:38–39`).
- `StratumDatabase` entities list has no in-progress session or draft-set tables
  (`core/data/src/main/kotlin/com/stratum/core/data/db/StratumDatabase.kt:8–37`).
- **Verified fact:** Training history in Stratum today is import-only via `historical_sessions` /
  `logged_sets`.

### 14–17. Analytics pipelines join sets to live `Lift` catalog

**Data loading for response profile**

`ExtractResponseProfileUseCase` (`core/domain/src/main/kotlin/com/stratum/domain/response/ExtractResponseProfileUseCase.kt:30–52`):

- Loads all sessions and sets from `TrainingHistoryRepository`
- Loads current lifts via `liftRepository.observeLifts().first()`
- Builds `ResponseExtractionInput(sessions, sets, lifts, provenLiftIds, …)`

**`ResponseExtractionInput` fields**
(`core/domain/src/main/kotlin/com/stratum/domain/response/ResponseModels.kt:154–165`):
`sessions`, `sets`, `lifts`, `provenLiftIds`, `specializeMuscles`, `liftInsights`,
`volumeBaselines`, analysis window, `activeExperiments`.

**Components that resolve `liftId` → `Lift` at analysis time**

| Component | File | Lift metadata used |
|-----------|------|-------------------|
| `MuscleVolumeCredit` | `core/domain/.../MuscleVolumeCredit.kt:19–38` | Full `Lift`: movement pattern via `ImportStubLiftGuesser.effectiveMovementPattern`, primary via `effectivePrimaryMuscles`, secondary via `CustomLiftMusclePolicy.effectiveSecondaryMuscles` |
| `StimulusSeriesBuilder` | `core/domain/.../StimulusSeriesBuilder.kt:13–21` | `lifts.associateBy { it.id }`; skips sets when lift missing; calls `MuscleVolumeCredit.accumulateSetCredits` |
| `MuscleWeeklyStimulusBuilder` | `core/domain/.../modeling/MuscleWeeklyStimulusBuilder.kt:21–48` | Same lift lookup and credit policy as `MuscleVolumeCredit` |
| `LiftEffectivenessAnalyzer` | `core/domain/.../LiftEffectivenessAnalyzer.kt:15` | Filters `input.lifts` by `movementPatterns`; scores per `liftId` on clean sets |
| `DefaultResponseObservationExtractor` | `core/domain/.../ResponseObservationExtractor.kt:29–39` | Iterates `input.lifts.flatMap { it.movementPatterns }` for effectiveness |
| `TrainingInsightsExtractor.computeVolumeBaselines` | `core/domain/.../TrainingInsightsExtractor.kt:184–207` | `liftById[set.liftId]`; credits `lift.primaryMuscles + lift.secondaryMuscles` per set (1 count each, not `MuscleVolumeCredit` weighting) |

**`TrainingSignalCleaner`** (`core/domain/.../TrainingSignalCleaner.kt:11–48`):

- Operates on `LoggedSet` weight/reps/rir only; does not read `Lift` metadata.

**Verified fact:** Muscle volume and effectiveness analytics traced above join `LoggedSet.liftId`
to the **current** `Lift` list supplied in extraction input, not to fields on the set row.

### 18–20. LiftEntity fields and bitmask encoding

**`LiftEntity` columns** (`core/data/src/main/kotlin/com/stratum/core/data/db/LiftEntity.kt:33–49`):

- `id`, `name`
- `movementPatternsBitmask`, `primaryMusclesBitmask`, `secondaryMusclesBitmask`
- `allowWeightChangeBetweenSets`, `presetTags`, `equipment?`, `isCustom`, `enabled`
- `description?`, `imageUrisCsv?`, `muscleLengthBias`, `mainstream`, `kinematicProfile`

Entity KDoc documents bitmask SQL filtering (`LiftEntity.kt:23–26`).

**Encoding/decoding** (`core/data/src/main/kotlin/com/stratum/core/data/mapper/LiftMapper.kt`):

- Bit position: `1 shl enum.ordinal` (`LiftMapper.kt:96–105`)
- Decode: `MovementPattern.entries.filter { bitmask and (1 shl ordinal) != 0 }` (same for
  `MuscleGroup`)
- `presetTags`: delimiter-bounded CSV of `GymPreset.name` (`LiftMapper.kt:112–125`)
- Increment tiers stored in child `IncrementTierEntity` rows (see `LiftEntity` KDoc)

### 21–23. EnumConverters and schema tests

**`EnumConverters`** (`core/data/src/main/kotlin/com/stratum/core/data/db/EnumConverters.kt`):

- History/import ids: `HistoricalSessionId`, `LoggedSetId`, `ImportJobId` (`:149–161`)
- Program ids/enums: `ProgramId`, `WorkoutId`, `WorkoutLiftId`, `LiftSetId`,
  `WorkoutSupersetGroupId`, `ProgressionScheme`, `SetType` (`:240–289`)
- Lift catalog: `LiftId`, `MovementPattern`, `MuscleLengthBias`, `KinematicProfile`, etc.
- Pattern: enum `.name` storage; decode via `runCatching { valueOf }` → `null` on unknown
  (`EnumConverters.kt:42–46`)

**Tests:**

- `core/data/src/test/kotlin/com/stratum/core/data/db/EnumConvertersTest.kt` — round-trip,
  null, unknown enum, `LiftId` blank
- `core/data/src/test/kotlin/com/stratum/core/data/db/ProgramSchemaContractTest.kt` — v1 tables
  exist, `schemas/.../1.json` present, OS-7 converter round-trips
- `core/data/src/test/kotlin/com/stratum/core/data/db/LoggedSetDaoContractTest.kt` —
  `getInWindow` query behavior (Robolectric + in-memory Room)

**Database:**

- `StratumDatabase` version `1`, `exportSchema = true`
  (`core/data/src/main/kotlin/com/stratum/core/data/db/StratumDatabase.kt:38–39`)
- Pre-release policy documented in entity KDocs: reinstall to apply schema changes
  (`ProgramEntities.kt:23–24`, `ProgramSchemaContractTest.kt:24–25`)

### 24. Program mutation use cases (no change history)

| Use case | Behavior | Persistence |
|----------|----------|-------------|
| `SwapWorkoutLiftUseCase` | Finds `WorkoutLift` by id, copies with new `liftId`, `saveProgram` | `core/domain/.../SwapWorkoutLiftUseCase.kt:14–40` |
| `SaveWorkoutUseCase` | `program.mergeWorkout` then `saveProgram` | `core/domain/.../SaveWorkoutUseCase.kt:13–20` |
| `GenerateProgramUseCase` | (generation slice) saves via `ProgramRepository` | Referenced in tests; same replace-all repository |
| `LiftSwapViewModel.swapLift` | Calls `SwapWorkoutLiftUseCase` on candidate selection | `feature/program/.../LiftSwapViewModel.kt:93–98` |
| `EditWorkoutViewModel` | Edits in memory; save via `SaveWorkoutUseCase` | `feature/program/.../EditWorkoutViewModel.kt:29–34` |

**Verified fact:** Grep finds no `change_event`, `program_change`, or `ProgramChange` symbols in
`*.kt`. No append-only mutation log exists in the traced codebase.

`RoomProgramRepository.saveProgram` overwrites the full template hierarchy; swaps and edits leave
no historical record beyond the replaced rows.

### 25. `:feature:workout` does not exist

- Not in `settings.gradle.kts` module includes.
- Mentioned as out-of-scope in ticket and in `contracts/ARCHITECTURE.md` / vertical-slice docs as
  a future module.

### 26. Shared fakes (`:core:testing`)

| Fake | Port / use |
|------|------------|
| `FakeProgramRepository` | `ProgramRepository` |
| `FakeTrainingHistoryRepository` | `TrainingHistoryRepository` |
| `FakeImportJobRepository` | Import jobs |
| `FakeImportInsightsRepositories` | Insights, baselines, metadata |
| `FakeLiftRepository` | Lift catalog |
| `FakeResponseProfilePersistencePort` / `FakeResponseRepositories` | Response profile persistence |
| `FakeLiftSwapPort` | Cloud lift-swap inference boundary |
| `MainDispatcherRule` | ViewModel coroutine tests |

`FakeTrainingHistoryRepository` mirrors port API with in-memory lists and filter for window
(`core/testing/.../FakeTrainingHistoryRepository.kt`).

### 27. Test framework and commands

From `contracts/UNIT_TESTING.md` and `AGENTS.md`:

- **Framework:** JUnit 5, Turbine for Flow, Robolectric for Room (`:core:data`
  `testDebugUnitTest`)
- **Doubles:** Fakes from `:core:testing`; MockK forbidden on owned ports
- **Golden examples:** `golden-test-examples/` via `./scripts/golden-test-examples-download.sh`

**Relevant commands:**

| Scope | Command |
|-------|---------|
| Domain unit tests | `./gradlew :core:domain:test` |
| Data layer (Room) | `./gradlew :core:data:testDebugUnitTest` |
| Feature program tests | `./gradlew :feature:program:testDebugUnitTest` |
| Full unit tests | `./gradlew testDebugUnitTest` and `./gradlew :core:domain:test` |
| Static analysis | `./gradlew detekt` |
| Room/KSP smoke | `./gradlew :app:kspDebugKotlin` |

**Tests in scope touched by this research:**

- `ProgramsViewModelTest`, `NextWorkoutSelectionTest`, `EditWorkoutViewModelTest`
- `SwapWorkoutLiftUseCaseTest`, `SaveWorkoutUseCaseTest`
- `TrainingInsightsExtractorTest`, `MuscleVolumeCreditTest`, `ResponseObservationExtractorTest`,
  `ExtractResponseProfileUseCaseTest`, `PersistImportedHistoryUseCaseResponseTest`
- `EnumConvertersTest`, `ProgramSchemaContractTest`, `LoggedSetDaoContractTest`,
  `RoomProgramRepositoryTest`

### 28. LiftLab persistence (external reference)

*Source: `~/Code/LiftLab` — factual comparison only.*

LiftLab separates **in-progress** and **finalized** logging:

| Table | Entity | Role |
|-------|--------|------|
| `workoutsInProgress` | `WorkoutInProgressEntity` | Active session pointer: `workoutId`, `startTime` (`WorkoutInProgressEntity.kt:28–32`) |
| `liveWorkoutCompletedSets` | `LiveWorkoutCompletedSetEntity` | Sets logged during live workout: `workoutId`, `liftId`, set positions, weight/reps/rpe/orm (`LiveWorkoutCompletedSetEntity.kt:28–44`) |
| `workoutLogEntries` | `WorkoutLogEntryEntity` | Finalized session header: cycle fields (`mesoCycle`, `microCycle`, `microcyclePosition`), date, duration (`WorkoutLogEntryEntity.kt:29–39`) |
| `setLogEntries` | `SetLogEntryEntity` | Finalized history sets with **snapshotted lift metadata**: `liftName`, `liftMovementPattern`, `progressionScheme`, `setType`, rep ranges, rpe targets, etc. (`SetLogEntryEntity.kt:42–67`) |

**Verified fact (LiftLab):** History rows in `setLogEntries` embed lift name and movement pattern
at write time; live logging uses separate `liveWorkoutCompletedSets` before finalize.

**Verified fact (Stratum):** No equivalent in-progress tables; `logged_sets` has no movement
pattern or muscle snapshot columns.

---

## Gaps and unknowns

1. **Stale KDoc on `ProgramsViewModel`:** Class comment references `liftSwapSheet` and
   `swapSelections` (`ProgramsViewModel.kt:21–26`) but the current class body contains only
   `programsUiState` — swap UI lives in `LiftSwapViewModel` / `LiftSwapRoute` instead.
2. **`Program.cycleLength` consumption:** Stored and mapped to domain, but not used by Programs
   screen next-workout logic in traced code; full consumption across generation/scheduling not
   exhaustively traced beyond `buildWeeklySchedule`.
3. **Incremental history APIs:** No repository methods for single-session insert/update/delete;
   whether any DAO-level methods exist outside the port was not required — port defines bulk
   replace only.

---

## Self-review

- [x] Every research question has an answer or gap note above.
- [x] Findings contain no implementation recommendations.
- [x] No placeholders (`TBD`, `TODO`, `similar to`) in findings.
