# Research: import-export

## Research Questions

### Settings feature module
1. What is the structure of `SettingsScreen` (sections, rows, callbacks) and how are new preference rows currently added?
2. How is Settings navigation registered (NavKeys, EntryProvider, back stack)?
3. What does `SettingsViewModel` own today (state, use cases, DI)?
4. What strings and resources exist under `feature/settings`?
5. How are Settings tests structured (framework, fakes, run module)?

### Onboarding import flow — logs / history
6. Which UI screens and composables implement historical (log) import during onboarding?
7. How does the onboarding ViewModel orchestrate historical import (events, state machines, file pickers)?
8. Which domain ports and use cases perform historical import validation, cloud conversion, lift resolution, and persistence?
9. What file types and MIME filters does the historical import file picker accept?

### Onboarding import flow — programs
10. Which UI screens and composables implement program template import during onboarding?
11. How does program import orchestration differ from historical import (ports, use cases, progress UI)?
12. What file types does the program import file picker accept?

### Firebase / TypeScript functions — history import
13. Which callable functions exist for historical import (`start`, status, process) and what are their request/response shapes?
14. How do history-import callables authenticate (Auth, App Check) and enforce rate limits or job guards?
15. What deterministic parsers / format detectors exist for third-party log exports, and is there a first-class Stratum log format?
16. When does history import use LLM vs deterministic conversion?

### Firebase / TypeScript functions — program import
17. Which callable functions exist for program template import and what are their request/response shapes?
18. How do program-import callables authenticate and enforce limits?
19. What deterministic parsers / format detectors exist for program files, and is there a first-class Stratum program format?
20. When does program import use LLM vs deterministic conversion?

### Domain models — logs and programs
21. What domain models represent workout/session logs and how are they stored (entities, DAOs, repositories)?
22. What domain models represent programs/templates and how are they stored?
23. Which repository / use-case APIs can list sessions by date range or list programs for selection?

### Existing export code
24. Is there any user-facing or domain export path for logs or programs (Android or TypeScript), including stubs?
25. What does `functions/src/import/sliceExport.ts` (and related tests) actually do?

### core/inference
26. How does Android invoke Firebase for historical import (adapter, encoding, polling)?
27. How does Android invoke Firebase for program template import?
28. How is inference DI wired (Hilt module bindings to domain ports)?

### Navigation graph
29. How are Settings and Onboarding routes registered in the app module?
30. Can onboarding import UI be navigated to outside the onboarding wizard today?

### Test patterns
31. What unit tests cover import-related domain use cases, ViewModels, and inference adapters?
32. Which fakes in `core/testing` support import ports?
33. What Gradle test commands cover these modules?

### Schema / serialization
34. What JSON / payload schemas does the history import pipeline use (client request, job document, normalized output)?
35. What JSON / payload schemas does the program template import pipeline use?
36. How does Android parse imported history / program results into domain models?

---

## Findings

### 1. SettingsScreen structure and preference rows

**Verified.**

`SettingsScreen` (`feature/settings/src/main/kotlin/com/stratum/feature/settings/ui/SettingsScreen.kt:34–232`) is a scrollable column of:

| Section | UI pattern | Callbacks |
|---------|------------|-----------|
| Appearance | `StratumSegmentedControl` | `onAppearanceSelected` |
| Theme family | `StratumSegmentedControl` | `onThemeFamilySelected` |
| Progress check-ins | `SettingsSwitchRow` / `StratumSwitch` | `onCheckInsEnabledChange` |
| Post-workout suggestions | two switches | `onPostWorkoutSuggestionEnabledChange` |
| Developer tools (debug only) | `StratumButton` when `uiState.showDebugTools` | `onForceCheckInPending` |
| Account | logout / delete buttons + confirm dialogs | `SettingsAccountActions` |
| Legal | `StratumPreferenceRow` | `onLegalClick` |

Sections are built with private `SettingsSection(title, description, content)` (`SettingsScreen.kt:234–255`). Navigation-style rows use `StratumPreferenceRow` (Legal is the only example today at lines 199–203).

New rows today plug in by: (a) adding string resources, (b) extending `SettingsUiState` / `SettingsViewModel` if state is needed, (c) adding a section or `StratumPreferenceRow` in `SettingsScreen`, (d) wiring a callback through `SettingsRoute` in `SettingsKey.kt`.

There is **no** Import Data or Export Data section, string, or callback in Settings today.

---

### 2. Settings navigation registration

**Verified.**

- Nav keys: `SettingsKey` and `LegalKey` as `@Serializable data object` implementing `NavKey` (`feature/settings/.../navigation/SettingsKey.kt:19–24`).
- Entry registration: `settingsEntry(onBackClick, onNavigateToLegal)` and `legalEntry(onBackClick)` (`SettingsKey.kt:30–49`).
- `SettingsRoute` composable obtains `SettingsViewModel` via `hiltViewModel()`, collects `uiState` / `userMessage`, and passes account actions (`SettingsKey.kt:51–85`).
- App shell: `StratumNavHost` registers both entries and pushes `SettingsKey` from main-tab `onNavigateToSettings`; Legal is `backStack.add(LegalKey)` (`app/src/main/java/com/stratum/navigation/StratumNavHost.kt:173–175`, `226–230`).

Pattern for a secondary screen under Settings is already established by Legal: new `NavKey` + `entry` function + `backStack.add(...)` from Settings callback.

---

### 3. SettingsViewModel ownership

**Verified.**

`SettingsViewModel` (`feature/settings/.../SettingsViewModel.kt:33–47`) is `@HiltViewModel` and injects:

- Theme: `ObserveThemePreferencesUseCase`, `SetColorModeUseCase`, `SetThemeFamilyUseCase`
- Check-in: `ObserveCheckInPreferencesUseCase`, `SetCheckInEnabledUseCase`, `MarkCheckInPendingUseCase`
- Post-workout: equipment / rep-range proposal observe + set use cases
- Account: `LogoutUseCase`, `DeleteAccountUseCase`

UI state is presentation-only (`SettingsUiState`, `AppearanceUi`, `ThemeFamilyUi`, `PostWorkoutSuggestionUi`) — domain types are mapped inside the ViewModel (`SettingsViewModel.kt:262–337`). Module dependencies are `:core:domain` and `:core:ui` only (`feature/settings/build.gradle.kts:42–43`); **no** `:core:inference` or `:core:data`.

---

### 4. Settings strings and resources

**Verified.**

All user-facing Settings/Legal copy lives in `feature/settings/src/main/res/values/strings.xml` (appearance, theme, check-ins, post-workout, debug tools, account logout/delete dialogs, Legal list). No import/export strings exist.

---

### 5. Settings tests

**Verified.**

- File: `feature/settings/src/test/kotlin/com/stratum/feature/settings/SettingsViewModelTest.kt`
- Framework: JUnit 5 (`@Test`, `@DisplayName`, `@ExtendWith(MainDispatcherRule::class)`), Turbine, `kotlinx.coroutines.test`
- Fakes: `FakeThemePreferencesRepository`, `FakeCheckInPreferencesRepository`, `FakeEquipmentProposalPreferences`, `FakeRepRangeShiftProposalPreferences`, `FakeAuthManager`, `FakeCloudSyncPort`, `FakeAccountDeletionPort`, `FakeLoggingPort` from `:core:testing`
- Run command (module unit tests): `./gradlew :feature:settings:testDebugUnitTest`

---

### 6. Historical (log) import UI in onboarding

**Verified.**

- Entry step: Gym & Lifts — `GymAndLiftOnboardingSetupScreen` with `GymImportCard` for start/cancel/progress (`feature/onboarding/.../ui/GymAndLiftOnboardingSetupScreen.kt:92–104`, `156+`).
- Progress mapping: `ImportProgressMapping.kt`, `HistoricalImportTimelineStep.kt`, shared timeline UI under `ui/HistoricalImportTimeline.kt`, `ui/ImportProgressUi.kt`.
- Insights after success: `ImportInsightsSummaryCard` / mapping types.
- Details bottom sheet explains AI conversion and supported formats (`GymAndLiftOnboardingSetupScreen.kt:128–152`).
- File pick + I/O: `OnboardingRoute` multi-document launcher + `OnboardingImportFiles.kt` (`readImportFilesFromUris` / `readImportFileFromUri`).

---

### 7. Historical import orchestration (ViewModel)

**Verified.**

`OnboardingViewModel` (`OnboardingViewModel.kt:67–68`, `384–490`):

- `onImportFilesSelected(files)` → `ExecuteHistoricalImportUseCase` Flow collection
- `onRetryImportFromFailedFile()` uses `HistoricalImportResumeState` for multi-file resume
- `onCancelImport()` cancels job and calls `CancelHistoricalImportUseCase`
- Events: `ValidationFailed` | `Progress` | `Completed` | `Failed` (`HistoricalImportOrchestrationEvent` in domain)
- Ephemeral UI fields: `importProgress`, `importError`, `importUnsupportedFile`, timeline index, resume scalars, insights summary (`OnboardingEphemeralState` / `GymLiftsDraft`)

File picker is not in the ViewModel: `OnboardingRoute` launches `ActivityResultContracts.OpenMultipleDocuments()` with `HISTORICAL_IMPORT_MIME_TYPES`, reads URIs on `Dispatchers.IO`, then calls `onImportFilesSelected` (`OnboardingRoute.kt:79–90`, `163–168`, `336–343`).

---

### 8. Historical import domain ports and use cases

**Verified.**

| Layer | Symbol | Path |
|-------|--------|------|
| Port (Firebase upload/start/poll/download) | `HistoricalImportPort` | `core/domain/.../importing/ImportPorts.kt:111–123` |
| Port (full pipeline runner) | `HistoricalImportRunnerPort` | `ImportPorts.kt:161–166` |
| Orchestration (validate → run → refresh) | `ExecuteHistoricalImportUseCase` | `ExecuteHistoricalImportUseCase.kt` |
| Validation | `ValidateImportFilesUseCase` | max 5 files, 10 MB each (`HistoricalImportLimits`, `ValidateImportFilesUseCase.kt`) |
| Cloud job create | `StartHistoricalImportUseCase` | via `ImportJobRepository` |
| Runner impl | `RunFullHistoricalImportUseCase` | `:core:inference` implements `HistoricalImportRunnerPort` |
| Merge multi-file | `MergeImportedHistoryUseCase` | |
| Persist + resolve lifts + insights | `PersistImportedHistoryUseCase` | `ImportUseCases.kt:508–578` |
| Rollback | `RollbackHistoricalImportUseCase` | clears import-origin history + insights |
| Cancel | `CancelHistoricalImportUseCase` | |
| Refresh UI flags | `RefreshPersistedImportStateUseCase` | |
| Lift matching | `ResolveImportLiftsUseCase` + `LiftNameResolverPort` | |
| Cloud assist | `CustomLiftInferencePort`, `ImportEquipmentInferencePort`, `LiftNameEmbeddingPort` | |

Persistence semantics: `PersistImportedHistoryUseCase` requires all sessions `origin == IMPORT`, then `trainingLogRepository.replaceImportHistory(...)` which **replaces prior import-origin rows only** (not live `LIVE` sessions) (`ImportUseCases.kt:524–552`; Room impl deletes imported then upserts, `RoomTrainingLogRepository` around lines 90–95).

---

### 9. Historical import MIME types

**Verified.**

```336:343:feature/onboarding/src/main/kotlin/com/stratum/feature/onboarding/OnboardingRoute.kt
private val HISTORICAL_IMPORT_MIME_TYPES = arrayOf(
    "text/*",
    "application/json",
    "text/csv",
    "text/comma-separated-values",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
)
```

Multi-select via `OpenMultipleDocuments()`.

---

### 10. Program template import UI in onboarding

**Verified.**

- Trigger: Choose Path → Import path, and/or Review step "Import Existing" CTA via `onImportExisting` (`OnboardingRoute.kt:140`, `OnboardingUiState.kt` comments around `ImportProgramState`).
- Loading: `ImportLoadingContent` + `ProgramImportTimeline` (`ui/ReviewActionContent.kt:40–57`, `ui/ProgramImportTimeline.kt`).
- Errors: `ImportProgramErrorContent` for rejected vs generic (`ReviewActionContent.kt:67+`).
- State: `ImportProgramState` Idle | Loading(phase) | Error | Rejected (`OnboardingUiState.kt:91–95`).

Program import is **not** on the Gym & Lifts step; that step is historical logs only.

---

### 11. Program import orchestration vs historical

**Verified.**

| Concern | Historical logs | Program templates |
|---------|-----------------|-------------------|
| Domain use case | `ExecuteHistoricalImportUseCase` → `RunHistoricalImportUseCase` | `ImportProgramTemplateUseCase` |
| Cloud port | `HistoricalImportPort` | `ProgramTemplateImportPort` |
| Files | Multi-file (≤5) | Single file |
| Size cap | 10 MB / file | 1 MB |
| Post-success | Insights + gym enablement; does **not** auto-complete onboarding | Saves program active + **`CompleteOnboardingUseCase`** (`ImportProgramTemplateUseCase.kt:89–101`) |
| Progress phases | Upload → reading → converter → validate → convert → enrich → extract | Upload → sniffing → analyzing → converting → parsing → matching catalog |
| ViewModel API | `onImportFilesSelected` / cancel / retry | `importExistingProgram(file)` (`OnboardingViewModel.kt:767–798`) |

`ImportProgramTemplateUseCase` always activates the imported program, appends `PROGRAM_REGENERATED` change event, and marks onboarding complete (`ImportProgramTemplateUseCase.kt:89–101`). That coupling is onboarding-specific.

Feature module `:feature:onboarding` depends on `:core:inference` (`feature/onboarding/build.gradle.kts:41`).

---

### 12. Program import MIME types

**Verified.**

```352:357:feature/onboarding/src/main/kotlin/com/stratum/feature/onboarding/OnboardingRoute.kt
private val PROGRAM_IMPORT_MIME_TYPES = arrayOf(
    "text/csv",
    "text/comma-separated-values",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
)
```

Single-file `OpenDocument()`. Client rejects files > `ProgramTemplateImportLimits.MAX_FILE_BYTES` (1 MB) before upload (`OnboardingRoute.kt:116–123`).

---

### 13. History import Firebase callables and shapes

**Verified.** Exported from `functions/src/index.ts:20–24`.

| Export | Type | Role |
|--------|------|------|
| `startImport` | `onCall` | Enqueue job after Storage upload |
| `getImportJobStatus` | `onCall` | Client poll |
| `processImportJob` | `onDocumentWritten` on `users/{uid}/importJobs/{jobId}` | Worker |

**`startImport` request** (`functions/src/import/startImport.ts:20–29`):

```ts
{ jobId: string, rawStoragePath: string, sampleStoragePath: string }
```

**Response** (`startImport.ts:84`): `{ jobId, status: "pending_agent" }`.

**`getImportJobStatus` request** (`getImportJobStatus.ts:11–14`): `{ jobId }`.

**Response** (`getImportJobStatus.ts:25–42`):

```ts
{
  status, progressPhase, progressMessage,
  latestConversion: { storagePath, rowCount, sessionCount } | null,
  errorMessage, lastToolError, lastToolName
}
```

Job statuses (`schemas.ts:9–15`): `pending_agent` | `agent_running` | `ready` | `failed` | `rejected`.

Storage layout (client adapter): `imports/{uid}/{jobId}/raw|sample/{fileName}`; conversion path pattern `imports/{uid}/{jobId}/conversions/{ts}/normalized.json` (`FirebaseHistoricalImportAdapter.kt:28–29`; `deterministicConvert.ts:80`).

---

### 14. History import auth, App Check, rate limits, guards

**Verified.**

**Auth:** Both callables throw `HttpsError("unauthenticated")` if `!request.auth` (`startImport.ts:16–18`, `getImportJobStatus.ts:7–9`). Storage rules require `request.auth.uid == userId` for `imports/{userId}/...` (`storage.rules:6–8`).

**App Check (Android):** Client installs App Check (debug vs Play Integrity) in `MainActivity` via `AppCheckInstaller` (`app/src/main/java/com/stratum/MainActivity.kt:94–100`; `app/src/debug|release/.../AppCheckInstaller.kt`).

**App Check (functions):** Grep of `functions/src` found **no** `enforceAppCheck` / App Check verification in TypeScript callables. Callables use default `onCall` without App Check options in source.

**Rate limits / fair-use quotas:** No per-user monthly import quota in history import sources. Guards that exist:

- One active job supersession: other `pending_agent` / `agent_running` jobs failed as "replaced" or "timed out" (`startImport.ts:33–54`)
- Stale window: `HISTORICAL_IMPORT_STALE_JOB_MS = 10 * 60 * 1000` (`importJobGuards.ts:8`)
- Concurrent claim: running non-stale same job → `failed-precondition` (`startImport.ts:58–65`)
- Agent turn caps: `MAX_TURNS = 15`, `MAX_BLOB_NUDGES = 5` (`ImportAgentSession.ts:18–19`)
- Client file caps: 5 files × 10 MB (`ImportPorts.kt:23–27`)

---

### 15. Deterministic history parsers and Stratum log format

**Verified.**

`ImportFormat` closed set (`functions/src/import/deterministic/types.ts:11–15`):

- `hevy`
- `strong`
- `macrofactor`
- `alpha_progression`

Detection order in `detectAndParse` (`formatDetector.ts:32–80`): XLSX MacroFactor → Alpha Progression text → Hevy CSV → MacroFactor CSV → Strong semicolon CSV. Unrecognized → `null` (agent path).

**First-class Stratum log format:** Not present in `ImportFormat` or format detector. No `stratum` parser under `functions/src/import/deterministic/`. Normalized output is the internal contract (`NormalizedHistory`), not a named import class for Stratum-native files.

---

### 16. History import LLM vs deterministic

**Verified.** `processImportJob` (`processImportJob.ts:10–17`, `65–138`):

1. Claim job `pending_agent` → `agent_running`
2. `deterministicConvert` — if success, job ends ready (no OpenRouter)
3. Else `assessHistoryImportFeasibility` — may `rejected` without OpenRouter
4. Else `runImportAgentSession` (OpenRouter multi-turn agent + Python sandbox)

Secrets: `OPENROUTER_API_KEY`, `OPENROUTER_MODEL` on the trigger (`processImportJob.ts:21`).

---

### 17. Program template import callables and shapes

**Verified.** Exported `functions/src/index.ts:29–31`.

| Export | Type | Path |
|--------|------|------|
| `startProgramTemplateImport` | `onCall` | `program-import/startProgramTemplateImport.ts` |
| `getProgramTemplateJobStatus` | `onCall` | `program-import/getProgramTemplateJobStatus.ts` |
| `processProgramTemplateJob` | Firestore trigger on `users/{uid}/programImportJobs/{jobId}` | `processProgramTemplateJob.ts` |

**Start request** (`startProgramTemplateImport.ts:20–24`):

```ts
{ jobId: string, rawStoragePath: string, fileName?: string }
```

**Response** (`startProgramTemplateImport.ts:81`): `{ jobId, status: "pending" }`.

**Status response** (`getProgramTemplateJobStatus.ts:26–32`):

```ts
{ status, progressPhase, progressMessage, programTemplateStoragePath, errorMessage }
```

Statuses: `pending` | `running` | `ready` | `rejected` | `failed` (domain enum `RemoteProgramTemplateJobStatus`).

Storage: client upload `programImports/{uid}/{jobId}/raw/{fileName}` (`FirebaseProgramTemplateImportAdapter.kt:40`).

---

### 18. Program import auth and limits

**Verified.**

- Auth same pattern as history (`startProgramTemplateImport.ts:16–18`, `getProgramTemplateJobStatus.ts:10–12`)
- Job supersession + stale guards (`programTemplateJobGuards`; start file comments/lines 33–62)
- Size: `MAX_PROGRAM_TEMPLATE_FILE_BYTES = 1 MiB` enforced server-side (`programTemplateLimits.ts:9–18`) and client-side (`ProgramTemplateImportLimits`)
- Storage rules: `programImports/{userId}/{jobId}/{allPaths=**}` auth-scoped (`storage.rules:11–13`)
- No monthly program-import quota found in `program-import/` sources (synthesis has separate quota under `program-synthesis/synthesisQuota.ts`)

---

### 19. Deterministic program formats and Stratum program format

**Verified.**

`ProgramTemplateFormat` (`program-import/deterministic/types.ts:62`): `"alpha_progression" | "macrofactor" | "llm_custom"`.

`detectAndParseProgramTemplate` (`formatDetector.ts:26–48`): MacroFactor XLSX, then Alpha Progression plan CSV. Hevy/Strong program exports are explicitly not supported (comment lines 24–25).

**First-class Stratum program format:** Not in the detector. Server output is `NormalizedProgramTemplate` JSON (name-keyed lifts), mirrored in Kotlin domain (`ProgramTemplateImportTypes.kt`).

---

### 20. Program import LLM vs deterministic

**Verified.** `processProgramTemplateJob` (`processProgramTemplateJob.ts:10–17`, `68–143`):

1. Claim `pending` → `running`
2. `deterministicProgramConvert` — success → ready
3. Else `customLlmProgramConvert` (normalize → feasibility → region slice → single-shot LLM JSON) → ready | rejected | failed

Oversized files → `rejected` with "File too large".

---

### 21. Domain models for logs / sessions

**Verified.**

- `TrainingSession` — id, soft program/workout refs, status, kind, **origin** (`IMPORT` | `LIVE`), timestamps, `sourceApp`, name snapshots (`core/domain/.../training/TrainingSession.kt:33–53`)
- `LoggedSet` — id, sessionId, exerciseNameRaw, liftId, setIndex, weight, reps, rir, completedAt, liftContext (`LoggedSet.kt:9–21`)
- Repository: `TrainingLogRepository` (`TrainingLogRepository.kt`) — `getAllSessions`, `getAllSets`, `getSetsInWindow(start, end)`, `getImportSessions`/`getImportSets`, `replaceImportHistory`, etc.
- Room implementation: `RoomTrainingLogRepository` in `:core:data`
- Import-specific mapping: sessions from import get `origin = IMPORT`, `programId = ProgramId("import-history")`, `programNameSnapshot = "Import History"` (`ImportHistoryTrainingMapper.kt:21–45`)

---

### 22. Domain models for programs

**Verified.**

Hierarchy `Program → Workout → WorkoutLift → LiftSet` (`Program.kt:15–22`, `23–100+`):

- `Program`: id, name, cycleLength, workouts, isActive, restSlotIndices, cycle pointer fields
- Multi-program library: one active; `observeLibraryPrograms` excludes active + import-history sentinel
- Repository: `ProgramRepository` (`ProgramRepository.kt:15–63`) — `getAllUserPrograms`, `getProgramById`, `observeLibraryPrograms`, `saveProgram`, `saveToLibrary`, `deleteProgram` (refuses `import-history`)

Import path produces domain `Program` via `ResolveProgramTemplateLiftsUseCase` after downloading normalized template JSON.

---

### 23. APIs for date-range logs and program selection

**Verified.**

| Need | Existing API |
|------|----------------|
| Sets in time window | `TrainingLogRepository.getSetsInWindow(windowStart, windowEnd)` (`TrainingLogRepository.kt:37`; Room `setDao.getInWindow`) |
| All sessions | `getAllSessions()` / `observeAllSessions()` |
| All sets | `getAllSets()` |
| Import-only rows | `getImportSessions()` / `getImportSets()` |
| Active program | `ObserveProgramUseCase` / `ProgramRepository.observeProgram()` |
| Library programs | `ObserveLibraryProgramsUseCase` / `observeLibraryPrograms()` |
| Program by id | `GetProgramByIdUseCase` / `getProgramById` |
| All user programs | `getAllUserPrograms()` |

**Gap note:** No dedicated use case packages "export logs for range" or "export selected programs" as JSON. Window query is set-centric (`getSetsInWindow`); sessions for a range would require filtering `getAllSessions()` by `startedAt`/`endedAt` or joining sets→sessions (no session-by-range method found on the repository interface).

---

### 24. Existing user-facing export code

**Verified.**

- No Android domain/port/use-case named export for user logs or programs under `core/domain` (searches hit Room **schema** export, OpenTelemetry comments, and import "export.csv" fixture names only).
- No Settings or feature UI for export.
- No Firebase callable for exporting user training data or programs.
- Scripts such as `scripts/export-emulator-program-payload.py` are tooling, not product export.
- `functions/out/test-export/` is build/tool output, not a client feature.

**Conclusion:** User data export for logs/programs does not exist in the product codebase today.

---

### 25. `sliceExport.ts`

**Verified.**

`functions/src/import/sliceExport.ts`:

- `limitSamplePreview(text)` — first 30 lines for agent sample context
- `buildFullSlice(rawText, path, bucket)` — persists full raw text to Storage for audit; conversion still uses the raw upload path (comment line 13)

This is **internal agent/import pipeline support**, not user export of Stratum data.

---

### 26. Android → Firebase historical import (`core/inference`)

**Verified.**

`FirebaseHistoricalImportAdapter` (`FirebaseHistoricalImportAdapter.kt:16–94`):

1. Upload raw + first 50 KB sample to Storage
2. Callable `startImport` with jobId + paths
3. Poll `getImportJobStatus` until terminal
4. Download conversion JSON from `latestConversion.storagePath`

`RunFullHistoricalImportUseCase` (`RunFullHistoricalImportUseCase.kt:34–219`):

- Implements `HistoricalImportRunnerPort`
- Per file: create local job → upload → start → poll → parse with `ImportedHistoryJsonParser` → `ImportedHistoryChunk`
- Multi-file merge → `PersistImportedHistoryUseCase`
- Poll timeout/backoff constants in same file (`pollUntilTerminal`)

Requires signed-in Firebase user (`requireUid()`).

---

### 27. Android → Firebase program template import

**Verified.**

`FirebaseProgramTemplateImportAdapter` (`FirebaseProgramTemplateImportAdapter.kt:26–111`):

1. Upload raw to `programImports/...`
2. Callable `startProgramTemplateImport`
3. Poll `getProgramTemplateJobStatus`
4. Download template JSON

`ImportProgramTemplateUseCase` owns poll loop, parse, lift resolve, save, complete onboarding (domain layer, not the adapter).

---

### 28. Inference DI

**Verified.** `InferenceModule` (`core/inference/.../InferenceModule.kt:20–76`) `@InstallIn(SingletonComponent::class)` binds:

| Domain port | Implementation |
|-------------|----------------|
| `HistoricalImportPort` | `FirebaseHistoricalImportAdapter` |
| `HistoricalImportRunnerPort` | `RunFullHistoricalImportUseCase` |
| `ProgramTemplateImportPort` | `FirebaseProgramTemplateImportAdapter` |
| `CustomLiftInferencePort` | `FirebaseCustomLiftInferenceAdapter` |
| `ImportEquipmentInferencePort` | `FirebaseImportEquipmentInferenceAdapter` |
| `LiftNameEmbeddingPort` | `FirebaseLiftNameEmbeddingAdapter` |
| (+ synthesis, naming, swap, account deletion, logging) | |

---

### 29. App navigation: Settings and Onboarding

**Verified.** `StratumNavHost.kt`:

- Cold start: if onboarding incomplete → `OnboardingKey`, else `MainTabKey` (line ~81)
- `onboardingEntry(onComplete, onBuildFromScratch)` (126–141)
- Settings from main tabs only: `backStack.add(SettingsKey)` (173–175)
- Settings stack: `settingsEntry` + `legalEntry` (226–230)
- Discard program can reopen `OnboardingKey` (147–150)

---

### 30. Import UI outside onboarding wizard

**Verified.**

- Historical and program import UI/orchestration live only under `:feature:onboarding` (Gym & Lifts + Review / Choose Path).
- No Settings route, no program-library import entry (`feature/program` grep for import hooks: no matches).
- Onboarding can be reopened (`ReopenOnboardingWizardUseCase` / discard program path) but that is the full wizard, not a dedicated post-onboarding import screen.

**Inference (explicit):** Settings import would need new routes/screens or extraction of shared import UI; nothing mounts onboarding import subflows from Settings today.

---

### 31. Import-related unit tests

**Verified.**

**Domain (`:core:domain`):** `core/domain/src/test/kotlin/com/stratum/domain/importing/` — 28 test files including `ExecuteHistoricalImportUseCaseTest`, `ValidateImportFilesUseCaseTest`, `MergeImportedHistoryUseCaseTest`, `PersistImportedHistoryUseCaseResponseTest`, `ResolveImportLiftsUseCaseTest`, etc. Program import: `program/importing/NormalizedProgramTemplateJsonTest`, `ResolveProgramTemplateLiftsUseCaseTest`.

**Onboarding feature:** `OnboardingImportProgramViewModelTest`, `OnboardingGymLiftsStepViewModelTest` (historical import selection), timeline/mapping tests, `ui/ImportProgressUiTest`.

**Inference:** `RunFullHistoricalImportUseCaseTest`, adapter tests for related callables.

**Settings:** `SettingsViewModelTest` only (no import coverage).

**Functions (Node):** `functions/package.json` script `"test": "node --test $(find lib -name '*.test.js' | sort)"` after `tsc` build. Extensive tests under `functions/src/import/**/*.test.ts` and `program-import/**/*.test.ts`.

**Run commands:**

- Domain: `./gradlew :core:domain:test`
- Inference unit: `./gradlew :core:inference:testDebugUnitTest`
- Onboarding: `./gradlew :feature:onboarding:testDebugUnitTest`
- Settings: `./gradlew :feature:settings:testDebugUnitTest`
- Functions: `cd functions && npm test` (requires `npm run build` / `tsc` so `lib/**/*.test.js` exists)

---

### 32. Fakes in `core/testing` for import

**Verified.** Under `core/testing/src/main/kotlin/com/stratum/core/testing/importing/`:

- `FakeHistoricalImportPort`
- `FakeHistoricalImportRunnerPort`
- `FakeProgramTemplateImportPort`
- `FakeImportJobRepository`
- `FakeCustomLiftInferencePort`
- `FakeImportEquipmentInferencePort`
- `FakeLiftNameResolverPort`
- `FakeLiftEmbeddingPorts`
- `FakeLiftEquipmentCatalog`
- `FakeImportInsightsRepositories`

Also: `FakeTrainingLogRepository` (window queries, import replace), `FakeProgramRepository` (library/all programs).

No fake for "export" ports (none exist).

---

### 33. Gradle / Node test commands in scope

**Verified** (from AGENTS.md and module layouts):

| Area | Command |
|------|---------|
| Domain unit tests | `./gradlew :core:domain:test` |
| Android lib unit tests | `./gradlew :core:inference:testDebugUnitTest`, `:feature:onboarding:testDebugUnitTest`, `:feature:settings:testDebugUnitTest` |
| Broader unit suite | `./gradlew testDebugUnitTest` |
| Functions | `cd functions && npm run build && npm test` |

---

### 34. History import JSON / payload schemas

**Verified.**

**Client → startImport:** `{ jobId, rawStoragePath, sampleStoragePath }` (not the file body).

**Job document fields** (`schemas.ts:32–48`): status, raw/sample paths, optional fullSlice/script, `latestConversion`, progress fields, timestamps, errors.

**Normalized conversion JSON** (`schemas.ts:50–73`):

```ts
NormalizedHistory {
  sessions: { id, startedAt, endedAt?, sourceApp? }[]
  sets: {
    id, sessionId, exerciseNameRaw, liftId?, setIndex,
    weightAmount, weightUnit, reps, rir?, completedAt
  }[]
}
```

Validated by `validateNormalizedHistory` (`schemas.ts:115+`). Deterministic parsers and agent both emit this shape.

Agent system prompt documents the same contract (`importDomainRules.ts` referenced from `schemas.ts`).

---

### 35. Program template import JSON schemas

**Verified.**

Server `NormalizedProgramTemplate` (`program-import/deterministic/types.ts:9–53`):

```ts
{
  programName: string | null
  cycleLength: number | null  // mesocycle weeks fidelity; client ignores for slot space
  workouts: [{
    name, weekday, position, restDay?,
    lifts: [{ name, position, supersetGroupKey, sets: [{
      position, setTypeLabel, repMin, repMax, rirGoal, restSeconds, isTopSet
    }]}]
  }]
}
```

Kotlin mirror: `NormalizedProgramTemplate` in `ProgramTemplateImportTypes.kt`. Validation: `validateProgramTemplate` on server (`types.ts:77+`).

---

### 36. Android parse of import results

**Verified.**

**History:** `ImportedHistoryJsonParser` (`core/inference/ImportedHistoryJsonParser.kt`) — kotlinx.serialization DTOs → `ImportHistoryTrainingMapper.parseSession` / `parseSet` → `TrainingSession` + `LoggedSet` (liftId still null until `ResolveImportLiftsUseCase`).

**Program:** `NormalizedProgramTemplateJson.parse` in domain (`NormalizedProgramTemplateJson.kt`) — hand-rolled `JsonReader` (no kotlinx.serialization in domain) → `NormalizedProgramTemplate` → `ResolveProgramTemplateLiftsUseCase` → `Program`.

---

## Cross-cutting facts relevant to mapping

1. **Import is cloud-assisted for unknown formats; known third-party formats are local-to-server deterministic.** Both pipelines share Firebase Auth + Storage + job docs + client poll.
2. **No Stratum-native import format class** exists on either history or program server detectors.
3. **No product export path** for logs or programs exists on client or functions.
4. **Historical re-import replaces import-origin history**, not live-logged sessions (`replaceImportHistory`).
5. **Program import use case completes onboarding** — not a pure library import today.
6. **Settings cannot call inference ports without new dependencies/wiring** — module currently depends only on domain + ui.
7. **File pick helpers** (`OnboardingImportFiles.kt`) and MIME constants live in onboarding; not shared as a core module API.
8. **App Check is installed on Android**; function source does not show callable-level App Check enforcement.

---

## Gaps / unknowns (repo cannot answer)

| Topic | Status |
|-------|--------|
| Whether Firebase Console has App Check enforcement enabled on callables despite no `enforceAppCheck` in source | **Unknown** from repo alone — only client install + absent function options verified |
| Production rate limits outside this repo (Cloud Armor, quota configs not in tree) | **Unknown** |
| Exact Room entity field list for export field-by-field | Not fully expanded in this research pass; domain models + repository APIs above are the export-relevant surface |
| Any uncommitted branch work on `feat/import-export` beyond ticket notes | Not inspected as separate design; ticket only states branch name |

---

## Self-review checklist

- [x] Every research question has a verified answer, gap note, or explicit unknown
- [x] Findings contain no implementation recommendations or design opinions
- [x] No placeholders (`TBD` / `TODO` / "similar to")
- [x] Test frameworks and run commands identified for in-scope modules
- [x] Non-trivial claims cite `path:line` or path
