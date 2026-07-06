# Structure: workout-logging

Vertical-slice execution outline for persistence and domain foundation. Signatures and
checkpoints only — no implementation bodies. Honors **Human approvals (2026-07-05)** in
`design.md`.

---

## Slice index

| # | Goal |
|---|------|
| 1 | Rename history tier to `training_sessions` / `TrainingSession` with extended schema columns |
| 2 | Import persist writes `LoggedLiftContext` snapshots; baseline volume credit uses snapshots |
| 3 | Incremental training-log repository CRUD + active-session observation |
| 4 | Program cycle pointer fields + ledger-based cycle progress resolver |
| 5 | Session lifecycle: start, finalize, abandon; block second active session; AD_HOC empty workout |
| 6 | Zero-set skip/rest `COMPLETED` sessions advance cycle without set logging |
| 7 | Live `LogSet` writes template-aware snapshots during `IN_PROGRESS` sessions |
| 8 | Append-only `program_change_events` wired to mutations + `CYCLE_COMPLETED` on cycle wrap |
| 9 | Response analytics pipeline fully decoupled from live `Lift` catalog joins |

---

## Dependencies between slices

```text
1 ──► 2 ──► 3 ──► 5 ──► 6 ──► 7
 │           │
 └───────────┴──► 4 ──► 5, 6, 8
                         8 ──► 9
2 ──► 9
```

- **Slice 1** blocks everything (schema + identity rename).
- **Slice 2** requires snapshot columns from 1; blocks analytics slice 9 baseline.
- **Slice 3** requires 1; blocks session/set writes in 5–7.
- **Slice 4** requires cycle columns from 1 + session read from 3; blocks finalize/skip pointer logic in 5–6 and `CYCLE_COMPLETED` in 8.
- **Slice 5** requires 3 + 4; blocks 6–7.
- **Slice 6** requires 5 + 4.
- **Slice 7** requires 5 + 2 (`LoggedLiftContext` builder).
- **Slice 8** requires 4 (cycle wrap hook) + existing mutation use cases; independent of 7.
- **Slice 9** requires 2; full green after 8 if mutation-triggered re-extract paths are tested.

---

## Public contracts

### Domain ids (`:core:domain`)

```kotlin
// com.stratum.domain.training.identity.TrainingIds.kt
@JvmInline value class TrainingSessionId(val value: String)
@JvmInline value class ProgramChangeEventId(val value: String)
// LoggedSetId unchanged — reuse com.stratum.domain.importing.identity.LoggedSetId
```

`HistoricalSessionId` removed; all references become `TrainingSessionId`.

### Domain enums (`com.stratum.domain.training`)

```kotlin
enum class TrainingSessionStatus { IN_PROGRESS, COMPLETED }

enum class TrainingSessionKind { PROGRAM_WORKOUT, REST_DAY, AD_HOC }

enum class TrainingSessionOrigin { IMPORT, LIVE }

enum class ProgramChangeEventType {
    LIFT_SWAPPED,
    WORKOUT_EDITED,
    PROGRAM_REGENERATED,
    CYCLE_COMPLETED,
}
```

### Domain models

```kotlin
// com.stratum.domain.training.LoggedLiftContext.kt
data class LoggedLiftContext(
    val liftName: String,
    val movementPatternsBitmask: Int,
    val primaryMusclesBitmask: Int,
    val secondaryMusclesBitmask: Int,
    val kinematicProfile: KinematicProfile,
    val muscleLengthBias: MuscleLengthBias,
    // Live-only template progression context (null on import — Q7)
    val workoutLiftId: WorkoutLiftId? = null,
    val progressionScheme: ProgressionScheme? = null,
    val setType: SetType? = null,
    val repMin: Int? = null,
    val repMax: Int? = null,
    val rirGoal: Int? = null,
)

// com.stratum.domain.training.TrainingSession.kt
data class TrainingSession(
    val id: TrainingSessionId,
    val programId: ProgramId,
    val workoutId: WorkoutId?,           // nullable — Q5
    val status: TrainingSessionStatus,
    val kind: TrainingSessionKind,
    val origin: TrainingSessionOrigin,
    val cycleNumberSnapshot: Int,
    val weekdaySnapshot: Weekday?,
    val startedAt: Instant,
    val endedAt: Instant?,
    val sourceApp: String?,              // import provenance; null for live
)

// com.stratum.domain.training.LoggedSet.kt — extends existing shape
data class LoggedSet(
    val id: LoggedSetId,
    val sessionId: TrainingSessionId,
    val exerciseNameRaw: String,
    val liftId: LiftId?,
    val setIndex: Int,
    val weight: Weight,
    val reps: Int,
    val rir: Int?,
    val completedAt: Instant,
    val liftContext: LoggedLiftContext,
)

// com.stratum.domain.training.ProgramChangeEvent.kt
data class ProgramChangeEvent(
    val id: ProgramChangeEventId,
    val programId: ProgramId,
    val eventType: ProgramChangeEventType,
    val occurredAt: Instant,
    val payload: ProgramChangeEventPayload,
)

sealed interface ProgramChangeEventPayload {
    data class LiftSwapped(
        val workoutLiftId: WorkoutLiftId,
        val previousLiftId: LiftId,
        val newLiftId: LiftId,
    ) : ProgramChangeEventPayload

    data class WorkoutEdited(val workoutId: WorkoutId) : ProgramChangeEventPayload

    data object ProgramRegenerated : ProgramChangeEventPayload

    data class CycleCompleted(val completedCycleNumber: Int) : ProgramChangeEventPayload
}
```

`HistoricalSession` removed. Import models package re-exports or typealiases only during
transition; end state: session/set types live in `com.stratum.domain.training`.

### Program model extension

```kotlin
// com.stratum.domain.program.Program — add fields
data class Program(
    // ... existing fields ...
    val currentCycleNumber: Int,   // default 1 on new program
    val cyclePosition: Int,        // denormalized completion count cache — Q2
)
```

### Ports

```kotlin
// com.stratum.domain.training.TrainingLogRepository.kt
// Renamed from TrainingHistoryRepository; supersedes ImportPorts.kt entry
interface TrainingLogRepository {
  // Bulk import (retained)
  suspend fun replaceAll(sessions: List<TrainingSession>, sets: List<LoggedSet>)

  // Reads
  suspend fun getAllSessions(): List<TrainingSession>
  suspend fun getAllSets(): List<LoggedSet>
  suspend fun getSetsInWindow(windowStart: Instant, windowEnd: Instant): List<LoggedSet>
  suspend fun getSession(id: TrainingSessionId): TrainingSession?
  suspend fun getSetsForSession(sessionId: TrainingSessionId): List<LoggedSet>
  suspend fun getCompletedSessionsForCycle(
      programId: ProgramId,
      cycleNumber: Int,
  ): List<TrainingSession>
  fun observeActiveSession(): Flow<TrainingSession?>

  // Incremental live logging
  suspend fun insertSession(session: TrainingSession)
  suspend fun updateSession(session: TrainingSession)
  suspend fun deleteSession(id: TrainingSessionId)  // CASCADE sets
  suspend fun insertSet(set: LoggedSet)
  suspend fun updateSet(set: LoggedSet)
  suspend fun deleteSet(id: LoggedSetId)
}

// com.stratum.domain.training.ProgramChangeEventRepository.kt
interface ProgramChangeEventRepository {
  suspend fun append(event: ProgramChangeEvent)
  suspend fun getEventsForProgram(
      programId: ProgramId,
      since: Instant? = null,
  ): List<ProgramChangeEvent>
}

// com.stratum.domain.program.ProgramRepository — extend
interface ProgramRepository {
  // ... existing observeProgram, getProgram, saveProgram ...
  suspend fun updateCyclePointer(
      programId: ProgramId,
      currentCycleNumber: Int,
      cyclePosition: Int,
  )
}
```

### Domain services and use cases

```kotlin
// com.stratum.domain.training.LoggedLiftContextFactory.kt
interface LoggedLiftContextFactory {
  fun fromLiftCatalog(lift: Lift, exerciseNameRaw: String): LoggedLiftContext
  fun fromLiveTemplate(
      lift: Lift,
      workoutLift: WorkoutLift,
      templateSet: LiftSet?,
  ): LoggedLiftContext
}

// com.stratum.domain.training.CycleProgressResolver.kt
object CycleProgressResolver {
  fun isWorkoutCompletedThisCycle(
      program: Program,
      sessions: List<TrainingSession>,
      workoutId: WorkoutId,
  ): Boolean

  fun resolveNextWorkout(program: Program, sessions: List<TrainingSession>): Workout?

  fun isCycleComplete(program: Program, sessions: List<TrainingSession>): Boolean

  fun reconcileCyclePosition(
      program: Program,
      sessions: List<TrainingSession>,
  ): Int  // ledger wins over cache — Q2
}

// com.stratum.domain.training.TrainingSessionConflictException.kt
class ActiveTrainingSessionExistsException(
    val activeSessionId: TrainingSessionId,
) : Exception()

// Use cases — com.stratum.domain.training.*
class StartTrainingSessionUseCase(
    trainingLogRepository: TrainingLogRepository,
    programRepository: ProgramRepository,
)
  suspend operator fun invoke(request: StartTrainingSessionRequest): Result<TrainingSession>

sealed class StartTrainingSessionRequest {
  data class ProgramWorkout(val workoutId: WorkoutId) : StartTrainingSessionRequest()
  data object AdHocEmpty : StartTrainingSessionRequest()
}

class FinalizeTrainingSessionUseCase(...)
  suspend operator fun invoke(sessionId: TrainingSessionId): Result<TrainingSession>

class AbandonTrainingSessionUseCase(...)
  suspend operator fun invoke(sessionId: TrainingSessionId): Result<Unit>

class RecordWorkoutSkipUseCase(...)
  suspend operator fun invoke(workoutId: WorkoutId): Result<TrainingSession>

class RecordRestDaySkipUseCase(...)
  suspend operator fun invoke(weekday: Weekday): Result<TrainingSession>

class LogSetUseCase(...)
  suspend operator fun invoke(command: LogSetCommand): Result<LoggedSet>

data class LogSetCommand(
    val sessionId: TrainingSessionId,
    val liftId: LiftId,
    val workoutLiftId: WorkoutLiftId?,
    val setIndex: Int,
    val weight: Weight,
    val reps: Int,
    val rir: Int?,
    val completedAt: Instant,
)

class ObserveCycleProgressUseCase(...)
  operator fun invoke(): Flow<CycleProgress>

data class CycleProgress(
    val program: Program,
    val activeSession: TrainingSession?,
    val completedWorkoutIdsThisCycle: Set<WorkoutId>,
    val nextWorkout: Workout?,
    val isCycleComplete: Boolean,
)
```

### Analytics signature changes

```kotlin
// com.stratum.domain.response.MuscleVolumeCredit.kt
object MuscleVolumeCredit {
  fun accumulateSetCredits(
      liftContext: LoggedLiftContext,
      reps: Int,
      rir: Int?,
      credits: MutableMap<MuscleGroup, Double>,
  )
  // Remove catalog-join overload after slice 9
}

// com.stratum.domain.response.ResponseExtractionInput.kt — lifts field optional
data class ResponseExtractionInput(
    val sessions: List<TrainingSession>,
    val sets: List<LoggedSet>,
    val lifts: List<Lift>,  // retained for lift-id effectiveness paths only
    // ... unchanged remainder ...
)
```

### Room schema (v1 reinstall — no migration)

```sql
-- Rename table historical_sessions → training_sessions
CREATE TABLE training_sessions (
    id TEXT PRIMARY KEY NOT NULL,
    program_id TEXT NOT NULL REFERENCES programs(id) ON DELETE CASCADE,
    workout_id TEXT REFERENCES workouts(id) ON DELETE SET NULL,
    status TEXT NOT NULL,
    kind TEXT NOT NULL,
    origin TEXT NOT NULL,
    cycle_number_snapshot INTEGER NOT NULL,
    weekday_snapshot TEXT,
    started_at_epoch_millis INTEGER NOT NULL,
    ended_at_epoch_millis INTEGER,
    source_app TEXT
);
CREATE INDEX idx_training_sessions_program_cycle
    ON training_sessions(program_id, cycle_number_snapshot);
CREATE INDEX idx_training_sessions_program_status
    ON training_sessions(program_id, status);

-- Extend logged_sets (snapshot columns)
ALTER logged_sets ADD COLUMN lift_name TEXT NOT NULL DEFAULT '';
ALTER logged_sets ADD COLUMN movement_patterns_bitmask INTEGER NOT NULL DEFAULT 0;
ALTER logged_sets ADD COLUMN primary_muscles_bitmask INTEGER NOT NULL DEFAULT 0;
ALTER logged_sets ADD COLUMN secondary_muscles_bitmask INTEGER NOT NULL DEFAULT 0;
ALTER logged_sets ADD COLUMN kinematic_profile TEXT;
ALTER logged_sets ADD COLUMN muscle_length_bias TEXT;
ALTER logged_sets ADD COLUMN workout_lift_id TEXT;
ALTER logged_sets ADD COLUMN progression_scheme TEXT;
ALTER logged_sets ADD COLUMN set_type TEXT;
ALTER logged_sets ADD COLUMN rep_min INTEGER;
ALTER logged_sets ADD COLUMN rep_max INTEGER;
ALTER logged_sets ADD COLUMN rir_goal INTEGER;
-- FK: session_id → training_sessions.id ON DELETE CASCADE (unchanged semantics)

-- Extend programs
ALTER programs ADD COLUMN current_cycle_number INTEGER NOT NULL DEFAULT 1;
ALTER programs ADD COLUMN cycle_position INTEGER NOT NULL DEFAULT 0;

CREATE TABLE program_change_events (
    id TEXT PRIMARY KEY NOT NULL,
    program_id TEXT NOT NULL REFERENCES programs(id) ON DELETE CASCADE,
    event_type TEXT NOT NULL,
    occurred_at_epoch_millis INTEGER NOT NULL,
    workout_lift_id TEXT,
    previous_lift_id TEXT,
    new_lift_id TEXT,
    workout_id TEXT,
    completed_cycle_number INTEGER
);
CREATE INDEX idx_program_change_events_program_time
    ON program_change_events(program_id, occurred_at_epoch_millis);
```

Pre-release: entity edits replace table definitions in source; reinstall applies shape.

---

## Per-slice checklist

### Slice 1 — Schema rename + extended columns

**Components touched**

| Layer | Artifacts |
|-------|-----------|
| Domain | `TrainingIds.kt`, `TrainingSession.kt`, `TrainingSessionStatus/Kind/Origin`, move/rename `LoggedSet` session id type |
| Data | `TrainingSessionEntity` (rename from `HistoricalSessionEntity`), `LoggedSetEntity` snapshot columns, `ProgramEntity` cycle columns, `EnumConverters`, `TrainingLogMapper` (rename from `ImportMapper` session/set), `StratumDatabase`, DAO renames |
| Tests | `EnumConvertersTest`, `ProgramSchemaContractTest`, `LoggedSetDaoContractTest` |

**Checkpoint:** `./gradlew :core:data:testDebugUnitTest --tests "*EnumConverters*" --tests "*SchemaContract*" --tests "*LoggedSetDao*"` and `./gradlew :app:kspDebugKotlin` pass. Import `replaceAll` round-trips sessions/sets with default snapshot values.

---

### Slice 2 — Import snapshots + baseline volume credit

**Components touched**

| Layer | Artifacts |
|-------|-----------|
| Domain | `LoggedLiftContext`, `LoggedLiftContextFactory` (catalog-only path), `PersistImportedHistoryUseCase` snapshot attachment, `MuscleVolumeCredit`, `TrainingInsightsExtractor.computeVolumeBaselines` |
| Data | Mapper encodes/decodes bitmasks via `LiftMapper` helpers |
| Tests | `PersistImportedHistoryUseCaseResponseTest`, `MuscleVolumeCreditTest`, `TrainingInsightsExtractorTest` |

**Checkpoint:** `./gradlew :core:domain:test --tests "*PersistImported*" --tests "*MuscleVolumeCredit*" --tests "*TrainingInsightsExtractor*"` — imported sets carry non-zero bitmasks; volume baselines computed without `Lift` join.

---

### Slice 3 — Incremental training-log repository

**Components touched**

| Layer | Artifacts |
|-------|-----------|
| Domain | `TrainingLogRepository` port (rename + new methods) |
| Data | `TrainingSessionDao`, `LoggedSetDao` incremental queries, `RoomTrainingLogRepository` (rename from `RoomTrainingHistoryRepository`), `DataModule` binding |
| Testing | `FakeTrainingLogRepository` (rename + CRUD) |

**Checkpoint:** `./gradlew :core:data:testDebugUnitTest --tests "*LoggedSetDao*" --tests "*TrainingLog*"` plus fake unit test verifying insert/update/delete/observeActiveSession.

---

### Slice 4 — Cycle pointer + ledger resolver

**Components touched**

| Layer | Artifacts |
|-------|-----------|
| Domain | `Program.currentCycleNumber`/`cyclePosition`, `ProgramRepository.updateCyclePointer`, `CycleProgressResolver` |
| Data | `ProgramMapper`, `RoomProgramRepository.updateCyclePointer` |
| Tests | `CycleProgressResolverTest` (new), `RoomProgramRepositoryTest` extension |

**Checkpoint:** `./gradlew :core:domain:test --tests "*CycleProgress*"` — next workout, cycle-complete detection, and reconcile from session ledger match Q2 scenarios (delete/add/replace workout mid-cycle).

---

### Slice 5 — Session lifecycle (start / finalize / abandon)

**Components touched**

| Layer | Artifacts |
|-------|-----------|
| Domain | `StartTrainingSessionUseCase`, `FinalizeTrainingSessionUseCase`, `AbandonTrainingSessionUseCase`, `ActiveTrainingSessionExistsException` |
| Data | Repository methods used by use cases |
| Tests | `StartTrainingSessionUseCaseTest`, `FinalizeTrainingSessionUseCaseTest`, `AbandonTrainingSessionUseCaseTest` |

**Behaviors**

- At most one global `IN_PROGRESS` session — Q3.
- `AdHocEmpty` → `kind = AD_HOC`, `workout_id = null`; finalize does not advance cycle — Q5.
- `ProgramWorkout` → `IN_PROGRESS`, `workout_id` set, `cycle_number_snapshot = program.currentCycleNumber`.
- Finalize → `COMPLETED`, `endedAt`, update `cycle_position` cache; if cycle complete → bump `current_cycle_number`, reset `cycle_position` to 0 (event emission deferred to slice 8).
- Abandon → `deleteSession` CASCADE.

**Checkpoint:** `./gradlew :core:domain:test --tests "*TrainingSession*"` — full start→finalize and start→abandon flows via fakes; second start throws `ActiveTrainingSessionExistsException`.

---

### Slice 6 — Zero-set skip / rest sessions

**Components touched**

| Layer | Artifacts |
|-------|-----------|
| Domain | `RecordWorkoutSkipUseCase`, `RecordRestDaySkipUseCase` |
| Tests | `RecordWorkoutSkipUseCaseTest`, `RecordRestDaySkipUseCaseTest`, `CycleProgressResolverTest` integration cases |

**Behaviors**

- Workout skip → `COMPLETED`, zero sets, `workout_id` set, `kind = PROGRAM_WORKOUT` — Q1.
- Rest day → `COMPLETED`, zero sets, `workout_id = null`, `kind = REST_DAY`, `weekday_snapshot` set — Q1.
- Both advance `cycle_position` / cycle wrap same as finalize — Q2.

**Checkpoint:** `./gradlew :core:domain:test --tests "*Skip*"` — skip marks workout done in ledger; `resolveNextWorkout` advances; rest-day row satisfied by `REST_DAY` session.

---

### Slice 7 — Live set logging

**Components touched**

| Layer | Artifacts |
|-------|-----------|
| Domain | `LogSetUseCase`, `LoggedLiftContextFactory.fromLiveTemplate` |
| Tests | `LogSetUseCaseTest` |

**Behaviors**

- Only allowed when session `IN_PROGRESS`.
- Snapshot populated from current `Lift` + `WorkoutLift`/`LiftSet` when `workoutLiftId` provided.
- Metrics path uses `liftContext` bitmasks only — Q7.

**Checkpoint:** `./gradlew :core:domain:test --tests "*LogSet*"` — logged set persisted with template fields on live path; import path still has null template fields.

---

### Slice 8 — Program change events

**Components touched**

| Layer | Artifacts |
|-------|-----------|
| Domain | `ProgramChangeEvent*`, `ProgramChangeEventRepository`, extend `SwapWorkoutLiftUseCase`, `SaveWorkoutUseCase`, `GenerateProgramUseCase`; `FinalizeTrainingSessionUseCase` + skip use cases emit `CYCLE_COMPLETED` — Q6 |
| Data | `ProgramChangeEventEntity`, `ProgramChangeEventDao`, `RoomProgramChangeEventRepository`, `EnumConverters` |
| Testing | `FakeProgramChangeEventRepository` |
| Tests | `SwapWorkoutLiftUseCaseTest`, `SaveWorkoutUseCaseTest`, `ProgramChangeEventRepositoryTest`, cycle-wrap emits `CYCLE_COMPLETED` |

**Checkpoint:** `./gradlew :core:domain:test --tests "*Swap*" --tests "*SaveWorkout*" --tests "*ChangeEvent*"` and `./gradlew :core:data:testDebugUnitTest --tests "*ProgramChange*"`.

---

### Slice 9 — Response analytics completion

**Components touched**

| Layer | Artifacts |
|-------|-----------|
| Domain | `StimulusSeriesBuilder`, `MuscleWeeklyStimulusBuilder`, `LiftEffectivenessAnalyzer`, `DefaultResponseObservationExtractor`, `ExtractResponseProfileUseCase` |
| Tests | Existing `*Response*` and `*Stimulus*` test suites updated |

**Checkpoint:** `./gradlew :core:domain:test --tests "*Response*" --tests "*Stimulus*"` and `./gradlew :core:domain:test --tests "*ExtractResponseProfile*"` — all green without catalog join for volume paths.

---

## File map preview

### Create

| Path | Responsibility |
|------|----------------|
| `core/domain/.../training/identity/TrainingIds.kt` | `TrainingSessionId`, `ProgramChangeEventId` |
| `core/domain/.../training/TrainingSession.kt` | Session model + status/kind/origin enums |
| `core/domain/.../training/LoggedLiftContext.kt` | Snapshot value type |
| `core/domain/.../training/LoggedSet.kt` | Unified set model with `liftContext` |
| `core/domain/.../training/TrainingLogRepository.kt` | Port (replaces `TrainingHistoryRepository`) |
| `core/domain/.../training/ProgramChangeEvent.kt` | Event model + payload sealed types |
| `core/domain/.../training/ProgramChangeEventRepository.kt` | Append-only event port |
| `core/domain/.../training/LoggedLiftContextFactory.kt` | Catalog + live snapshot builders |
| `core/domain/.../training/CycleProgressResolver.kt` | Ledger-based next workout / cycle complete |
| `core/domain/.../training/CycleProgress.kt` | Read model for Programs consumption |
| `core/domain/.../training/ActiveTrainingSessionExistsException.kt` | Conflict type — Q3 |
| `core/domain/.../training/StartTrainingSessionUseCase.kt` | Start program or AD_HOC session |
| `core/domain/.../training/FinalizeTrainingSessionUseCase.kt` | O(1) complete + pointer update |
| `core/domain/.../training/AbandonTrainingSessionUseCase.kt` | CASCADE delete |
| `core/domain/.../training/RecordWorkoutSkipUseCase.kt` | Zero-set workout skip — Q1 |
| `core/domain/.../training/RecordRestDaySkipUseCase.kt` | Zero-set rest day — Q1 |
| `core/domain/.../training/LogSetUseCase.kt` | Incremental set write + snapshot |
| `core/domain/.../training/ObserveCycleProgressUseCase.kt` | Flow read model for future Programs wiring |
| `core/data/.../db/TrainingSessionEntity.kt` | Renamed session entity + new columns |
| `core/data/.../db/ProgramChangeEventEntity.kt` | Append-only event row |
| `core/data/.../db/TrainingLogDaos.kt` | Session/set DAOs (split from `ImportDaos`) |
| `core/data/.../db/ProgramChangeEventDao.kt` | Event insert + query |
| `core/data/.../mapper/TrainingLogMapper.kt` | Session/set/event entity ↔ domain |
| `core/data/.../repository/RoomTrainingLogRepository.kt` | Port implementation |
| `core/data/.../repository/RoomProgramChangeEventRepository.kt` | Event port implementation |
| `core/testing/.../training/FakeTrainingLogRepository.kt` | Extended fake |
| `core/testing/.../training/FakeProgramChangeEventRepository.kt` | Event fake |
| `core/domain/src/test/.../training/CycleProgressResolverTest.kt` | Ledger scenarios |
| `core/domain/src/test/.../training/*UseCaseTest.kt` | Per use case |
| `core/data/src/test/.../db/ProgramChangeEventDaoContractTest.kt` | Room contract |

### Modify

| Path | Responsibility |
|------|----------------|
| `core/domain/.../importing/ImportModels.kt` | Remove `HistoricalSession`; re-export or delete session/set types |
| `core/domain/.../importing/identity/ImportIds.kt` | Remove `HistoricalSessionId` |
| `core/domain/.../importing/ImportPorts.kt` | Remove `TrainingHistoryRepository`; update import types to `training` package |
| `core/domain/.../importing/ImportUseCases.kt` | `PersistImportedHistoryUseCase` → snapshots + `TrainingSession` |
| `core/domain/.../program/Program.kt` | Add `currentCycleNumber`, `cyclePosition` |
| `core/domain/.../program/ProgramRepository.kt` | Add `updateCyclePointer` |
| `core/domain/.../program/SwapWorkoutLiftUseCase.kt` | Append `LIFT_SWAPPED` event |
| `core/domain/.../program/SaveWorkoutUseCase.kt` | Append `WORKOUT_EDITED` event |
| `core/domain/.../program/GenerateProgramUseCase.kt` | Append `PROGRAM_REGENERATED` event |
| `core/domain/.../response/MuscleVolumeCredit.kt` | `LoggedLiftContext` overload |
| `core/domain/.../response/StimulusSeriesBuilder.kt` | Snapshot credits |
| `core/domain/.../response/modeling/MuscleWeeklyStimulusBuilder.kt` | Snapshot credits |
| `core/domain/.../response/LiftEffectivenessAnalyzer.kt` | Reduce live `Lift` dependency |
| `core/domain/.../response/ResponseObservationExtractor.kt` | Snapshot-aware paths |
| `core/domain/.../response/ExtractResponseProfileUseCase.kt` | Optional lifts for volume |
| `core/domain/.../response/ResponseModels.kt` | `TrainingSession` in input types |
| `core/domain/.../importing/TrainingInsightsExtractor.kt` | Snapshot baselines |
| `core/data/.../db/ImportEntities.kt` | Remove `HistoricalSessionEntity`; move session entity |
| `core/data/.../db/LoggedSetEntity` (in ImportEntities or TrainingLogEntities) | Snapshot columns |
| `core/data/.../db/ProgramEntities.kt` | Cycle pointer columns |
| `core/data/.../db/EnumConverters.kt` | New ids and enums |
| `core/data/.../db/StratumDatabase.kt` | Entity/DAO registration |
| `core/data/.../mapper/ImportMapper.kt` | Delegate session/set mapping to `TrainingLogMapper` |
| `core/data/.../repository/RoomImportRepositories.kt` | Rename training repo class |
| `core/data/.../repository/RoomProgramRepository.kt` | Map cycle fields + `updateCyclePointer` |
| `core/data/schemas/.../1.json` | Regenerated schema export |
| `app/.../di/DataModule.kt` | Bind renamed repositories |
| `core/inference/...` import runners | Type renames only |
| `core/testing/.../FakeTrainingHistoryRepository.kt` | Rename → `FakeTrainingLogRepository` |
| `feature/onboarding/...` test fixtures | Type renames |
| All test fixtures referencing `HistoricalSession*` | Rename to `TrainingSession*` |

### Delete (end of slice 1 rename sweep)

| Path | Responsibility |
|------|----------------|
| `HistoricalSessionId` references | Replaced by `TrainingSessionId` |
| `HistoricalSession` / `HistoricalSessionEntity` / `HistoricalSessionDao` names | Replaced by `Training*` equivalents |
| `TrainingHistoryRepository` symbol | Replaced by `TrainingLogRepository` |

---

## Test checkpoints (summary)

| After slice | Runnable verification |
|-------------|----------------------|
| 1 | `:core:data:testDebugUnitTest` (schema + converter + DAO contracts); `:app:kspDebugKotlin` |
| 2 | `:core:domain:test` — import persist + `MuscleVolumeCredit` + `TrainingInsightsExtractor` |
| 3 | `:core:data:testDebugUnitTest` — incremental DAO; fake CRUD unit test |
| 4 | `:core:domain:test` — `CycleProgressResolverTest` |
| 5 | `:core:domain:test` — session lifecycle use cases |
| 6 | `:core:domain:test` — skip/rest use cases + resolver integration |
| 7 | `:core:domain:test` — `LogSetUseCaseTest` |
| 8 | `:core:domain:test` + `:core:data:testDebugUnitTest` — mutation events + `CYCLE_COMPLETED` |
| 9 | `./gradlew :core:domain:test` + `./gradlew detekt` — full domain green, static analysis |

**CI-aligned gate before merge:** `./gradlew detekt`, `./gradlew assembleDebug`, `./gradlew testDebugUnitTest`, `./gradlew :core:domain:test`.

---

## Out of scope (unchanged from design)

- `:feature:workout` Compose UI
- Programs screen callback wiring (`MainTabShell`, `ProgramsViewModel` methods)
- Weekly LLM check-in Callable/UI
- Post-release Room migrations

`ObserveCycleProgressUseCase` is included as a domain read hook only — no feature-module UI in this ticket.

---

## Self-review

- [x] Every slice is vertically integrated with an explicit checkpoint.
- [x] Checkpoints exist after each slice, not only at the end.
- [x] File map aligns with design decisions (rename, snapshots, ledger, events, AD_HOC, import lift-only).
- [x] No horizontal-layer-only phase (analytics split is slice 2 baseline + slice 9 completion; both are end-to-end via import/response tests).
- [x] Human approvals Q1–Q7 reflected in signatures and slice 5–8 behaviors.
- [x] No placeholders (`TBD`, `TODO`, `implement later`).
