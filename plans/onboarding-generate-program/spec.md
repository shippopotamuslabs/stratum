# Spec — Onboarding Generate Program (OS-7)

**Ticket:** `onboarding-generate-program` | **Phase:** Plan (Synthitect)
**Status:** awaiting human approval
**Grounded in:** `plans/onboarding-generate-program/design.md` (approved 2026-06-26, D1–D4),
`plans/onboarding-generate-program/structure.md` (approved 2026-06-26),
`plans/onboarding-generate-program/research.md` (verified),
`docs/onboarding-vertical-slices.md` §OS-7 + §2.3/§2.4/§2.5/§2.7,
`AGENTS.md` §4.

This document is the tactical implementation spec. It expands the approved 6-slice
structure into ordered tasks, file maps, contracts, and scenario references. It does not
re-open D1–D4. No runnable code; signatures, type shapes, and API contracts only.

---

## 1. Feature summary (from approved design)

A user reaches the last onboarding step (**Generate**) and taps **Generate program**. A single
bounded server call (`generateProgram` Firebase Callable, Auth + App Check, one generation per
onboarding completion) sends the assembled onboarding payload (built by the existing
`LoadProgramGenerationPayloadUseCase`) to OpenRouter (`OPENROUTER_MODEL` = `deepseek/deepseek-v4-pro`)
under a strict JSON schema; the model returns one structured `Program`. The server then runs a
deterministic **validate-and-clamp** post-pass (assign `progressionScheme` from `TrainingAge`,
clamp back-off RIR to the §2.3 lift-class table, enforce straight sets) and returns the normalized
document. `:core:inference` parses the response into a domain `Program`; `ProgramRepository`
persists the full hierarchy (Program → Workout → WorkoutLift → Set) in four normalized Room tables
in one transaction. On success, onboarding is marked complete and the user lands on a new
read-only **Programs** screen in a new `:feature:program` module. On failure, onboarding is **not**
marked complete and the Generate step shows retry + back-to-review.

Design decisions honored:
- **D1** — normalized 4-table persistence; DB stays at **v1** (tables added to v1 entity set, no
  version bump, no migration, reinstall to apply).
- **D2** — single-shot generation + deterministic post-pass; one retry on schema/parse failure.
- **D3** — read-only Programs screen in `:feature:program` as the post-onboarding landing.
- **D4** — Generate CTA on the existing `WIZARD_STEP_GENERATE`, replacing the stub button.

---

## 2. Vertical slice table (from structure.md)

| # | Slice | Goal (one line) | Primary decision |
|---|-------|-----------------|------------------|
| 1 | Domain core + fake-driven use case | `GenerateProgramUseCase` turns a payload into a persisted `Program` via fakes; JVM test green. | D2 (orchestration), D4 (entry point) |
| 2 | Normalized Room persistence at DB v1 | A real `Program` hierarchy round-trips through 4 new v1 tables in one transaction. | D1 |
| 3 | Read-only Programs screen + routing | New `:feature:program` observes the repo `Flow` and renders Program → Workout → WorkoutLift → Set, reachable via Nav3. | D3 |
| 4 | Firebase Callable client | `FirebaseProgramGenerator` parses a recorded Callable response into domain `Program`; `InferenceModule` binds it. | D2 (client), AGENTS.md §4.6 |
| 5 | Generate step wiring | Generate CTA calls the use case with loading/error states, persists, completes onboarding, navigates to Programs. | D4 |
| 6 | Server `generateProgram` Callable | One OpenRouter completion under a strict schema + deterministic validate-and-clamp post-pass; `node --test` green; deploy enables live happy path. | D2 (post-pass) |

Slice dependency order: `1 → 2 → 3 → 5 → 6`; `1 → 4 → 5`. DB stays v1 in every slice.

---

## 3. File structure table (Create / Modify)

Paths are absolute under repo root. `C` = create, `M` = modify.

### `:core:domain`

| Action | Path | Responsibility |
|--------|------|----------------|
| C | `core/domain/src/main/kotlin/com/stratum/domain/program/identity/ProgramId.kt` | `@JvmInline value class ProgramId(override val value: String) : EntityId` (non-blank `init`). |
| C | `…/program/identity/WorkoutId.kt` | Same shape, `WorkoutId`. |
| C | `…/program/identity/WorkoutLiftId.kt` | Same shape, `WorkoutLiftId`. |
| C | `…/program/identity/SetId.kt` | Same shape, `SetId`. |
| C | `…/program/ProgramEnums.kt` | `enum class SetType { Standard, MyoRep, DropSet }`; `enum class ProgressionScheme { Linear, Double }` (only these variants). |
| C | `…/program/Program.kt` | `Program`, `Workout`, `WorkoutLift`, `Set` data classes (hierarchy; leaf named `LiftSet` to avoid `kotlin.collections.Set` collision — finalized in Task 1.2). |
| C | `…/program/ProgramGeneratorPort.kt` | `interface ProgramGeneratorPort { suspend fun generateProgram(payload: String): Result<Program> }`. |
| C | `…/program/ProgramRepository.kt` | `interface ProgramRepository { fun observeProgram(): Flow<Program?>; suspend fun getProgram(): Program?; suspend fun saveProgram(program: Program) }`. |
| C | `…/program/GenerateProgramUseCase.kt` | Orchestrator: load payload → generate → persist → complete onboarding. |

(No change to `LoadProgramGenerationPayloadUseCase` or `ProgramGenerationPromptBuilder` — they are inputs.)

### `:core:testing`

| Action | Path | Responsibility |
|--------|------|----------------|
| C | `core/testing/src/main/kotlin/com/stratum/core/testing/program/FakeProgramGeneratorPort.kt` | In-memory `ProgramGeneratorPort`; configurable success/failure + invocation capture. |
| C | `…/program/FakeProgramRepository.kt` | In-memory `ProgramRepository` over `MutableStateFlow<Program?>`; `saveProgram` overwrites (replace semantics). |

### `:core:data`

| Action | Path | Responsibility |
|--------|------|----------------|
| C | `core/data/src/main/kotlin/com/stratum/core/data/db/ProgramEntities.kt` | `ProgramEntity`, `WorkoutEntity`, `WorkoutLiftEntity`, `SetEntity` (`@Entity`, value-class PKs, FK columns, enum columns). |
| C | `…/db/ProgramDaos.kt` | `ProgramDao`, `WorkoutDao`, `WorkoutLiftDao`, `SetDao` (`insertAll`, `deleteAll`, reads). |
| C | `…/mapper/ProgramMapper.kt` | `toDomain` / `toEntity` for the 4 entities. |
| C | `…/repository/RoomProgramRepository.kt` | `RoomProgramRepository @Inject constructor(database, 4 daos) : ProgramRepository`; `saveProgram` = `withTransaction { deleteAll four; singleton upsert root; replaceAll children }`. |
| M | `…/db/EnumConverters.kt` | Add `ProgressionScheme`, `SetType`, and 4 typed-id converter pairs (`ProgramId`, `WorkoutId`, `WorkoutLiftId`, `SetId`). |
| M | `…/db/StratumDatabase.kt` | Add the 4 entities to `entities = […]` + 4 abstract DAO accessors; **`version` stays 1**. |

### `:core:inference`

| Action | Path | Responsibility |
|--------|------|----------------|
| C | `core/inference/src/main/kotlin/com/stratum/core/inference/FirebaseProgramGenerator.kt` | `@Singleton class FirebaseProgramGenerator @Inject constructor() : ProgramGeneratorPort`; Callable client; `internal parseProgram(data)` assigns UUID IDs. |
| M | `…/InferenceModule.kt` | Add `@Binds @Singleton ProgramGeneratorPort` → `FirebaseProgramGenerator`. |

### `:app`

| Action | Path | Responsibility |
|--------|------|----------------|
| M | `app/src/main/java/com/stratum/di/DataModule.kt` | `DatabaseModule`: `@Provides` for the 4 program DAOs. `RepositoryModule`: `@Binds ProgramRepository → RoomProgramRepository`. |
| M | `app/src/main/java/com/stratum/navigation/StratumNavHost.kt` | Register `programsEntry`; on completion remove `OnboardingKey` + add `ProgramsKey`; post-`isComplete==true` start key = `ProgramsKey`. |
| M | `app/build.gradle.kts` | `implementation(project(":feature:program"))`. |
| M | `settings.gradle.kts` | `include(":feature:program")`. |

### `:feature:program` (new module)

| Action | Path | Responsibility |
|--------|------|----------------|
| C | `feature/program/build.gradle.kts` | Android library module; depends on `:core:domain`, `:core:ui`; Compose + Hilt + Nav3 + serialization. |
| C | `…/navigation/ProgramsKey.kt` | `@Serializable data object ProgramsKey : NavKey` + `EntryProviderScope<NavKey>.programsEntry()`. |
| C | `…/ProgramsViewModel.kt` | `@HiltViewModel @Inject constructor(repository: ProgramRepository)`; exposes `programsUiState: StateFlow<ProgramsUiState>` from `observeProgram()`. |
| C | `…/ProgramsRoute.kt` | `@Composable fun ProgramsRoute(viewModel)`; collects `programsUiState`, renders `ProgramsScreen`. |
| C | `…/ui/ProgramsUiState.kt` | `ProgramsUiState` + presentation models (Program/Workout/WorkoutLift/Set view states). |
| C | `…/ui/ProgramsScreen.kt` (+ child composables) | Read-only header → workout list → expandable WorkoutLifts → Sets. Light + Dark `@Preview`. |
| C | `…/res/values/strings.xml` | Programs screen strings. |

### `:feature:onboarding`

| Action | Path | Responsibility |
|--------|------|----------------|
| M | `feature/onboarding/src/main/kotlin/com/stratum/feature/onboarding/OnboardingViewModel.kt` | Replace `complete()` with `generate()`; inject `GenerateProgramUseCase`; add generate UI state (`Idle | Loading | Error`). |
| M | `feature/onboarding/src/main/kotlin/com/stratum/feature/onboarding/OnboardingRoute.kt` | `GenerateScreen(onGenerate, state)` with loading ("Building your program…") + error (retry + back-to-review). |
| M | `feature/onboarding/src/main/res/values/strings.xml` | Loading/error copy. |

### `functions/`

| Action | Path | Responsibility |
|--------|------|----------------|
| C | `functions/src/program/generateProgram.ts` | `export const generateProgram = onCall({ secrets: [...] }, async (request) => …)`. Auth gate, secret read, OpenRouter call, one retry, rate limit, returns normalized program. |
| C | `functions/src/program/programSchema.ts` | `programJsonSchema()` (strict) + `parseAndNormalizeProgram()` (validate + clamp). |
| C | `functions/src/program/programRules.ts` | §2.3 RIR table + `TrainingAge → ProgressionScheme` rule. |
| C | `functions/src/program/programGenerationSystemPrompt.ts` | Versioned system prompt (§2.7). |
| C | `functions/src/program/__fixtures__/*.json` | Recorded OpenRouter response fixtures. |
| C | `functions/src/program/generateProgram.test.ts` | `node --test`; retry-once + error paths. |
| C | `functions/src/program/programSchema.test.ts` | `node --test`; deserializer + post-pass assertions on fixtures. |
| M | `functions/src/index.ts` | Re-export `generateProgram`. |

---

## 4. API contracts and interfaces (signatures from structure.md "Public contracts")

### 4.1 Domain models (`:core:domain`, package `com.stratum.domain.program`)

Value-class IDs (each `@JvmInline value class …(override val value: String) : EntityId`,
non-blank `init` — mirrors `core/domain/.../lift/identity/LiftId.kt`):
```
ProgramId | WorkoutId | WorkoutLiftId | SetId
```

Enums (OS-7 only; do NOT port LiftLab's 5-variant enum):
```
enum class SetType { Standard, MyoRep, DropSet }
enum class ProgressionScheme { Linear, Double }
```

Hierarchy (field names are load-bearing for the schema + mapper):
```
data class Program(id: ProgramId, name: String, createdAt: Instant, workouts: List<Workout>)
data class Workout(id: WorkoutId, programId: ProgramId, name: String,
                   weekday: Weekday?, position: Int, workoutLifts: List<WorkoutLift>)
data class WorkoutLift(id: WorkoutLiftId, workoutId: WorkoutId, liftId: LiftId,
                       progressionScheme: ProgressionScheme, position: Int, sets: List<LiftSet>)
data class LiftSet(id: SetId, workoutLiftId: WorkoutLiftId, position: Int, setType: SetType,
                      repMin: Int, repMax: Int, rirGoal: Int, isTopSet: Boolean)
```
- `weekday` nullable ("weekday-bound where possible", D3).
- The leaf class is named **`LiftSet`** (not `Set`) to avoid the `kotlin.collections.Set`
  collision in-package. The entity is `SetEntity`; the table is `sets`.
- `LiftSet` carries **no weight/load field** (straight sets — single load per lift; D2).
- `progressionScheme` lives on `WorkoutLift` and is **server-assigned** from `TrainingAge` (D2); the
  model never authors it.

### 4.2 Ports

```
interface ProgramGeneratorPort {
    suspend fun generateProgram(payload: String): Result<Program>
}

interface ProgramRepository {
    fun observeProgram(): Flow<Program?>
    suspend fun getProgram(): Program?
    suspend fun saveProgram(program: Program)
}
```
- `payload` is the JSON string produced by `LoadProgramGenerationPayloadUseCase.invoke(): String`.
- `Result<T>` / `Flow` mirror existing inference/repo port conventions.

### 4.3 Use case

`CompleteOnboardingUseCase.invoke` takes a `UserTrainingProfile` (verified signature), but
`GenerateProgramUseCase` must not reach into the ViewModel for it. The use case obtains the
persisted profile itself:

```
class GenerateProgramUseCase @Inject constructor(
    private val loadPayload: LoadProgramGenerationPayloadUseCase,
    private val generator: ProgramGeneratorPort,
    private val repository: ProgramRepository,
    private val completeOnboarding: CompleteOnboardingUseCase,
    private val profileRepository: UserTrainingProfileRepository,
) {
    suspend operator fun invoke(): Result<Unit>
}
```
Flow: `val payload = loadPayload()`; `val program = generator.generateProgram(payload).getOrThrow()`;
`repository.saveProgram(program)`;
`completeOnboarding(profileRepository.observeProfile().first() ?: UserTrainingProfile.Empty)`;
return `Result.success(Unit)`. Any exception → `Result.failure(exception)`. Order is strict: persist
**before** complete so onboarding is never complete without a stored program; the `getOrThrow`
ordering guarantees no persist on generation failure. `javax.inject.Inject` only.

### 4.4 Server strict JSON schema — `programJsonSchema()` (field-level)

OpenRouter `json_schema`, `strict: true`, `additionalProperties: false` at every object, closed
`enum`s (mirrors `inferImportEquipmentSchema.ts`). Top level:
```
program: object {
  name: string
  workouts: array of object {
    name: string
    weekday: string|null  (enum: weekday names ∪ null)
    position: integer
    workoutLifts: array of object {
      liftId: string
      position: integer
      // progressionScheme is OMITTED — server assigns it in the post-pass (D2)
      workoutLiftSets: array of object {
        position: integer
        setType: string  (enum: ["Standard","MyoRep","DropSet"])
        repMin: integer
        repMax: integer
        rirGoal: integer
        isTopSet: boolean
      }  (required: all listed)
    }  (required: liftId, position, workoutLiftSets)
  }  (required: name, weekday, position, workoutLifts)
}  (required: name, workouts)
```
The model returns structure only; `progressionScheme`, clamped `rirGoal`, and straight-set
discipline are imposed by `parseAndNormalizeProgram` (Slice 6).

### 4.5 `generateProgram` Callable (server) — signature

```
export const generateProgram = onCall(
  { secrets: ["OPENROUTER_API_KEY", "OPENROUTER_MODEL"] },
  async (request): Promise<{ program: NormalizedProgram }>
)
```
- Input: `request.data as { payload: string }`. The `payload` string is forwarded verbatim as the
  OpenRouter user message content. The server extracts `trainingAge` and `enabledLiftIds` (the union
  of `provenLifts[].id` + `availableLifts[].id`) plus `movementPattern` per lift from the parsed
  payload for the post-pass.
- Auth (`request.auth` → `unauthenticated`) + App Check enforced; per-uid
  one-generation-per-completion rate limit.
- Single completion under `programJsonSchema()`; **one retry** on schema/parse failure; second
  failure throws `internal`.
- Returns the normalized `Program` document; `FirebaseProgramGenerator` parses it client-side.

### 4.6 `FirebaseProgramGenerator` (client) — signature

```
@Singleton
class FirebaseProgramGenerator @Inject constructor() : ProgramGeneratorPort {
    override suspend fun generateProgram(payload: String): Result<Program>
    // internal parseProgram(data): Program  — assigns UUID value-class IDs at parse time
}
```
Bound in `InferenceModule` via:
```
@Binds @Singleton
abstract fun bindProgramGeneratorPort(impl: FirebaseProgramGenerator): ProgramGeneratorPort
```
Callable invocation:
```
functions.getHttpsCallable("generateProgram")
    .call(mapOf("payload" to payload))
    .await()
```
The client sends only `{ payload }`; `enabledLiftIds` and `trainingAge` are already embedded in the
payload JSON by `LoadProgramGenerationPayloadUseCase`/`ProgramGenerationPromptBuilder`.

---

## 5. Data model / schema changes (DB stays v1)

### 5.1 Entities (4 new, added to v1 entity set — no version bump, no migration)

Column names are load-bearing for the mapper and schema export.

**`programs`** (`ProgramEntity`):
- `id: String` (PK) ← `ProgramId`
- `name: String NOT NULL`
- `created_at: String NOT NULL` (ISO-8601 `Instant` string)

**`workouts`** (`WorkoutEntity`):
- `id: String` (PK) ← `WorkoutId`
- `program_id: String NOT NULL` (FK → `programs.id`, `onDelete CASCADE`)
- `name: String NOT NULL`
- `weekday: String NULL` (← `Weekday?.name`; null when unbound)
- `position: Int NOT NULL`

**`workout_lifts`** (`WorkoutLiftEntity`):
- `id: String` (PK) ← `WorkoutLiftId`
- `workout_id: String NOT NULL` (FK → `workouts.id`, `onDelete CASCADE`)
- `lift_id: String NOT NULL` ← `LiftId`
- `progression_scheme: String NOT NULL` ← `ProgressionScheme.name`
- `position: Int NOT NULL`

**`sets`** (`SetEntity`):
- `id: String` (PK) ← `SetId`
- `workout_lift_id: String NOT NULL` (FK → `workout_lifts.id`, `onDelete CASCADE`)
- `position: Int NOT NULL`
- `set_type: String NOT NULL` ← `SetType.name`
- `rep_min: Int NOT NULL`
- `rep_max: Int NOT NULL`
- `rir_goal: Int NOT NULL`
- `is_top_set: Int NOT NULL` (0/1 boolean — Room stores `Boolean` as INTEGER)

Each entity indexes its FK column. The `sets` table is named `sets` (entity `SetEntity`).

### 5.2 EnumConverters additions

Add to the existing `EnumConverters` class:
```
@TypeConverter fun fromProgressionScheme(s: ProgressionScheme?): String? = s?.name
@TypeConverter fun toProgressionScheme(v: String?): ProgressionScheme? = v?.let { runCatching { ProgressionScheme.valueOf(it) }.getOrNull() }

@TypeConverter fun fromSetType(t: SetType?): String? = t?.name
@TypeConverter fun toSetType(v: String?): SetType? = v?.let { runCatching { SetType.valueOf(it) }.getOrNull() }
```
Plus 4 typed-id converter pairs (mirroring `fromLiftId/toLiftId`) for `ProgramId`, `WorkoutId`,
`WorkoutLiftId`, `SetId`: each unwraps `.value` to SQL and re-wraps with `runCatching` on decode.

### 5.3 `StratumDatabase` change

Add the 4 entities to the `entities = […]` array and 4 abstract DAO accessors
(`programDao()`, `workoutDao()`, `workoutLiftDao()`, `setDao()`). **`version = 1` is untouched.**
The exported schema `core/data/schemas/com.stratum.core.data.db.StratumDatabase/1.json` regenerates
with the four new tables; no `2.json` is produced.

### 5.4 Repository write semantics

`RoomProgramRepository.saveProgram(program)`:
```
database.withTransaction {
    setDao.deleteAll()
    workoutLiftDao.deleteAll()
    workoutDao.deleteAll()
    programDao.deleteAll()
    programDao.upsert(program.toEntity())
    workoutDao.insertAll(program.workouts.map { it.toEntity() })
    workoutLiftDao.insertAll(program.workouts.flatMap { it.workoutLifts }.map { wl -> wl.toEntity() })
    setDao.insertAll(program.workouts.flatMap { it.workoutLifts }.flatMap { it.sets }.map { s -> s.toEntity() })
}
```
This mirrors `RoomResponseProfilePersistenceRepository.replaceExtractedProfile` (delete-all-then-insert
in one transaction). `saveProgram` **replaces** the program (mutation-defense scenario PROG-GEN-203).
`getProgram()` / `observeProgram()` rejoin the 4 tables into a `Program?` via `ProgramMapper`.

---

## 6. Ordered implementation tasks (grouped by slice)

Each task lists the scenario IDs it satisfies. Scenario IDs are defined in `test_spec.md`.

### Slice 1 — Domain core + fake-driven use case (JVM)

**Task 1.1 — Create value-class IDs + enums.** Satisfies: PROG-GEN-101, PROG-GEN-102.
Create `ProgramId`, `WorkoutId`, `WorkoutLiftId`, `SetId` (each `@JvmInline value class … : EntityId`,
non-blank `init`) and `ProgramEnums.kt` (`SetType { Standard, MyoRep, DropSet }`,
`ProgressionScheme { Linear, Double }`). No behavior beyond validation.

**Task 1.2 — Create domain hierarchy.** Satisfies: PROG-GEN-101.
Create `Program.kt` with `Program`, `Workout`, `WorkoutLift`, `LiftSet` (leaf named `LiftSet`).
No weight/load field; `weekday: Weekday?`.

**Task 1.3 — Create `ProgramGeneratorPort` + `ProgramRepository` + `GenerateProgramUseCase`.**
Satisfies: PROG-GEN-100, PROG-GEN-201, PROG-GEN-202, PROG-GEN-204.
- `ProgramGeneratorPort.generateProgram(payload: String): Result<Program>`.
- `ProgramRepository.observeProgram(): Flow<Program?>`, `getProgram(): Program?`, `saveProgram(Program)`.
- `GenerateProgramUseCase @Inject constructor(loadPayload, generator, repository, completeOnboarding,
  profileRepository)` per §4.3. Order is strict: load → generate (`getOrThrow`) → persist → complete.
  Any exception → `Result.failure`; no persist, no completion on failure.

**Task 1.4 — Create `FakeProgramGeneratorPort` + `FakeProgramRepository`.**
Satisfies: PROG-GEN-100, PROG-GEN-201, PROG-GEN-202, PROG-GEN-204.
- `FakeProgramGeneratorPort(program: Program? = null, fail: Boolean = false)` — returns configured
  `Result`; captures `invokedPayload`.
- `FakeProgramRepository` — `MutableStateFlow<Program?>(null)`; `saveProgram` sets `value = program`
  (overwrite); `saveProgramCalls: List<Program>`.

**Checkpoint:** `./gradlew :core:domain:test --tests "*GenerateProgramUseCaseTest*"` green.

### Slice 2 — Normalized Room persistence at DB v1

**Task 2.1 — Create 4 entities.** Satisfies: PROG-GEN-301.
Create `ProgramEntities.kt` with the 4 entities per §5.1. FKs with `onDelete CASCADE`; indexes on FK
columns.

**Task 2.2 — Create 4 DAOs.** Satisfies: PROG-GEN-301, PROG-GEN-302.
`ProgramDaos.kt`: each DAO has `@Insert(onConflict = REPLACE) suspend fun insertAll(items: List<…Entity>)`,
`@Query("DELETE FROM <table>") suspend fun deleteAll()`, plus reads:
- `ProgramDao`: `@Query("SELECT * FROM programs") suspend fun get(): ProgramEntity?`,
  `upsert` via `@Insert(onConflict = REPLACE)`.
- `WorkoutDao`: `getForProgram(programId)`.
- `WorkoutLiftDao`: `getForWorkout(workoutId)`.
- `SetDao`: `getForWorkoutLift(workoutLiftId)`.

**Task 2.3 — Create `ProgramMapper`.** Satisfies: PROG-GEN-301.
`ProgramMapper.kt`: `toEntity`/`toDomain` for all 4 entities. `Instant` ↔ ISO-8601 string.
`Boolean` ↔ `Int` for `is_top_set`.

**Task 2.4 — Create `RoomProgramRepository`.** Satisfies: PROG-GEN-302, PROG-GEN-203.
`RoomProgramRepository @Inject constructor(database, programDao, workoutDao, workoutLiftDao, setDao)`.
`saveProgram` = transaction in §5.4. `getProgram()` rejoins; `observeProgram()` emits a `Flow` that
rejoins the four tables (match `observeLifts()` semantics). Replace-not-append is guaranteed by the
delete-all-then-insert in the transaction.

**Task 2.5 — Modify `EnumConverters` + `StratumDatabase`.** Satisfies: PROG-GEN-301.
Add converters (§5.2); add 4 entities + 4 DAO accessors; **version stays 1**.

**Task 2.6 — Modify `DataModule`.** Satisfies: PROG-GEN-302.
`DatabaseModule`: `@Provides` for `programDao/workoutDao/workoutLiftDao/setDao`.
`RepositoryModule`: `@Binds @Singleton ProgramRepository → RoomProgramRepository`.

**Checkpoint:** `./gradlew :core:data:testDebugUnitTest` green (Robolectric `ProgramDaoTest` +
`RoomProgramRepositoryTest` round-trip + replace semantics); `./gradlew :app:kspDebugKotlin` green;
only `1.json` exists post-build.

### Slice 3 — Read-only Programs screen + routing (`:feature:program`)

**Task 3.1 — Create `:feature:program` module.** Satisfies: PROG-GEN-401, PROG-GEN-402.
`feature/program/build.gradle.kts` (Android lib; `:core:domain`, `:core:ui`; Compose, Hilt, Nav3,
serialization — mirror `:feature:onboarding`'s plugin/dependency block, minus `:core:inference`).
Add to `settings.gradle.kts`; add `implementation(project(":feature:program"))` to `app/build.gradle.kts`.

**Task 3.2 — Create `ProgramsKey` + `programsEntry`.** Satisfies: PROG-GEN-403.
`navigation/ProgramsKey.kt`: `@Serializable data object ProgramsKey : NavKey` +
`EntryProviderScope<NavKey>.programsEntry()` rendering `ProgramsRoute()` (mirrors `OnboardingKey`).

**Task 3.3 — Create `ProgramsViewModel` + `ProgramsUiState`.** Satisfies: PROG-GEN-400, PROG-GEN-401.
`ProgramsViewModel @HiltViewModel @Inject constructor(repository: ProgramRepository)`: maps
`repository.observeProgram()` to `programsUiState: StateFlow<ProgramsUiState>` (Idle/Empty/Loaded).
Domain models are never passed to composables.

**Task 3.4 — Create `ProgramsRoute` + `ProgramsScreen` (+ children) + strings.**
Satisfies: PROG-GEN-401, PROG-GEN-402.
Read-only: header (program name) → workout list (weekday-bound) → expandable WorkoutLifts → Sets
(rep range, RIR goal, set type). `StratumTheme` + `Stratum*` only; 48dp touch targets; tabular
figures. Light + Dark `@Preview` in `StratumTheme { }`. No edit/swap/regenerate affordances.

**Task 3.5 — Modify `StratumNavHost`.** Satisfies: PROG-GEN-403.
Register `programsEntry`; on `onboardingEntry` completion remove `OnboardingKey` + add `ProgramsKey`;
post-`isComplete==true` start key = `ProgramsKey` (replacing `HomeKey`). `HomePlaceholder` is
superseded as the landing.

**Checkpoint:** `./gradlew :feature:program:testDebugUnitTest` green (`ProgramsViewModelTest` with
`FakeProgramRepository` + Turbine); `./gradlew :app:assembleDebug` green; `./gradlew detekt` green.

### Slice 4 — Firebase Callable client (`FirebaseProgramGenerator`)

**Task 4.1 — Create `FirebaseProgramGenerator`.** Satisfies: PROG-GEN-501, PROG-GEN-502, PROG-GEN-503.
`FirebaseProgramGenerator @Singleton @Inject constructor() : ProgramGeneratorPort`. Field-init
`FirebaseFunctions.getInstance()` / `FirebaseAuth.getInstance()`. `generateProgram(payload) =
runCatching { requireAuth(); val result = functions.getHttpsCallable("generateProgram")
.call(mapOf("payload" to payload)).await(); parseProgram(result.data) }`.
`requireAuth()` = `checkNotNull(auth.currentUser) { "Sign-in required for program generation." }`.

**Task 4.2 — `internal parseProgram(data: Any?): Program`.** Satisfies: PROG-GEN-500, PROG-GEN-501, PROG-GEN-502, PROG-GEN-503.
Maps the response `Map` (`program.name`, `program.workouts[]` with nested `workoutLifts[]` and
`workoutLiftSets[]`) to the domain hierarchy. Assigns UUID value-class IDs client-side at parse time
(`ProgramId(UUID.randomUUID().toString())`, etc.). Maps `weekday` string → `Weekday?` (null on
unknown), `setType` string → `SetType` (reject/drop row on unknown), `isTopSet` → `Boolean`.
Drops/rejects rows missing required fields; throws on a top-level malformed response.

**Task 4.3 — Modify `InferenceModule`.** Satisfies: PROG-GEN-501.
Add `@Binds @Singleton abstract fun bindProgramGeneratorPort(impl: FirebaseProgramGenerator):
ProgramGeneratorPort`.

**Checkpoint:** `./gradlew :core:inference:testDebugUnitTest` green (recorded-fixture `parseProgram`
contract test).

### Slice 5 — Generate step wiring (loading / error + navigation)

**Task 5.1 — Modify `OnboardingViewModel`.** Satisfies: PROG-GEN-600, PROG-GEN-601, PROG-GEN-602, PROG-GEN-603.
Inject `generateProgram: GenerateProgramUseCase`. Add `fun generate()` launching
`viewModelScope.launch { generateState = Loading; val result = generateProgram(); generateState =
if (result.isSuccess) { /* isComplete flips via repo */ } else Error }`.
Expose `generateState` on `OnboardingUiState` (`Idle | Loading | Error`). On success,
`OnboardingStatusViewModel.isComplete` flips true (driven by `completeOnboarding` persisting the
profile + marking onboarding complete), which the existing `LaunchedEffect(state.isComplete)` →
`onComplete()` consumes. Remove/repurpose `complete()` so the Generate button no longer bypasses
generation.

**Task 5.2 — Modify `GenerateScreen`.** Satisfies: PROG-GEN-600, PROG-GEN-602, PROG-GEN-603.
`GenerateScreen(onGenerate: () -> Unit, generateState: GenerateState)`: Idle shows the Generate CTA;
Loading shows "Building your program…" (disable CTA, no back-to-review during loading); Error shows
retry + back-to-review (retry calls `onGenerate` again; back-to-review calls `viewModel::back`).
Success is implicit (navigation happens via the existing `onComplete` effect).

**Task 5.3 — Modify strings.** Satisfies: PROG-GEN-600, PROG-GEN-602.
Add `onboarding_generate_loading` ("Building your program…"), `onboarding_generate_error_title`,
`onboarding_generate_error_retry`, `onboarding_generate_error_back_to_review`.

**Task 5.4 — Verify `OnboardingViewModelTestFixtures` wiring.** Satisfies: PROG-GEN-600, PROG-GEN-601, PROG-GEN-602, PROG-GEN-603.
Extend the fixtures object to construct `OnboardingViewModel` with the new `GenerateProgramUseCase`
backed by `FakeProgramGeneratorPort` + `FakeProgramRepository`.

**Checkpoint:** `./gradlew :feature:onboarding:testDebugUnitTest` green (`OnboardingViewModelTest`
generate success → Loading then `isComplete == true`; failure → Error, repo untouched,
`isComplete == false`); `./gradlew :app:assembleDebug` green.

### Slice 6 — Server `generateProgram` Callable + deterministic validate-and-clamp (D2)

**Task 6.1 — Create `programRules.ts`.** Satisfies: PROG-GEN-700, PROG-GEN-703, PROG-GEN-704, PROG-GEN-705.
Export:
- `MAJOR_COMPOUND_PATTERNS: Set<string>` = exactly
  `{"LegPush","HipHinge","HorizontalPush","VerticalPush","InclinePush","HorizontalPull","VerticalPull"}`.
- `schemeForTrainingAge(trainingAge: string): ProgressionScheme` →
  `trainingAge === "Beginner" ? "Linear" : "Double"`. (Mutation-defense: this is the sole authority
  for `progressionScheme`; the model never authors it.)
- `clampBackOffRir(isMajorCompound: boolean, proposed: number): number` → major compound:
  `proposed` clamped to `1..2`, default `2` when out of range; everything else: `0`.
- `TOP_SET_RIR = 2` (constant).
- A `movementPatternByLiftId` lookup populated from the payload's `provenLifts`/`availableLifts`
  (`movementPattern` per lift) so the post-pass can classify each `WorkoutLift`'s lift id as
  major-compound or not.

**Task 6.2 — Create `programSchema.ts` (`programJsonSchema()` + `parseAndNormalizeProgram`).**
Satisfies: PROG-GEN-700, PROG-GEN-701, PROG-GEN-702, PROG-GEN-703, PROG-GEN-704, PROG-GEN-705, PROG-GEN-706, PROG-GEN-707.
- `programJsonSchema()`: strict per §4.4 (`strict: true`, `additionalProperties: false`, closed
  enums). `progressionScheme` is **omitted** from the schema (server assigns it).
- `parseAndNormalizeProgram(payload, request): NormalizedProgram` runs the D2 post-pass:

  **Validate (throw on violation):**
  1. Schema-conformant (JSON parses; required fields present).
  2. Every `workoutLift.liftId ∈ enabledLiftIds` (extracted from payload) — else drop the lift; if a
     workout ends with zero valid lifts, drop the workout.
  3. `program.workouts.length >= 1` after validation — else throw (callable maps to `invalid-argument`).

  **Normalize (overwrite model-derived numerics):**
  4. For each `WorkoutLift`: `progressionScheme = schemeForTrainingAge(trainingAge)` — the model's
     proposed scheme is discarded (not even in the schema).
  5. For each `WorkoutLift`, classify its lift as major-compound via `movementPatternByLiftId`.
  6. For each set:
     - If `progressionScheme === "Double"` and `isTopSet === true`: `rirGoal = TOP_SET_RIR` (2).
     - If `progressionScheme === "Double"` and `isTopSet === false`: `rirGoal =
       clampBackOffRir(isMajorCompound, proposedRir)`.
     - If `progressionScheme === "Linear"`: `rirGoal = clampBackOffRir(isMajorCompound, proposedRir)`
       (Linear has no top-set/back-off split, but RIR is still clamped to the lift-class table —
       major compound 1..2 default 2, else 0).
  7. **Enforce straight sets:** strip any within-set weight field (none is in the schema, so this is
     a defensive strip of any extra fields); every set in a `WorkoutLift` shares one load.
  8. **Double top-set rule:** after scheme assignment, if `progressionScheme === "Double"`, the lift
     must have **exactly one** set with `isTopSet === true` and that set must carry a rep range
     (`repMin`/`repMax`). Violation → validation failure (triggers the one retry).

  Returns the normalized `Program` object (without ids — client assigns ids at parse time).

**Task 6.3 — Create `programGenerationSystemPrompt.ts`.**
Export a versioned system prompt string carrying §2.3 progression rules, split templates, MEV/MRV
landmarks, push/pull balance, and time-budget heuristics (§2.7).

**Task 6.4 — Create `generateProgram.ts`.** Satisfies: PROG-GEN-700, PROG-GEN-701, PROG-GEN-702, PROG-GEN-706, PROG-GEN-707.
`export const generateProgram = onCall({ secrets: ["OPENROUTER_API_KEY","OPENROUTER_MODEL"] },
async (request) => { … })`. Mirror `inferImportEquipment.ts`:
- `if (!request.auth) throw new HttpsError("unauthenticated", "Sign-in required.")`.
- Read `data as { payload: string }`; non-string/empty payload → `invalid-argument`.
- `apiKey = process.env.OPENROUTER_API_KEY` (missing → `failed-precondition` with message
  `"OPENROUTER_API_KEY missing."`); `model = process.env.OPENROUTER_MODEL ?? "deepseek/deepseek-v4-pro"`.
- Extract `trainingAge` and `enabledLiftIds` (union of `provenLifts[].id` + `availableLifts[].id`)
  from the parsed `payload` for the post-pass.
- Sticky session id (`programGenerationSessionId(uid)` from Task 6.5).
- `client.chat.completions.create({ model, messages: [{system},{user: data.payload}],
  response_format: { type: "json_schema", json_schema: programJsonSchema() },
  ...buildOpenRouterChatCompletionExtras(sessionId, { requireParameters: true }) })`.
- `content = completion.choices[0]?.message?.content`; empty → `internal`.
- `parseAndNormalizeProgram(JSON.parse(content), { trainingAge, enabledLiftIds })`; on throw →
  **retry once** (second completion); on second failure → `internal`.
- Per-uid one-generation-per-completion rate limit (minimal v1: deny if a
  `programs_generation/{uid}` doc exists). Reject a second call per uid within an onboarding completion.
- Return `{ program: normalizedProgram }`.

**Task 6.5 — Add `programGenerationSessionId` to `openrouterSession.ts`.**
Satisfies: PROG-GEN-700.
`export function programGenerationSessionId(uid: string): string { return
sanitizeOpenRouterSessionId(\`stratum-program-generate:\${uid}\`); }`.

**Task 6.6 — Modify `functions/src/index.ts`.** Satisfies: PROG-GEN-700.
Re-export `generateProgram`.

**Checkpoint:** `npm --prefix functions test` green (`programSchema.test.ts` deserializes recorded
fixtures and asserts the post-pass output; `generateProgram.test.ts` covers retry-once + error
paths). Deploy enables the live happy path.

---

## 7. Drift and escalation rules (for later phases)

- **Scope creep guard:** any task that adds edit/swap/regenerate UI, a DB migration, a `2.json`
  schema export, a v2 DB version, a server tool loop, an on-device LLM call, or a `NavController`
  passed into a composable is **out of scope** and must be escalated to the maintainer before
  implementation. These violate D1–D4 / AGENTS.md §4.1/§4.5/§4.6.
- **Signature drift:** if the verified signatures in §4 differ from what implementation needs, stop
  and re-confirm with the maintainer; do not silently change a port or use-case signature. In
  particular, `CompleteOnboardingUseCase.invoke(finalProfile: UserTrainingProfile)` is fixed; the
  `GenerateProgramUseCase` must obtain the profile itself (§4.3).
- **Server post-pass drift:** the rules in Task 6.1/6.2 are normative (§2.3). Any deviation (e.g.,
  different major-compound list, different default RIR, trusting model `progressionScheme`) breaks
  the mutation-defense scenarios and must be escalated.
- **DB v1 invariant:** if any task requires `StratumDatabase.version` to change or a `Migration` to
  be authored, escalate — the maintainer-approved policy is reinstall-to-apply, v1 unchanged (D1).
- **Live-LLM-in-CI:** no task may hit the live OpenRouter API in CI. Slice 6 tests use recorded
  fixtures only (AGENTS.md §4.6). The live happy path is exercised only post-deploy, manually.
- **Scenario coverage:** if a behavior-changing task is added during implementation that is not
  covered by a scenario ID, escalate before merging — every behavior-changing task must reference at
  least one scenario ID (Self-Review rule).

---

## 8. Self-review

- **D1** — Tasks 2.1–2.6 (v1 4-table, no migration, no version bump). PASS.
- **D2** — Tasks 6.1–6.2 (single-shot + deterministic validate-and-clamp) + 1.3 (use-case order).
  PASS.
- **D3** — Tasks 3.1–3.5 (read-only `:feature:program`, routing to `ProgramsKey`). PASS.
- **D4** — Tasks 5.1–5.4 (Generate CTA on existing step replacing stub). PASS.
- **Slice order** — Tasks 1.x → 2.x → 3.x → 4.x → 5.x → 6.x, matching structure.md checkpoints.
  PASS.
- **Every behavior-changing task references ≥1 scenario ID.** PASS (see each task).
- **DB v1 everywhere** — no task authors a Migration or bumps the version; §5.3 states version
  untouched. PASS.
- **No placeholders.** PASS.
- **Names/signatures match spec.md and test_spec.md.** PASS (verified: `GenerateProgramUseCase`,
  `ProgramGeneratorPort`, `ProgramRepository`, `FirebaseProgramGenerator`, `ProgramsKey`,
  `ProgramsViewModel`, `generateProgram` callable, `parseAndNormalizeProgram`,
  `schemeForTrainingAge`, `clampBackOffRir`).
