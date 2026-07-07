# Research: workout-screen

Objective codebase map of the runtime architecture surface for the live workout
experience. No design recommendations.

This ticket succeeds `workout-logging` (persistence foundation — landed, at
Final Review) and consumes templates from `program-synthesis` /
`program-generation-v2`. Findings here build on, and cite, the research already
published in `plans/workout-logging/research.md` and
`plans/response-profile/research.md` rather than restating them.

---

## Research Questions

### Existing program / workout template model

1. What domain types and Room entities represent the `Program → Workout →
   WorkoutLift → LiftSet` template hierarchy today, and which module owns them?
2. Where are set ordering (`position`), set types (Standard / MyoRep / DropSet),
   top-set marking (`isTopSet`), rep ranges (`repMin`/`repMax`), and RIR goals
   represented on the template model?
3. Where is the progression scheme (`Linear` / `Double`) assigned, and what is
   the single authority that decides it?
4. Is there any "backoff vs top set" structural distinction on `LiftSet` beyond
   the `isTopSet` flag and RIR value?

### Existing progression / snapshot / recommendation code

5. Does any code in `:core:domain` (or elsewhere) compute a per-set recommended
   weight, an initial progression snapshot, or a live next-set recommendation
   today?
6. Is there any `LinearProgression` / `DoubleProgression` engine, port, or use
   case implementing progression math?
7. Does any code compute the inverse e1RM problem ("what weight lets the user
   hit the bottom of the target rep range")?
8. Are there any legacy or deprecated progression patterns still in the tree?

### Existing live-session / workout-screen code

9. Does a `:feature:workout` module exist, and is any WorkoutScreen Compose UI,
   ViewModel, or presentation model present?
10. Which domain use cases implement the live-session lifecycle (start, log set,
    finalize, abandon, skip), and what are their contracts?
11. How is the active session observed and how is the "next workout" for a cycle
    resolved today?

### Historical-data access patterns for snapshot generation

12. How is training history queried today (port API, read patterns), and which
    repository owns live vs import history?
13. What does the `LoggedLiftContext` snapshot carry, and when/at what point is
    it captured and persisted?
14. How are `workout-logging` cycle pointers (`currentCycleNumber`,
    `cyclePosition`) and program change events persisted and read?
15. What data priority signals exist for "same lift, same workout, same
    position, previous session" vs "same lift from another workout"?

### Response / e1RM / fatigue estimation

16. Where is e1RM calculated, what formula is used, and what is its input
    contract (reps threshold, RIR adjustment)?
17. What fatigue-model outputs exist today (`fatigueDecaySlope`,
    `fatigueDecayIntercept`), how are they computed, and where are they
    persisted/consumed?
18. Is any fatigue model parameterized per-user vs population, and where are
    population defaults referenced?

### EditWorkoutScreen editing model and rep-range display

19. What is the current `EditWorkoutUiState` editing model (lifts, sets, fields),
   and how are rep ranges / RIR / rest rendered per set?
20. Does the editing model enforce (or even represent) the constraints the
    ticket calls out — Myo-rep block as only/final set, drop set immediately
    following another set, rep range only on the top set?
21. How does `EditWorkoutViewModel` add/remove sets and persist edits, and what
    is the save path?

### Program generation: rep ranges and set counts

22. How are rep range (`repMin`/`repMax`), RIR, and top-set vs backoff produced
    during program generation, and is the same rep range applied to all sets of
    a lift?
23. What caps exist on sets-per-lift and sets-per-muscle-per-session, and is
    there any guard against unrealistic rep-range × set-count combinations?
24. What is the relationship between the two `ProgressionScheme` enums in the
    tree?

### Test patterns and run commands

25. What shared fakes in `:core:testing` cover training-log and program ports,
    and what fixtures exist for training sessions / logged sets?
26. What test framework, patterns, and Gradle commands apply to the layers this
    ticket will touch (domain use cases, future feature ViewModel, generation)?

---

## Findings

### 1. Template hierarchy ownership and shape

- Domain model: `core/domain/src/main/kotlin/com/stratum/domain/program/Program.kt`
  - `Program` (`:23`) — `id`, `name`, `createdAt`, `cycleLength`,
    `trainingWeekdays`, `workouts`, plus `currentCycleNumber`/`cyclePosition`
    added by `workout-logging` (`:32`).
  - `Workout` (`:36`) — `id`, `programId`, `name`, `weekday`, `position`,
    `workoutLifts`.
  - `WorkoutLift` (`:45`) — `id`, `workoutId`, `liftId`, `progressionScheme`,
    `position`, `sets`, `supersetGroupId?`, `useRestTimerBetweenLifts`.
  - `LiftSet` (`:64`) — `id`, `workoutLiftId`, `position`, `setType`, `repMin`,
    `repMax`, `rirGoal`, `isTopSet`, `restSeconds`.
  - KDoc (`:15-22`) states templates are produced by generation (OS-7), distinct
    from logged-set hierarchy; all working sets are straight sets (single load
    per lift); `LiftSet` carries **no weight/load field**.
- Room entities: `core/data/src/main/kotlin/com/stratum/core/data/db/ProgramEntities.kt`
  — `ProgramEntity`, `WorkoutEntity`, `WorkoutLiftEntity`, `LiftSetEntity`
  mirror the domain shape 1:1 with FK CASCADE deletes (`:30-111`).
- Module: domain owns models; `:core:data` owns entities/mappers; both consumed
  by `:feature:program`.

### 2. Set ordering, set types, top-set, rep ranges, RIR

- `SetType` enum: `core/domain/src/main/kotlin/com/stratum/domain/program/ProgramEnums.kt:7`
  — `enum class SetType { Standard, MyoRep, DropSet }`. KDoc references the
  §2.4 set-type taxonomy. `MyoRep` and `DropSet` variants exist as enum values
  but are **not produced by generation today** (see Finding 22) and carry no
  sub-type (rep-floor vs rep-matching) distinction.
- `LiftSet.isTopSet` (`Program.kt:71`) is a boolean; the KDoc (`:60-63`) states
  that on a `Double` progression lift exactly one set has `isTopSet = true` and
  carries the rep range; "back-offs may omit the range (server-side
  convention)". `rirGoal` is deterministically clamped by the generation
  post-pass (D2).
- `LiftSet.repMin`/`repMax`/`rirGoal`/`restSeconds` are per-set fields
  (`Program.kt:69-73`); persistence mirrors them on `LiftSetEntity`
  (`ProgramEntities.kt:105-110`).
- **Verified fact:** the model has no separate "top set" vs "backoff" type —
  the distinction is the `isTopSet` boolean plus which RIR band the row carries.

### 3. Progression scheme assignment authority

- Two `ProgressionScheme` enums exist (see Finding 24). The generation-package
  one (`ProgramRules.kt:21`) is the authority during generation.
- `schemeForTrainingAge(trainingAge: String)`
  (`core/domain/src/main/kotlin/com/stratum/domain/program/generation/reference/ProgramRules.kt:30-31`)
  — `"Beginner" -> Linear`, everything else `Double`. KDoc: "SOLE authority for
  `progressionScheme` — the model never authors it."
- Consumers: `ProgramNormalizer.kt:25` (schema normalization), `WorkoutAssembler.kt:188`
  (build pass).
- Result is mapped to the domain-package `ProgressionScheme`
  (`com.stratum.domain.program.ProgressionScheme`) via `.toDomain()`
  (`ProgramNormalizer.kt:25`) and stored on `WorkoutLiftEntity.progressionScheme`.

### 4. No structural backoff distinction beyond `isTopSet` + RIR

- `LiftSet` carries no `Backoff`/`Accessory`/`Working` type field. The only
  structural signal beyond `isTopSet` is `setType` (`Standard`/`MyoRep`/`DropSet`).
- `WorkoutAssembler.buildLiftDrafts`
  (`core/domain/src/main/kotlin/com/stratum/domain/program/generation/build/WorkoutAssembler.kt:155-178`)
  builds `setCount` rows where `isTopSet = (scheme == Double && i == 0)` and all
  sets share the same `repMin`/`repMax`; only `rirGoal` differs (top vs
  backoff band from `repRirForSlot`).
- **Inference:** "backoff" is purely a runtime/presence concept (sets after the
  top set); the template marks only the top set and the RIR target per row.

### 5. No existing progression snapshot / recommendation code

- Grep for `Recommendation`, `ProgressionSnapshot`, `LiveWorkout`,
  `recommendedWeight`, `recommendedReps` across `core/`, `feature/`, `app/`
  returns **zero** production source matches.
- No use case, port, or domain type computes a per-set recommended weight or a
  "next set" recommendation. `LogSetUseCase`
  (`core/domain/src/main/kotlin/com/stratum/domain/training/LogSetUseCase.kt`)
  persists exactly what the caller passes in `LogSetCommand` (`weight`, `reps`,
  `rir`); it performs no recommendation math.
- **Verified fact:** the three layers the ticket proposes (structure / initial
  snapshot / live overlay) have no existing implementation; this is greenfield
  for snapshot + overlay.

### 6. No `LinearProgression` / `DoubleProgression` engine

- No class, port, or use case implements progression math. `ProgressionScheme`
  is an assignment label only (`Linear` / `Double`); no engine consumes it to
  produce a recommended load.
- The closest existing logic is the generation-time RIR/rep-band assignment
  (`RepRirPlanner`, `WorkoutAssembler`) which decides *template* rep/RIR, not
  runtime load progression.
- **Gap:** there is no existing code to cite for "increase weight when top of
  rep range is hit" (double progression) or "increase weight every session /
  weekly; transition to double after stalls" (linear progression).

### 7. No inverse e1RM ("weight for target reps") helper

- `EstimatedOneRm` (`core/domain/src/main/kotlin/com/stratum/domain/importing/LiftNameResolver.kt:272-286`)
  is **forward-only**: `calculate(weight: Double, reps: Int): Double`.
- Formula: Brzycki for `reps <= 10`, Epley otherwise (`:278-285`). Constants are
  `private const` (`:273-276`).
- A RIR-adjusted variant exists in `TrainingSignalCleaner`
  (`core/domain/src/main/kotlin/com/stratum/domain/response/TrainingSignalCleaner.kt:24-27`)
  — it calls `EstimatedOneRm.calculate(weight, reps + rir)`.
- **Verified fact:** no `weightForReps(e1rm, targetReps)` / inverse function
  exists in the tree. The ticket's "what weight lets the user hit the bottom of
  the target rep range" calculation is not yet implemented.

### 8. Legacy / deprecated progression patterns

- `StubLoggedLiftContextFactory`
  (`core/domain/src/main/kotlin/com/stratum/domain/training/LoggedLiftContextFactory.kt:43-44`)
  is marked `@deprecated` ("Kept for test references during migration") and
  delegates to `DefaultLoggedLiftContextFactory`. No progression logic in it.
- No `LinearProgression`, no LiftLab-style coupled progression-emit-Flow code is
  present in the Stratum tree (LiftLab is an external reference repo at
  `~/Code/LiftLab`, not part of Stratum).
- **Verified fact:** no deprecated progression engine exists in-tree.

### 9. No `:feature:workout` module

- `settings.gradle.kts` includes `:app`, `:core:domain`, `:core:domain-solver`,
  `:core:domain-solver-tests`, `:core:data`, `:core:ui`, `:core:testing`,
  `:core:inference`, `:detekt-rules`, `:feature:onboarding`, `:feature:program`.
  No `:feature:workout`.
- Filesystem search for `feature/workout` returns no paths outside `build/`.
- ARCHITECTURE.md §1.2 (`contracts/ARCHITECTURE.md:46`) lists `:feature:workout`
  as a planned module ("Active session logging — sets, timers, in-workout
  flow").
- **Verified fact:** the entire WorkoutScreen UI, ViewModel, and presentation
  model layer is greenfield. This matches the ticket's "Out of scope" note
  (Compose pixel implementation is built during implementation slice 1).

### 10. Live-session lifecycle use cases

All in `core/domain/src/main/kotlin/com/stratum/domain/training/`:

| Use case | Contract |
|----------|----------|
| `StartTrainingSessionUseCase` (`:10-64`) | `operator fun invoke(request: StartTrainingSessionRequest): Result<TrainingSession>`. Throws `ActiveTrainingSessionExistsException` if an `IN_PROGRESS` session exists. Snapshots `program.currentCycleNumber` and `workout.weekday` onto the session. |
| `LogSetUseCase` (`:10-61`) | `operator fun invoke(command: LogSetCommand): Result<LoggedSet>`. Requires `IN_PROGRESS` session; resolves lift from catalog and `LoggedLiftContext` from template (`workoutLiftId`) or catalog. Persists via `insertSet`. |
| `FinalizeTrainingSessionUseCase` (`:8-47`) | `operator fun invoke(sessionId): Result<TrainingSession>`. Sets `COMPLETED` + `endedAt`; for `PROGRAM_WORKOUT` kind computes cycle advancement and persists atomically with pointer/event. |
| `AbandonTrainingSessionUseCase` (`AbandonTrainingSessionUseCase.kt`) | Deletes the session (CASCADE deletes sets). |
| `RecordWorkoutSkipUseCase` / `RecordRestDaySkipUseCase` | Zero-set `COMPLETED` sessions (rest-day = `REST_DAY` kind, ad-hoc/workout skip per design Q1). |

- `LogSetCommand` (`TrainingCommands.kt:16-25`) carries `sessionId`, `liftId`,
  `workoutLiftId?`, `setIndex`, `weight`, `reps`, `rir?`, `completedAt`. **No
  recommended-weight / recommended-reps field** — the command is the *performed*
  set, not a recommendation.
- `StartTrainingSessionRequest` (`TrainingCommands.kt:10-14`) is sealed:
  `ProgramWorkout(workoutId)` or `AdHocEmpty`.

### 11. Active-session observation and next-workout resolution

- `TrainingLogRepository.observeActiveSession(): Flow<TrainingSession?>`
  (`TrainingLogRepository.kt:21`) — backed by
  `TrainingSessionDao.observeByStatus(IN_PROGRESS)`
  (`core/data/src/main/kotlin/com/stratum/core/data/db/TrainingLogDaos.kt`).
- `ObserveCycleProgressUseCase`
  (`core/domain/src/main/kotlin/com/stratum/domain/training/ObserveCycleProgressUseCase.kt:11-56`)
  combines `programRepository.observeProgram()` with
  `trainingLogRepository.observeActiveSession()`, then emits a `CycleProgress`
  (`CycleProgress.kt:7-13`) read model: `program`, `activeSession`,
  `completedWorkoutIdsThisCycle`, `nextWorkout`, `isCycleComplete`.
- `CycleProgressResolver.resolveNextWorkout`
  (`CycleProgressResolver.kt:15-40`) picks the next incomplete workout by
  `position` (see `workout-logging` research Finding 3 / design Q2 for full
  rules).
- **Verified fact:** a domain read model for "next workout" + "active session"
  already exists and is the natural entry point for a future WorkoutScreen.

### 12. History query patterns and repository ownership

- `TrainingLogRepository`
  (`core/domain/src/main/kotlin/com/stratum/domain/training/TrainingLogRepository.kt:9-38`)
  is the **single** port for both import and live history (the
  `workout-logging` design D1 unified them). Methods relevant to snapshot
  generation:
  - `getAllSessions()` / `getAllSets()` (`:12-13`)
  - `getSetsInWindow(windowStart, windowEnd)` (`:16`)
  - `getSession(id)` / `getSetsForSession(sessionId)` (`:17-18`)
  - `getCompletedSessionsForCycle(programId, cycleNumber)` (`:19`)
  - `getActiveSession()` / `observeActiveSession()` (`:20-21`)
  - `getImportSessions()` / `getImportSets()` (`:14-15`) — import-scoped reads.
- `RoomTrainingLogRepository`
  (`core/data/src/main/kotlin/com/stratum/core/data/repository/RoomTrainingLogRepository.kt`)
  implements all of the above; `getSetsInWindow` uses an indexed SQL range
  query on `completedAtEpochMillis`
  (`TrainingLogDaos.kt` `getInWindow`), unlike the older import-only path.
- The legacy `TrainingHistoryRepository` port (in
  `core/domain/src/main/kotlin/com/stratum/domain/importing/ImportPorts.kt`)
  still exists and is consumed by `ExtractResponseProfileUseCase` /
  `PersistImportedHistoryUseCase`. **Verified fact:** two history-reading ports
  coexist; `TrainingLogRepository` is the post-`workout-logging` unified surface.

### 13. `LoggedLiftContext` snapshot shape and capture point

- Domain type: `core/domain/src/main/kotlin/com/stratum/domain/training/LoggedLiftContext.kt:9-22`
  — `liftName`, `movementPatternsBitmask`, `primaryMusclesBitmask`,
  `secondaryMusclesBitmask`, `kinematicProfile`, `muscleLengthBias`, plus
  optional template context: `workoutLiftId?`, `progressionScheme?`, `setType?`,
  `repMin?`, `repMax?`, `rirGoal?`.
- Persisted on `LoggedSetEntity` (`ImportEntities.kt:57-68`) — all snapshot
  columns default to empty/zero/`NonAxialCompound` so import rows (which have
  no template context) coexist.
- Capture point (`LoggedLiftContextFactory.kt`):
  - `fromLiveTemplate(lift, workoutLift, templateSet)` (`:28-41`) — used by
    `LogSetUseCase` when `workoutLiftId != null`; copies the template's
    `progressionScheme`, `setType`, `repMin`, `repMax`, `rirGoal` plus the
    lift's catalog metadata.
  - `fromLiftCatalog(lift, exerciseNameRaw)` (`:18-26`) — used for ad-hoc sets
    and import rows (template fields null).
- **Verified fact:** snapshot generation already exists for *persistence*
  purposes; it is **not** a progression snapshot (no recommended weight). The
  ticket's "initial progression snapshot" is a separate concept that will
  consume this same template context.

### 14. Cycle pointer and program change event persistence

- `ProgramEntity` carries `current_cycle_number` and `cycle_position`
  (`ProgramEntities.kt:37-38`); updated atomically by
  `RoomTrainingLogRepository.persistSessionWithCycleEffects`
  (`RoomTrainingLogRepository.kt:124-142`) via `programDao.updateCyclePointer`.
- `ProgramChangeEvent` (`ProgramChangeEvent.kt:17-23`) + `ProgramChangeEventEntity`
  (in `TrainingSessionEntity.kt` package siblings) — append-only table; event
  types `LIFT_SWAPPED`, `WORKOUT_EDITED`, `PROGRAM_REGENERATED`, `CYCLE_COMPLETED`
  (`ProgramChangeEvent.kt:10-15`).
- `ProgramChangeEventRepository` (port) — `append(event)`; `FakeProgramChangeEventRepository`
  and `NoOpProgramChangeEventRepository` exist in `:core:testing`.
- `computeCycleAdvancement` (`CycleAdvancement.kt:20-49`) is the single function
  that derives pointer update + optional `CYCLE_COMPLETED` event from program +
  sessions.
- **Verified fact:** the snapshot-generation layer can read cycle history and
  change events through existing ports; no new persistence is required to know
  "what changed when".

### 15. Historical-data priority signals

- `LoggedSet.liftContext` carries `workoutLiftId?` (the template slot the set
  filled) and the session carries `workoutId` + `cycleNumberSnapshot`
  (`TrainingSession.kt:25-37`). These are the join keys for "same lift, same
  workout, same position, previous session".
- `CycleProgressResolver.completedProgramWorkoutSessions`
  (`CycleProgressResolver.kt:60-70`) already filters by `programId` +
  `cycleNumberSnapshot` + `COMPLETED` + `PROGRAM_WORKOUT` + non-null `workoutId`
  — the same predicate shape a "previous session for this slot" query would
  need.
- `getSetsForSession(sessionId)` and `getCompletedSessionsForCycle(programId,
  cycleNumber)` are the available read primitives; **there is no existing
  query that joins `(programId, workoutId, workoutLiftId, setIndex)` across
  sessions** to find n−1 / n−2 set history for a given slot.
- **Gap:** the historical-priority lookup the ticket specifies (same lift +
  same workout + same position; then same lift other workouts) has no existing
  repository method — it would be a new read.

### 16. e1RM calculation

- `EstimatedOneRm.calculate(weight, reps)`
  (`LiftNameResolver.kt:272-286`): Brzycki for `reps <= 10`, Epley otherwise.
  `require(reps > 0)`.
- RIR-adjusted e1RM: `EstimatedOneRm.calculate(weight, reps + rir)` in
  `TrainingSignalCleaner.kt:24-27`, stored as `CleanTrainingSet.rirAdjustedEstimatedOneRm`
  (`ResponseModels.kt:59`).
- Used pervasively by the response pipeline (`StimulusSeriesBuilder`,
  `OutcomeSeriesBuilder`, `LiftPerformancePanelBuilder`, `ChangePointDetector`,
  `OutlierGuard`, `VolumeResponseAnalyzer`, `LiftEffectivenessAnalyzer`) and by
  `TrainingInsightsExtractor` for fatigue regression
  (`TrainingInsightsExtractor.kt:107`).
- **Verified fact:** a per-set e1RM is already computed in the response
  pipeline; the same primitive is available for live per-set e1RM estimation,
  but no runtime/live consumer exists yet.

### 17. Fatigue-model outputs

- `LiftTrainingInsights.fatigueDecaySlope` / `fatigueDecayIntercept`
  (`core/domain/src/main/kotlin/com/stratum/domain/importing/ImportModels.kt:42-43`)
  — nullable `Double`.
- Computed in `TrainingInsightsExtractor`
  (`TrainingInsightsExtractor.kt:100-118`): per session, e1RM is computed per
  set, then a linear regression of e1RM over set-index-within-session yields a
  slope + intercept; the per-lift aggregate is the **average** slope/intercept
  across sessions (`:117-118`).
- Persisted on `LiftTrainingInsightsEntity` (`ImportEntities.kt:71-81`) and
  surfaced into the generation context via
  `ProgramGenerationContextMapper.kt:68-69` (`fatigueDecaySlope`,
  `fatigueDecayIntercept`) and `LiftPriority.kt:29-34`.
- **Verified fact:** the fatigue model is *within-session e1RM decay per set
  index*, averaged across sessions. It is per-lift, derived from history — i.e.
  **user-specific** (per lift) once a lift has ≥2 sets in ≥1 session.

### 18. Population vs user-specific fatigue

- No "population gradient" default constant exists in `ResponseConstants.kt`
  (`core/domain/src/main/kotlin/com/stratum/domain/response/ResponseConstants.kt`)
  — all constants there are extraction thresholds (epoch gaps, outlier MAD,
  confidence floors), not fatigue slopes.
- `LiftTrainingInsights.fatigueDecaySlope` is nullable; when a lift has no
  insights (no history), it is `null`. `ProgramGenerationContextMapper` passes
  `null` through (`:68-69`).
- `LiftStressClassification` and `RpReferenceTables` provide *rep-band* defaults
  (`RP_REP_RIR_BANDS`, `RpReferenceTables.kt:79-88`) but not fatigue-slope
  defaults.
- **Gap:** the ticket's "population gradient initially" fallback for fatigue
  has no existing constant or table in the tree. The current model is
  user-specific-or-null.

### 19. `EditWorkoutUiState` editing model

- `feature/program/src/main/kotlin/com/stratum/feature/program/ui/EditWorkoutUiState.kt`
  - `EditWorkoutUiState` sealed (`:9`): `Loading`, `NotFound`, `Loaded`.
  - `Loaded` (`:14-29`): `workoutId`, `workoutName`, `weekdayLabel`, `lifts:
    List<EditLiftState>`, volume cards, `estimatedDurationMinutes`,
    `isReorderMode`, `pendingReorderLifts?`, `isDirty`, `isSaving`,
    `saveBlockedReason?`.
  - `EditLiftState` (`:36-44`): `workoutLiftId`, `liftId`, `name`, `position`,
    `sets: List<EditSetState>`, `supersetGroupId?`, `useRestTimerBetweenLifts`.
  - `EditSetState` (`:46-53`): `setId`, `setNumber`, `repMin`, `repMax`,
    `restSeconds`, `rirGoal`. **No `setType`, no `isTopSet`** — the editing
    model does not represent set type or top-set marking.
- Defaults (`:61-66`): `DEFAULT_REST_SECONDS=120`, `DEFAULT_REP_MIN=8`,
  `DEFAULT_REP_MAX=12`, `DEFAULT_RIR_GOAL=2`, `SIX_PLUS_RIR_GOAL=6`.

### 20. EditWorkoutScreen rep-range display and constraints

- `EditWorkoutSetTable`
  (`feature/program/src/main/kotlin/com/stratum/feature/program/ui/EditWorkoutSetTable.kt`)
  renders a per-row `RepRangeCell` (`:274-308`) showing `repMin`–`repMax` for
  **every** set — there is no top-set-only display rule today.
- Column headers (`:90-114`): Set | Rep Range | Rest | RIR. The Rep Range column
  weight is `0.44f` (`:454`).
- **Verified fact:** the ticket's "rep range should display only on the top
  set" constraint is **not** currently enforced; every editable set shows a rep
  range. There is also no Myo-rep / drop-set authoring UI and no constraint
  enforcing "Myo-rep block must be only/final set" or "drop set must follow
  another set" — `EditSetState` has no `setType` field to even represent them.
- There is a `RirTargetBottomSheet`
  (`feature/program/src/main/kotlin/com/stratum/feature/program/ui/RirTargetBottomSheet.kt`)
  for picking RIR; no analogous bottom sheet for set type.

### 21. `EditWorkoutViewModel` add/remove/save

- `feature/program/src/main/kotlin/com/stratum/feature/program/EditWorkoutViewModel.kt`
  - `addSet(workoutLiftId)` (`:125-139`) appends a `defaultEditSetState` and
    renumbers.
  - `removeSet(setId)` (`:218-232`) filters and renumbers.
  - `setRepMin`/`setRepMax`/`setRirGoal`/`setRestSeconds` (`:256-288`) update
    individual set fields.
  - `saveWorkout(onComplete)` (`:97-123`) maps `Loaded.lifts` back to a
    `Workout` via `toWorkout(baseline, workoutId)` and calls
    `SaveWorkoutUseCase`, then refreshes `baselineWorkout` + `programSnapshot`.
- Save path: `EditWorkoutUiStateMapper.toWorkout` → `SaveWorkoutUseCase`
  (`core/domain/src/main/kotlin/com/stratum/domain/program/SaveWorkoutUseCase.kt`)
  → `program.mergeWorkout` → `ProgramRepository.saveProgram` (full-hierarchy
  replace — see `workout-logging` research Finding 7).
- **Verified fact:** edits persist through the same replace-all template
  repository; there is no per-set patch API.

### 22. Rep-range / RIR / top-set production in generation

- `repRirForSlot(slot, lift)`
  (`core/domain/src/main/kotlin/com/stratum/domain/program/generation/prescription/RepRirPlanner.kt:44-56`)
  returns a single `RepRirPlan(repMin, repMax, topSetRir, backOffRir)` per lift,
  sourced from `RP_REP_RIR_BANDS` (`RpReferenceTables.kt:79-88`) keyed by
  `RpRepProfile`.
- `WorkoutAssembler.buildLiftDrafts`
  (`WorkoutAssembler.kt:155-178`) builds `setCount` `LiftSetDraft`s; **all sets
  receive the same `repMin`/`repMax`** (`:166-167`); only `rirGoal` differs
  (top vs backoff) and only `isTopSet` differs (`:163`).
- All generated sets have `setType = SetType.Standard` (`:166`). **No `MyoRep`
  or `DropSet` sets are produced by generation today** — those enum values are
  unused in production generation code.
- **Verified fact relevant to the "unrealistic workouts" concern:** the rep
  range is constant across all sets of a lift, so a `LowerMachineOrUpperPress`
  band of `4–12` × 5 sets genuinely means 5 sets all prescribed `4–12`. There is
  no generation-time logic that narrows the range on later sets or caps
  set-count as a function of the rep band.

### 23. Set-count and volume caps

- `SETS_PER_LIFT_CAP = 5` (`RpReferenceTables.kt:17`) — "Max hard sets on a
  single exercise in one session."
- `SESSION_MUSCLE_SET_CAP = 11` (`RpReferenceTables.kt:13`) — per-muscle per
  workout; enforced as an **Error** in `validateSessionMuscleVolumeCap`
  (`ProgramValidation.kt:253-278`).
- `sessionHardSetCapForLength(minutes)` (`RpReferenceTables.kt:19-22`) floors
  the cap at `minutes / MINUTES_PER_HARD_SET`; `validateWorkoutDuration`
  (`ProgramValidation.kt:116-144`) emits an **Error** when estimate exceeds
  budget.
- **Verified fact:** there is a 5-set-per-lift cap, but **no validation that
  ties set-count to the rep band** (e.g. "5 sets at a 4-rep-min band is
  unrealistic"). The ticket's concern is not guarded.

### 24. The two `ProgressionScheme` enums

- `com.stratum.domain.program.ProgressionScheme`
  (`core/domain/src/main/kotlin/com/stratum/domain/program/ProgramEnums.kt:7-19`)
  — the **persisted/domain** enum stored on `WorkoutLift` / `WorkoutLiftEntity`,
  registered in `EnumConverters`.
- `com.stratum.domain.program.generation.reference.ProgressionScheme`
  (`core/domain/src/main/kotlin/com/stratum/domain/program/generation/reference/ProgramRules.kt:21-24`)
  — the **generation-package** enum used by `schemeForTrainingAge` and
  `WorkoutAssembler`.
- `ProgramNormalizer.kt:25` bridges them with `.toDomain()`.
- **Verified fact:** a runtime progression engine consuming the domain enum
  would need to switch on `com.stratum.domain.program.ProgressionScheme`
  (`Linear` / `Double`); the generation enum is not the runtime authority.

### 25. Shared fakes and fixtures

`:core:testing` fakes relevant to this ticket
(`core/testing/src/main/kotlin/com/stratum/core/testing/`):

| Fake | Port |
|------|------|
| `training/FakeTrainingLogRepository.kt` | `TrainingLogRepository` — in-memory sessions/sets, `observeActiveSession` via `MutableStateFlow`, `insertSetCallCount` probe. |
| `training/FakeProgramChangeEventRepository.kt` | `ProgramChangeEventRepository`. |
| `training/NoOpProgramChangeEventRepository.kt` | No-op variant. |
| `training/WorkoutLoggingFixtures.kt` | Fixtures: `Program`, `Workout`, `WorkoutLift`, `LiftSet`, `TrainingSession`, `LoggedSet` with `LoggedLiftContext`, bitmask constants. |
| `program/FakeProgramRepository.kt` | `ProgramRepository`. |
| `program/WorkoutDomainTestHelpers.kt` | Domain workout/program builders. |
| `onboarding/FakeLiftRepository.kt` | `LiftRepository`. |
| `response/FakeResponseRepositories.kt` | Response observation repos. |
| `MainDispatcherRule.kt` | `@ExtendWith` coroutine test rule. |

- **Verified fact:** the fakes a future `:feature:workout` ViewModel and
  progression-engine tests need already exist for the persistence layer. A
  snapshot/recommendation port would require a **new** fake (per
  `UNIT_TESTING.md` §6).

### 26. Test framework, patterns, and commands

From `contracts/UNIT_TESTING.md` and `AGENTS.md` §5/§9:

- **Framework:** JUnit 5 (`org.junit.jupiter.api`), Turbine for `Flow`,
  Robolectric for Room (`:core:data` `testDebugUnitTest`).
- **Doubles:** fakes from `:core:testing`; MockK forbidden on owned ports
  (Detekt `ForbiddenMockKInTests`); `verify()` forbidden in ViewModel tests
  (Detekt `ForbiddenVerifyInViewModelTests`).
- **Golden examples:** `golden-test-examples/` via
  `./scripts/golden-test-examples-download.sh`.

Existing tests in scope (the layers this ticket will touch):

| Area | Test files |
|------|-----------|
| Training lifecycle (domain) | `core/domain/src/test/.../training/{StartTrainingSessionUseCaseTest,LogSetUseCaseTest,FinalizeTrainingSessionUseCaseTest,AbandonTrainingSessionUseCaseTest,RecordWorkoutSkipUseCaseTest,RecordRestDaySkipUseCaseTest}.kt` |
| Cycle progress | `.../training/{CycleProgressResolverTest,ObserveCycleProgressUseCaseTest}.kt` |
| Logging defenses | `.../training/{WorkoutLoggingImportSnapshotTest,WorkoutLoggingMutationsDefenseTest,WorkoutLoggingProgramChangeEventTest}.kt` |
| Data layer | `core/data/src/test/.../{TrainingSessionEntityContractTest,RoomTrainingLogRepositoryTest,RoomTrainingLogRepositoryImportFkTest}.kt` |
| EditWorkout | `feature/program/src/test/.../feature/program/{EditWorkoutDomainMapperTest,EditWorkoutViewModelTest,EditWorkoutProgramVolumeTest,EditWorkoutSupersetTest}.kt` |
| Generation prescription | `core/domain/src/test/.../program/generation/**` (RepRir/Synthesis/Validation) |

Relevant Gradle commands:

| Scope | Command |
|-------|---------|
| Domain unit tests | `./gradlew :core:domain:test` |
| Data layer (Room) | `./gradlew :core:data:testDebugUnitTest` |
| Feature program tests | `./gradlew :feature:program:testDebugUnitTest` |
| Full unit tests | `./gradlew testDebugUnitTest` and `./gradlew :core:domain:test` |
| Static analysis | `./gradlew detekt` (`maxIssues: 0`) |
| Room/KSP smoke | `./gradlew :app:kspDebugKotlin` |

---

## Gaps and unknowns

1. **No progression engine exists.** Neither double progression ("bump load when
   top of rep range is achieved at RIR target") nor linear progression ("add
   weight every session/week; transition to double after ~2 stalls") is
   implemented. The `ProgressionScheme` enum is an assignment label only.
2. **No inverse e1RM helper.** `EstimatedOneRm` is forward-only
   (`weight, reps -> e1rm`); the "weight to hit bottom of target rep range"
   calculation required for live recommendations has no existing primitive.
3. **No population fatigue default.** `fatigueDecaySlope` is per-lift-from-history
   or `null`; the ticket's "population gradient initially" fallback has no
   existing constant or reference table.
4. **No historical-priority query.** The repository has no method to find
   "previous sessions for this `(programId, workoutId, workoutLiftId, setIndex)`";
   the closest existing primitive is `getSetsForSession` and the cycle-completion
   predicate in `CycleProgressResolver`.
5. **Generation applies one rep range to all sets of a lift** and there is no
   validation tying set-count to the rep band — the "unrealistic workouts"
   concern in the ticket is not guarded by any existing code.
6. **EditWorkoutScreen has no set-type or top-set concept.** `EditSetState`
   carries `repMin`/`repMax`/`rirGoal` only; Myo-rep / drop-set authoring and
   the "rep range only on top set" display rule have no representation in the
   current editing model.
7. **Linear-progression best practices (external).** The ticket asks to validate
   the LiftLab recollection ("increase every session/week; transition to double
   after ~2 stalls") against research. That is a literature question, not a
   codebase question; this research does not source external literature.

---

## Self-review

- [x] Every research question has an answer, a gap note, or an explicit unknown.
- [x] Findings contain no implementation recommendations.
- [x] No placeholders (`TBD`, `TODO`, `similar to`) in findings.
- [x] Test framework and run commands identified (Finding 26).
