# Spec: import-export

**Ticket:** `import-export`  
**Phase:** Plan (Synthitect)  
**Grounding:** `research.md`, `design.md` (approved 2026-07-16), `structure.md` (approved 2026-07-16), `ticket.md`  
**Workspace:** `/home/sean/Code/Stratum`  
**Companion:** `test_spec.md`

Implementation specification only — no runnable code. Signatures, type shapes, file maps,
ordered tasks, and scenario-ID links.

---

## Feature summary

Add Settings **Import Data** (Logs | Program) and **Export Data** (Logs | Program):

1. **Export logs** for AllTime / LastDays(30|90) / YTD / Custom device-local calendar ranges as
   versioned Stratum JSON (`stratum.export.logs`) with stable session and set ids; delivery via
   **Share sheet only**.
2. **Export programs** (multi-select, select-all, exclude `import-history`) as multi-program
   Stratum JSON (`stratum.export.programs`) with stable program ids; Share sheet only.
3. **Firebase first-class Stratum detectors** for log + program export envelopes (deterministic,
   no LLM); third-party parsers unchanged.
4. **Settings log import** reuses historical import pipeline with **`MergeSkipDuplicates`**
   (id skip for Stratum; calendar-day occupancy for third-party). Never calls
   `replaceImportHistory` from Settings. LIVE rows always untouched.
5. **Settings program import** uses decoupled **`ImportProgramTemplatesUseCase`** with
   **`LibraryOnly`** — never activates program, never completes onboarding; multi-program file
   imports all non-skipped templates to library.
6. **Onboarding** keeps **`ReplaceImportOrigin`** historical import and thin program wrapper
   with **`ActivateAsActive` + complete onboarding**.

**Non-goals:** cloud backup product, Firebase export callable, SAF CreateDocument save UI,
import quotas, App Check callable option changes, Room tables/columns for selection/progress,
`:feature:import_export` module, Settings → onboarding Gradle dependency.

---

## Binding decisions (locked — do not reopen)

| ID | Decision |
|----|----------|
| D1 | Settings nav: Import/Export hubs → Logs \| Program (Legal pattern) |
| D2 | Screens in `:feature:settings`; domain export/import in `:core:domain`; shared MIME/readers/progress in `:core:ui`; Share helper Android-only in settings; no feature→feature deps |
| D3 | Program import core + options; onboarding thin wrapper activates + completes; Settings library-only |
| D4 | Settings import merge-safe: Stratum id skip; third-party day occupancy (+ content match after day filter); programs id or soft match |
| D5 | Stratum export envelopes with stable ids; `schema` + `source` + `schemaVersion: 1` |
| D6 | Export local; import still cloud jobs for third-party and Stratum |
| D7 | Log range UX + preview counts; program multi-select + select-all |
| D8 | Import UX mirrors onboarding pickers/progress; Settings does not advance wizard |
| D9 | Multi-program Stratum: import **all** non-skipped to library |
| D10 | Domain export use cases return `ExportFileResult` (bytes + name + mime) |
| D11 | Import requires `AuthState.Authenticated`; export offline OK |
| D12 | JUnit 5 + Turbine + fakes; functions `*.test.ts`; onboarding regression after extract |

**Human locks:** library-only programs; Share-only export; device-local custom ranges;
multi-program import-all; merge-safe no clobber.

---

## Vertical slice table

| # | Goal | Verifiable artifact | Upstream |
|---|------|---------------------|----------|
| 1 | Stratum log export schema + `ExportTrainingLogsUseCase` / preview | `:core:domain:test` export log scenarios | — |
| 2 | Stratum program export schema + multi-select export | `:core:domain:test` export program scenarios | 1 (shared envelope helpers) |
| 3 | Firebase Stratum log + program detectors | `cd functions && npm run build && npm test` | 1–2 contracts |
| 4 | Merge-safe Settings log persist + policy plumbing | domain + inference unit tests | 1 (stable ids); 3 for E2E format |
| 5 | Program import core + library merge + onboarding wrapper | `:core:domain:test` program import | 2 multi shape; 3 multi parse |
| 6 | Settings export UI + Share | `:feature:settings:testDebugUnitTest`; assemble smoke | 1–2 |
| 7 | Shared import extract + Settings import UI | settings + domain tests; detekt | 4–5; shared UI |
| 8 | Onboarding re-wire; replace + complete preserved | `:feature:onboarding:testDebugUnitTest` + domain import suite | 5 + 7 |

**Dependency chain (from structure.md):**

```text
1 ──► 2
 │     │
 └──┬──┘
    ▼
    3
    │
4 ◄─┘
 │
5
 ├──► 6   (export UI after 1–2; parallel with 3–5)
 └──► 7
         ▼
         8
```

**Serial implementation order for tasks:** 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8.  
Slices 4 and 5 may overlap after envelope/id contracts in §API contracts are fixed. Slice 6 may
overlap 3–5 once 1–2 pass.

---

## File structure

Paths verified against `/home/sean/Code/Stratum` on 2026-07-16.

### Create

| Path | Action | Responsibility |
|------|--------|----------------|
| `core/domain/src/main/kotlin/com/stratum/domain/export/ExportFileResult.kt` | Create | Share-ready `bytes`, `fileName`, `mimeType` |
| `core/domain/src/main/kotlin/com/stratum/domain/export/ExportLogRange.kt` | Create | Sealed `ExportLogRange` + range → instant bounds |
| `core/domain/src/main/kotlin/com/stratum/domain/export/ExportLogPreview.kt` | Create | `sessionCount`, `setCount` |
| `core/domain/src/main/kotlin/com/stratum/domain/export/ExportTrainingLogsUseCase.kt` | Create | Read range, encode logs envelope, empty → failure |
| `core/domain/src/main/kotlin/com/stratum/domain/export/PreviewExportTrainingLogsUseCase.kt` | Create | Preview counts for range |
| `core/domain/src/main/kotlin/com/stratum/domain/export/ExportProgramOption.kt` | Create | `id`, `name`, `isActive` for picker |
| `core/domain/src/main/kotlin/com/stratum/domain/export/ExportProgramsUseCase.kt` | Create | Multi-id program envelope encode |
| `core/domain/src/main/kotlin/com/stratum/domain/export/PreviewExportProgramsUseCase.kt` | Create | List exportable programs (no import-history) |
| `core/domain/src/main/kotlin/com/stratum/domain/export/StratumExportSchemas.kt` | Create | Schema constants `LOGS`, `PROGRAMS`, `VERSION`, `SOURCE` |
| `core/domain/src/main/kotlin/com/stratum/domain/export/StratumExportJson.kt` | Create | `encodeLogsEnvelope`, `encodeProgramsEnvelope` via `JsonEncode` |
| `core/domain/src/main/kotlin/com/stratum/domain/export/ProgramToNormalizedTemplateMapper.kt` | Create | `Program` → `NormalizedProgramTemplate` + export id |
| `core/domain/src/main/kotlin/com/stratum/domain/export/ExportRangeBounds.kt` | Create | Pure helpers: range → `[startInstant, endInstantExclusive)?` |
| `core/domain/src/main/kotlin/com/stratum/domain/importing/ImportHistoryPersistPolicy.kt` | Create | `ReplaceImportOrigin` \| `MergeSkipDuplicates` |
| `core/domain/src/main/kotlin/com/stratum/domain/importing/ImportHistoryDedupe.kt` | Create | Id / day / content skip filters |
| `core/domain/src/main/kotlin/com/stratum/domain/importing/ImportDedupeSourceKind.kt` | Create | `StratumStableIds` \| `ThirdParty` |
| `core/domain/src/main/kotlin/com/stratum/domain/importing/FilteredImportHistory.kt` | Create | Filtered sessions/sets + skip counts |
| `core/domain/src/main/kotlin/com/stratum/domain/importing/MergeImportStats.kt` | Create | Inserted/skipped session/set counts |
| `core/domain/src/main/kotlin/com/stratum/domain/program/importing/ProgramTemplateImportOptions.kt` | Create | Activation + completeOnboarding + change-event flags |
| `core/domain/src/main/kotlin/com/stratum/domain/program/importing/ProgramImportActivation.kt` | Create | `ActivateAsActive` \| `LibraryOnly` |
| `core/domain/src/main/kotlin/com/stratum/domain/program/importing/ProgramTemplateImportResult.kt` | Create | `importedProgramIds`, `skippedProgramCount` |
| `core/domain/src/main/kotlin/com/stratum/domain/program/importing/ParsedProgramTemplate.kt` | Create | `exportedId: String?`, `template: NormalizedProgramTemplate` |
| `core/domain/src/main/kotlin/com/stratum/domain/program/importing/ImportProgramTemplatesUseCase.kt` | Create | Core program import pipeline |
| `core/domain/src/main/kotlin/com/stratum/domain/program/importing/ProgramImportDedupe.kt` | Create | Library id + soft structure skip |
| `functions/src/import/deterministic/stratumLogParser.ts` | Create | `matchesStratumLogExport`, `parseStratumLogExport` |
| `functions/src/import/deterministic/stratumLogParser.test.ts` | Create | Log detector fixtures |
| `functions/src/program-import/deterministic/stratumProgramParser.ts` | Create | `matchesStratumProgramExport`, `parseStratumProgramExport` |
| `functions/src/program-import/deterministic/stratumProgramParser.test.ts` | Create | Program detector fixtures |
| `functions/src/import/deterministic/fixtures/stratum_logs_v1.json` | Create | Fixture matching Kotlin export envelope |
| `functions/src/program-import/deterministic/fixtures/stratum_programs_v1.json` | Create | Multi-program fixture |
| `core/ui/src/main/kotlin/com/stratum/core/ui/importing/ImportMimeTypes.kt` | Create | Shared historical + program MIME arrays |
| `core/ui/src/main/kotlin/com/stratum/core/ui/importing/ImportFileReaders.kt` | Create | URI → `ImportFileInput` (moved from onboarding) |
| `core/ui/src/main/kotlin/com/stratum/core/ui/progress/importing/HistoricalImportProgressUi.kt` | Create | Moved presentation types for historical timeline |
| `core/ui/src/main/kotlin/com/stratum/core/ui/progress/importing/HistoricalImportTimelineStep.kt` | Create | Moved timeline step enum/index mapping |
| `core/ui/src/main/kotlin/com/stratum/core/ui/progress/importing/ProgramImportTimelineStep.kt` | Create | Moved program import timeline steps |
| `feature/settings/src/main/kotlin/com/stratum/feature/settings/navigation/ImportExportKeys.kt` | Create | Six NavKeys + entry functions |
| `feature/settings/src/main/kotlin/com/stratum/feature/settings/ui/ImportDataScreen.kt` | Create | Hub: Logs \| Program |
| `feature/settings/src/main/kotlin/com/stratum/feature/settings/ui/ExportDataScreen.kt` | Create | Hub: Logs \| Program |
| `feature/settings/src/main/kotlin/com/stratum/feature/settings/ui/ExportLogsScreen.kt` | Create | Range + preview + export CTA |
| `feature/settings/src/main/kotlin/com/stratum/feature/settings/ui/ExportProgramsScreen.kt` | Create | Multi-select + export CTA |
| `feature/settings/src/main/kotlin/com/stratum/feature/settings/ui/ImportLogsScreen.kt` | Create | File pick + progress + insights |
| `feature/settings/src/main/kotlin/com/stratum/feature/settings/ui/ImportProgramScreen.kt` | Create | File pick + timeline + result message |
| `feature/settings/src/main/kotlin/com/stratum/feature/settings/ExportLogsViewModel.kt` | Create | Export logs presentation |
| `feature/settings/src/main/kotlin/com/stratum/feature/settings/ExportProgramsViewModel.kt` | Create | Export programs presentation |
| `feature/settings/src/main/kotlin/com/stratum/feature/settings/ImportLogsViewModel.kt` | Create | Settings merge log import |
| `feature/settings/src/main/kotlin/com/stratum/feature/settings/ImportProgramViewModel.kt` | Create | Settings library program import |
| `feature/settings/src/main/kotlin/com/stratum/feature/settings/ShareExport.kt` | Create | Cache file + FileProvider + `ACTION_SEND` |
| `feature/settings/src/test/kotlin/com/stratum/feature/settings/ExportLogsViewModelTest.kt` | Create | Export logs VM scenarios |
| `feature/settings/src/test/kotlin/com/stratum/feature/settings/ExportProgramsViewModelTest.kt` | Create | Export programs VM scenarios |
| `feature/settings/src/test/kotlin/com/stratum/feature/settings/ImportLogsViewModelTest.kt` | Create | Import logs VM scenarios |
| `feature/settings/src/test/kotlin/com/stratum/feature/settings/ImportProgramViewModelTest.kt` | Create | Import program VM scenarios |
| `core/domain/src/test/kotlin/com/stratum/domain/export/ExportTrainingLogsUseCaseTest.kt` | Create | Slice 1 domain tests |
| `core/domain/src/test/kotlin/com/stratum/domain/export/PreviewExportTrainingLogsUseCaseTest.kt` | Create | Preview counts |
| `core/domain/src/test/kotlin/com/stratum/domain/export/ExportProgramsUseCaseTest.kt` | Create | Slice 2 domain tests |
| `core/domain/src/test/kotlin/com/stratum/domain/export/StratumExportJsonTest.kt` | Create | Envelope marker + id fields |
| `core/domain/src/test/kotlin/com/stratum/domain/importing/ImportHistoryDedupeTest.kt` | Create | Dedupe pure unit tests |
| `core/domain/src/test/kotlin/com/stratum/domain/importing/PersistImportedHistoryMergeTest.kt` | Create | Merge vs replace persist |
| `core/domain/src/test/kotlin/com/stratum/domain/importing/ExecuteHistoricalImportPolicyTest.kt` | Create | Rollback policy branching |
| `core/domain/src/test/kotlin/com/stratum/domain/program/importing/ImportProgramTemplatesUseCaseTest.kt` | Create | Core program import |
| `core/domain/src/test/kotlin/com/stratum/domain/program/importing/ProgramImportDedupeTest.kt` | Create | Program skip rules |
| `core/domain/src/test/kotlin/com/stratum/domain/program/importing/NormalizedProgramTemplateJsonParseAllTest.kt` | Create | Multi-template parse |
| `app/src/main/res/xml/export_file_paths.xml` | Create | FileProvider cache path for Share |
| `core/inference/src/test/kotlin/com/stratum/core/inference/RunFullHistoricalImportMergePolicyTest.kt` | Create | Runner policy + no merge rollback wipe |

### Modify

| Path | Action | Responsibility |
|------|--------|----------------|
| `core/domain/src/main/kotlin/com/stratum/domain/training/TrainingLogRepository.kt` | Modify | Add `appendImportHistory(sessions, sets)` |
| `core/data/src/main/kotlin/com/stratum/core/data/repository/RoomTrainingLogRepository.kt` | Modify | Implement append (insert only; no delete) |
| `core/testing/src/main/kotlin/com/stratum/core/testing/training/FakeTrainingLogRepository.kt` | Modify | Implement append |
| `core/domain/src/main/kotlin/com/stratum/domain/importing/ImportUseCases.kt` | Modify | `PersistImportedHistoryUseCase` policy param + merge path |
| `core/domain/src/main/kotlin/com/stratum/domain/importing/ExecuteHistoricalImportUseCase.kt` | Modify | `persistPolicy` param; conditional rollback |
| `core/domain/src/main/kotlin/com/stratum/domain/importing/RunHistoricalImportUseCase.kt` | Modify | Forward `persistPolicy` to runner |
| `core/domain/src/main/kotlin/com/stratum/domain/importing/ImportPorts.kt` | Modify | `HistoricalImportRunnerPort.run(..., persistPolicy)`; optional `conversionFormat` on snapshot if needed |
| `core/domain/src/main/kotlin/com/stratum/domain/importing/MergeImportedHistoryUseCase.kt` | Modify | Preserve stable ids when chunk `preserveStableIds == true` |
| `core/domain/src/main/kotlin/com/stratum/domain/importing/ImportModels.kt` (or chunk file) | Modify | `ImportedHistoryChunk.preserveStableIds: Boolean = false` |
| `core/inference/src/main/kotlin/com/stratum/core/inference/RunFullHistoricalImportUseCase.kt` | Modify | Accept policy; pass to persist; skip full rollback on merge failure |
| `core/inference/src/main/kotlin/com/stratum/core/inference/FirebaseHistoricalImportAdapter.kt` | Modify | Map optional `format` from status when present |
| `core/testing/src/main/kotlin/com/stratum/core/testing/importing/FakeHistoricalImportRunnerPort.kt` | Modify | Capture `persistPolicy` on invocations |
| `core/domain/src/main/kotlin/com/stratum/domain/program/importing/ImportProgramTemplateUseCase.kt` | Modify | Thin wrapper over core with ActivateAsActive + complete |
| `core/domain/src/main/kotlin/com/stratum/domain/program/importing/NormalizedProgramTemplateJson.kt` | Modify | Add `parseAll` multi-program support |
| `functions/src/import/deterministic/types.ts` | Modify | Add `"stratum"` to `ImportFormat` |
| `functions/src/import/deterministic/formatDetector.ts` | Modify | Stratum JSON branch after XLSX, before Alpha Progression |
| `functions/src/import/deterministic/deterministicConvert.ts` | Modify | Persist `format` on `latestConversion` when known |
| `functions/src/import/getImportJobStatus.ts` | Modify | Return `latestConversion.format` when present |
| `functions/src/import/schemas.ts` | Modify | Optional `format` on `LatestConversion` type |
| `functions/src/program-import/deterministic/types.ts` | Modify | Add `"stratum"` to `ProgramTemplateFormat`; multi result type |
| `functions/src/program-import/deterministic/formatDetector.ts` | Modify | Stratum branch after XLSX MacroFactor |
| `functions/src/program-import/deterministic/deterministicProgramConvert.ts` | Modify | Persist multi-program conversion JSON when Stratum multi |
| `functions/src/import/deterministic/formatDetector.test.ts` | Modify | Stratum + regression third-party |
| `functions/src/program-import/deterministic/formatDetector.test.ts` | Modify | Stratum + regression third-party |
| `feature/settings/src/main/kotlin/com/stratum/feature/settings/ui/SettingsScreen.kt` | Modify | Import Data + Export Data preference rows |
| `feature/settings/src/main/kotlin/com/stratum/feature/settings/navigation/SettingsKey.kt` | Modify | Hub navigation callbacks on settings entry |
| `feature/settings/src/main/res/values/strings.xml` | Modify | All import/export copy + signed-out guidance |
| `app/src/main/java/com/stratum/navigation/StratumNavHost.kt` | Modify | Register six import/export entries; wire pushes |
| `app/src/main/AndroidManifest.xml` | Modify | FileProvider for export Share |
| `feature/onboarding/src/main/kotlin/com/stratum/feature/onboarding/OnboardingRoute.kt` | Modify | Use shared MIME + readers; keep replace policy |
| `feature/onboarding/src/main/kotlin/com/stratum/feature/onboarding/OnboardingImportFiles.kt` | Modify or Delete | Re-export from `:core:ui` or delete after move |
| `feature/onboarding/src/main/kotlin/com/stratum/feature/onboarding/ImportProgressUi.kt` | Modify | Thin re-export of core:ui types (same pattern as program generation progress) |
| `feature/onboarding/src/main/kotlin/com/stratum/feature/onboarding/HistoricalImportTimelineStep.kt` | Modify | Thin re-export after move |
| `feature/onboarding/src/main/kotlin/com/stratum/feature/onboarding/ui/HistoricalImportTimeline.kt` | Modify | Import shared chrome or stay presentation wrapper |
| `feature/onboarding/src/main/kotlin/com/stratum/feature/onboarding/ui/ProgramImportTimeline.kt` | Modify | Import shared chrome |
| `feature/onboarding/src/main/kotlin/com/stratum/feature/onboarding/OnboardingViewModel.kt` | Modify | Historical import still default `ReplaceImportOrigin`; program still wrapper |
| Existing domain/inference/onboarding import tests | Modify | Policy defaults; wrapper expectations; fake runner arity |

### Do not create

| Path | Why |
|------|-----|
| `:feature:import_export` module | D2 |
| Room entities/columns for range or multi-select | Schema lock |
| Firebase export callable | D6 |
| SAF Save-as UI | Human: Share only |
| Settings path calling `CompleteOnboardingUseCase` | D3/D8 |
| Settings path calling `replaceImportHistory` | D4 |

---

## API contracts and interfaces

### Export file result (`:core:domain`)

```kotlin
// com.stratum.domain.export.ExportFileResult
data class ExportFileResult(
    val bytes: ByteArray,
    val fileName: String,
    val mimeType: String, // always "application/json"
)
```

Equality for tests: compare `fileName`, `mimeType`, and `bytes.contentEquals`. No Android types.

### Log export range

```kotlin
// com.stratum.domain.export.ExportLogRange
sealed interface ExportLogRange {
    data class Custom(
        val startDate: LocalDate,
        val endDateInclusive: LocalDate,
        val zone: ZoneId,
    ) : ExportLogRange

    data class LastDays(val days: Int, val zone: ZoneId) : ExportLogRange

    data class YearToDate(val zone: ZoneId) : ExportLogRange

    data object AllTime : ExportLogRange
}
```

**Bounds algorithm (`ExportRangeBounds.resolve`):**

| Range | `startDate` | `endDateInclusive` |
|-------|-------------|--------------------|
| `AllTime` | no bounds (null pair) | — |
| `LastDays(d, zone)` | `LocalDate.now(zone).minusDays(d - 1L)` | `LocalDate.now(zone)` |
| `YearToDate(zone)` | `LocalDate.of(LocalDate.now(zone).year, 1, 1)` | `LocalDate.now(zone)` |
| `Custom` | given | given; require `startDate <= endDateInclusive` else failure |

Convert calendar bounds to instants:

- `startInstant = startDate.atStartOfDay(zone).toInstant()`
- `endInstantExclusive = endDateInclusive.plusDays(1).atStartOfDay(zone).toInstant()`
- Session included when `startedAt >= startInstant && startedAt < endInstantExclusive`
- Sets included when their `sessionId` is in the included session set

Clock for `LocalDate.now` and `exportedAt`: injectable `() -> Instant` / zone-aware “today” from the
same clock (tests fix zone `America/New_York` and instant).

### Preview + export logs

```kotlin
data class ExportLogPreview(val sessionCount: Int, val setCount: Int)

class PreviewExportTrainingLogsUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val clock: () -> Instant = { Instant.now() },
) {
    suspend operator fun invoke(range: ExportLogRange): ExportLogPreview
}

class ExportTrainingLogsUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val clock: () -> Instant = { Instant.now() },
) {
    /**
     * Empty selection (zero sessions or zero sets) →
     * Result.failure(IllegalArgumentException("No training logs in the selected range."))
     */
    suspend operator fun invoke(range: ExportLogRange): Result<ExportFileResult>
}
```

**Filename:** `stratum-logs-yyyyMMdd-HHmmss.json` using `clock()` in UTC for the stamp.  
**MIME:** `application/json`.

### Program export

```kotlin
data class ExportProgramOption(
    val id: ProgramId,
    val name: String,
    val isActive: Boolean,
)

class PreviewExportProgramsUseCase @Inject constructor(
    private val programRepository: ProgramRepository,
) {
    /** Active + library; excludes ProgramId.ImportHistory. Sorted name ASC, then id ASC. */
    suspend operator fun invoke(): List<ExportProgramOption>
}

class ExportProgramsUseCase @Inject constructor(
    private val programRepository: ProgramRepository,
    private val clock: () -> Instant = { Instant.now() },
) {
    /**
     * programIds empty → failure message "Select at least one program to export."
     * Any id missing or ImportHistory → failure message "One or more programs could not be exported."
     */
    suspend operator fun invoke(programIds: List<ProgramId>): Result<ExportFileResult>
}
```

**Filename:** `stratum-programs-yyyyMMdd-HHmmss.json`.  
**MIME:** `application/json`.

### Stratum export envelopes

```text
// Logs
{
  "schema": "stratum.export.logs",
  "schemaVersion": 1,
  "source": "stratum",
  "exportedAt": "<ISO-8601 instant>",
  "range": {
    "kind": "all_time" | "last_days" | "year_to_date" | "custom",
    "days": <int, only last_days>,
    "startInstant": "<ISO or null>",
    "endInstantExclusive": "<ISO or null>"
  },
  "history": {
    "sessions": [
      { "id": "<string>", "startedAt": "<ISO>", "endedAt": "<ISO|omit>", "sourceApp": "<string|omit>" }
    ],
    "sets": [
      {
        "id": "<string>",
        "sessionId": "<string>",
        "exerciseNameRaw": "<string>",
        "liftId": "<string|omit>",
        "setIndex": <int>,
        "weightAmount": <number>,
        "weightUnit": "Lb" | "Kg",
        "reps": <int>,
        "rir": <int|omit>,
        "completedAt": "<ISO>"
      }
    ]
  }
}

// Programs
{
  "schema": "stratum.export.programs",
  "schemaVersion": 1,
  "source": "stratum",
  "exportedAt": "<ISO-8601 instant>",
  "programs": [
    {
      "id": "<stable program id>",
      "template": {
        "programName": "<string|null>",
        "cycleLength": <int|null>,
        "workouts": [ /* NormalizedProgramTemplateWorkout fields */ ]
      }
    }
  ]
}
```

```kotlin
object StratumExportSchemas {
    const val LOGS = "stratum.export.logs"
    const val PROGRAMS = "stratum.export.programs"
    const val VERSION = 1
    const val SOURCE = "stratum"
}

object StratumExportJson {
    fun encodeLogsEnvelope(
        exportedAt: Instant,
        range: ExportLogRange,
        rangeBounds: Pair<Instant, Instant>?, // null for AllTime
        sessions: List<TrainingSession>,
        sets: List<LoggedSet>,
    ): String

    fun encodeProgramsEnvelope(
        exportedAt: Instant,
        programs: List<Pair<ProgramId, NormalizedProgramTemplate>>,
    ): String
}
```

**Stable ids:** session `TrainingSessionId.value`, set `LoggedSetId.value`, program `ProgramId.value`
emitted verbatim (no rename at export).

**Session `sourceApp` on export:** use stored `TrainingSession.sourceApp` when non-null; otherwise
emit `"stratum"` for LIVE-origin sessions so re-import can classify.

**Program → template mapping rules (`ProgramToNormalizedTemplateMapper`):**

- `programName` = `Program.name`
- `cycleLength` = `Program.cycleLength` (domain slot count for fidelity)
- Workouts sorted by `Workout.position`; rest slots become workouts with `restDay = true`, empty lifts,
  name `"Rest"`, position = slot index
- Training workouts: name, weekday = `Weekday.name` or null, lifts by position
- Lift name = catalog display name when `liftId` resolvable is **not** required at export; emit
  `WorkoutLift` lift id string only if mapper has name map — mapper accepts
  `liftNames: Map<LiftId, String>` and uses name or falls back to `liftId.value`
- Sets: `position`, `setTypeLabel` from `SetType.name`, `repMin`/`repMax`/`rirGoal`/`restSeconds`/`isTopSet`

### Firebase TypeScript contracts

```ts
// ImportFormat adds "stratum"
// ProgramTemplateFormat adds "stratum"

export function matchesStratumLogExport(text: string): boolean;
export function parseStratumLogExport(text: string): NormalizedHistory;
// Match: JSON parse + schema === "stratum.export.logs" + source === "stratum" + schemaVersion === 1
// Output history.sessions/sets preserve envelope history ids verbatim

export function matchesStratumProgramExport(text: string): boolean;
export function parseStratumProgramExport(text: string): {
  programs: Array<{ id: string | null; template: NormalizedProgramTemplate }>;
};
// Match: schema === "stratum.export.programs" + source === "stratum" + schemaVersion === 1
```

**Detection order (history):** (1) XLSX binary branch unchanged → (2) Stratum log JSON →
(3) Alpha Progression → (4) Hevy / MacroFactor CSV / Strong.

**Detection order (program):** (1) XLSX MacroFactor unchanged → (2) Stratum program JSON →
(3) Alpha Progression plan CSV.

**Conversion output multi-program:** when Stratum multi-program detected, Storage JSON body:

```json
{
  "programs": [
    {
      "id": "<string|omit>",
      "template": { "programName": null, "cycleLength": null, "workouts": [] }
    }
  ]
}
```

Single-template third-party output remains a bare `NormalizedProgramTemplate` object (no `programs`
array). Client `NormalizedProgramTemplateJson.parseAll` accepts both shapes; `parse` remains
single-template only and fails if root has `programs` array without being a single template.

**Job status format field:** `latestConversion.format` optional string (`"stratum"`, `"hevy"`, …)
written by deterministic convert; returned by `getImportJobStatus` for client source-kind mapping.

### Merge-safe log persist

```kotlin
enum class ImportHistoryPersistPolicy {
    ReplaceImportOrigin,
    MergeSkipDuplicates,
}

enum class ImportDedupeSourceKind {
    StratumStableIds,
    ThirdParty,
}

data class FilteredImportHistory(
    val sessions: List<TrainingSession>,
    val sets: List<LoggedSet>,
    val skippedSessionCount: Int,
    val skippedSetCount: Int,
)

data class MergeImportStats(
    val sessionsInserted: Int,
    val setsInserted: Int,
    val sessionsSkipped: Int,
    val setsSkipped: Int,
)

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
```

**Dedupe algorithm:**

1. **Always (both kinds):** build `existingSessionIds` / `existingSetIds` from all local rows
   (LIVE + IMPORT).
2. **`StratumStableIds`:**
   - Drop incoming sessions whose `id.value` is in `existingSessionIds`.
   - Drop incoming sets whose `id.value` is in `existingSetIds` OR whose `sessionId` was dropped.
   - Do **not** apply day occupancy.
3. **`ThirdParty`:**
   - First drop sessions/sets already matching existing ids (same as step 2) if any collide.
   - `occupiedDays` = set of `LocalDate` for every existing session’s `startedAt` at `zone`.
   - Drop incoming sessions whose `startedAt` local date is in `occupiedDays`.
   - Drop orphan sets for dropped sessions.
   - **Content match (after day filter):** for remaining sessions, if any existing session on a
     free day shares the same local date (race) OR if an existing session has identical set
     multiset fingerprint `(normalize(exerciseNameRaw), weight.amount, weight.unit, reps, rir)` for
     **all** of that session’s sets vs an incoming session’s sets, skip that incoming session and
     its sets. Fingerprint match is only applied when day occupancy did not already drop the session.
4. Skip counts = dropped sessions / dropped sets (including orphans).

**Source kind selection:**

- When any converted chunk has `preserveStableIds == true` (format `"stratum"`), use
  `StratumStableIds` for the merged batch if **all** chunks are Stratum; if mixed multi-file
  batches contain non-Stratum, use `ThirdParty` for the whole batch (conservative).
- Settings single-file Stratum → `StratumStableIds`.
- Default third-party / agent path → `ThirdParty`.
- Zone for day occupancy: `ZoneId.systemDefault()` in production; injectable in tests (fixed
  `America/New_York`).

**Persist:**

```kotlin
// TrainingLogRepository
suspend fun appendImportHistory(
    sessions: List<TrainingSession>,
    sets: List<LoggedSet>,
)
// Inserts only; never deletes IMPORT or LIVE rows.

// PersistImportedHistoryUseCase
suspend operator fun invoke(
    sessions: List<TrainingSession>,
    sets: List<LoggedSet>,
    onProgress: ImportProgressCallback = {},
    policy: ImportHistoryPersistPolicy = ImportHistoryPersistPolicy.ReplaceImportOrigin,
    sourceKind: ImportDedupeSourceKind = ImportDedupeSourceKind.ThirdParty,
    zone: ZoneId = ZoneId.systemDefault(),
): Result<ExtractTrainingInsightsSummary>
```

| Policy | Write | Empty after filter | Insights failure |
|--------|-------|--------------------|------------------|
| `ReplaceImportOrigin` | `replaceImportHistory` (existing) | existing empty-sets require still applies | existing: clear import history |
| `MergeSkipDuplicates` | filter then `appendImportHistory`; if zero sets remain after filter → success with summary zeros and `MergeImportStats` all-skip (no throw) | no wipe | **do not** call `replaceImportHistory(empty)`; rethrow only |

**`MergeImportStats` delivery:** attach to `HistoricalImportEvent.Completed` as optional
`mergeStats: MergeImportStats?` (null under replace policy). Orchestration
`HistoricalImportOrchestrationEvent.Completed` mirrors the field for Settings UI.

### Historical orchestration policy

```kotlin
// HistoricalImportRunnerPort
fun run(
    files: List<ImportFileInput> = emptyList(),
    resume: HistoricalImportResumeState? = null,
    persistPolicy: ImportHistoryPersistPolicy =
        ImportHistoryPersistPolicy.ReplaceImportOrigin,
): Flow<HistoricalImportEvent>

// ExecuteHistoricalImportUseCase
operator fun invoke(
    files: List<ImportFileInput>,
    resume: HistoricalImportResumeState?,
    persistPolicy: ImportHistoryPersistPolicy =
        ImportHistoryPersistPolicy.ReplaceImportOrigin,
): Flow<HistoricalImportOrchestrationEvent>
```

| Policy | On `HistoricalImportEvent.Failed` / catch |
|--------|-------------------------------------------|
| `ReplaceImportOrigin` | Call `RollbackHistoricalImportUseCase` (existing) |
| `MergeSkipDuplicates` | **Do not** rollback import history; emit Failed only; prior local data unchanged |

`RunFullHistoricalImportUseCase.buildImportFailureEvent` must not call
`rollbackHistoricalImport()` when `persistPolicy == MergeSkipDuplicates`.

**Id namespacing (`MergeImportedHistoryUseCase`):** when `ImportedHistoryChunk.preserveStableIds`
is true, do not prefix `fN::` on session/set ids. When false, keep existing `fN::` behavior.
When merging multiple preserve-stable chunks, if an id collides with an earlier chunk’s id,
namespace only the later collision with `f{index}::`.

### Program import core

```kotlin
enum class ProgramImportActivation {
    ActivateAsActive,
    LibraryOnly,
}

data class ProgramTemplateImportOptions(
    val activation: ProgramImportActivation,
    val completeOnboarding: Boolean,
    val appendProgramRegeneratedEvent: Boolean,
)

data class ProgramTemplateImportResult(
    val importedProgramIds: List<ProgramId>,
    val skippedProgramCount: Int,
)

data class ParsedProgramTemplate(
    val exportedId: String?,
    val template: NormalizedProgramTemplate,
)

// NormalizedProgramTemplateJson
fun parse(json: String): NormalizedProgramTemplate // existing single
fun parseAll(json: String): List<ParsedProgramTemplate>
// parseAll:
//  - if root has "programs" array → each element id? + template object or nested "template"
//  - else treat root as single NormalizedProgramTemplate with exportedId = null

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

**Core pipeline:** upload → poll → download → `parseAll` → for each template: resolve lifts →
`ProgramImportDedupe.shouldSkip` → if not skip: `LibraryOnly` → `saveToLibrary(program.copy(isActive=false))`;
`ActivateAsActive` → first non-skipped only `saveProgram(isActive=true)`, subsequent non-skipped
`saveToLibrary`; optional `PROGRAM_REGENERATED` when `appendProgramRegeneratedEvent`;
`completeOnboarding` once when flag true after at least one import.

**Settings options:**

```kotlin
ProgramTemplateImportOptions(
    activation = ProgramImportActivation.LibraryOnly,
    completeOnboarding = false,
    appendProgramRegeneratedEvent = false,
)
```

**Onboarding wrapper:**

```kotlin
class ImportProgramTemplateUseCase @Inject constructor(
    private val core: ImportProgramTemplatesUseCase,
) {
    suspend operator fun invoke(
        bytes: ByteArray,
        fileName: String,
        onProgress: ProgramTemplateImportProgressCallback = {},
    ): Result<Unit> // success when core succeeds; maps to Unit
    // options: ActivateAsActive, completeOnboarding=true, appendProgramRegeneratedEvent=true
}
```

**Program soft match (`ProgramImportDedupe.shouldSkip`):**

Return true if any existing user program (active + library, excluding import-history) matches:

1. `exportedId != null && existing.id.value == exportedId`, OR
2. Soft match: `existing.name.equals(candidate.name, ignoreCase=true)` AND
   `existing.cycleLength == candidate.cycleLength` AND ordered training workout names equal AND
   per training workout ordered lift names equal AND per lift set counts equal.

### Settings presentation contracts

```kotlin
// Export logs
enum class ExportRangePresetUi { AllTime, Last30, Last90, YearToDate, Custom }

data class ExportLogsUiState(
    val rangePreset: ExportRangePresetUi = ExportRangePresetUi.AllTime,
    val customStart: LocalDate? = null,
    val customEnd: LocalDate? = null,
    val preview: ExportLogPreview? = null,
    val isExporting: Boolean = false,
    val errorMessage: String? = null,
)

class ExportLogsViewModel @Inject constructor(
    private val previewExport: PreviewExportTrainingLogsUseCase,
    private val exportLogs: ExportTrainingLogsUseCase,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    val uiState: StateFlow<ExportLogsUiState>
    val shareEvents: SharedFlow<ExportFileResult>
    fun onRangePresetSelected(preset: ExportRangePresetUi)
    fun onCustomDatesChanged(start: LocalDate?, end: LocalDate?)
    fun onExportClick()
}

// Export programs
data class ExportProgramRowUi(val id: String, val name: String, val isActive: Boolean)

data class ExportProgramsUiState(
    val programs: List<ExportProgramRowUi> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val isExporting: Boolean = false,
    val errorMessage: String? = null,
)

class ExportProgramsViewModel @Inject constructor(
    private val previewPrograms: PreviewExportProgramsUseCase,
    private val exportPrograms: ExportProgramsUseCase,
) {
    val uiState: StateFlow<ExportProgramsUiState>
    val shareEvents: SharedFlow<ExportFileResult>
    fun onToggleProgram(id: String)
    fun onSelectAll()
    fun onExportClick()
}

// Import logs (Settings)
data class ImportLogsUiState(
    val isAuthenticated: Boolean,
    val progress: ImportProgressUi?,
    val errorMessage: String?,
    val insightsSummary: HistoricalImportSummary?,
    val mergeStats: MergeImportStats?,
    val signedOutGuidanceVisible: Boolean,
)

class ImportLogsViewModel @Inject constructor(
    private val executeImport: ExecuteHistoricalImportUseCase,
    private val cancelImport: CancelHistoricalImportUseCase,
    private val authManager: AuthManager,
) {
    fun onFilesSelected(files: List<ImportFileInput>)
    // Always persistPolicy = MergeSkipDuplicates
    fun onCancelImport()
    fun onRetryFromFailedFile()
}

// Import program (Settings)
data class ImportProgramUiState(
    val isAuthenticated: Boolean,
    val phase: ProgramTemplateImportPhase?,
    val errorMessage: String?,
    val successMessage: String?,
    val signedOutGuidanceVisible: Boolean,
)

class ImportProgramViewModel @Inject constructor(
    private val importPrograms: ImportProgramTemplatesUseCase,
    private val authManager: AuthManager,
) {
    fun onFileSelected(bytes: ByteArray, fileName: String)
    // LibraryOnly options as above
}
```

**Share helper (feature only):**

```kotlin
fun shareJsonExport(context: Context, file: ExportFileResult)
// Writes to cacheDir/exports/{fileName}, FileProvider authority "${packageName}.export",
// Intent.ACTION_SEND type application/json. No SAF CreateDocument.
```

### Shared import MIME + readers (`:core:ui`)

```kotlin
object ImportMimeTypes {
    val HISTORICAL: Array<String> = arrayOf(
        "text/*",
        "application/json",
        "text/csv",
        "text/comma-separated-values",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    )
    val PROGRAM: Array<String> = arrayOf(
        "text/csv",
        "text/comma-separated-values",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/json",
        "text/*",
    )
}
```

Move `readImportFilesFromUris`, `readImportFileFromUri`, `ensureReadPermission` from
`feature/onboarding/OnboardingImportFiles.kt` into `core/ui/.../ImportFileReaders.kt` as public
functions (same behavior).

### Navigation keys

```kotlin
@Serializable data object ImportDataKey : NavKey
@Serializable data object ImportLogsKey : NavKey
@Serializable data object ImportProgramKey : NavKey
@Serializable data object ExportDataKey : NavKey
@Serializable data object ExportLogsKey : NavKey
@Serializable data object ExportProgramsKey : NavKey
```

Tree: `SettingsKey` → hubs → leaf screens. Entries registered in `StratumNavHost` beside legal.

### Auth (D11)

| Action | Rule |
|--------|------|
| Export | No auth gate; local repository only |
| Import | If not `AuthState.Authenticated`, show signed-out guidance; do not start upload |

---

## Data model / schema changes

| Area | Change |
|------|--------|
| Room DB version | **No change** (remains 1). No new tables/columns. |
| `TrainingLogRepository` | New method `appendImportHistory` only (behavioral API, same entities). |
| Firebase job docs | Optional `latestConversion.format` string field (no migration; additive). |
| Domain entities | No new Room entities; export is pure JSON. |

---

## Ordered implementation tasks

### Slice 1 — Log export domain

| Task | Work | Scenarios |
|------|------|-----------|
| T1-1 | Create `ExportFileResult`, `ExportLogRange`, `ExportLogPreview`, `ExportRangeBounds`, `StratumExportSchemas` | IE-S1-001–IE-S1-006, IE-S1-010 |
| T1-2 | Implement `StratumExportJson.encodeLogsEnvelope` with stable session/set ids and schema markers | IE-S1-007, IE-S1-008, IE-S1-M001 |
| T1-3 | Implement `PreviewExportTrainingLogsUseCase` and `ExportTrainingLogsUseCase` against `TrainingLogRepository.getAllSessions` / `getAllSets` + range filter | IE-S1-001–IE-S1-006, IE-S1-009, IE-S1-011 |
| T1-4 | Domain unit tests for range bounds (device-local), empty failure, filename/mime | IE-S1-001–IE-S1-011, IE-S1-M001 |
| **Checkpoint** | `./gradlew :core:domain:test --tests '*Export*Log*' --tests '*StratumExportJson*'` | |

### Slice 2 — Program export domain

| Task | Work | Scenarios |
|------|------|-----------|
| T2-1 | Create `ProgramToNormalizedTemplateMapper`, `ExportProgramOption` | IE-S2-001, IE-S2-004 |
| T2-2 | Implement `StratumExportJson.encodeProgramsEnvelope` with program ids | IE-S2-003, IE-S2-M001 |
| T2-3 | Implement `PreviewExportProgramsUseCase` (exclude import-history) and `ExportProgramsUseCase` | IE-S2-001–IE-S2-005 |
| T2-4 | Domain unit tests multi-id, empty selection, exclude sentinel | IE-S2-001–IE-S2-005, IE-S2-M001 |
| **Checkpoint** | `./gradlew :core:domain:test --tests '*Export*Program*'` | |

### Slice 3 — Firebase Stratum detectors

| Task | Work | Scenarios |
|------|------|-----------|
| T3-1 | Add `stratum` to history + program format unions | IE-S3-001, IE-S3-004 |
| T3-2 | Implement `stratumLogParser` + fixture; wire detector after XLSX | IE-S3-001–IE-S3-003, IE-S3-007 |
| T3-3 | Implement `stratumProgramParser` multi-program; wire detector | IE-S3-004–IE-S3-006 |
| T3-4 | Write `format` on history `latestConversion`; return via getImportJobStatus | IE-S3-001, IE-S4-001 |
| T3-5 | Multi-program conversion JSON shape for program convert | IE-S3-005 |
| T3-6 | Tests: Stratum detect, id preserve, third-party still matches, unknown null | IE-S3-001–IE-S3-008, IE-S3-M001 |
| **Checkpoint** | `cd functions && npm run build && npm test` | |

### Slice 4 — Merge-safe log persist

| Task | Work | Scenarios |
|------|------|-----------|
| T4-1 | Add `appendImportHistory` to repository + Room + Fake | IE-S4-001, IE-S4-003 |
| T4-2 | Implement `ImportHistoryDedupe` (id + day + content) | IE-S4-001–IE-S4-004, IE-S4-M001 |
| T4-3 | `PersistImportedHistoryUseCase` policy + sourceKind; merge no wipe on insight failure | IE-S4-001–IE-S4-006 |
| T4-4 | Policy param on ports/execute/runner; conditional rollback | IE-S4-005, IE-S4-007 |
| T4-5 | `preserveStableIds` on chunks; merge namespacing fix for Stratum | IE-S4-001, IE-S4-M001 |
| T4-6 | Fake runner captures policy; unit tests domain + inference | IE-S4-001–IE-S4-007, IE-S4-M001 |
| **Checkpoint** | `./gradlew :core:domain:test` + `./gradlew :core:inference:testDebugUnitTest` | |

### Slice 5 — Program import core + library merge

| Task | Work | Scenarios |
|------|------|-----------|
| T5-1 | `parseAll` + `ParsedProgramTemplate` | IE-S5-003, IE-S5-004 |
| T5-2 | `ProgramImportDedupe` id + soft match | IE-S5-002, IE-S5-005 |
| T5-3 | `ImportProgramTemplatesUseCase` with options | IE-S5-001–IE-S5-004, IE-S5-M001 |
| T5-4 | Thin `ImportProgramTemplateUseCase` wrapper ActivateAsActive + complete | IE-S5-006, IE-S8-002 |
| T5-5 | Domain tests library-only vs wrapper | IE-S5-001–IE-S5-006, IE-S5-M001 |
| **Checkpoint** | `./gradlew :core:domain:test --tests '*ImportProgram*'` | |

### Slice 6 — Settings export UI + Share

| Task | Work | Scenarios |
|------|------|-----------|
| T6-1 | Nav keys, hubs, SettingsScreen rows, strings, StratumNavHost | (manual nav smoke; VM tests cover actions) |
| T6-2 | `ExportLogsViewModel` + screen; share events | IE-S6-001–IE-S6-004 |
| T6-3 | `ExportProgramsViewModel` + screen; multi-select / select-all | IE-S6-005–IE-S6-008 |
| T6-4 | `ShareExport` + FileProvider manifest/xml | IE-S6-001, IE-S6-006 |
| T6-5 | Settings VM unit tests with fakes | IE-S6-001–IE-S6-008, IE-S6-M001 |
| **Checkpoint** | `./gradlew :feature:settings:testDebugUnitTest` | |

### Slice 7 — Shared import extract + Settings import UI

| Task | Work | Scenarios |
|------|------|-----------|
| T7-1 | Move MIME/readers/progress types to `:core:ui`; onboarding re-exports | IE-S8-001 |
| T7-2 | `ImportLogsViewModel` with `MergeSkipDuplicates` + signed-out gate | IE-S7-001–IE-S7-004, IE-S7-M001 |
| T7-3 | `ImportProgramViewModel` with LibraryOnly options + signed-out gate | IE-S7-005–IE-S7-007 |
| T7-4 | Screens + strings + nav leaf entries | IE-S7-001–IE-S7-007 |
| T7-5 | Settings import VM tests; detekt | IE-S7-001–IE-S7-007, IE-S7-M001 |
| **Checkpoint** | `./gradlew :feature:settings:testDebugUnitTest` + `./gradlew detekt` | |

### Slice 8 — Onboarding regression

| Task | Work | Scenarios |
|------|------|-----------|
| T8-1 | OnboardingRoute uses shared MIME/readers; historical default replace | IE-S8-001, IE-S8-003 |
| T8-2 | Program import still completes onboarding via wrapper | IE-S8-002 |
| T8-3 | Existing onboarding import tests green; fix signature churn | IE-S8-001–IE-S8-003 |
| **Checkpoint** | `./gradlew :feature:onboarding:testDebugUnitTest` + domain import suite | |

### Final gate (after all slices)

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

## Scenario coverage map (requirements → IDs)

| Requirement area | Scenario IDs |
|------------------|--------------|
| Export logs ranges + empty + ids + schema + preview | IE-S1-001–IE-S1-011, IE-S1-M001 |
| Export programs multi/select-all/empty/exclude/ids | IE-S2-001–IE-S2-005, IE-S2-M001 |
| Firebase Stratum + third-party + unknown | IE-S3-001–IE-S3-008, IE-S3-M001 |
| Merge logs + LIVE + no wipe + replace rollback | IE-S4-001–IE-S4-007, IE-S4-M001 |
| Program library import + multi + wrapper | IE-S5-001–IE-S5-006, IE-S5-M001 |
| Settings export VM share/range/select | IE-S6-001–IE-S6-008, IE-S6-M001 |
| Settings import VM auth/merge/library | IE-S7-001–IE-S7-007, IE-S7-M001 |
| Onboarding regression | IE-S8-001–IE-S8-003 |

---

## Drift and escalation rules

1. **Contract conflict:** If implementation needs a change to `contracts/*`, stop and request an
   explicit **"Contract Waiver"** from the user. Do not edit contracts.
2. **Schema lock:** No new Room tables/columns. If append requires schema change, escalate —
   redesign using existing session/set insert APIs only.
3. **Signature drift vs this plan:** Prefer plan signatures. If a locked design decision
   (D1–D12 / human locks) would break, escalate before coding an alternative.
4. **Detector false positive:** If Stratum matcher would claim third-party JSON, tighten match to
   require all three of `schema`, `source === "stratum"`, `schemaVersion === 1`.
5. **Onboarding regression:** Any change that causes Settings merge path to become default for
   onboarding is a stop-ship defect (IE-S8-003 / IE-S7-M001).
6. **Lazy merge:** Implementation that always inserts without skip fails IE-S4-M001 — must not ship.
7. **Export without ids:** Implementation omitting session/set/program ids fails IE-S1-M001 /
   IE-S2-M001 — must not ship.
8. **Scope creep:** Cloud backup, SAF save, auto-activate Settings programs, and import quotas are
   out of scope; do not implement.
9. **Red phase:** After human approval of this plan, TDD Red writes failing tests from
   `test_spec.md` before production code.
10. **AndroJack:** For Kotlin/Compose production generation, follow AGENTS.md §4.2 (AndroJack
    pre/post validation). Plan phase does not generate production code.

---

## Self-review (plan author)

- [x] D1–D12 and human locks appear in tasks or explicit non-goals
- [x] Slice order matches structure.md (1→8 with documented parallelism)
- [x] Every requirement area has tasks and scenario IDs
- [x] Behavior-changing tasks reference scenario IDs
- [x] Names/signatures aligned with `test_spec.md` companion
- [x] Existing paths verified in workspace (2026-07-16)
- [x] No TBD / TODO / "implement later" placeholders
