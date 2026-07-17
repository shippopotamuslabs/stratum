# Structure: import-export

Vertical-slice execution outline for Settings Import/Export (Logs | Program). Signatures,
types, phases, and checkpoints only — no implementation bodies.

**Grounding:** `design.md` (human-approved 2026-07-16), `research.md`, human-locked
decisions (merge-safe import; library-only programs; Share sheet only; device-local
calendar ranges; multi-program import-all with skip).

---

## Slice index

| # | Goal |
|---|------|
| 1 | Stratum log export schema + `ExportTrainingLogsUseCase` → share-ready file result |
| 2 | Stratum program export schema + `ExportProgramsUseCase` (all / multi-select ids) |
| 3 | Firebase first-class Stratum log + program import detectors (TypeScript, deterministic) |
| 4 | Domain merge-safe Settings log persist (id skip / day occupancy) + non-destructive orchestration |
| 5 | Domain program-import core decoupled from onboarding; library-only merge persist |
| 6 | Settings nav tree + Export Logs / Export Programs screens (Share sheet only) |
| 7 | Shared import UI/MIME extract + Settings Import Logs / Import Program screens |
| 8 | Onboarding re-wire after extract/decouple; wizard still completes and replaces history |

---

## Dependencies between slices

```text
1 ──► 2
 │     │
 └──┬──┘
    │
    ▼
    3          (fixtures must match export envelopes from 1–2 contracts)
    │
4 ◄─┘          (log merge uses same session/set ids Stratum export preserves;
 │              third-party path independent of detectors but E2E needs 3)
 │
5              (program library merge; multi-program envelope from 2/3)
 │
 ├──► 6        (Export UI needs 1 + 2)
 │
 └──► 7        (Import UI needs 4 + 5 + shared extract; round-trip E2E needs 3)
         │
         ▼
         8     (onboarding consumes core from 5 + shared UI from 7)
```

| Slice | Must land before |
|-------|------------------|
| 1 | 2, 3 (log fixture contract), 6 |
| 2 | 3 (program fixture contract), 5 (multi-program body shape), 6 |
| 3 | 7 full Stratum round-trip checkpoint |
| 4 | 7 Import Logs |
| 5 | 7 Import Program, 8 onboarding wrapper |
| 6 | Independent of import slices once 1–2 exist |
| 7 | 8 (shared extract used by onboarding) |
| 8 | Final gate only after 5 + 7 |

Slices 4 and 5 may proceed in parallel after contracts for envelopes/ids are fixed in §Public contracts. Slice 6 may proceed in parallel with 3–5 once 1–2 pass.

---

## Public contracts

### Export file result (`:core:domain`)

```kotlin
// com.stratum.domain.export.ExportFileResult
data class ExportFileResult(
    val bytes: ByteArray,
    val fileName: String,
    val mimeType: String, // "application/json"
)
```

No Android types (`Uri`, `Context`, `Intent`) in domain.

### Log export range (`:core:domain`)

```kotlin
// com.stratum.domain.export.ExportLogRange
sealed interface ExportLogRange {
    /** Inclusive device-local calendar bounds using [zone]. */
    data class Custom(
        val startDate: LocalDate,
        val endDateInclusive: LocalDate,
        val zone: ZoneId,
    ) : ExportLogRange

    data class LastDays(val days: Int, val zone: ZoneId) : ExportLogRange // presets 30 / 90

    data class YearToDate(val zone: ZoneId) : ExportLogRange

    data object AllTime : ExportLogRange
}
```

Bounds convert to `[startInstant, endInstantExclusive)` via device-local midnights.
Custom requires `startDate <= endDateInclusive`.

### Log export preview + use case

```kotlin
// com.stratum.domain.export.ExportLogPreview
data class ExportLogPreview(
    val sessionCount: Int,
    val setCount: Int,
)

class PreviewExportTrainingLogsUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
) {
    suspend operator fun invoke(range: ExportLogRange): ExportLogPreview
}

class ExportTrainingLogsUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val clock: () -> Instant = { Instant.now() },
) {
    /**
     * Reads sessions/sets in [range], maps to Stratum log envelope with stable ids,
     * serializes pure JSON bytes. Empty selection → failure (no empty file).
     */
    suspend operator fun invoke(range: ExportLogRange): Result<ExportFileResult>
}
```

### Program export use case

```kotlin
class PreviewExportProgramsUseCase @Inject constructor(
    private val programRepository: ProgramRepository,
) {
    /** Active + library user programs; excludes `import-history` sentinel. */
    suspend operator fun invoke(): List<ExportProgramOption>
}

data class ExportProgramOption(
    val id: ProgramId,
    val name: String,
    val isActive: Boolean,
)

class ExportProgramsUseCase @Inject constructor(
    private val programRepository: ProgramRepository,
    private val clock: () -> Instant = { Instant.now() },
) {
    /**
     * Exports selected programs (must be non-empty, all resolvable, none import-history)
     * as one multi-program Stratum envelope with stable program ids.
     */
    suspend operator fun invoke(programIds: List<ProgramId>): Result<ExportFileResult>
}
```

### Stratum export envelopes (round-trip JSON)

Shared contract between Kotlin serializers and TypeScript detectors.

```text
// Logs file
{
  "schema": "stratum.export.logs",
  "schemaVersion": 1,
  "source": "stratum",
  "exportedAt": "<ISO-8601 instant>",
  "range": { /* metadata: kind + bound timestamps or "all_time" */ },
  "history": {
    "sessions": [ { "id", "startedAt", "endedAt?", "sourceApp?" } ],
    "sets": [ {
      "id", "sessionId", "exerciseNameRaw", "liftId?", "setIndex",
      "weightAmount", "weightUnit", "reps", "rir?", "completedAt"
    } ]
  }
}

// Programs file (one or many templates)
{
  "schema": "stratum.export.programs",
  "schemaVersion": 1,
  "source": "stratum",
  "exportedAt": "<ISO-8601 instant>",
  "programs": [
    {
      "id": "<stable program id>",
      "template": { /* NormalizedProgramTemplate fields */ }
    }
  ]
}
```

- Bodies reuse existing `NormalizedHistory` / `NormalizedProgramTemplate` field shapes.
- **Stable ids required** (session, set, program) for D4 re-import skip (D5).
- Log `sourceApp` on export may be `"stratum"` or original import provenance.
- Serialization lives in pure JVM domain (`JsonEncode` / hand-built JSON); no kotlinx in domain if that remains project policy.

```kotlin
// com.stratum.domain.export.StratumExportSchemas
object StratumExportSchemas {
    const val LOGS = "stratum.export.logs"
    const val PROGRAMS = "stratum.export.programs"
    const val VERSION = 1
    const val SOURCE = "stratum"
}

object StratumExportJson {
    fun encodeLogsEnvelope(/* … */): String
    fun encodeProgramsEnvelope(/* … */): String
}
```

### Firebase TypeScript — format unions + detectors

```ts
// functions/src/import/deterministic/types.ts
export type ImportFormat =
  | "hevy"
  | "strong"
  | "macrofactor"
  | "alpha_progression"
  | "stratum";

// functions/src/program-import/deterministic/types.ts
export type ProgramTemplateFormat =
  | "alpha_progression"
  | "macrofactor"
  | "llm_custom"
  | "stratum";
```

```ts
// functions/src/import/deterministic/stratumLogParser.ts
export function matchesStratumLogExport(text: string): boolean;
export function parseStratumLogExport(text: string): NormalizedHistory;
// Preserves session/set ids from envelope.history

// functions/src/program-import/deterministic/stratumProgramParser.ts
export function matchesStratumProgramExport(text: string): boolean;
export function parseStratumProgramExport(text: string): {
  programs: Array<{ id: string | null; template: NormalizedProgramTemplate }>;
};
```

Detection order:

| Pipeline | Order |
|----------|--------|
| History | (1) XLSX binary branch unchanged → (2) **Stratum JSON envelope** (text) → (3) Alpha Progression → (4) Hevy / MacroFactor CSV / Strong |
| Program | (1) XLSX MacroFactor unchanged → (2) **Stratum JSON envelope** → (3) Alpha Progression plan CSV |

Stratum match: JSON parseable + `schema` + `source === "stratum"` + `schemaVersion === 1`. Never via LLM.

**Multi-program conversion output:** job still stores one `programTemplateStoragePath`. For Stratum multi-program, conversion JSON is:

```ts
// Extended ready payload (client-visible download body)
{
  programs: Array<{ id?: string; /* NormalizedProgramTemplate fields or nested template */ }>
}
// OR single-template legacy shape remains valid for third-party converters
```

Client multi-parse accepts both single-template object and multi-program wrapper.

### Merge-safe log persist (`:core:domain`)

```kotlin
// com.stratum.domain.importing.ImportHistoryPersistPolicy
enum class ImportHistoryPersistPolicy {
    /** Onboarding: replace prior IMPORT-origin rows only; keep LIVE. */
    ReplaceImportOrigin,
    /** Settings: never replace; skip duplicates per D4. */
    MergeSkipDuplicates,
}

data class MergeImportHistoryResult(
    val sessionsInserted: Int,
    val setsInserted: Int,
    val sessionsSkipped: Int,
    val setsSkipped: Int,
    val summary: ExtractTrainingInsightsSummary,
)

/**
 * Replace path: existing PersistImportedHistoryUseCase behavior.
 * Merge path: D4 skip rules, then append-only write; does not call replaceImportHistory.
 */
class PersistImportedHistoryUseCase /* existing + policy param */ {
    suspend operator fun invoke(
        sessions: List<TrainingSession>,
        sets: List<LoggedSet>,
        onProgress: ImportProgressCallback = {},
        policy: ImportHistoryPersistPolicy = ImportHistoryPersistPolicy.ReplaceImportOrigin,
    ): Result<ExtractTrainingInsightsSummary>
}
```

D4 skip rules (signatures only — algorithms in Plan):

```kotlin
// com.stratum.domain.importing.ImportHistoryDedupe
internal object ImportHistoryDedupe {
    fun filterForMerge(
        incomingSessions: List<TrainingSession>,
        incomingSets: List<LoggedSet>,
        existingSessions: List<TrainingSession>,
        existingSets: List<LoggedSet>,
        zone: ZoneId,
        sourceKind: ImportDedupeSourceKind,
    ): FilteredImportHistory
}

enum class ImportDedupeSourceKind {
    /** Prefer id equality on session/set ids from Stratum envelope. */
    StratumStableIds,
    /**
     * Prefer device-local calendar-day occupancy: drop sessions whose local date
     * already has any session (LIVE or IMPORT). Optional content match
     * (exerciseNameRaw + weight + reps + rir) only after day filter when needed.
     */
    ThirdParty,
}

data class FilteredImportHistory(
    val sessions: List<TrainingSession>,
    val sets: List<LoggedSet>,
    val skippedSessionCount: Int,
    val skippedSetCount: Int,
)
```

Repository append (no new tables/columns):

```kotlin
// TrainingLogRepository — add:
suspend fun appendImportHistory(
    sessions: List<TrainingSession>,
    sets: List<LoggedSet>,
)
// Inserts only; does not delete prior IMPORT or LIVE rows.
// Room impl + FakeTrainingLogRepository.
```

### Settings vs onboarding historical orchestration

```kotlin
// HistoricalImportRunnerPort.run — extend signature:
fun run(
    files: List<ImportFileInput>,
    resume: HistoricalImportResumeState?,
    persistPolicy: ImportHistoryPersistPolicy =
        ImportHistoryPersistPolicy.ReplaceImportOrigin,
): Flow<HistoricalImportEvent>

// ExecuteHistoricalImportUseCase — same Flow events; policy parameter:
operator fun invoke(
    files: List<ImportFileInput>,
    resume: HistoricalImportResumeState?,
    persistPolicy: ImportHistoryPersistPolicy =
        ImportHistoryPersistPolicy.ReplaceImportOrigin,
): Flow<HistoricalImportOrchestrationEvent>
```

Failure semantics:

| Policy | On failure |
|--------|------------|
| `ReplaceImportOrigin` | Existing: `RollbackHistoricalImportUseCase` (clear import-origin history) |
| `MergeSkipDuplicates` | **No** full import-history rollback; prior local data untouched; emit Failed only |

Settings ViewModel always passes `MergeSkipDuplicates`. Onboarding keeps default `ReplaceImportOrigin`.

### Program import core (decoupled)

```kotlin
// com.stratum.domain.program.importing.ProgramTemplateImportOptions
data class ProgramTemplateImportOptions(
    val activation: ProgramImportActivation,
    val completeOnboarding: Boolean,
    val appendProgramRegeneratedEvent: Boolean,
)

enum class ProgramImportActivation {
    /** Onboarding wizard path: save with isActive=true (existing behavior). */
    ActivateAsActive,
    /** Settings: ProgramRepository.saveToLibrary only; never force-activate. */
    LibraryOnly,
}

data class ProgramTemplateImportResult(
    val importedProgramIds: List<ProgramId>,
    val skippedProgramCount: Int,
)

/**
 * Core pipeline: upload → poll → download → parse (single or multi) → resolve lifts
 * → persist per options → optional CompleteOnboardingUseCase.
 */
class ImportProgramTemplatesUseCase @Inject constructor(
    private val importPort: ProgramTemplateImportPort,
    private val resolver: ResolveProgramTemplateLiftsUseCase,
    private val repository: ProgramRepository,
    private val completeOnboarding: CompleteOnboardingUseCase,
    private val profileRepository: UserTrainingProfileRepository,
    private val programChangeEventRepository: ProgramChangeEventRepository,
    @DefaultDispatcher private val importDispatcher: CoroutineDispatcher,
) {
    suspend operator fun invoke(
        bytes: ByteArray,
        fileName: String,
        options: ProgramTemplateImportOptions,
        onProgress: ProgramTemplateImportProgressCallback = {},
    ): Result<ProgramTemplateImportResult>
}
```

```kotlin
// Soft / id dedupe for library import (D4 programs)
internal object ProgramImportDedupe {
    fun shouldSkip(
        candidate: Program,
        existing: List<Program>,
        exportedId: String?,
    ): Boolean
    // id match OR soft name+structure match (structure defined in Plan)
}
```

Onboarding thin wrapper (preserve public API used by ViewModel):

```kotlin
class ImportProgramTemplateUseCase @Inject constructor(
    private val core: ImportProgramTemplatesUseCase,
) {
    suspend operator fun invoke(
        bytes: ByteArray,
        fileName: String,
        onProgress: ProgramTemplateImportProgressCallback = {},
    ): Result<Unit> // maps core Result; ActivateAsActive + completeOnboarding=true
}
```

Settings uses `ImportProgramTemplatesUseCase` directly with:

```kotlin
ProgramTemplateImportOptions(
    activation = ProgramImportActivation.LibraryOnly,
    completeOnboarding = false,
    appendProgramRegeneratedEvent = false,
)
```

Multi-program: import **all** non-skipped templates to library (D9). Onboarding wrapper: if multi returned, activate **first** only and complete onboarding once (wizard expects one active program).

### Multi-template JSON parse (`:core:domain`)

```kotlin
// Extend NormalizedProgramTemplateJson (or sibling)
object NormalizedProgramTemplateJson {
    fun parse(json: String): NormalizedProgramTemplate // existing single
    fun parseAll(json: String): List<ParsedProgramTemplate>
}

data class ParsedProgramTemplate(
    val exportedId: String?,
    val template: NormalizedProgramTemplate,
)
```

### Shared import presentation + MIME (`:core:ui`)

Prefer `:core:ui` (both features already depend; no feature→feature). No new feature module.

```kotlin
// com.stratum.core.ui.importing.ImportMimeTypes
object ImportMimeTypes {
    val HISTORICAL: Array<String> // existing onboarding historical list
    val PROGRAM: Array<String>
    // PROGRAM = spreadsheet MIMEs + "application/json" + "text/*" for Stratum
}

// com.stratum.core.ui.importing.ImportFileReaders
fun readImportFilesFromUris(contentResolver: ContentResolver, uris: List<Uri>): List<ImportFileInput>
fun readImportFileFromUri(contentResolver: ContentResolver, uri: Uri): Result<ImportFileInput>
fun ensureReadPermission(context: Context, uri: Uri): Boolean
```

Move/adapt progress chrome currently under `:feature:onboarding`:

```kotlin
// com.stratum.core.ui.progress.importing (names illustrative)
// HistoricalImportTimeline, ProgramImportTimeline, ImportProgressUi mapping types
// Keep presentation-only; domain ImportProgress remains source of truth mapped in feature VMs
```

### Settings navigation keys

```kotlin
// feature/settings/.../navigation/SettingsKey.kt (and/or ImportExportKeys.kt)
@Serializable data object ImportDataKey : NavKey
@Serializable data object ImportLogsKey : NavKey
@Serializable data object ImportProgramKey : NavKey
@Serializable data object ExportDataKey : NavKey
@Serializable data object ExportLogsKey : NavKey
@Serializable data object ExportProgramsKey : NavKey
```

```text
SettingsKey
  ├─ ImportDataKey → ImportLogsKey | ImportProgramKey
  └─ ExportDataKey → ExportLogsKey | ExportProgramsKey
```

Entry functions: `importDataEntry`, `importLogsEntry`, `importProgramEntry`,
`exportDataEntry`, `exportLogsEntry`, `exportProgramsEntry` (+ existing settings/legal).
`StratumNavHost` registers all and wires `backStack.add`.

### Settings presentation contracts (ViewModels)

```kotlin
// Export logs
data class ExportLogsUiState(
    val rangeSelection: ExportRangeSelectionUi,
    val customStart: LocalDate?,
    val customEnd: LocalDate?,
    val preview: ExportLogPreviewUi?,
    val isExporting: Boolean,
    val errorMessage: String?,
)

class ExportLogsViewModel @Inject constructor(
    private val previewExport: PreviewExportTrainingLogsUseCase,
    private val exportLogs: ExportTrainingLogsUseCase,
) {
    val uiState: StateFlow<ExportLogsUiState>
    fun onRangePresetSelected(/* … */)
    fun onCustomDatesChanged(start: LocalDate?, end: LocalDate?)
    fun onExportClick() // emits one-shot ShareExportEvent(ExportFileResult)
    val shareEvents: SharedFlow<ExportFileResult>
}

// Export programs
data class ExportProgramsUiState(
    val programs: List<ExportProgramRowUi>,
    val selectedIds: Set<String>,
    val isExporting: Boolean,
    val errorMessage: String?,
)

class ExportProgramsViewModel @Inject constructor(
    private val previewPrograms: PreviewExportProgramsUseCase,
    private val exportPrograms: ExportProgramsUseCase,
) {
    fun onToggleProgram(id: String)
    fun onSelectAll()
    fun onExportClick()
    val shareEvents: SharedFlow<ExportFileResult>
}

// Import logs (Settings) — reuses ExecuteHistoricalImportUseCase(MergeSkipDuplicates)
class ImportLogsViewModel @Inject constructor(
    private val executeImport: ExecuteHistoricalImportUseCase,
    private val cancelImport: CancelHistoricalImportUseCase,
    private val authManager: AuthManager,
) {
    // No wizard step advance; insights on success; signed-out guidance
    fun onFilesSelected(files: List<ImportFileInput>)
    fun onCancelImport()
    fun onRetryFromFailedFile()
}

// Import program (Settings)
class ImportProgramViewModel @Inject constructor(
    private val importPrograms: ImportProgramTemplatesUseCase,
    private val authManager: AuthManager,
) {
    fun onFileSelected(bytes: ByteArray, fileName: String)
    // LibraryOnly options; success snackbar with inserted/skipped counts
}
```

Share delivery (feature only, D2/resolved Q2):

```kotlin
// feature/settings — Android helper, not domain
fun shareJsonExport(context: Context, file: ExportFileResult)
// Cache file + FileProvider + Intent.ACTION_SEND; no SAF CreateDocument for v1
```

### Auth / offline (D11)

| Action | Auth |
|--------|------|
| Export | Offline OK (local DB) |
| Import | Requires `AuthState.Authenticated`; else guidance UI, no upload |

### Non-contracts (explicit)

- No Room tables/columns for range, multi-select, or progress UI (schema lock).
- No Firebase export callable.
- No `settings` → `onboarding` Gradle dependency.
- No Settings path that calls `CompleteOnboardingUseCase` or `replaceImportHistory`.
- No App Check option changes on callables (non-goal).

---

## Per-slice checklist

### Slice 1 — Log export domain

| | |
|--|--|
| **Components** | Domain export package; `TrainingLogRepository` reads only (no schema change); unit tests |
| **New types** | `ExportFileResult`, `ExportLogRange`, `ExportLogPreview`, `StratumExportSchemas`, `StratumExportJson.encodeLogsEnvelope`, `PreviewExportTrainingLogsUseCase`, `ExportTrainingLogsUseCase` |
| **Touched** | Optional clock injection pattern only; no Room migrations |
| **Checkpoint** | `./gradlew :core:domain:test` — range filter (AllTime / LastDays / YTD / Custom device-local), stable ids in JSON, empty range fails, filename/mime set |

### Slice 2 — Program export domain

| | |
|--|--|
| **Components** | Domain export programs; maps `Program` → `NormalizedProgramTemplate` + id |
| **New types** | `ExportProgramOption`, `PreviewExportProgramsUseCase`, `ExportProgramsUseCase`, `StratumExportJson.encodeProgramsEnvelope` |
| **Touched** | `ProgramRepository.getAllUserPrograms` / `getProgramById` only |
| **Checkpoint** | Domain tests: multi-id envelope, excludes import-history, empty selection fails, stable program ids present |

### Slice 3 — Firebase Stratum detectors

| | |
|--|--|
| **Components** | `stratumLogParser`, `stratumProgramParser`, format detector order, type unions, fixture tests |
| **New types** | `ImportFormat` / `ProgramTemplateFormat` `"stratum"`; multi-program parse result |
| **Touched** | `formatDetector.ts` (history + program), `types.ts`, `deterministicConvert` / program convert call sites if format switch needs branch |
| **Checkpoint** | `cd functions && npm run build && npm test` — Stratum log/program fixtures detect as `stratum`, preserve ids, multi-program file yields all templates, third-party fixtures still match, unknown still falls through |

### Slice 4 — Merge-safe log persist

| | |
|--|--|
| **Components** | `ImportHistoryPersistPolicy`, dedupe helpers, `appendImportHistory`, runner + execute policy param, Room + Fake impl |
| **New types** | Policy enum, `ImportHistoryDedupe`, `FilteredImportHistory`, `appendImportHistory` |
| **Touched** | `PersistImportedHistoryUseCase`, `ExecuteHistoricalImportUseCase`, `HistoricalImportRunnerPort`, `RunFullHistoricalImportUseCase`, `TrainingLogRepository`, Room + Fake |
| **Checkpoint** | Domain + inference unit tests: merge skips existing session/set ids; day-occupancy skips third-party on occupied local dates; LIVE untouched; merge failure does not wipe import history; replace policy still rolls back |

### Slice 5 — Program import core + library merge

| | |
|--|--|
| **Components** | `ImportProgramTemplatesUseCase`, options, multi-parse, program dedupe, thin onboarding wrapper |
| **New types** | `ProgramTemplateImportOptions`, `ProgramImportActivation`, `ProgramTemplateImportResult`, `ParsedProgramTemplate`, `parseAll` |
| **Touched** | `ImportProgramTemplateUseCase` → wrapper; tests updated to core + wrapper |
| **Checkpoint** | Domain tests: LibraryOnly never activates / never completes onboarding; id skip; multi-program all library; ActivateAsActive + complete still works via wrapper |

### Slice 6 — Settings export UI + Share

| | |
|--|--|
| **Components** | Nav keys/entries, hub screens, Export Logs / Programs screens + VMs, Share helper, strings, `StratumNavHost` |
| **New types** | NavKeys, `ExportLogsViewModel`, `ExportProgramsViewModel`, presentation UI models |
| **Touched** | `SettingsScreen` rows, `settingsEntry` callbacks, app nav host; module still domain + ui (Share is Android in feature) |
| **Checkpoint** | `./gradlew :feature:settings:testDebugUnitTest` — range/preview/export events; program multi-select; `./gradlew :app:assembleDebug` optional smoke |

### Slice 7 — Shared import extract + Settings import UI

| | |
|--|--|
| **Components** | Move MIME/readers/timeline chrome to `:core:ui`; Settings Import hubs/screens/VMs; wire `MergeSkipDuplicates` + library program import |
| **New types** | `ImportMimeTypes`, shared readers, Settings import VMs |
| **Touched** | Onboarding imports of moved symbols; Settings Gradle only if new deps needed (prefer domain+ui only) |
| **Checkpoint** | Settings VM tests: signed-out block; happy-path event mapping; program LibraryOnly options; detekt clean for UI imports |

### Slice 8 — Onboarding regression

| | |
|--|--|
| **Components** | Onboarding route pickers use shared MIME/readers; historical import still `ReplaceImportOrigin`; program wrapper still completes onboarding |
| **New types** | None required beyond re-exports |
| **Touched** | `OnboardingRoute`, `OnboardingViewModel`, onboarding tests |
| **Checkpoint** | `./gradlew :feature:onboarding:testDebugUnitTest` + domain import tests green; gym import + import-existing program paths unchanged in behavior |

---

## File map preview

### Create

| Path | Responsibility |
|------|----------------|
| `core/domain/.../export/ExportFileResult.kt` | Share-ready bytes + name + mime |
| `core/domain/.../export/ExportLogRange.kt` | Device-local range sealed type |
| `core/domain/.../export/ExportLogPreview.kt` | Session/set counts |
| `core/domain/.../export/ExportTrainingLogsUseCase.kt` | Log read + serialize |
| `core/domain/.../export/PreviewExportTrainingLogsUseCase.kt` | Preview counts |
| `core/domain/.../export/ExportProgramsUseCase.kt` | Program multi export |
| `core/domain/.../export/PreviewExportProgramsUseCase.kt` | List exportable programs |
| `core/domain/.../export/StratumExportSchemas.kt` | Schema constants |
| `core/domain/.../export/StratumExportJson.kt` | Envelope encode |
| `core/domain/.../export/ProgramToNormalizedTemplateMapper.kt` | Domain Program → template + id |
| `core/domain/.../importing/ImportHistoryPersistPolicy.kt` | Replace vs Merge |
| `core/domain/.../importing/ImportHistoryDedupe.kt` | Id / day / content skip filters |
| `core/domain/.../program/importing/ProgramTemplateImportOptions.kt` | Activation + onboarding flags |
| `core/domain/.../program/importing/ImportProgramTemplatesUseCase.kt` | Core program import pipeline |
| `core/domain/.../program/importing/ProgramImportDedupe.kt` | Library skip rules |
| `functions/src/import/deterministic/stratumLogParser.ts` | Stratum log match/parse |
| `functions/src/import/deterministic/stratumLogParser.test.ts` | Log detector fixtures |
| `functions/src/program-import/deterministic/stratumProgramParser.ts` | Stratum program match/parse |
| `functions/src/program-import/deterministic/stratumProgramParser.test.ts` | Program detector fixtures |
| `core/ui/.../importing/ImportMimeTypes.kt` | Shared MIME arrays |
| `core/ui/.../importing/ImportFileReaders.kt` | URI → `ImportFileInput` |
| `core/ui/.../progress/importing/*` | Shared timeline/progress chrome (moved) |
| `feature/settings/.../navigation/ImportExportKeys.kt` | NavKeys + entry functions |
| `feature/settings/.../ui/ImportDataScreen.kt` | Import hub Logs \| Program |
| `feature/settings/.../ui/ExportDataScreen.kt` | Export hub Logs \| Program |
| `feature/settings/.../ui/ExportLogsScreen.kt` | Range + preview + export CTA |
| `feature/settings/.../ui/ExportProgramsScreen.kt` | Multi-select + export CTA |
| `feature/settings/.../ui/ImportLogsScreen.kt` | File pick + progress + insights |
| `feature/settings/.../ui/ImportProgramScreen.kt` | File pick + timeline + snackbar |
| `feature/settings/.../ExportLogsViewModel.kt` | Export logs presentation |
| `feature/settings/.../ExportProgramsViewModel.kt` | Export programs presentation |
| `feature/settings/.../ImportLogsViewModel.kt` | Settings merge log import |
| `feature/settings/.../ImportProgramViewModel.kt` | Settings library program import |
| `feature/settings/.../ShareExport.kt` | Share sheet helper |
| `feature/settings/src/test/.../*Export*Test.kt` | VM tests with fakes |
| `feature/settings/src/test/.../*Import*Test.kt` | VM tests with fakes |
| `core/domain/src/test/.../export/*` | Export use case tests |
| `core/domain/src/test/.../importing/*Merge*` | Merge persist / dedupe tests |
| `core/domain/src/test/.../program/importing/ImportProgramTemplates*` | Core program import tests |

### Modify

| Path | Responsibility |
|------|----------------|
| `core/domain/.../training/TrainingLogRepository.kt` | Add `appendImportHistory` |
| `core/data/.../RoomTrainingLogRepository.kt` | Implement append |
| `core/testing/.../FakeTrainingLogRepository.kt` | Implement append |
| `core/domain/.../importing/ImportUseCases.kt` (`PersistImportedHistoryUseCase`) | Policy-aware replace vs merge |
| `core/domain/.../importing/ExecuteHistoricalImportUseCase.kt` | Policy param; conditional rollback |
| `core/domain/.../importing/ImportPorts.kt` | Runner `persistPolicy` param |
| `core/inference/.../RunFullHistoricalImportUseCase.kt` | Pass policy into persist; no merge rollback side effects |
| `core/domain/.../program/importing/ImportProgramTemplateUseCase.kt` | Thin wrapper over core |
| `core/domain/.../program/importing/NormalizedProgramTemplateJson.kt` | `parseAll` multi support |
| `functions/src/import/deterministic/types.ts` | `stratum` format |
| `functions/src/import/deterministic/formatDetector.ts` | Stratum branch after XLSX |
| `functions/src/program-import/deterministic/types.ts` | `stratum` format |
| `functions/src/program-import/deterministic/formatDetector.ts` | Stratum branch |
| `feature/settings/.../ui/SettingsScreen.kt` | Import Data / Export Data rows |
| `feature/settings/.../navigation/SettingsKey.kt` | Hub navigation callbacks |
| `feature/settings/.../res/values/strings.xml` | Copy for hubs, export, import, auth guidance |
| `app/.../StratumNavHost.kt` | Register import/export entries |
| `feature/onboarding/OnboardingRoute.kt` | Shared MIME + readers; keep replace policy |
| `feature/onboarding/OnboardingImportFiles.kt` | Delete or re-export after move |
| `feature/onboarding` progress/timeline files | Delete or thin re-export after move to core:ui |
| Existing domain/inference/onboarding import tests | Policy defaults; wrapper expectations |

### Do not create

| Path | Why |
|------|-----|
| `:feature:import_export` module | D2 — stay in settings + domain + ui |
| Room entities for selection/range | Schema lock / non-goal |
| Firebase export callable | D6 — local export only |
| SAF Save UI | Human: Share only |

---

## Test checkpoints (runnable after each slice)

| After slice | Command(s) | Asserts |
|-------------|------------|---------|
| 1 | `./gradlew :core:domain:test --tests '*ExportTrainingLogs*'` (or full domain test) | Range bounds device-local; ids in envelope; empty fails |
| 2 | `./gradlew :core:domain:test --tests '*ExportPrograms*'` | Multi-program file; import-history excluded |
| 3 | `cd functions && npm run build && npm test` | Stratum detectors + no regression on third-party |
| 4 | `./gradlew :core:domain:test` + `./gradlew :core:inference:testDebugUnitTest` | Merge skip; no clobber; replace path intact |
| 5 | `./gradlew :core:domain:test --tests '*ImportProgram*'` | LibraryOnly vs ActivateAsActive; multi import |
| 6 | `./gradlew :feature:settings:testDebugUnitTest` | Export VMs emit share payloads; selection rules |
| 7 | Settings + domain tests as needed; `./gradlew detekt` | Import VMs; shared UI compiles; stratum UI rules |
| 8 | `./gradlew :feature:onboarding:testDebugUnitTest` + domain import suite | Wizard complete + replace history unchanged |

Final CI-aligned set before feature complete (Plan/Implement phases, not this phase):

```text
./gradlew detekt
./gradlew :core:domain:test
./gradlew :core:inference:testDebugUnitTest
./gradlew :feature:settings:testDebugUnitTest
./gradlew :feature:onboarding:testDebugUnitTest
./gradlew :app:assembleDebug
cd functions && npm run build && npm test
```

---

## Self-review

- [x] Eight slices; each vertically integrated with a checkpoint
- [x] Checkpoints after every slice, not only end
- [x] File map aligns with design D1–D12 and human-locked merge-safe / Share / library / device-local / multi-program decisions
- [x] No horizontal-only phase (no “all Room then all UI”)
- [x] Merge-safe Settings persist contracts (`MergeSkipDuplicates`, `appendImportHistory`, no Settings `replaceImportHistory`)
- [x] Share sheet only; device-local ranges; library-only program import; onboarding replace + complete preserved via policy/wrapper
- [x] No `TBD` / `TODO` placeholders; signatures explicit without algorithm bodies

---

## Status

Structure complete. Gate: **awaiting human approval (structure)**. Next phase after approval: Plan.
