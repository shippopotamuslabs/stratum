# Spec: workout-logging

Implementation specification derived from approved `design.md` (2026-07-05) and
`structure.md` (2026-07-05). No runnable code — signatures, types, file maps, and
ordered tasks only.

---

## Binding decisions

| ID | Decision |
|----|----------|
| Q1 | Rest/skip → zero-set `COMPLETED` session; rest day uses `kind = REST_DAY`, `workout_id = null` |
| Q2 | Session ledger is cycle source of truth; `cycle_position` is denormalized cache; ledger wins on reconcile |
| Q3 | Block second `IN_PROGRESS` session via `ActiveTrainingSessionExistsException` |
| Q4 | Rename `HistoricalSession` → `TrainingSession`; table `historical_sessions` → `training_sessions` |
| Q5 | Nullable `workout_id`; `AD_HOC` empty workout does not advance cycle |
| Q6 | `program_change_events`: `LIFT_SWAPPED`, `WORKOUT_EDITED`, `PROGRAM_REGENERATED`, `CYCLE_COMPLETED` |
| Q7 | Import snapshots: lift catalog metadata only; template progression fields null on import rows |

Pre-release schema policy: `StratumDatabase` version 1; reinstall to apply entity changes.

---

## Vertical slices

| Slice | Goal |
|-------|------|
| 1 | Rename history tier + extended schema columns |
| 2 | Import persist writes `LoggedLiftContext`; baseline volume credit uses snapshots |
| 3 | Incremental `TrainingLogRepository` CRUD + active-session observation |
| 4 | Program cycle pointer + ledger-based `CycleProgressResolver` |
| 5 | Session lifecycle: start, finalize, abandon; block second active; AD_HOC |
| 6 | Zero-set skip/rest `COMPLETED` sessions advance cycle |
| 7 | Live `LogSet` writes template-aware snapshots |
| 8 | Append-only `program_change_events` + `CYCLE_COMPLETED` on cycle wrap |
| 9 | Response analytics fully decoupled from live `Lift` catalog joins |

Dependency order: `1 → 2 → 3 → 5 → 6 → 7`; `1 → 4 → 5,6,8`; `2 → 9`; `8 → 9`.

---

## Domain contracts

### Package: `com.stratum.domain.training`

#### Identity (`training/identity/TrainingIds.kt`)

```kotlin
@JvmInline value class TrainingSessionId(val value: String)
@JvmInline value class ProgramChangeEventId(val value: String)
// LoggedSetId — retain in com.stratum.domain.importing.identity.LoggedSetId
```

`HistoricalSessionId` removed; all call sites use `TrainingSessionId`.

#### Enums (`training/TrainingSession.kt` or split files)

```kotlin
enum class TrainingSessionStatus { IN_PROGRESS, COMPLETED }
enum class TrainingSessionKind { PROGRAM_WORKOUT, REST_DAY, AD_HOC }
enum class TrainingSessionOrigin { IMPORT, LIVE }
enum class ProgramChangeEventType {
    LIFT_SWAPPED, WORKOUT_EDITED, PROGRAM_REGENERATED, CYCLE_COMPLETED,
}
```

`Weekday` — reuse `com.stratum.domain.onboarding.Weekday`.

#### Models

```kotlin
// training/LoggedLiftContext.kt
data class LoggedLiftContext(
    val liftName: String,
    val movementPatternsBitmask: Int,
    val primaryMusclesBitmask: Int,
    val secondaryMusclesBitmask: Int,
    val kinematicProfile: KinematicProfile,      // com.stratum.domain.lift
    val muscleLengthBias: MuscleLengthBias,      // com.stratum.domain.lift
    val workoutLiftId: WorkoutLiftId? = null,    // null on import — Q7
    val progressionScheme: ProgressionScheme? = null,
    val setType: SetType? = null,
    val repMin: Int? = null,
    val repMax: Int? = null,
    val rirGoal: Int? = null,
)

// training/TrainingSession.kt
data class TrainingSession(
    val id: TrainingSessionId,
    val programId: ProgramId,
    val workoutId: WorkoutId?,
    val status: TrainingSessionStatus,
    val kind: TrainingSessionKind,
    val origin: TrainingSessionOrigin,
    val cycleNumberSnapshot: Int,
    val weekdaySnapshot: Weekday?,
    val startedAt: Instant,
    val endedAt: Instant?,
    val sourceApp: String?,
)

// training/LoggedSet.kt
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

// training/ProgramChangeEvent.kt
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

// training/CycleProgress.kt
data class CycleProgress(
    val program: Program,
    val activeSession: TrainingSession?,
    val completedWorkoutIdsThisCycle: Set<WorkoutId>,
    val nextWorkout: Workout?,
    val isCycleComplete: Boolean,
)
```

#### Program extension (`program/Program.kt`)

```kotlin
data class Program(
    // existing: id, name, createdAt, cycleLength, trainingWeekdays, workouts
    val currentCycleNumber: Int,   // default 1
    val cyclePosition: Int,        // denormalized cache — Q2
)
```

#### Ports

```kotlin
// training/TrainingLogRepository.kt — supersedes TrainingHistoryRepository
interface TrainingLogRepository {
    suspend fun replaceAll(sessions: List<TrainingSession>, sets: List<LoggedSet>)
    suspend fun getAllSessions(): List<TrainingSession>
    suspend fun getAllSets(): List<LoggedSet>
    suspend fun getSetsInWindow(windowStart: Instant, windowEnd: Instant): List<LoggedSet>
    suspend fun getSession(id: TrainingSessionId): TrainingSession?
    suspend fun getSetsForSession(sessionId: TrainingSessionId): List<LoggedSet>
    suspend fun getCompletedSessionsForCycle(programId: ProgramId, cycleNumber: Int): List<TrainingSession>
    fun observeActiveSession(): Flow<TrainingSession?>
    suspend fun insertSession(session: TrainingSession)
    suspend fun updateSession(session: TrainingSession)
    suspend fun deleteSession(id: TrainingSessionId)
    suspend fun insertSet(set: LoggedSet)
    suspend fun updateSet(set: LoggedSet)
    suspend fun deleteSet(id: LoggedSetId)
}

// training/ProgramChangeEventRepository.kt
interface ProgramChangeEventRepository {
    suspend fun append(event: ProgramChangeEvent)
    suspend fun getEventsForProgram(programId: ProgramId, since: Instant? = null): List<ProgramChangeEvent>
}

// program/ProgramRepository.kt — extend existing
suspend fun updateCyclePointer(programId: ProgramId, currentCycleNumber: Int, cyclePosition: Int)
```

#### Services

```kotlin
// training/LoggedLiftContextFactory.kt
interface LoggedLiftContextFactory {
    fun fromLiftCatalog(lift: Lift, exerciseNameRaw: String): LoggedLiftContext
    fun fromLiveTemplate(lift: Lift, workoutLift: WorkoutLift, templateSet: LiftSet?): LoggedLiftContext
}

// training/CycleProgressResolver.kt
object CycleProgressResolver {
    fun isWorkoutCompletedThisCycle(program: Program, sessions: List<TrainingSession>, workoutId: WorkoutId): Boolean
    fun resolveNextWorkout(program: Program, sessions: List<TrainingSession>): Workout?
    fun isCycleComplete(program: Program, sessions: List<TrainingSession>): Boolean
    fun reconcileCyclePosition(program: Program, sessions: List<TrainingSession>): Int
}

// training/ActiveTrainingSessionExistsException.kt
class ActiveTrainingSessionExistsException(val activeSessionId: TrainingSessionId) : Exception()
```

#### Use cases (`training/`)

```kotlin
class StartTrainingSessionUseCase(...)
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
```

#### Analytics signature changes

```kotlin
// response/MuscleVolumeCredit.kt — add overload; remove Lift overload in slice 9
fun accumulateSetCredits(
    liftContext: LoggedLiftContext,
    weekStart: Instant,
    muscleWeekCredits: MutableMap<MuscleGroup, MutableMap<Instant, Double>>,
)

// response/ResponseModels.kt — ResponseExtractionInput
val sessions: List<TrainingSession>   // was HistoricalSession
// lifts: List<Lift> retained for lift-id effectiveness paths only
```

Bitmask encode/decode: reuse `LiftMapper` helpers (`1 shl enum.ordinal`).

---

## Room schema (v1 source definitions)

### `training_sessions` (replaces `historical_sessions`)

| Column | Type | Notes |
|--------|------|-------|
| `id` | TEXT PK | `TrainingSessionId` |
| `program_id` | TEXT FK → `programs.id` CASCADE | required |
| `workout_id` | TEXT FK → `workouts.id` SET NULL | nullable — Q5 |
| `status` | TEXT | `TrainingSessionStatus` |
| `kind` | TEXT | `TrainingSessionKind` |
| `origin` | TEXT | `TrainingSessionOrigin` |
| `cycle_number_snapshot` | INTEGER | |
| `weekday_snapshot` | TEXT | nullable `Weekday` |
| `started_at_epoch_millis` | INTEGER | |
| `ended_at_epoch_millis` | INTEGER | nullable |
| `source_app` | TEXT | nullable; import provenance |

Indexes: `(program_id, cycle_number_snapshot)`, `(program_id, status)`.

### `logged_sets` — add snapshot columns

| Column | Type | Notes |
|--------|------|-------|
| `lift_name` | TEXT NOT NULL | |
| `movement_patterns_bitmask` | INTEGER NOT NULL | |
| `primary_muscles_bitmask` | INTEGER NOT NULL | |
| `secondary_muscles_bitmask` | INTEGER NOT NULL | |
| `kinematic_profile` | TEXT | |
| `muscle_length_bias` | TEXT | |
| `workout_lift_id` | TEXT | nullable; live only |
| `progression_scheme` | TEXT | nullable |
| `set_type` | TEXT | nullable |
| `rep_min` | INTEGER | nullable |
| `rep_max` | INTEGER | nullable |
| `rir_goal` | INTEGER | nullable |

FK `session_id` → `training_sessions.id` ON DELETE CASCADE (unchanged semantics).

### `programs` — add columns

| Column | Type | Default |
|--------|------|---------|
| `current_cycle_number` | INTEGER NOT NULL | 1 |
| `cycle_position` | INTEGER NOT NULL | 0 |

### `program_change_events` (new)

| Column | Type | Notes |
|--------|------|-------|
| `id` | TEXT PK | |
| `program_id` | TEXT FK CASCADE | |
| `event_type` | TEXT | `ProgramChangeEventType` |
| `occurred_at_epoch_millis` | INTEGER | |
| `workout_lift_id` | TEXT | nullable; `LIFT_SWAPPED` |
| `previous_lift_id` | TEXT | nullable |
| `new_lift_id` | TEXT | nullable |
| `workout_id` | TEXT | nullable; `WORKOUT_EDITED` |
| `completed_cycle_number` | INTEGER | nullable; `CYCLE_COMPLETED` |

Index: `(program_id, occurred_at_epoch_millis)`.

Regenerate `core/data/schemas/com.stratum.core.data.db.StratumDatabase/1.json` after entity edits.

---

## File map

### Create

| Path | Responsibility |
|------|----------------|
| `core/domain/src/main/kotlin/com/stratum/domain/training/identity/TrainingIds.kt` | `TrainingSessionId`, `ProgramChangeEventId` |
| `core/domain/src/main/kotlin/com/stratum/domain/training/TrainingSession.kt` | Session model + status/kind/origin enums |
| `core/domain/src/main/kotlin/com/stratum/domain/training/LoggedLiftContext.kt` | Snapshot value type |
| `core/domain/src/main/kotlin/com/stratum/domain/training/LoggedSet.kt` | Unified set model with `liftContext` |
| `core/domain/src/main/kotlin/com/stratum/domain/training/TrainingLogRepository.kt` | Port replacing `TrainingHistoryRepository` |
| `core/domain/src/main/kotlin/com/stratum/domain/training/ProgramChangeEvent.kt` | Event model + payload sealed types |
| `core/domain/src/main/kotlin/com/stratum/domain/training/ProgramChangeEventRepository.kt` | Append-only event port |
| `core/domain/src/main/kotlin/com/stratum/domain/training/LoggedLiftContextFactory.kt` | Catalog + live snapshot builders |
| `core/domain/src/main/kotlin/com/stratum/domain/training/CycleProgressResolver.kt` | Ledger-based next workout / cycle complete |
| `core/domain/src/main/kotlin/com/stratum/domain/training/CycleProgress.kt` | Read model for Programs consumption |
| `core/domain/src/main/kotlin/com/stratum/domain/training/ActiveTrainingSessionExistsException.kt` | Conflict type — Q3 |
| `core/domain/src/main/kotlin/com/stratum/domain/training/StartTrainingSessionUseCase.kt` | Start program or AD_HOC session |
| `core/domain/src/main/kotlin/com/stratum/domain/training/FinalizeTrainingSessionUseCase.kt` | O(1) complete + pointer update |
| `core/domain/src/main/kotlin/com/stratum/domain/training/AbandonTrainingSessionUseCase.kt` | CASCADE delete |
| `core/domain/src/main/kotlin/com/stratum/domain/training/RecordWorkoutSkipUseCase.kt` | Zero-set workout skip — Q1 |
| `core/domain/src/main/kotlin/com/stratum/domain/training/RecordRestDaySkipUseCase.kt` | Zero-set rest day — Q1 |
| `core/domain/src/main/kotlin/com/stratum/domain/training/LogSetUseCase.kt` | Incremental set write + snapshot |
| `core/domain/src/main/kotlin/com/stratum/domain/training/ObserveCycleProgressUseCase.kt` | Flow read model hook |
| `core/data/src/main/kotlin/com/stratum/core/data/db/TrainingSessionEntity.kt` | Renamed session entity + new columns |
| `core/data/src/main/kotlin/com/stratum/core/data/db/ProgramChangeEventEntity.kt` | Append-only event row |
| `core/data/src/main/kotlin/com/stratum/core/data/db/TrainingLogDaos.kt` | Session/set DAOs |
| `core/data/src/main/kotlin/com/stratum/core/data/db/ProgramChangeEventDao.kt` | Event insert + query |
| `core/data/src/main/kotlin/com/stratum/core/data/mapper/TrainingLogMapper.kt` | Session/set/event entity ↔ domain |
| `core/data/src/main/kotlin/com/stratum/core/data/repository/RoomTrainingLogRepository.kt` | Port implementation |
| `core/data/src/main/kotlin/com/stratum/core/data/repository/RoomProgramChangeEventRepository.kt` | Event port implementation |
| `core/testing/src/main/kotlin/com/stratum/core/testing/training/FakeTrainingLogRepository.kt` | Extended fake |
| `core/testing/src/main/kotlin/com/stratum/core/testing/training/FakeProgramChangeEventRepository.kt` | Event fake |
| `core/domain/src/test/kotlin/com/stratum/domain/training/CycleProgressResolverTest.kt` | Ledger scenarios |
| `core/domain/src/test/kotlin/com/stratum/domain/training/StartTrainingSessionUseCaseTest.kt` | Start flows |
| `core/domain/src/test/kotlin/com/stratum/domain/training/FinalizeTrainingSessionUseCaseTest.kt` | Finalize flows |
| `core/domain/src/test/kotlin/com/stratum/domain/training/AbandonTrainingSessionUseCaseTest.kt` | Abandon flows |
| `core/domain/src/test/kotlin/com/stratum/domain/training/RecordWorkoutSkipUseCaseTest.kt` | Workout skip |
| `core/domain/src/test/kotlin/com/stratum/domain/training/RecordRestDaySkipUseCaseTest.kt` | Rest day skip |
| `core/domain/src/test/kotlin/com/stratum/domain/training/LogSetUseCaseTest.kt` | Live set logging |
| `core/domain/src/test/kotlin/com/stratum/domain/training/ObserveCycleProgressUseCaseTest.kt` | Read model |
| `core/data/src/test/kotlin/com/stratum/core/data/db/ProgramChangeEventDaoContractTest.kt` | Room contract |
| `core/data/src/test/kotlin/com/stratum/core/data/repository/RoomTrainingLogRepositoryTest.kt` | Incremental CRUD |
| `core/testing/src/test/kotlin/com/stratum/core/testing/training/FakeTrainingLogRepositoryTest.kt` | Fake CRUD unit test |

### Modify

| Path | Responsibility |
|------|----------------|
| `core/domain/src/main/kotlin/com/stratum/domain/importing/ImportModels.kt` | Remove `HistoricalSession`/`LoggedSet`; import types reference `training` package |
| `core/domain/src/main/kotlin/com/stratum/domain/importing/identity/ImportIds.kt` | Remove `HistoricalSessionId` |
| `core/domain/src/main/kotlin/com/stratum/domain/importing/ImportPorts.kt` | Remove `TrainingHistoryRepository` |
| `core/domain/src/main/kotlin/com/stratum/domain/importing/ImportUseCases.kt` | `PersistImportedHistoryUseCase` → snapshots + `TrainingSession` |
| `core/domain/src/main/kotlin/com/stratum/domain/importing/MergeImportedHistoryUseCase.kt` | `TrainingSessionId` rename |
| `core/domain/src/main/kotlin/com/stratum/domain/importing/TrainingInsightsExtractor.kt` | Snapshot baselines |
| `core/domain/src/main/kotlin/com/stratum/domain/program/Program.kt` | Add cycle pointer fields |
| `core/domain/src/main/kotlin/com/stratum/domain/program/ProgramRepository.kt` | Add `updateCyclePointer` |
| `core/domain/src/main/kotlin/com/stratum/domain/program/SwapWorkoutLiftUseCase.kt` | Append `LIFT_SWAPPED` event |
| `core/domain/src/main/kotlin/com/stratum/domain/program/SaveWorkoutUseCase.kt` | Append `WORKOUT_EDITED` event |
| `core/domain/src/main/kotlin/com/stratum/domain/program/GenerateProgramUseCase.kt` | Append `PROGRAM_REGENERATED` event |
| `core/domain/src/main/kotlin/com/stratum/domain/response/MuscleVolumeCredit.kt` | `LoggedLiftContext` overload |
| `core/domain/src/main/kotlin/com/stratum/domain/response/StimulusSeriesBuilder.kt` | Snapshot credits |
| `core/domain/src/main/kotlin/com/stratum/domain/response/modeling/MuscleWeeklyStimulusBuilder.kt` | Snapshot credits |
| `core/domain/src/main/kotlin/com/stratum/domain/response/LiftEffectivenessAnalyzer.kt` | Reduce live `Lift` dependency |
| `core/domain/src/main/kotlin/com/stratum/domain/response/ResponseObservationExtractor.kt` | Snapshot-aware paths |
| `core/domain/src/main/kotlin/com/stratum/domain/response/ExtractResponseProfileUseCase.kt` | Optional lifts for volume |
| `core/domain/src/main/kotlin/com/stratum/domain/response/ResponseModels.kt` | `TrainingSession` in input types |
| `core/domain/src/main/kotlin/com/stratum/domain/response/EpochSplitter.kt` | `TrainingSession` type rename |
| `core/data/src/main/kotlin/com/stratum/core/data/db/ImportEntities.kt` | Remove `HistoricalSessionEntity`; extend `LoggedSetEntity` |
| `core/data/src/main/kotlin/com/stratum/core/data/db/ProgramEntities.kt` | Cycle pointer columns on `ProgramEntity` |
| `core/data/src/main/kotlin/com/stratum/core/data/db/EnumConverters.kt` | New ids and enums |
| `core/data/src/main/kotlin/com/stratum/core/data/db/StratumDatabase.kt` | Entity/DAO registration |
| `core/data/src/main/kotlin/com/stratum/core/data/db/ImportDaos.kt` | Remove session DAO; delegate to `TrainingLogDaos` |
| `core/data/src/main/kotlin/com/stratum/core/data/mapper/ImportMapper.kt` | Delegate session/set to `TrainingLogMapper` |
| `core/data/src/main/kotlin/com/stratum/core/data/mapper/ProgramMapper.kt` | Map cycle fields |
| `core/data/src/main/kotlin/com/stratum/core/data/repository/RoomImportRepositories.kt` | Remove/rename training repo class |
| `core/data/src/main/kotlin/com/stratum/core/data/repository/RoomProgramRepository.kt` | Map cycle fields + `updateCyclePointer` |
| `core/data/schemas/com.stratum.core.data.db.StratumDatabase/1.json` | Regenerated export |
| `app/src/main/java/com/stratum/di/DataModule.kt` | Bind renamed repositories |
| `core/inference/src/main/kotlin/com/stratum/core/inference/ImportedHistoryJsonParser.kt` | Type renames |
| `core/inference/src/main/kotlin/com/stratum/core/inference/RunFullHistoricalImportUseCase.kt` | Type renames |
| `core/testing/src/main/kotlin/com/stratum/core/testing/importing/FakeTrainingHistoryRepository.kt` | Rename → `FakeTrainingLogRepository` |
| All test fixtures referencing `HistoricalSession*` | Rename to `TrainingSession*` |

### Delete (end of slice 1 rename sweep)

| Symbol / name | Replaced by |
|---------------|-------------|
| `HistoricalSessionId` | `TrainingSessionId` |
| `HistoricalSession` | `TrainingSession` |
| `HistoricalSessionEntity` | `TrainingSessionEntity` |
| `HistoricalSessionDao` | `TrainingSessionDao` (in `TrainingLogDaos`) |
| `TrainingHistoryRepository` | `TrainingLogRepository` |
| `RoomTrainingHistoryRepository` | `RoomTrainingLogRepository` |
| `FakeTrainingHistoryRepository` | `FakeTrainingLogRepository` |

---

## Implementation tasks

Tasks are ordered within each slice. Each task lists satisfied Gherkin scenario IDs from
`test_spec.md`.

### Slice 1 — Schema rename + extended columns

| Task | Description | Scenarios |
|------|-------------|-----------|
| 1.1 | Create `com.stratum.domain.training` package: `TrainingIds.kt`, enums, `TrainingSession`, `LoggedSet` with required `liftContext` field (default factory for transition tests only in data layer) | WL-001, WL-002 |
| 1.2 | Remove `HistoricalSession` / `HistoricalSessionId` from `ImportModels.kt` and `ImportIds.kt`; update all domain/import references to `TrainingSession` / `TrainingSessionId` | WL-001 |
| 1.3 | Create `TrainingSessionEntity` (`tableName = "training_sessions"`) with all columns; remove `HistoricalSessionEntity` from `ImportEntities.kt` | WL-001 |
| 1.4 | Extend `LoggedSetEntity` with snapshot columns; update FK parent to `TrainingSessionEntity` | WL-002 |
| 1.5 | Add `current_cycle_number`, `cycle_position` to `ProgramEntity` and `ProgramMapper` | WL-001 |
| 1.6 | Add `EnumConverters` for `TrainingSessionId`, `ProgramChangeEventId`, `TrainingSessionStatus`, `TrainingSessionKind`, `TrainingSessionOrigin` | WL-003 |
| 1.7 | Create `TrainingLogMapper` for session/set entity ↔ domain; wire snapshot column mapping | WL-001, WL-002 |
| 1.8 | Create `TrainingLogDaos.kt` (`TrainingSessionDao`, `LoggedSetDao` incremental queries); update `StratumDatabase` entity/DAO list; remove old session DAO from `ImportDaos.kt` | WL-001, WL-002 |
| 1.9 | Rename `TrainingHistoryRepository` → `TrainingLogRepository` (port signature only; CRUD bodies in slice 3); update `RoomTrainingLogRepository` shell with `replaceAll` + reads | WL-001 |
| 1.10 | Update `EnumConvertersTest`, `ProgramSchemaContractTest`, `LoggedSetDaoContractTest` for new table/column names | WL-001, WL-002, WL-003 |
| 1.11 | Regenerate `1.json` schema export; verify `./gradlew :app:kspDebugKotlin` | WL-001 |

**Slice 1 checkpoint:** `./gradlew :core:data:testDebugUnitTest --tests "*EnumConverters*" --tests "*SchemaContract*" --tests "*LoggedSetDao*"` and `./gradlew :app:kspDebugKotlin`.

### Slice 2 — Import snapshots + baseline volume credit

| Task | Description | Scenarios |
|------|-------------|-----------|
| 2.1 | Create `LoggedLiftContext.kt` and `LoggedLiftContextFactory` with `fromLiftCatalog(lift, exerciseNameRaw)` using `LiftMapper` bitmask helpers | WL-004, WL-005 |
| 2.2 | Update `PersistImportedHistoryUseCase`: map import sessions to `TrainingSession` (`origin = IMPORT`, `status = COMPLETED`, `programId` from active program); attach `liftContext` via factory after `resolveImportLifts` | WL-004, WL-005 |
| 2.3 | Ensure import `LoggedLiftContext` has null `workoutLiftId`, `progressionScheme`, `setType`, `repMin`, `repMax`, `rirGoal` | WL-005 |
| 2.4 | Add `MuscleVolumeCredit.accumulateSetCredits(liftContext, weekStart, muscleWeekCredits)` decoding bitmasks; keep existing `Lift` overload temporarily | WL-006, WL-007, WL-044 |
| 2.5 | Refactor `TrainingInsightsExtractor.computeVolumeBaselines` to use `LoggedLiftContext` on sets, not `liftById` join | WL-006, WL-007 |
| 2.6 | Update `PersistImportedHistoryUseCaseResponseTest`, `MuscleVolumeCreditTest`, `TrainingInsightsExtractorTest` | WL-004, WL-005, WL-006, WL-007, WL-044 |

**Slice 2 checkpoint:** `./gradlew :core:domain:test --tests "*PersistImported*" --tests "*MuscleVolumeCredit*" --tests "*TrainingInsightsExtractor*"`.

### Slice 3 — Incremental training-log repository

| Task | Description | Scenarios |
|------|-------------|-----------|
| 3.1 | Implement `TrainingLogRepository` incremental methods in `RoomTrainingLogRepository` | WL-008, WL-009, WL-010 |
| 3.2 | Add `observeActiveSession(): Flow<TrainingSession?>` query (`status = IN_PROGRESS`, `LIMIT 1`) | WL-011, WL-012 |
| 3.3 | Add `getCompletedSessionsForCycle(programId, cycleNumber)` filtered query | WL-013 |
| 3.4 | Create `FakeTrainingLogRepository` with in-memory CRUD + `observeActiveSession` | WL-008–WL-013 |
| 3.5 | Update `DataModule` binding; rename fake references across tests | WL-008–WL-013 |
| 3.6 | Add `RoomTrainingLogRepositoryTest` and `FakeTrainingLogRepositoryTest` | WL-008–WL-013 |

**Slice 3 checkpoint:** `./gradlew :core:data:testDebugUnitTest --tests "*LoggedSetDao*" --tests "*TrainingLog*"` plus fake unit test.

### Slice 4 — Cycle pointer + ledger resolver

| Task | Description | Scenarios |
|------|-------------|-----------|
| 4.1 | Add `currentCycleNumber`, `cyclePosition` to domain `Program` with defaults (1, 0) on generation/read paths | WL-014 |
| 4.2 | Implement `ProgramRepository.updateCyclePointer` in `RoomProgramRepository` | WL-024, WL-029 |
| 4.3 | Implement `CycleProgressResolver.isWorkoutCompletedThisCycle` — true when `COMPLETED` session exists for `(program.id, program.currentCycleNumber, workoutId)` | WL-015 |
| 4.4 | Implement `CycleProgressResolver.resolveNextWorkout` — first template workout by `position` without completion session this cycle | WL-014, WL-018, WL-019 |
| 4.5 | Implement `CycleProgressResolver.isCycleComplete` — every current-template workout has `COMPLETED` session for `currentCycleNumber` | WL-016, WL-030 |
| 4.6 | Implement `CycleProgressResolver.reconcileCyclePosition` — count distinct completed workout sessions in ledger; ledger wins over cache | WL-017, WL-044 |
| 4.7 | Add `CycleProgressResolverTest` covering mid-cycle template delete/add/replace | WL-014–WL-019, WL-044 |
| 4.8 | Extend `RoomProgramRepositoryTest` for cycle pointer persistence | WL-024 |

**Slice 4 checkpoint:** `./gradlew :core:domain:test --tests "*CycleProgress*"`.

### Slice 5 — Session lifecycle

| Task | Description | Scenarios |
|------|-------------|-----------|
| 5.1 | Implement `StartTrainingSessionUseCase`: check `observeActiveSession` / `getAllSessions` for `IN_PROGRESS`; throw `ActiveTrainingSessionExistsException` if found — Q3 | WL-020, WL-021, WL-022 |
| 5.2 | `ProgramWorkout` request: create `IN_PROGRESS`, `kind = PROGRAM_WORKOUT`, `workout_id` set, `cycle_number_snapshot = program.currentCycleNumber`, `origin = LIVE` | WL-020 |
| 5.3 | `AdHocEmpty` request: create `IN_PROGRESS`, `kind = AD_HOC`, `workout_id = null`, `origin = LIVE` | WL-021 |
| 5.4 | Implement `FinalizeTrainingSessionUseCase`: set `status = COMPLETED`, `endedAt`; for `PROGRAM_WORKOUT` increment `cycle_position`; if `isCycleComplete` bump `current_cycle_number`, reset `cycle_position` to 0 (defer `CYCLE_COMPLETED` event to slice 8) | WL-023, WL-024, WL-025, WL-030, WL-039 |
| 5.5 | `AD_HOC` finalize: `COMPLETED` + `endedAt` only; no pointer change | WL-025 |
| 5.6 | Implement `AbandonTrainingSessionUseCase`: `deleteSession` (CASCADE sets) | WL-026 |
| 5.7 | Add use-case tests with `FakeTrainingLogRepository` + `FakeProgramRepository` | WL-020–WL-026, WL-039 |

**Slice 5 checkpoint:** `./gradlew :core:domain:test --tests "*TrainingSession*"`.

### Slice 6 — Zero-set skip / rest sessions

| Task | Description | Scenarios |
|------|-------------|-----------|
| 6.1 | Implement `RecordWorkoutSkipUseCase`: insert `COMPLETED` session, `kind = PROGRAM_WORKOUT`, `workout_id` set, zero sets, `endedAt = now`; advance `cycle_position` / cycle wrap same as finalize | WL-027, WL-029, WL-030 |
| 6.2 | Implement `RecordRestDaySkipUseCase`: insert `COMPLETED`, `kind = REST_DAY`, `workout_id = null`, `weekday_snapshot` set, zero sets; advance pointer | WL-028, WL-029 |
| 6.3 | Integrate `CycleProgressResolver.resolveNextWorkout` after skip — next pending workout advances | WL-027, WL-028 |
| 6.4 | Add skip use-case tests + resolver integration cases | WL-027–WL-030 |

**Slice 6 checkpoint:** `./gradlew :core:domain:test --tests "*Skip*"`.

### Slice 7 — Live set logging

| Task | Description | Scenarios |
|------|-------------|-----------|
| 7.1 | Implement `LoggedLiftContextFactory.fromLiveTemplate(lift, workoutLift, templateSet)` populating template fields | WL-033 |
| 7.2 | Implement `LogSetUseCase`: reject non-`IN_PROGRESS` session; build snapshot via factory; `insertSet` | WL-031, WL-032, WL-033, WL-034 |
| 7.3 | When `workoutLiftId` null (ad-hoc), use `fromLiftCatalog` only | WL-033 |
| 7.4 | Add `LogSetUseCaseTest` asserting template fields populated on live path, null on import fixtures | WL-031–WL-034 |

**Slice 7 checkpoint:** `./gradlew :core:domain:test --tests "*LogSet*"`.

### Slice 8 — Program change events

| Task | Description | Scenarios |
|------|-------------|-----------|
| 8.1 | Create `ProgramChangeEventEntity`, `ProgramChangeEventDao`, `RoomProgramChangeEventRepository`, `FakeProgramChangeEventRepository` | WL-035–WL-040 |
| 8.2 | Wire `SwapWorkoutLiftUseCase` to `append(LIFT_SWAPPED)` with payload | WL-035 |
| 8.3 | Wire `SaveWorkoutUseCase` to `append(WORKOUT_EDITED)` | WL-036 |
| 8.4 | Wire `GenerateProgramUseCase` to `append(PROGRAM_REGENERATED)` | WL-037 |
| 8.5 | Wire `FinalizeTrainingSessionUseCase` + skip use cases to `append(CYCLE_COMPLETED)` when cycle wraps | WL-038, WL-030 |
| 8.6 | Verify individual workout finalize/skip does not append non-cycle events | WL-039 |
| 8.7 | Verify `getEventsForProgram` returns append-only history (prior events preserved) | WL-040 |
| 8.8 | Add `ProgramChangeEventDaoContractTest`; extend mutation use-case tests | WL-035–WL-040 |

**Slice 8 checkpoint:** `./gradlew :core:domain:test --tests "*Swap*" --tests "*SaveWorkout*" --tests "*ChangeEvent*"` and `./gradlew :core:data:testDebugUnitTest --tests "*ProgramChange*"`.

### Slice 9 — Response analytics completion

| Task | Description | Scenarios |
|------|-------------|-----------|
| 9.1 | Refactor `StimulusSeriesBuilder` to credit from `LoggedLiftContext`; skip sets with zero bitmasks only | WL-041 |
| 9.2 | Refactor `MuscleWeeklyStimulusBuilder` similarly | WL-041 |
| 9.3 | Update `LiftEffectivenessAnalyzer` — retain `liftId` filter where needed; volume from snapshots | WL-043 |
| 9.4 | Update `DefaultResponseObservationExtractor` snapshot paths | WL-041 |
| 9.5 | Update `ExtractResponseProfileUseCase` — do not require live `Lift` list for volume/stimulus paths | WL-042, WL-044 |
| 9.6 | Remove `MuscleVolumeCredit` `Lift` overload | WL-006, WL-007 |
| 9.7 | Update `ResponseExtractionInput` and all `*Response*` / `*Stimulus*` / `ExtractResponseProfile*` tests | WL-041–WL-044 |

**Slice 9 checkpoint:** `./gradlew :core:domain:test --tests "*Response*" --tests "*Stimulus*"` and `./gradlew detekt`.

---

## CI gate (before merge)

```text
./gradlew detekt
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew :core:domain:test
```

---

## Out of scope

- `:feature:workout` Compose UI and navigation wiring
- Programs screen callback wiring (`MainTabShell`, `ProgramsViewModel` methods)
- Weekly LLM check-in Callable/UI
- Post-release Room migrations

`ObserveCycleProgressUseCase` is a domain read hook only — no feature-module UI in this ticket.

---

## Self-review

- [x] All 9 vertical slices have ordered tasks with scenario references.
- [x] File map covers every create/modify/delete from `structure.md`.
- [x] Binding decisions Q1–Q7 reflected in tasks and contracts.
- [x] No placeholders (`TBD`, `TODO`, `similar to`).
- [x] Signatures verified against repo (`HistoricalSession` in `ImportModels.kt`, `TrainingHistoryRepository` in `ImportPorts.kt`, `Weekday` in `TrainingProfileEnums.kt`, `KinematicProfile`/`MuscleLengthBias` in `com.stratum.domain.lift`).
