# Structure — Onboarding Generate Program (OS-7)

**Ticket:** `onboarding-generate-program` | **Phase:** Structure (Synthitect)
**Status:** awaiting human approval
**Grounded in:** `plans/onboarding-generate-program/design.md` (approved 2026-06-26, D1–D4),
`plans/onboarding-generate-program/research.md` (verified), `docs/onboarding-vertical-slices.md` §OS-7,
`AGENTS.md` §4.

This is a C-header for the feature: signatures, types, vertical slices, and checkpoints.
Implementation bodies, the full test list, and step-by-step tasks belong to Plan. All file
paths are real and cited from research.md.

---

## Slice ordering principle

Each slice lands a thin vertical path that is independently runnable/testable end-to-end
before the next begins. The LLM-call seam is exploited: the deterministic + local path
(domain → persist → read-only Programs screen → Generate-step wiring) is landed and verified
with fakes / recorded fixtures and no live LLM, and the real server `generateProgram` Callable
(the only piece that needs a live model to fully exercise the happy path) is the final slice.

**DB stays at v1 across every slice.** The four new tables are added to the v1 entity set and
picked up via reinstall (D1). No `Migration` is authored anywhere; no `2.json` schema export is
produced. `StratumDatabase.version = 1` is untouched.

---

## Slice index

| # | Slice | Goal (one line) |
|---|-------|-----------------|
| 1 | Domain core + fake-driven use case | `GenerateProgramUseCase` turns a payload into a persisted `Program` via fakes; JVM test green. |
| 2 | Normalized Room persistence at DB v1 | A real `Program` hierarchy round-trips through 4 new v1 tables in one transaction. |
| 3 | Read-only Programs screen + routing | New `:feature:program` observes the repo `Flow` and renders Program → Workout → WorkoutLift → Set, reachable via Nav3. |
| 4 | Firebase Callable client | `FirebaseProgramGenerator` parses a recorded Callable response into domain `Program`; `InferenceModule` binds it. |
| 5 | Generate step wiring | Generate CTA calls the use case with loading/error states, persists, completes onboarding, navigates to Programs. |
| 6 | Server `generateProgram` Callable | One OpenRouter completion under a strict schema + deterministic validate-and-clamp post-pass; `node --test` green; deploy enables live happy path. |

---

## Slice 1 — Domain core + fake-driven use case (JVM)

**Goal:** the entire generation pipeline runs on the JVM with a fake generator and a fake repo.

**Components touched**
- `:core:domain` (create) — program hierarchy models, enums, value-class IDs, ports, use case.
- `:core:testing` (create) — `FakeProgramGeneratorPort`, `FakeProgramRepository`.

**New types / signatures** (see "Public contracts" below for full shapes)
- `ProgramId`, `WorkoutId`, `WorkoutLiftId`, `SetId` (value classes over `EntityId`).
- `SetType { Standard, MyoRep, DropSet }`, `ProgressionScheme { Linear, Double }`.
- `Program`, `Workout`, `WorkoutLift`, `Set`.
- `ProgramGeneratorPort.generateProgram(payload: String): Result<Program>`.
- `ProgramRepository.observeProgram(): Flow<Program?>`, `saveProgram(Program)`, `getProgram(): Program?`.
- `GenerateProgramUseCase.invoke(): Result<Unit>` (load payload → generate → persist → complete onboarding).

**Checkpoint**
- `./gradlew :core:domain:test --tests "*GenerateProgramUseCaseTest*"` green: happy path
  (fake generator returns a `Program` → repo receives it → onboarding marked complete → `Result.success`),
  and failure path (generator returns `Result.failure` → repo not written → onboarding NOT complete → `Result.failure`).
- End-to-end vertical on pure JVM; no Android, no network, no Room.

---

## Slice 2 — Normalized Room persistence at DB v1

**Goal:** a real `Program` (with children) persists to and reads from Room through four new
normalized tables, written in one transaction, with the database still at version 1.

**Components touched**
- `:core:data` (create) — 4 entities, 4 DAOs, `ProgramMapper`, `RoomProgramRepository`.
- `:core:data` (modify) — `EnumConverters`, `StratumDatabase` entity set + DAO accessors.
- `:app` (modify) — `DataModule`: `DatabaseModule` `@Provides` for the 4 DAOs;
  `RepositoryModule` `@Binds ProgramRepository → RoomProgramRepository`.

**New types / signatures**
- `ProgramEntity`, `WorkoutEntity`, `WorkoutLiftEntity`, `SetEntity` (`@Entity`, value-class PKs,
  FK columns parent→child; enum columns `progressionScheme`, `setType`).
- `ProgramDao`, `WorkoutDao`, `WorkoutLiftDao`, `SetDao` — each `insertAll`, `deleteAll`,
  plus the reads the repo needs (e.g. `getProgram()`, `getWorkoutsFor(programId)`, etc.).
- `EnumConverters`: `fromProgressionScheme/toProgressionScheme`, `fromSetType/toSetType`, and
  typed-id converters for the four new IDs (mirrors the existing `fromLiftId/toLiftId` pair).
- `RoomProgramRepository.saveProgram(program)` = `database.withTransaction { deleteAll four;
  insert program root (singleton upsert); replaceAll children }` — mirrors
  `RoomResponseProfilePersistenceRepository.replaceExtractedProfile`.

**Schema rule (D1, hard):** add the four entities to `StratumDatabase`'s `entities = […]`
array. **Do not** bump `version` (stays `1`) and **do not** add a `Migration`. The exported
schema file `core/data/schemas/com.stratum.core.data.db.StratumDatabase/1.json` regenerates
with the four new tables; no `2.json` is created.

**Checkpoint**
- `./gradlew :core:data:testDebugUnitTest` green: Robolectric `ProgramDaoTest` +
  `RoomProgramRepositoryTest` round-trip (insert hierarchy → read back → equals domain model;
  second `saveProgram` replaces, not appends).
- `./gradlew :app:kspDebugKotlin` green (Room/KSP compiles with the new entities).
- Confirm only `1.json` exists post-build (no v2/migration authored).

---

## Slice 3 — Read-only Programs screen + routing (`:feature:program`)

**Goal:** a new module renders the persisted program read-only and is the post-onboarding
landing destination. Verified with a fake repo before any generation trigger exists.

**Components touched**
- `:feature:program` (create module) — `build.gradle.kts`, Nav3 key + entry, ViewModel, route,
  screen + child composables, presentation models, strings.
- `:app` (modify) — `settings.gradle.kts` `include(":feature:program")`;
  `app/build.gradle.kts` `implementation(project(":feature:program"))`;
  `StratumNavHost` registers `programsEntry` and routes completion to `ProgramsKey`.
- `:core:domain` — consumes the existing `ProgramRepository.observeProgram()` (no new code).

**New types / signatures**
- `ProgramsKey : NavKey` (`@Serializable data object`) + `EntryProviderScope<NavKey>.programsEntry()`
  (mirrors `OnboardingKey` / `onboardingEntry`).
- `ProgramsViewModel @HiltViewModel @Inject constructor(repository: ProgramRepository)` —
  exposes `programsUiState: StateFlow<ProgramsUiState>` from `observeProgram()`.
- `ProgramsUiState` + presentation models (Program/Workout/WorkoutLift/Set view states) —
  domain models are never passed to composables (ARCHITECTURE.md §4.1).
- `ProgramsRoute`, `ProgramsScreen` (header → workout list → expandable WorkoutLifts → Sets),
  Light + Dark `@Preview` in `StratumTheme { }`.

**UI contract (D3):** read-only overview only. No edit / swap / regenerate affordances in OS-7.
`StratumTheme` + `Stratum*` components only; 48dp touch targets; tabular figures for rep/RIR
data. Detekt `stratum` UI rules must pass.

**Routing change (D3):** on onboarding completion, `StratumNavHost` removes `OnboardingKey` and
adds `ProgramsKey` (replacing today's `HomeKey`). The post-`isComplete == true` start key
becomes `ProgramsKey`. `HomePlaceholder` is superseded as the landing.

**Checkpoint**
- `./gradlew :feature:program:testDebugUnitTest` green: `ProgramsViewModelTest` with
  `FakeProgramRepository` + Turbine (empty state, populated state, WorkoutLift expansion).
- `./gradlew :app:assembleDebug` green; `./gradlew detekt` green (UI rules).
- Read-only Programs screen reachable when a program is present.

---

## Slice 4 — Firebase Callable client (`FirebaseProgramGenerator`)

**Goal:** the Callable client parses a recorded response into a domain `Program` and is bound
in DI. Verified by a recorded-fixture contract test — no live LLM, no deployed server required.

**Components touched**
- `:core:inference` (create) — `FirebaseProgramGenerator`.
- `:core:inference` (modify) — `InferenceModule` adds `@Binds @Singleton ProgramGeneratorPort`.

**New types / signatures**
- `FirebaseProgramGenerator @Singleton @Inject constructor() : ProgramGeneratorPort` —
  field-init `FirebaseFunctions.getInstance()` / `FirebaseAuth.getInstance()`; `requireAuth()`
  via `auth.currentUser`; `functions.getHttpsCallable("generateProgram").call(mapOf("payload" to payload)).await()`;
  `Result<Program> = runCatching { … }` (mirrors `FirebaseImportEquipmentInferenceAdapter`).
- `internal fun parseProgram(data: Any?): Program` — maps the response `Map` to the domain
  hierarchy and **assigns value-class IDs (UUID) client-side at parse time** (D1 / "Patterns
  to follow"). Drops/rejects rows missing required fields.

**Checkpoint**
- `./gradlew :core:inference:testDebugUnitTest` green: recorded-fixture test feeds a captured
  Callable response `Map` through `parseProgram` and asserts the domain `Program` (ids assigned,
  enums mapped, hierarchy intact, malformed rows rejected).

---

## Slice 5 — Generate step wiring (loading / error + navigation)

**Goal:** the existing Generate step's stub button becomes the generation trigger (D4). Tapping
it runs the full pipeline; loading and error states are surfaced; success completes onboarding
and lands on the Programs screen.

**Components touched**
- `:feature:onboarding` (modify) — `OnboardingViewModel` (generate flow + UI state),
  `OnboardingRoute` / `GenerateScreen` (loading + error UI), strings.
- `:feature:onboarding` `build.gradle.kts` — already depends on `:core:inference` and
  `:core:domain`; `GenerateProgramUseCase` is now injected into the ViewModel.

**New / changed signatures**
- `OnboardingViewModel`: replace `complete()` with a generate entry point (e.g. `fun generate()`)
  launching `generateProgramUseCase()` in `viewModelScope`; add a generate-state field on
  `OnboardingUiState` (`Idle | Loading | Error`).
- `GenerateScreen(onGenerate, state)` — loading copy ("Building your program…"), error state
  with **retry + back-to-review** (D4). Success drives the existing `state.isComplete` effect
  → `onComplete` → `StratumNavHost` navigates to `ProgramsKey`.
- `GenerateProgramUseCase.invoke(): Result<Unit>` — on success: `repository.saveProgram(program)`
  then `completeOnboarding` (compose `CompleteOnboardingUseCase` or inline its two calls, per
  design "Notes carried to Structure"). On failure: return `Result.failure`, do NOT mark complete.

**Checkpoint**
- `./gradlew :feature:onboarding:testDebugUnitTest` green: `OnboardingViewModelTest` —
  generate success → Loading then `isComplete == true` (and `FakeProgramRepository` received the
  program); generate failure → Error state, repo untouched, `isComplete == false`.
- `./gradlew :app:assembleDebug` green — the whole local vertical is wired. End-to-end runnable
  on device/emulator; against the not-yet-deployed server it exercises the real error→retry path.

---

## Slice 6 — Server `generateProgram` Callable + deterministic validate-and-clamp (D2)

**Goal:** the server performs one OpenRouter completion under a strict JSON schema, then
deterministically normalizes before returning. This is the only slice requiring a live model to
exercise the happy path; it is verified with recorded fixtures + `node --test` until deploy.

**Components touched**
- `functions/` (create) — `functions/src/program/` package.
- `functions/` (modify) — `functions/src/index.ts` re-exports `generateProgram`.

**New files / signatures**
- `functions/src/program/generateProgram.ts` — `export const generateProgram = onCall(
  { secrets: ["OPENROUTER_API_KEY", "OPENROUTER_MODEL"] }, async (request) => { … } )`.
  Auth gate (`request.auth`), App Check enforced at deploy, reads `process.env.OPENROUTER_API_KEY`
  (`failed-precondition` if missing) + `OPENROUTER_MODEL ?? "deepseek/deepseek-v4-pro"`,
  sticky session id, `createOpenRouterClient` + `buildOpenRouterChatCompletionExtras(sessionId,
  { requireParameters: true })`, strict `response_format: { type: "json_schema",
  json_schema: programJsonSchema() }`, `JSON.parse` + normalizer, **one retry** on schema/parse
  failure, per-uid "one generation per onboarding completion" rate limit. Mirrors
  `functions/src/import/inferImportEquipment.ts`.
- `functions/src/program/programSchema.ts` — `programJsonSchema()` (strict; field-level shape
  below) + the parser/normalizer (`parseAndNormalizeProgram`).
- `functions/src/program/programRules.ts` — the §2.3 lift-class RIR table + the
  `TrainingAge → ProgressionScheme` rule consumed by the post-pass.
- `functions/src/program/programGenerationSystemPrompt.ts` (or bundled asset) — versioned system
  prompt carrying §2.3 progression rules, split templates, MEV/MRV landmarks, push/pull balance,
  time-budget heuristics (§2.7).
- `functions/src/program/generateProgram.test.ts`, `programSchema.test.ts`, and a
  `__fixtures__/` dir of recorded OpenRouter responses (no live API in CI).

**Deterministic validate-and-clamp post-pass (D2 — must be in this slice):**
- **Validate:** schema-conformant; every `workoutLift.liftId ∈ request.enabledLiftIds`;
  ≥ 1 workout; (post-scheme-assignment) Double lifts have exactly one top set carrying a rep range.
- **Normalize (overwrite the model's derived numerics):**
  - `progressionScheme` per `WorkoutLift` ← `TrainingAge` (`Beginner → Linear`, else `Double`); the model's proposed scheme is discarded.
  - Clamp back-off `rirGoal` to the §2.3 lift-class table (major compound 1–2, default 2; everything else 0); top-set `rirGoal ← 2` on Double.
  - Enforce straight sets (single load per lift; any within-set weight field is dropped/rejected).
- Returns `{ program: <normalized Program JSON> }`.

**Checkpoint**
- `npm --prefix functions test` green: `programSchema.test.ts` deserializes recorded fixtures
  and asserts the post-pass output (scheme assigned from `TrainingAge`, RIR clamped, straight
  sets enforced, unknown lift ids rejected); `generateProgram.test.ts` covers retry-once + error
  paths at the unit level.
- Deploy enables the live happy path; final acceptance end-to-end (fresh install → sign in →
  complete onboarding → generate → program exists in DB → Programs screen shows program name /
  first workout).

---

## Public contracts

### Domain models (`:core:domain`, package `com.stratum.domain.program`)

Value-class IDs (each `@JvmInline value class …(override val value: String) : EntityId`,
non-blank `init` — mirrors `core/domain/.../lift/identity/LiftId.kt`):
```
ProgramId | WorkoutId | WorkoutLiftId | SetId
```

Enums:
```
enum class SetType { Standard, MyoRep, DropSet }            // OS-7 only
enum class ProgressionScheme { Linear, Double }             // OS-7 only (NOT LiftLab's 5)
```

Hierarchy (signatures only; field names are load-bearing for the schema + mapper):
```
data class Program(id: ProgramId, name: String, createdAt: Instant, workouts: List<Workout>)
data class Workout(id: WorkoutId, programId: ProgramId, name: String,
                   weekday: Weekday?, position: Int, workoutLifts: List<WorkoutLift>)
data class WorkoutLift(id: WorkoutLiftId, workoutId: WorkoutId, liftId: LiftId,
                       progressionScheme: ProgressionScheme, position: Int, sets: List<Set>)
data class Set(id: SetId, workoutLiftId: WorkoutLiftId, position: Int, setType: SetType,
               repMin: Int, repMax: Int, rirGoal: Int, isTopSet: Boolean)
```
- `weekday` nullable ("weekday-bound where possible", D3).
- `Set` carries **no weight/load field** (straight sets — single load per lift; D2).
- `progressionScheme` lives on `WorkoutLift` and is **server-assigned** from `TrainingAge`
  (D2); the model never authors it.
- Naming note: the domain class `Set` collides with `kotlin.collections.Set` inside the
  `com.stratum.domain.program` package — `kotlin.collections.Set` must be fully qualified where
  needed. The entity is `SetEntity`; the table is `sets`. (Plan finalizes `Set` vs `LiftSet`.)

### Ports
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
- `payload` is the JSON string produced by the existing
  `LoadProgramGenerationPayloadUseCase.invoke(): String`.
- `Result<T>` / `Flow` mirror existing inference/repo port conventions (research Findings 7, 15).

### Use case
```
class GenerateProgramUseCase @Inject constructor(
    private val loadPayload: LoadProgramGenerationPayloadUseCase,
    private val generator: ProgramGeneratorPort,
    private val repository: ProgramRepository,
    private val completeOnboarding: CompleteOnboardingUseCase,
) {
    suspend operator fun invoke(): Result<Unit>
}
```
On success: `saveProgram(program)` then `completeOnboarding`. On any failure: return
`Result.failure` and do NOT mark onboarding complete. `javax.inject.Inject` only (AGENTS.md §4.1).

### Server strict JSON schema — `programJsonSchema()` (field-level)
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

### `generateProgram` Callable (server) — signature
```
export const generateProgram = onCall(
  { secrets: ["OPENROUTER_API_KEY", "OPENROUTER_MODEL"] },
  async (request): Promise<{ program: NormalizedProgram }>
)
```
- Input: `request.data as { payload: string; enabledLiftIds: string[]; trainingAge: string }`
  (the inputs the post-pass needs to validate lift membership and assign the scheme).
- Auth + App Check enforced; per-uid one-generation-per-completion rate limit.
- Single completion under `programJsonSchema()`; one retry on schema/parse failure.
- Returns the normalized `Program` document; `FirebaseProgramGenerator` parses it client-side.

### `FirebaseProgramGenerator` (client) — signature
```
@Singleton
class FirebaseProgramGenerator @Inject constructor() : ProgramGeneratorPort {
    override suspend fun generateProgram(payload: String): Result<Program>
    // internal parseProgram(data): Program  — assigns UUID value-class IDs at parse time
}
```
Bound in `InferenceModule` via `@Binds @Singleton abstract fun bindProgramGeneratorPort(
impl: FirebaseProgramGenerator): ProgramGeneratorPort`.

---

## File map preview (Create / Modify)

### `:core:domain` — Create
- `core/domain/src/main/kotlin/com/stratum/domain/program/identity/ProgramId.kt`
- `…/identity/WorkoutId.kt`
- `…/identity/WorkoutLiftId.kt`
- `…/identity/SetId.kt`
- `core/domain/src/main/kotlin/com/stratum/domain/program/Program.kt` (`Program`, `Workout`, `WorkoutLift`, `Set`)
- `…/program/ProgramEnums.kt` (`SetType`, `ProgressionScheme`)
- `…/program/ProgramGeneratorPort.kt`
- `…/program/ProgramRepository.kt`
- `…/program/GenerateProgramUseCase.kt`
- (No change to the existing `LoadProgramGenerationPayloadUseCase` / `ProgramGenerationPromptBuilder` — they are inputs.)

### `:core:testing` — Create
- `core/testing/src/main/kotlin/com/stratum/core/testing/program/FakeProgramGeneratorPort.kt`
- `…/program/FakeProgramRepository.kt`

### `:core:data` — Create
- `core/data/src/main/kotlin/com/stratum/core/data/db/ProgramEntities.kt` (`ProgramEntity`, `WorkoutEntity`, `WorkoutLiftEntity`, `SetEntity`)
- `…/db/ProgramDaos.kt` (`ProgramDao`, `WorkoutDao`, `WorkoutLiftDao`, `SetDao`)
- `…/mapper/ProgramMapper.kt` (`toDomain` / `toEntity` for the 4 entities)

### `:core:data` — Modify
- `…/db/EnumConverters.kt` — add `ProgressionScheme`, `SetType`, and 4 typed-id converter pairs.
- `…/db/StratumDatabase.kt` — add the 4 entities to `entities = […]` + 4 DAO accessors; **version stays 1**.

### `:core:inference` — Create / Modify
- Create `core/inference/src/main/kotlin/com/stratum/core/inference/FirebaseProgramGenerator.kt`
- Modify `…/InferenceModule.kt` — `@Binds` `ProgramGeneratorPort`.

### `:app` — Modify
- `app/src/main/java/com/stratum/di/DataModule.kt` — `DatabaseModule` `@Provides` for the 4 DAOs; `RepositoryModule` `@Binds ProgramRepository → RoomProgramRepository` (create `RoomProgramRepository` under `core/data/.../repository/`).
- `app/src/main/java/com/stratum/navigation/StratumNavHost.kt` — register `programsEntry`; completion + post-`isComplete` start key → `ProgramsKey` (replaces `HomeKey`).
- `app/build.gradle.kts` — `implementation(project(":feature:program"))`.
- `settings.gradle.kts` — `include(":feature:program")`.

### `:feature:program` — Create (new module)
- `feature/program/build.gradle.kts`
- `…/navigation/ProgramsKey.kt` (`ProgramsKey` + `programsEntry`)
- `…/ProgramsViewModel.kt`, `…/ProgramsRoute.kt`
- `…/ui/ProgramsScreen.kt` (+ child composables), `…/ui/ProgramsUiState.kt`
- `…/res/values/strings.xml`

### `:feature:onboarding` — Modify
- `…/OnboardingViewModel.kt` — generate flow + generate UI state.
- `…/OnboardingRoute.kt` (and an extracted `ui/GenerateScreen.kt` if preferred) — loading + error UI.
- `…/res/values/strings.xml` — loading/error copy.

### `functions/` — Create / Modify
- Create `functions/src/program/generateProgram.ts`
- Create `functions/src/program/programSchema.ts` (`programJsonSchema()` + `parseAndNormalizeProgram`)
- Create `functions/src/program/programRules.ts` (§2.3 RIR table + `TrainingAge → ProgressionScheme`)
- Create `functions/src/program/programGenerationSystemPrompt.ts` (versioned system prompt)
- Create `functions/src/program/generateProgram.test.ts`, `programSchema.test.ts`, `__fixtures__/`
- Modify `functions/src/index.ts` — re-export `generateProgram`

---

## Test checkpoints (cumulative)

| After slice | Runnable check |
|-------------|----------------|
| 1 | `:core:domain:test --tests "*GenerateProgramUseCaseTest*"` — fake generator happy + failure. |
| 2 | `:core:data:testDebugUnitTest` — Robolectric 4-table round-trip + `RoomProgramRepository` replace semantics; `:app:kspDebugKotlin` green; only `1.json` exists. |
| 3 | `:feature:program:testDebugUnitTest` — `ProgramsViewModelTest` (Turbine + `FakeProgramRepository`); `:app:assembleDebug` + `detekt` green. |
| 4 | `:core:inference:testDebugUnitTest` — recorded-fixture `parseProgram` contract test. |
| 5 | `:feature:onboarding:testDebugUnitTest` — `OnboardingViewModelTest` generate success/failure; `:app:assembleDebug` green (full local vertical wired). |
| 6 | `npm --prefix functions test` — schema/normalizer + retry-once/error unit tests with recorded fixtures; deploy enables live end-to-end acceptance. |

---

## Dependencies between slices

```
1 ──► 2 ──► 3 ──► 5 ──► 6
1 ──► 4 ──► 5
```
- **2 → 1:** persistence maps the domain models from slice 1.
- **3 → 1, 2:** the screen consumes `ProgramRepository.observeProgram()` (slice 1) and the real
  binding (slice 2) for the assembled build; the ViewModel unit test needs only slice 1's fake.
- **4 → 1:** the client returns domain `Program` and implements `ProgramGeneratorPort`.
- **5 → 1, 2, 3, 4:** the Generate step calls `GenerateProgramUseCase` (1), persists via the
  repo binding (2), navigates to `ProgramsKey` (3), and the port is bound to
  `FirebaseProgramGenerator` (4).
- **6 ↔ 4:** co-designed response shape (the client's `parseProgram` mirrors the server's
  normalized output). Slice 6 is unit-tested with recorded fixtures independently; the live
  contract is exercised only after 6 is deployed.

---

## Self-review

- Every slice is vertically integrated with an end-to-end-runnable checkpoint (JVM pipeline,
  DB round-trip, screen-from-Flow, fixture→domain, tap→persist→navigate, request→model→normalize).
- A checkpoint exists after each slice, not only at the end.
- File map entries map 1:1 to D1 (slice 2, v1 normalized 4-table), D2 (slice 6, single-shot +
  deterministic post-pass), D3 (slice 3, read-only `:feature:program`), D4 (slice 5, Generate
  CTA on the existing Generate step).
- No slice is a horizontal "all of one layer" phase: slice 2 is a domain↔DB round-trip vertical;
  slice 4 is one client with its contract test and is consumed by slice 5.
- The D2 deterministic validate-and-clamp post-pass is located explicitly in slice 6
  (`programSchema.ts` / `parseAndNormalizeProgram` + `programRules.ts`).
- DB remains at v1 in every slice; no `Migration` and no `2.json` appear anywhere.
