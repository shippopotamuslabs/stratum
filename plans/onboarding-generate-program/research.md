# Onboarding Generate Program (OS-7) — Codebase Research

**Ticket:** `onboarding-generate-program` | **Phase:** Research (Synthitect)
**Scope:** Objective codebase map before any design. Verified facts only.

---

## Research Questions

### Onboarding flow & Review step
1. What does the current onboarding wizard look like (steps, routing, ViewModel), and where does the Review step live?
2. Is there a Generate affordance wired or stubbed, and what does it do today?
3. How is onboarding completion marked today, and what does completion navigate to?

### Domain models & ports
4. Do domain models `Program`, `Workout`, `WorkoutLift`, `Set`, `SetType`, `ProgressionScheme` exist in `:core:domain`?
5. Does `ProgramGeneratorPort` or `GenerateProgramUseCase` exist?
6. Do supporting domain types already exist: `TrainingAge`, `MuscleGroup`, `SpecializationPreference`, `Weight`, `IncrementTier`, `MovementPattern`, `Lift`?
7. Where are ports declared, and how are they shaped (suspend / Result / Flow)? How are existing use cases constructed (Hilt `@Inject constructor`)?

### OS-6 persisted insights (consumer) and program-generation payload
8. What insight tables/repositories exist (lift training insights, superset affinity, muscle volume baselines, import analysis metadata, logged sets, proven lift IDs)?
9. What is the current shape of the program-generation payload builder and the loader use case, and what does it emit?
10. What does the sibling `response-profile` work add to the payload and persistence, and where does it connect to OS-7?
11. Does any feature module or inference adapter currently consume `LoadProgramGenerationPayloadUseCase`?

### Persistence layer
12. How is Room structured in `:core:data` (entities, DAOs, database version, converters), and is there an existing programs/workouts table?
13. What is the current schema version and migration policy?
14. What is the repository pattern, and how are enum <-> string converters handled?

### Inference module
15. Does `:core:inference` exist, and how is the closest existing analog (Firebase Callable client) shaped?
16. How are inference adapters provided via DI (Hilt)?

### Firebase Functions server side
17. Does a `functions/` directory exist, and which callables are present today?
18. How are OpenRouter calls, secret binding, strict JSON schema, auth, and provider routing enforced today?
19. Does `functions-python/` exist and what does it host?

### DI & app wiring
20. How is Hilt wired (database, repositories, inference), and how does NavHost route between onboarding and home?
21. Is there a `:feature:program` module, or would OS-7 create it?

### OS-0 auth state
22. Where does the auth gate live, and how would the generate step know the session is authenticated + App Check active?

### Test patterns
23. What is the JUnit 5 + Turbine + fakes pattern, and what shared fakes exist in `:core:testing`?
24. Are there existing tests for the payload builder / loader, and what fixture/contract patterns exist?
25. What Gradle commands run the relevant tests?

### Contracts
26. What do `ARCHITECTURE.md`, `UI_DESIGN.md`, `UNIT_TESTING.md` require that constrains this work (module direction, schema lock, detekt, StratumTheme/Stratum*)?

### Reference repos
27. What patterns exist in pocket-crew (`OpenRouterInferenceServiceImpl`, `JsonSchemaWireSupport`) and LiftLab (`Program`/`Workout`/`ProgressionScheme`) to port?

---

## Findings

### 1-3. Onboarding wizard, Review step, and Generate affordance

- The onboarding wizard lives in `:feature:onboarding`. Root composable `OnboardingRoute` at `feature/onboarding/src/main/kotlin/com/stratum/feature/onboarding/OnboardingRoute.kt:46` (takes `onComplete: () -> Unit`, no `NavController` — ARCHITECTURE.md §4.5).
- Steps are constants `WIZARD_STEP_WELCOME … WIZARD_STEP_GENERATE` in `OnboardingRoute.kt:257-263`; the `when (state.step.id)` switch renders each screen (`OnboardingRoute.kt:145-171`). The Review step renders `ReviewScreen` (`OnboardingRoute.kt:169`); the Generate step renders `GenerateScreen` (`OnboardingRoute.kt:170`).
- `ReviewScreen` is read-only and renders no Generate button of its own — `feature/onboarding/src/main/kotlin/com/stratum/feature/onboarding/ui/ReviewScreen.kt:42`. The shared wizard `WizardControls` render Back/Next only (`OnboardingRoute.kt:227-255`). The ticket's "Generate program" affordance is **not** on the Review screen today; it is on the dedicated Generate step.
- **Generate step is already stubbed but does no generation.** `GenerateScreen` (`OnboardingRoute.kt:209-225`) renders a title (`R.string.onboarding_generate_title`) and a `StratumButton` whose `onClick = onComplete`. `onComplete` is wired to `viewModel::complete` (`OnboardingRoute.kt:82, 170`).
- `OnboardingViewModel.complete()` calls `completeOnboarding(effectiveProfile.value)` (`feature/onboarding/src/main/kotlin/com/stratum/feature/onboarding/OnboardingViewModel.kt:418-420`). There is **no** `GenerateProgramUseCase`, payload loading, or program persistence anywhere in the ViewModel.
- `CompleteOnboardingUseCase` (`core/domain/src/main/kotlin/com/stratum/domain/onboarding/CompleteOnboardingUseCase.kt:10-18`) does two things only: `profileRepository.saveProfile(finalProfile)` then `onboardingRepository.markComplete()`. No program is produced.
- Onboarding completion routes via `OnboardingRoute`: a `LaunchedEffect(state.isComplete)` calls `onComplete()` (`OnboardingRoute.kt:53-55`). The app-level `StratumNavHost` (`app/src/main/java/com/stratum/navigation/StratumNavHost.kt:39-44`) removes the `OnboardingKey` and adds `HomeKey` on completion.
- Home is a placeholder: `app/src/main/java/com/stratum/HomePlaceholder.kt:15-26` ("Your program is ready. / Home lands in a later slice."). No Programs screen exists.
- `OnboardingStep` enum is stable: `Welcome, Profile, Schedule, Specialization, GymAndLifts, Review, Generate`; `Generate.isLast` (`core/domain/src/main/kotlin/com/stratum/domain/onboarding/OnboardingStep.kt:10-38`).

### 4-5. Program domain models and ports (absent)

- **None of the OS-7 program hierarchy exists in code.** A repo-wide search of `:core:domain` for `Program`, `Workout`, `WorkoutLift`, `ProgressionScheme`, `SetType`, `ProgramGeneratorPort`, `GenerateProgramUseCase` finds only a comment in `core/domain/src/main/kotlin/com/stratum/domain/lift/Lift.kt:17` ("applies that mapping when building `WorkoutLift`s"). No classes, enums, ports, or use cases.
- The only existing files under `:core:domain/.../program/` are the payload builder and loader (Findings 8-11). These are pre-generation artifacts, not the `Program` output hierarchy.

### 6-7. Supporting domain types (present) and port shape

- Present and reusable:
  - `Lift` — `core/domain/src/main/kotlin/com/stratum/domain/lift/Lift.kt:32` (fields: `id: LiftId`, `name`, `movementPattern: MovementPattern`, `primaryMuscles: Set<MuscleGroup>`, `secondaryMuscles`, `allowWeightChangeBetweenSets: Boolean`, `incrementTiers: List<IncrementTier>`, `presetTags`, `equipment`, `enabled`, `isCustom`). Note `Lift.kt:15-17` documents that `allowWeightChangeBetweenSets` is "the deterministic progression seed" mapping to a scheme; OS-7's `Linear`/`Double` scheme is derived from `TrainingAge` instead (vertical slices §2.3) — the field is the closest existing scheme-related signal.
  - `Weight(amount: Double, unit: WeightUnit)` with `WeightUnit { Lb, Kg }` — `core/domain/src/main/kotlin/com/stratum/domain/lift/Weight.kt:12`. `LiftTrainingInsights.estimatedOneRm` is a `Weight` (`ProgramGenerationPromptBuilder.kt:83`).
  - `IncrementTier` — `core/domain/src/main/kotlin/com/stratum/domain/lift/IncrementTier.kt`.
  - `MovementPattern` — `core/domain/src/main/kotlin/com/stratum/domain/lift/MovementPattern.kt`.
  - `MuscleGroup`, `SpecializationLevel`, `SpecializationPreference` — `core/domain/src/main/kotlin/com/stratum/domain/onboarding/MuscleGroup.kt`, `SpecializationLevel.kt`.
  - `TrainingAge` and `LastTrainedBucket` exist in `:core:domain` onboarding package (referenced in `UserTrainingProfile` / `OnboardingViewModel`).
  - Value-class IDs: `LiftId` (`core/domain/src/main/kotlin/com/stratum/domain/lift/identity/LiftId.kt`), generic `EntityId` (`.../identity/EntityId.kt`).
- **Port shape conventions:** Repository ports expose `Flow` observers (e.g. `observeLifts(): Flow<List<Lift>>`) plus suspend reads (`getAll()`), and write ports use suspend functions. Import/inference ports return `Result<T>` (e.g. `HistoricalImportPort` — `core/inference/.../FirebaseHistoricalImportAdapter.kt:22-63`). Use cases are classes with `@Inject constructor` and `operator fun invoke(...)` (suspend where needed) — see `CompleteOnboardingUseCase.kt:10`, `LoadProgramGenerationPayloadUseCase.kt:19`.
- `javax.inject.Inject` is the only DI import allowed in `:core:domain` (ARCHITECTURE.md §3, §2 dependency graph).

### 8-11. OS-6 insights, payload builder/loader, and response-profile connection

- Insight repositories / ports (`core/domain/src/main/kotlin/com/stratum/domain/importing/ImportPorts.kt`): `LiftTrainingInsightsRepository`, `SupersetAffinityRepository`, `MuscleVolumeBaselineRepository`, `ImportAnalysisMetadataRepository`, `TrainingHistoryRepository`, `ImportJobRepository`. Domain models in `ImportModels.kt`.
- `ImportAnalysisMetadata` carries `provenLiftIds` (Tier A) — used to partition proven vs available lifts (`LoadProgramGenerationPayloadUseCase.kt:38-46`).
- **Payload builder (the OS-7 prompt input) exists and is tested:**
  - `ProgramGenerationPromptBuilder.buildPayload(...)` — `core/domain/src/main/kotlin/com/stratum/domain/program/ProgramGenerationPromptBuilder.kt:24-79`. Emits a JSON string with keys: `profile` (`trainingAge`, `lastTrained`, `sessionLengthMinutes`, `weekdays`), `specialization`, `provenLifts[]`, `availableLifts[]`, optional `supersetAffinities[]`, optional `volumeBaselines[]`, optional `responseProfile`, and (when import metadata present) `importAnalysis` snippet (`windowStart/End`, `totalSessions`, `totalSets`).
  - Per-lift JSON includes `id`, `name`, `movementPattern`, `proven: bool`, and an optional `trainingInsights` object (`sessionCount`, `estimatedOneRm`, `fatigueDecaySlope/Intercept`, `progressTrend`) — `ProgramGenerationPromptBuilder.kt:81-101`.
  - **Deliberately omitted from the payload:** `SupersetAffinity.confidence`, `ImportAnalysisMetadata.provenLiftIds`, `ImportAnalysisMetadata.unmatchedExerciseNames` (no gym-type hint is sent). Confirmed by test `ProgramGenerationPromptBuilderTest.kt`.
- **Loader use case (the OS-7 payload assembler) exists:**
  - `LoadProgramGenerationPayloadUseCase.invoke()` — `core/domain/src/main/kotlin/com/stratum/domain/program/LoadProgramGenerationPayloadUseCase.kt:19-58`. Returns `String` (the JSON payload). Reads profile, specialization, enabled lifts, partitions proven/available by `metadata.provenLiftIds` ∩ insights, assembles response profile, calls builder.
  - Inputs are repository ports (Hilt-injected). This is the single domain entry point the OS-7 Generate step would call.
- **Response-profile sibling artifacts are implemented** (further along than `plans/response-profile/research.md` documented):
  - `ResponseProfilePayload` + `LiftWithInsights` + `BuildProgramGenerationPayloadUseCase` live in `:core:domain/program/`.
  - The loader reads `VolumeResponseObservationRepository`, `LiftEffectivenessObservationRepository`, `ResponseProfileMetadataRepository`, `MuscleVolumeLandmarkEstimateRepository` and assembles `responseProfile` JSON with `epochsAnalyzed`, `analysisWindow`, `volumeResponses[]`, `liftEffectiveness[]`, `volumeLandmarks[]`, and an `informational` block (`ProgramGenerationPromptBuilder.kt:137-192`).
  - `LoadProgramGenerationPayloadUseCase.assembleResponseProfilePayload()` — `LoadProgramGenerationPayloadUseCase.kt:60-77`.
- **No production consumer** of `LoadProgramGenerationPayloadUseCase` or `BuildProgramGenerationPayloadUseCase` exists (the OS-7 Generate step is absent) — verified by inspection of `:feature:onboarding` ViewModel and `:core:inference`.

### 12-14. Persistence layer (Room)

- `StratumDatabase` — `core/data/src/main/kotlin/com/stratum/core/data/db/StratumDatabase.kt:7-90`. **version = 1**, `exportSchema = true`. Type converters: `WeekdaySetConverter`, `EnumConverters` (`StratumDatabase.kt:37`).
- Current entities (24 total): `OnboardingProgressEntity`, `UserProfileEntity`, `SpecializationPreferenceEntity`, `LiftEntity`, `IncrementTierEntity`, `GymPreferenceEntity`, import tables (`ImportJobEntity`, `HistoricalSessionEntity`, `LoggedSetEntity`), insight tables (`LiftTrainingInsightsEntity`, `SupersetAffinityEntity`, `MuscleVolumeBaselineEntity`, `ImportAnalysisMetadataEntity`), and response-profile tables (`ResponseEpochEntity`, `VolumeResponseObservationEntity`, `LiftEffectivenessObservationEntity`, `ExperimentBlockEntity`, `ResponseProfileMetadataEntity`, `MuscleWeeklyStimulusEntity`, `LiftWeeklyPerformanceEntity`, `MuscleWeeklyOutcomeIndexEntity`, `MuscleDoseResponsePointEntity`, `MuscleVolumeLandmarkEstimateEntity`, `MuscleResponseModelDiagnosticEntity`).
- **No `programs`, `workouts`, `workout_lifts`, or `sets` tables exist.** OS-7 would add them.
- Schema export: single file `core/data/schemas/com.stratum.core.data.db.StratumDatabase/1.json`.
- Enum <-> string: a single `EnumConverters` `@TypeConverter` class standardizes onboarding enums (`core/data/src/main/kotlin/com/stratum/core/data/db/EnumConverters.kt`); entity columns are typed enums.
- Repository pattern: Room implementations in `core/data/src/main/kotlin/com/stratum/core/data/repository/` (e.g. `RoomLiftRepository`, `RoomUserTrainingProfileRepository`); insight repos use `replaceAll` = delete-all-then-insert; metadata uses singleton upsert (per `response-profile/research.md` Finding 27). Mappers in `core/data/src/main/kotlin/com/stratum/core/data/mapper/`.
- **Migration policy (contract):** ARCHITECTURE.md §6 — keep `StratumDatabase` at **version 1** with **no migrations** until first store release; apply entity changes by bumping version and **uninstalling/reinstalling** (or clearing app data). AGENTS.md §4.5 schema lock: no new tables/columns without explicit authorization.

### 15-16. Inference module (exists) and DI

- `:core:inference` **exists** and is a Firebase Functions/Storage client (no Compose). Files (`core/inference/src/main/kotlin/com/stratum/core/inference/`): `FirebaseHistoricalImportAdapter`, `FirebaseImportEquipmentInferenceAdapter`, `FirebaseCustomLiftInferenceAdapter`, `FirebaseLiftNameEmbeddingAdapter`, `RunFullHistoricalImportUseCase`, `ImportedHistoryJsonParser`, `ImportLiftResolutionLogger`, `InferenceModule`.
- **Closest analog to OS-7's `FirebaseProgramGenerator`:** `FirebaseHistoricalImportAdapter` (`core/inference/src/main/kotlin/com/stratum/core/inference/FirebaseHistoricalImportAdapter.kt:17`). Pattern: `@Singleton class … @Inject constructor()`; obtains `FirebaseStorage.getInstance()`, `FirebaseFunctions.getInstance()`, `FirebaseAuth.getInstance()` as field initializers; calls `functions.getHttpsCallable("startImport").call(mapOf(...)).await()`; parses `result.data as? Map<*, *>`; wraps each op in `Result<T> = runCatching { … }`; requires auth via `auth.currentUser?.uid ?: error("Sign-in required…")` (`FirebaseHistoricalImportAdapter.kt:66-67`). Uses `kotlinx.coroutines.tasks.await`.
- The simpler single-shot callable client is `FirebaseImportEquipmentInferenceAdapter` (transient equipment inference) — same Call-shape, mirrors the server `inferImportEquipment` callable.
- DI: `InferenceModule` (`core/inference/.../InferenceModule.kt:14-42`) is a Hilt `@Module @InstallIn(SingletonComponent::class) abstract class` using `@Binds @Singleton` to bind each `Firebase*Adapter` to its domain port. OS-7 would add a `ProgramGeneratorPort` bind for a new `FirebaseProgramGenerator`.

### 17-19. Firebase Functions (exist; no generateProgram yet)

- `functions/` **exists** (Node/TS, `nodejs22`, codebase `default` per `firebase.json:12-27`). Package: `functions/package.json` — deps `firebase-admin`, `firebase-functions` v7, `openai` v4 (OpenAI SDK pointed at OpenRouter base URL); `npm run build` = `tsc`; tests via `node --test`.
- Exported callables (`functions/src/index.ts:1-10`): `startImport`, `getImportJobStatus`, `processImportJob`, `inferCustomLifts`, `inferImportEquipment`. **No `generateProgram` exists.**
- **Strict JSON schema + auth + secrets pattern to mirror (closest single-shot analog):** `functions/src/import/inferImportEquipment.ts:23-99`:
  - `onCall({ secrets: ["OPENROUTER_API_KEY", "OPENROUTER_MODEL_SMALL"] }, async (request) => { … })`.
  - Auth gate: `if (!request.auth) throw new HttpsError("unauthenticated", …)`.
  - Reads `process.env.OPENROUTER_API_KEY` and `process.env.OPENROUTER_MODEL_SMALL ?? "deepseek/deepseek-v4-flash"` (returns `failed-precondition` if key missing).
  - Sticky session id via `importEquipmentInferenceSessionId(uid)` → `createOpenRouterClient(apiKey, sessionId)`.
  - `client.chat.completions.create({ model, messages, response_format: { type: "json_schema", json_schema: importEquipmentJsonSchema() }, …buildOpenRouterChatCompletionExtras(sessionId, { requireParameters: true }) })`.
  - Parses `completion.choices[0]?.message?.content` (JSON.parse), validates, returns structured object.
- **Strict JSON schema helper to mirror:** `functions/src/import/inferImportEquipmentSchema.ts:40-68` — `importEquipmentJsonSchema()` returns `{ name, strict: true, schema: { type: "object", additionalProperties: false, properties: {…}, required: […] } }`. Same pattern (strict `json_schema`, `additionalProperties: false`, per-field `enum` arrays) applies to a program schema.
- **OpenRouter wiring helpers (reusable):** `functions/src/openrouterSession.ts` — `OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1"`, `createOpenRouterClient(apiKey, sessionId)` (OpenAI SDK + `X-Session-Id` header), `buildOpenRouterChatCompletionExtras(sessionId, routingOptions)` (returns `session_id` + provider routing), `sanitizeOpenRouterSessionId`. `functions/src/openrouterRouting.ts` — `buildOpenRouterProviderRouting` (sort by throughput, allow fallbacks, deny data collection, optional sticky session).
- Note: existing import callables are mostly in `functions/src/import/`; OS-7's `generateProgram` would likely live in a sibling location (e.g. `functions/src/program/`).
- **`functions-python/` exists** (`firebase.json:28-40`, codebase `python`, `python312`) — hosts the sandboxed conversion script (`functions-python/main.py`); not relevant to OS-7's single-shot generation (which uses the Node `default` codebase + OpenRouter).

### 20-21. DI & app wiring; `:feature:program`

- Hilt: `app/src/main/java/com/stratum/di/DataModule.kt` defines `DatabaseModule` (`@Provides` Room DB + DAOs), `ResponseDatabaseModule`, and `RepositoryModule` (`@Binds` port → impl). `StratumApplication` (`app/src/main/java/com/stratum/StratumApplication.kt`) is the `@HiltAndroidApp` entry. ViewModels use `@HiltViewModel` + `@Inject constructor` of use cases (not repository ports — ARCHITECTURE.md §4.4).
- Navigation: `StratumNavHost` (`app/src/main/java/com/stratum/navigation/StratumNavHost.kt:22`) uses **Navigation3** (`androidx.navigation3.runtime` + `NavDisplay`), keyed by `NavKey`s (`OnboardingKey`, `HomeKey`). Start key chosen from `OnboardingStatusViewModel.isComplete` (null → blank; true → Home; else → Onboarding). Feature routes register via `entryProvider`/`entry<HomeKey>`.
- The onboarding→home transition on completion removes `OnboardingKey` and adds `HomeKey` (`StratumNavHost.kt:40-44`).
- **`:feature:program` does NOT exist** (`settings.gradle.kts` lists only `:feature:onboarding`). ARCHITECTURE.md §1.2 lists `:feature:program` as planned ("Program view, exercise selection, substitutions"). OS-7 would create it (or extend `:app` routing for the Programs screen). Note: `HomePlaceholder.kt:22-23` says "Home lands in a later slice."
- Module dependency direction (ARCHITECTURE.md §2): `:app → :core:domain, :core:data, :core:ui, :feature:*`; `:feature:* → :core:domain, :core:ui` (and `:core:data` only when declared in that feature's build.gradle.kts); `:core:data → :core:domain`; `:core:inference → :core:domain` (per AGENTS.md §3). No reverse dependencies.

### 22. OS-0 auth gate

- Auth gate is at the activity level in `MainActivity` (`app/src/main/java/com/stratum/MainActivity.kt:24-52`): `@AndroidEntryPoint class MainActivity : FragmentActivity()`. Observes `MainViewModel.authState` (`StratumAuthUiState`); renders `SignInScreen` for `Loading`/`Unauthenticated`/`Error`, and `StratumNavHost()` only for `Authenticated` (`MainActivity.kt:37-49`).
- **App Check is initialized in `MainActivity`** (`initFirebaseAppCheck`, `MainActivity.kt:31, 59-69`): `FirebaseApp.initializeApp(this)` then `FirebaseAppCheck.getInstance().installAppCheckProviderFactory(...)` — `DebugAppCheckProviderFactory` in debug, `PlayIntegrityAppCheckProviderFactory` in release.
- Domain auth contract: `AuthManager` port + `AuthState` sealed interface in `core/domain/src/main/kotlin/com/stratum/domain/auth/AuthManager.kt:5-32`. `AuthState { Unauthenticated, Authenticated(user), Error(message), Loading }`; `authState: StateFlow<AuthState>`; `signInWithGoogle(): Result<UserProfile>`.
- Inference adapters require an authenticated session at call time via `FirebaseAuth.getInstance().currentUser?.uid` (e.g. `FirebaseHistoricalImportAdapter.requireUid()` — `FirebaseHistoricalImportAdapter.kt:66-67`). App Check is enforced by Firebase on the Callable itself (configured at deploy). The OS-7 Generate step reaches the user only after `MainActivity` has already routed to `StratumNavHost` (i.e. authenticated).

### 23-25. Test patterns, fakes, and run commands

- Contract: `contracts/UNIT_TESTING.md`. JUnit 5 (`org.junit.jupiter.api`, `@Test`, `@BeforeEach`, `@Nested`, `@DisplayName`), Turbine for Flow/StateFlow, `MainDispatcherRule` (`@ExtendWith`), fakes over MockK (MockK only for third-party SDK with `@file:Suppress("ForbiddenMockKInTests")`). Golden references in `golden-test-examples/` (sync via `./scripts/golden-test-examples-download.sh`). Robolectric for Room DAO round-trips in `:core:data`.
- Existing payload tests: `core/domain/src/test/kotlin/com/stratum/domain/program/ProgramGenerationPromptBuilderTest.kt` (asserts proven/available split, `proven:true/false`, omitted keys, no `enabledLifts`) and `ResponseProfilePayloadTest.kt`. These are the fixture/contract patterns for the prompt-builder side.
- Shared fakes in `:core:testing` (`core/testing/src/main/kotlin/com/stratum/core/testing/`): onboarding (`FakeLiftRepository`, `FakeUserTrainingProfileRepository`, `FakeSpecializationPreferenceRepository`, `FakeOnboardingRepository`), importing (`FakeHistoricalImportPort`, `FakeHistoricalImportRunnerPort`, `FakeTrainingHistoryRepository`, `FakeImportJobRepository`, `FakeCustomLiftInferencePort`, `FakeImportEquipmentInferencePort`, `FakeLiftEmbeddingPorts`, `FakeLiftEquipmentCatalog`), response (`FakeResponseRepositories`, `FakeResponseProfilePersistencePort`, `FakeMuscleVolumeLandmarkEstimateRepository`), auth (`FakeAuthManager`), plus `MainDispatcherRule.kt`.
  - **No `FakeProgramGeneratorPort` exists** (port is absent). No fake for `LoadProgramGenerationPayloadUseCase` dependencies beyond the listed repos.
- Functions-side tests use Node's built-in `node --test` (e.g. `functions/src/import/inferImportEquipmentSchema.test.ts`, `tools.test.ts`, `openrouterSession.test.ts`). Recorded fixture patterns: callables parse a model JSON payload through their schema/parser (`parseInferredEquipment`) — analogous to the OS-7 "recorded OpenRouter response fixtures" contract test.
- Run commands (AGENTS.md §5, UNIT_TESTING.md §5):
  - Domain: `./gradlew :core:domain:test` (narrow: `--tests "*GenerateProgramUseCaseTest*"`)
  - Data (Android libs): `./gradlew :core:data:testDebugUnitTest`
  - Inference: `./gradlew :core:inference:testDebugUnitTest` (inferred from module type)
  - Feature: `./gradlew :feature:onboarding:testDebugUnitTest`
  - Full: `./gradlew testDebugUnitTest`
  - Style: `./gradlew ktlintCheck`; Static analysis: `./gradlew detekt` (`maxIssues: 0`, includes `stratum` UI rules + `ForbiddenMockKInTests` + `ForbiddenVerifyInViewModelTests`)
  - Room/KSP smoke: `./gradlew :app:kspDebugKotlin`; App compile: `./gradlew :app:assembleDebug`
  - Functions: `npm --prefix functions test`

### 26. Contracts constraints

- ARCHITECTURE.md: ports-and-adapter, inward dependency (`:core:domain` is innermost JVM module); domain models never passed to composables; no `NavController` in composables; ViewModels inject use cases (not repos); write paths go through use cases; new Room tables/columns require explicit authorization; keep DB at version 1 with no migrations pre-release (uninstall/reinstall). Feature modules must not depend on other feature modules; cross-feature nav wired in `:app`.
- UI_DESIGN.md: `StratumTheme` + `Stratum*` components only in feature/app UI (never raw Material 3); semantic color roles via `StratumTheme.colors.*`; tabular figures (`StratumNumber`/`textData`) for data; Light+Dark `@Preview` in `StratumTheme { }`; 48dp touch targets; Detekt rules `ForbiddenMaterialComposeInFeatures`, `ForbiddenRawColorLiteralInFeatures` enforce `maxIssues: 0`. The stub `GenerateScreen` already uses `StratumButton`/`StratumText`/`StratumThemeAccessor`.
- UNIT_TESTING.md: JUnit 5 + Turbine + fakes from `:core:testing`; Robolectric for Room DAO; no MockK on owned ports; copy golden patterns; detekt must pass.
- AGENTS.md §4.6: no on-device LLM; all LLM work through Firebase Callable → OpenRouter; never ship provider keys in APK; `OPENROUTER_API_KEY` + `OPENROUTER_MODEL` (`deepseek/deepseek-v4-pro`) provisioned in Secret Manager; domain defines ports, inference implements; unit tests use fakes and recorded fixtures — no live API in CI.

### 27. Reference repos

- pocket-crew (`~/Code/pocket-crew`):
  - `OpenRouterInferenceServiceImpl` — `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/OpenRouterInferenceServiceImpl.kt:15-73`. Extends `BaseOpenAiSdkInferenceService`; constructor takes an OpenAI client, `modelId`, routing config, `baseUrl`; applies `X-Session-Id` header + `session_id` body property via `applyPromptCacheRouting`; `applyProviderChatExtras` injects OpenRouter routing. (This is an on-device service in pocket-crew; Stratum's analog is server-side in `functions/`, not in `:core:inference` — `:core:inference` is only the Callable client. The session/routing helpers already exist in Stratum's `functions/src/openrouterSession.ts` / `openrouterRouting.ts`.)
  - `JsonSchemaWireSupport` — `feature/inference/src/main/kotlin/.../JsonSchemaWireSupport.kt:15-49`. kotlinx-json → provider wire-map conversion (handles `JsonNull` → Kotlin null). Stratum's server-side equivalent is the strict `json_schema` builder pattern in `functions/src/import/inferImportEquipmentSchema.ts`.
- LiftLab (`~/Code/LiftLab`):
  - `Program` — `app/src/main/java/com/browntowndev/liftlab/core/domain/models/programConfiguration/Program.kt:5` (`id: Long, name, isActive, deloadWeek, microcycle/mesocycle counters, workouts: List<Workout>`).
  - `Workout` — `core/domain/models/workout/Workout.kt:5` (`id, programId, name, position, lifts: List<GenericWorkoutLift>`).
  - `ProgressionScheme` enum — `core/domain/enums/ProgressionScheme.kt:9` (5 variants: WAVE_LOADING, LINEAR, DOUBLE, DYNAMIC_DOUBLE, TOP_SET; each with `canHaveCustomSets`, `canVolumeCycle`). **Stratum locks to `Linear | Double` only** (vertical slices §2.3) — do not port the full enum.
  - `SetType` enum — `core/domain/enums/SetType.kt`. `GenericWorkoutLift` interface — `core/domain/models/interfaces/GenericWorkoutLift.kt`.
  - LiftLab uses Long auto-IDs; Stratum uses value-class IDs (`LiftId`, `EntityId`) and would define its own `ProgramId`/`WorkoutId`/etc.

---

## Existence summary (explicit, as required)

- **`:core:inference` exists** (8 Kotlin files; Firebase Callable/Storage client; Hilt `InferenceModule`).
- **`functions/` exists** (Node/TS, codebase `default`, `nodejs22`; 5 callables exported, none named `generateProgram`; OpenRouter session + strict-schema + secret-binding + auth patterns in place).
- **`functions-python/` exists** (codebase `python`, `python312`; sandboxed conversion script — not used by OS-7).
- **`:feature:program` does NOT exist** (`settings.gradle.kts` has only `:feature:onboarding`); OS-7 would create it for the Programs screen.
- **Program hierarchy domain models / ports / use cases do NOT exist** (`Program`, `Workout`, `WorkoutLift`, `Set`, `SetType`, `ProgressionScheme`, `ProgramGeneratorPort`, `GenerateProgramUseCase`).
- **Program persistence tables do NOT exist** (`programs`/`workouts`/`workout_lifts`/`sets`); `StratumDatabase` is at version 1 with 24 entities.
- **Generate step UI is stubbed** (`GenerateScreen` calls `onComplete` → `CompleteOnboardingUseCase` → marks complete; no generation, no payload load, no persistence).
- **The payload builder (`ProgramGenerationPromptBuilder`) and loader (`LoadProgramGenerationPayloadUseCase`) exist and are tested**, including the `response-profile` payload extension — these are the OS-7 prompt inputs that the Generate step and server function consume.

## Open unknowns (carried to Design; not pre-decided here)

- The ticket's open questions remain open (not research targets): normalized program tables vs single JSON blob; single-shot structured generation vs a server tool loop exposing deterministic volume validation to the model; Programs screen scope (read-only now vs edit later); where exactly the "Generate program" CTA lives (Review screen per ticket vs the existing dedicated Generate step).
- No WorkManager or scheduled jobs exist in the repo for any periodic work; re-extraction cadence (response-profile) is documented as a future extension point only.
- Units default (lb vs kg) is unresolved (§10) — seed catalog ships lb tiers (`Weight.kt:7`).
