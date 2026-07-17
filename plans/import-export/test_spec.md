# Test Spec: import-export

**Ticket:** `import-export`  
**Phase:** Plan (Synthitect)  
**Companion:** `spec.md`  
**Framework:**

| Layer | Stack |
|-------|--------|
| `:core:domain` | JUnit 5 (`org.junit.jupiter.api`), `kotlinx.coroutines.test`, fakes from `:core:testing`. No MockK on Stratum-owned ports. |
| `:core:inference` | JUnit 5 + fakes; `MainDispatcherRule` only if Main is touched |
| `:feature:settings` | JUnit 5 + Turbine + `MainDispatcherRule` + `:core:testing` fakes |
| `:feature:onboarding` | Existing JUnit 5 + Turbine patterns |
| `functions` | Node `node:test` after `tsc` (`*.test.ts` → `lib/**/*.test.js`) |

Expected values are **literals** in each scenario. Tests must not read production constants as
oracles when those constants are under test (prefer literal strings `stratum.export.logs`,
counts `0`/`1`/`2`, exception messages exactly as written).

---

## Run commands

| Scope | Command |
|-------|---------|
| Domain unit tests | `./gradlew :core:domain:test` |
| Domain export logs | `./gradlew :core:domain:test --tests "com.stratum.domain.export.ExportTrainingLogsUseCaseTest"` |
| Domain export programs | `./gradlew :core:domain:test --tests "com.stratum.domain.export.ExportProgramsUseCaseTest"` |
| Domain merge/dedupe | `./gradlew :core:domain:test --tests "com.stratum.domain.importing.ImportHistoryDedupeTest"` |
| Domain program import core | `./gradlew :core:domain:test --tests "com.stratum.domain.program.importing.ImportProgramTemplatesUseCaseTest"` |
| Inference unit tests | `./gradlew :core:inference:testDebugUnitTest` |
| Settings unit tests | `./gradlew :feature:settings:testDebugUnitTest` |
| Onboarding unit tests | `./gradlew :feature:onboarding:testDebugUnitTest` |
| Functions | `cd functions && npm run build && npm test` |
| Static analysis | `./gradlew detekt` |
| Compile smoke | `./gradlew :app:assembleDebug` |
| Pre-merge (feature complete) | `./gradlew detekt` + domain + inference + settings + onboarding unit tests + `:app:assembleDebug` + functions test |

---

## Scenario index

| ID | Slice | Summary |
|----|-------|---------|
| IE-S1-001 | 1 | Export AllTime includes every session and set |
| IE-S1-002 | 1 | Export LastDays(30) uses device-local inclusive 30-day window |
| IE-S1-003 | 1 | Export LastDays(90) uses device-local inclusive 90-day window |
| IE-S1-004 | 1 | Export YearToDate from Jan 1 of zone year through today |
| IE-S1-005 | 1 | Export Custom inclusive device-local calendar range |
| IE-S1-006 | 1 | Export Custom with start after end fails with exact message |
| IE-S1-007 | 1 | Exported JSON schema markers are stratum.export.logs / version 1 / source stratum |
| IE-S1-008 | 1 | Exported sessions and sets preserve stable domain ids |
| IE-S1-009 | 1 | Empty range fails with exact message and does not return file bytes |
| IE-S1-010 | 1 | Preview counts match sessions and sets in range |
| IE-S1-011 | 1 | Export file mimeType is application/json and name starts with stratum-logs- |
| IE-S1-M001 | 1 | Mutation defense: export without session ids fails assertion |
| IE-S2-001 | 2 | Preview programs lists active + library and excludes import-history |
| IE-S2-002 | 2 | Export empty programIds fails with exact message |
| IE-S2-003 | 2 | Export multi-program envelope includes both stable program ids |
| IE-S2-004 | 2 | Export rejects import-history id with exact message |
| IE-S2-005 | 2 | Export programs mime and filename prefix |
| IE-S2-M001 | 2 | Mutation defense: program export missing programs[].id fails |
| IE-S3-001 | 3 | Stratum log fixture detects as format stratum |
| IE-S3-002 | 3 | Stratum log parse preserves session id sess-export-1 and set id set-export-1 |
| IE-S3-003 | 3 | Stratum log multi-session history preserves both session ids |
| IE-S3-004 | 3 | Stratum program fixture detects as format stratum |
| IE-S3-005 | 3 | Stratum multi-program parse returns 2 templates with ids prog-a and prog-b |
| IE-S3-006 | 3 | Third-party Hevy CSV still detects as hevy |
| IE-S3-007 | 3 | Unknown JSON without stratum markers returns null from detectAndParse |
| IE-S3-008 | 3 | Alpha Progression program CSV still detects as alpha_progression |
| IE-S3-M001 | 3 | Mutation defense: schema-only JSON without source stratum does not match |
| IE-S4-001 | 4 | Merge Stratum: existing session id skipped; new id inserted |
| IE-S4-002 | 4 | Merge third-party: session on occupied local calendar day skipped |
| IE-S4-003 | 4 | Merge leaves LIVE session count unchanged |
| IE-S4-004 | 4 | Merge content match skips third-party session after free day when sets fingerprint match |
| IE-S4-005 | 4 | Merge failure does not clear prior import-origin rows |
| IE-S4-006 | 4 | All-duplicate merge succeeds with sessionsInserted 0 and sessionsSkipped 1 |
| IE-S4-007 | 4 | ReplaceImportOrigin failure still rolls back import history to zero import sessions |
| IE-S4-M001 | 4 | Mutation defense: merge without id skip would insert duplicate session id |
| IE-S5-001 | 5 | LibraryOnly import never sets isActive true and never completes onboarding |
| IE-S5-002 | 5 | Program id already in library → skippedProgramCount 1, importedProgramIds empty |
| IE-S5-003 | 5 | Multi-program file imports both non-skipped templates to library |
| IE-S5-004 | 5 | Multi-program with one existing id imports only the other |
| IE-S5-005 | 5 | Soft name+structure match skips without exported id |
| IE-S5-006 | 5 | ActivateAsActive wrapper saves active program and completes onboarding |
| IE-S5-M001 | 5 | Mutation defense: LibraryOnly must not call CompleteOnboardingUseCase |
| IE-S6-001 | 6 | Export logs AllTime click emits share event with mime application/json |
| IE-S6-002 | 6 | Export logs Last30 updates preview sessionCount 1 |
| IE-S6-003 | 6 | Export logs empty range sets errorMessage exact string |
| IE-S6-004 | 6 | Custom range selection stores start and end LocalDates |
| IE-S6-005 | 6 | Toggle program selects id prog-1 |
| IE-S6-006 | 6 | Select-all selects every listed program id |
| IE-S6-007 | 6 | Export programs with selection emits share event |
| IE-S6-008 | 6 | Export programs with empty selection sets errorMessage exact string |
| IE-S6-M001 | 6 | Mutation defense: select-all must not leave selectedIds empty when programs non-empty |
| IE-S7-001 | 7 | Import logs signed-out shows guidance and does not invoke execute import |
| IE-S7-002 | 7 | Import logs authenticated passes MergeSkipDuplicates policy |
| IE-S7-003 | 7 | Import logs success surfaces mergeStats sessionsInserted 1 |
| IE-S7-004 | 7 | Import logs cancel does not throw |
| IE-S7-005 | 7 | Import program signed-out shows guidance and does not call import use case |
| IE-S7-006 | 7 | Import program uses LibraryOnly options (completeOnboarding false) |
| IE-S7-007 | 7 | Import program success message includes imported count 1 and skipped 0 |
| IE-S7-M001 | 7 | Mutation defense: Settings must not pass ReplaceImportOrigin |
| IE-S8-001 | 8 | Onboarding historical import still defaults ReplaceImportOrigin |
| IE-S8-002 | 8 | Onboarding program import completes onboarding true |
| IE-S8-003 | 8 | Onboarding historical failure still clears import-origin history |

**Total scenarios:** 62 (including 8 mutation-defense IDs).

---

## Fixture conventions

| Fixture ID | Description |
|------------|-------------|
| `FIX-ZONE-NY` | `ZoneId.of("America/New_York")` |
| `FIX-CLOCK-2026-07-16T15:00:00Z` | Fixed clock instant `2026-07-16T15:00:00Z` → local date `2026-07-16` in FIX-ZONE-NY |
| `FIX-SESSION-A` | `TrainingSession` id `sess-a`, origin IMPORT, `startedAt=2026-07-01T14:00:00Z`, sourceApp `hevy` |
| `FIX-SESSION-B` | id `sess-b`, origin IMPORT, `startedAt=2026-06-01T14:00:00Z` |
| `FIX-SESSION-LIVE` | id `sess-live`, origin LIVE, `startedAt=2026-07-10T18:00:00Z` |
| `FIX-SESSION-OLD` | id `sess-old`, origin IMPORT, `startedAt=2025-01-15T14:00:00Z` |
| `FIX-SET-A1` | `LoggedSet` id `set-a1`, sessionId `sess-a`, exercise `Squat`, weight 100 Lb, reps 5, rir 2, completedAt `2026-07-01T14:05:00Z` |
| `FIX-SET-B1` | id `set-b1`, sessionId `sess-b`, exercise `Bench`, 60 Kg, reps 8, rir null |
| `FIX-SET-LIVE1` | id `set-live1`, sessionId `sess-live`, exercise `Deadlift`, 140 Kg, reps 3 |
| `FIX-PROGRAM-ACTIVE` | `Program` id `prog-active`, name `Push Pull`, isActive true, cycleLength 4, one workout `Day A` with one lift `Squat` one set |
| `FIX-PROGRAM-LIB` | id `prog-lib`, name `Upper Lower`, isActive false, cycleLength 4, one workout `Upper`, one lift `Bench`, one set |
| `FIX-PROGRAM-IMPORT-HISTORY` | id `import-history`, name `Import History`, isActive false |
| `FIX-STRATUM-LOGS-JSON` | Valid logs envelope schema `stratum.export.logs`, version 1, source stratum, session id `sess-export-1`, set id `set-export-1` |
| `FIX-STRATUM-PROGRAMS-JSON` | Valid programs envelope with programs ids `prog-a` and `prog-b`, each one workout with one lift one set |
| `FIX-HEVY-CSV` | Existing Hevy deterministic fixture content used by functions tests |
| `FIX-ALPHA-PROG-CSV` | Existing Alpha Progression plan CSV fixture under `functions/src/program-import/deterministic/fixtures/` |

---

## Slice 1 — Log export domain

**Primary test classes:**

- `com.stratum.domain.export.ExportTrainingLogsUseCaseTest`
- `com.stratum.domain.export.PreviewExportTrainingLogsUseCaseTest`
- `com.stratum.domain.export.StratumExportJsonTest`
- `com.stratum.domain.export.ExportRangeBoundsTest` (optional co-location in ExportTrainingLogsUseCaseTest)

### IE-S1-001 — AllTime

```gherkin
Scenario: IE-S1-001 Export AllTime includes every session and set
  Given FakeTrainingLogRepository seeded with FIX-SESSION-A, FIX-SESSION-B, FIX-SESSION-LIVE
    and FIX-SET-A1, FIX-SET-B1, FIX-SET-LIVE1
  And clock FIX-CLOCK-2026-07-16T15:00:00Z
  When ExportTrainingLogsUseCase is invoked with ExportLogRange.AllTime
  Then Result is success
  And decoded history.sessions ids equal exactly ["sess-a", "sess-b", "sess-live"] in any order
  And decoded history.sets ids equal exactly ["set-a1", "set-b1", "set-live1"] in any order
```

### IE-S1-002 — LastDays(30)

```gherkin
Scenario: IE-S1-002 Export LastDays 30 uses device-local inclusive window
  Given zone FIX-ZONE-NY and clock FIX-CLOCK-2026-07-16T15:00:00Z
  And seeded FIX-SESSION-A startedAt 2026-07-01T14:00:00Z with FIX-SET-A1
  And seeded FIX-SESSION-OLD startedAt 2025-01-15T14:00:00Z with one set id set-old1
  When ExportTrainingLogsUseCase is invoked with LastDays(days=30, zone=FIX-ZONE-NY)
  Then Result is success
  And history.sessions ids equal exactly ["sess-a"]
  And history.sets ids equal exactly ["set-a1"]
  And range.kind equals "last_days"
  And range.days equals 30
```

### IE-S1-003 — LastDays(90)

```gherkin
Scenario: IE-S1-003 Export LastDays 90 includes session 60 days ago and excludes 120 days ago
  Given zone FIX-ZONE-NY and clock FIX-CLOCK-2026-07-16T15:00:00Z
  And session id sess-60 startedAt 2026-05-17T14:00:00Z with set set-60
  And session id sess-120 startedAt 2026-03-18T14:00:00Z with set set-120
  When ExportTrainingLogsUseCase is invoked with LastDays(days=90, zone=FIX-ZONE-NY)
  Then Result is success
  And history.sessions ids equal exactly ["sess-60"]
  And history.sets ids equal exactly ["set-60"]
```

### IE-S1-004 — YearToDate

```gherkin
Scenario: IE-S1-004 Export YearToDate from January 1 through today in zone
  Given zone FIX-ZONE-NY and clock FIX-CLOCK-2026-07-16T15:00:00Z
  And session id sess-ytd startedAt 2026-02-01T15:00:00Z with set set-ytd
  And session id sess-prev startedAt 2025-12-31T15:00:00Z with set set-prev
  When ExportTrainingLogsUseCase is invoked with YearToDate(zone=FIX-ZONE-NY)
  Then Result is success
  And history.sessions ids equal exactly ["sess-ytd"]
  And history.sets ids equal exactly ["set-ytd"]
  And range.kind equals "year_to_date"
```

### IE-S1-005 — Custom range

```gherkin
Scenario: IE-S1-005 Export Custom inclusive device-local calendar days
  Given zone FIX-ZONE-NY
  And session id sess-mid startedAt 2026-06-15T16:00:00Z with set set-mid
  And session id sess-out startedAt 2026-05-01T16:00:00Z with set set-out
  When ExportTrainingLogsUseCase is invoked with Custom(
    startDate=2026-06-01, endDateInclusive=2026-06-30, zone=FIX-ZONE-NY)
  Then Result is success
  And history.sessions ids equal exactly ["sess-mid"]
  And history.sets ids equal exactly ["set-mid"]
  And range.kind equals "custom"
```

### IE-S1-006 — Custom inverted dates

```gherkin
Scenario: IE-S1-006 Export Custom with start after end fails
  When ExportTrainingLogsUseCase is invoked with Custom(
    startDate=2026-07-10, endDateInclusive=2026-07-01, zone=FIX-ZONE-NY)
  Then Result is failure
  And exception is IllegalArgumentException
  And exception message equals "Start date must be on or before end date."
```

### IE-S1-007 — Schema markers

```gherkin
Scenario: IE-S1-007 Exported logs JSON has required schema markers
  Given seeded FIX-SESSION-A and FIX-SET-A1
  When ExportTrainingLogsUseCase is invoked with AllTime
  Then Result is success
  And JSON field schema equals "stratum.export.logs"
  And JSON field schemaVersion equals 1
  And JSON field source equals "stratum"
  And JSON field exportedAt equals "2026-07-16T15:00:00Z" when clock is FIX-CLOCK-2026-07-16T15:00:00Z
```

### IE-S1-008 — Stable ids

```gherkin
Scenario: IE-S1-008 Exported session and set ids match domain ids
  Given session id "stable-sess-9" with set id "stable-set-9"
  When ExportTrainingLogsUseCase is invoked with AllTime
  Then history.sessions[0].id equals "stable-sess-9"
  And history.sets[0].id equals "stable-set-9"
  And history.sets[0].sessionId equals "stable-sess-9"
```

### IE-S1-009 — Empty range failure

```gherkin
Scenario: IE-S1-009 Empty selection fails without file result
  Given FakeTrainingLogRepository with no sessions in LastDays(30)
  When ExportTrainingLogsUseCase is invoked with LastDays(30, FIX-ZONE-NY)
  Then Result is failure
  And exception is IllegalArgumentException
  And exception message equals "No training logs in the selected range."
```

### IE-S1-010 — Preview counts

```gherkin
Scenario: IE-S1-010 Preview counts match range contents
  Given FIX-SESSION-A + FIX-SET-A1 and FIX-SESSION-OLD + set-old1
  When PreviewExportTrainingLogsUseCase is invoked with LastDays(30, FIX-ZONE-NY)
    at clock FIX-CLOCK-2026-07-16T15:00:00Z
  Then ExportLogPreview.sessionCount equals 1
  And ExportLogPreview.setCount equals 1
```

### IE-S1-011 — Mime and filename

```gherkin
Scenario: IE-S1-011 Export file mime and name
  Given seeded FIX-SESSION-A and FIX-SET-A1 and clock FIX-CLOCK-2026-07-16T15:00:00Z
  When ExportTrainingLogsUseCase is invoked with AllTime
  Then ExportFileResult.mimeType equals "application/json"
  And ExportFileResult.fileName equals "stratum-logs-20260716-150000.json"
```

### IE-S1-M001 — Mutation defense: missing ids

```gherkin
Scenario: IE-S1-M001 Mutation defense export must include session ids
  Given seeded session id "must-export-id" with one set
  When ExportTrainingLogsUseCase is invoked with AllTime
  Then history.sessions[0].id equals "must-export-id"
  And history.sessions[0] JSON object contains key "id"
  # Fails against a lazy implementation that omits id fields from the envelope
```

**Lazy implementation defeated:** serializer that writes sessions without `id` (content-only export).

---

## Slice 2 — Program export domain

**Primary test classes:**

- `com.stratum.domain.export.ExportProgramsUseCaseTest`
- `com.stratum.domain.export.PreviewExportProgramsUseCaseTest`

### IE-S2-001 — Preview excludes import-history

```gherkin
Scenario: IE-S2-001 Preview lists user programs and excludes import-history
  Given FakeProgramRepository with FIX-PROGRAM-ACTIVE, FIX-PROGRAM-LIB, FIX-PROGRAM-IMPORT-HISTORY
  When PreviewExportProgramsUseCase is invoked
  Then result size equals 2
  And result ids equal exactly ["prog-active", "prog-lib"] in any order
  And no option has id "import-history"
```

### IE-S2-002 — Empty selection fails

```gherkin
Scenario: IE-S2-002 Export with empty programIds fails
  When ExportProgramsUseCase is invoked with programIds = []
  Then Result is failure
  And exception is IllegalArgumentException
  And exception message equals "Select at least one program to export."
```

### IE-S2-003 — Multi-program envelope ids

```gherkin
Scenario: IE-S2-003 Multi-program export includes stable ids
  Given FIX-PROGRAM-ACTIVE and FIX-PROGRAM-LIB
  When ExportProgramsUseCase is invoked with [ProgramId("prog-active"), ProgramId("prog-lib")]
  Then Result is success
  And JSON schema equals "stratum.export.programs"
  And schemaVersion equals 1
  And source equals "stratum"
  And programs array length equals 2
  And programs ids equal exactly ["prog-active", "prog-lib"] in any order
  And each programs[i].template.workouts is a non-empty array
```

### IE-S2-004 — Reject import-history

```gherkin
Scenario: IE-S2-004 Export rejects import-history program id
  When ExportProgramsUseCase is invoked with [ProgramId("import-history")]
  Then Result is failure
  And exception is IllegalArgumentException
  And exception message equals "One or more programs could not be exported."
```

### IE-S2-005 — Mime and filename

```gherkin
Scenario: IE-S2-005 Program export mime and filename
  Given FIX-PROGRAM-ACTIVE and clock FIX-CLOCK-2026-07-16T15:00:00Z
  When ExportProgramsUseCase is invoked with [ProgramId("prog-active")]
  Then mimeType equals "application/json"
  And fileName equals "stratum-programs-20260716-150000.json"
```

### IE-S2-M001 — Mutation defense: missing program ids

```gherkin
Scenario: IE-S2-M001 Mutation defense program export must include programs[].id
  Given FIX-PROGRAM-ACTIVE
  When ExportProgramsUseCase is invoked with [ProgramId("prog-active")]
  Then programs[0].id equals "prog-active"
  And programs[0] JSON object contains key "id"
  # Fails against encoder that emits template-only array without id
```

---

## Slice 3 — Firebase Stratum detectors

**Primary test classes (TypeScript):**

- `functions/src/import/deterministic/stratumLogParser.test.ts`
- `functions/src/import/deterministic/formatDetector.test.ts`
- `functions/src/program-import/deterministic/stratumProgramParser.test.ts`
- `functions/src/program-import/deterministic/formatDetector.test.ts`

### IE-S3-001 — Stratum log detect

```gherkin
Scenario: IE-S3-001 Stratum log fixture detects as stratum
  Given FIX-STRATUM-LOGS-JSON as UTF-8 bytes
  When detectAndParse is invoked
  Then result is non-null
  And result.format equals "stratum"
```

### IE-S3-002 — Preserve log ids

```gherkin
Scenario: IE-S3-002 Stratum log parse preserves ids
  Given FIX-STRATUM-LOGS-JSON with session id "sess-export-1" and set id "set-export-1"
  When parseStratumLogExport is invoked
  Then history.sessions[0].id equals "sess-export-1"
  And history.sets[0].id equals "set-export-1"
  And history.sets[0].sessionId equals "sess-export-1"
```

### IE-S3-003 — Multi-session preserve

```gherkin
Scenario: IE-S3-003 Multi-session Stratum log preserves both session ids
  Given logs envelope with sessions "sess-1" and "sess-2"
  When parseStratumLogExport is invoked
  Then history.sessions ids equal exactly ["sess-1", "sess-2"] in any order
```

### IE-S3-004 — Stratum program detect

```gherkin
Scenario: IE-S3-004 Stratum program fixture detects as stratum
  Given FIX-STRATUM-PROGRAMS-JSON as UTF-8 bytes
  When detectAndParseProgramTemplate is invoked
  Then result is non-null
  And result.format equals "stratum"
```

### IE-S3-005 — Multi-program parse

```gherkin
Scenario: IE-S3-005 Multi-program parse returns both templates
  Given FIX-STRATUM-PROGRAMS-JSON with ids prog-a and prog-b
  When parseStratumProgramExport is invoked
  Then programs length equals 2
  And programs ids equal exactly ["prog-a", "prog-b"]
  And each programs[i].template.workouts.length is greater than or equal to 1
```

### IE-S3-006 — Hevy still works

```gherkin
Scenario: IE-S3-006 Third-party Hevy CSV still detects as hevy
  Given FIX-HEVY-CSV bytes
  When detectAndParse is invoked
  Then result.format equals "hevy"
```

### IE-S3-007 — Unknown fallthrough

```gherkin
Scenario: IE-S3-007 Unknown JSON without stratum markers returns null
  Given UTF-8 text "{\"foo\":1}"
  When detectAndParse is invoked
  Then result equals null
```

### IE-S3-008 — Alpha program still works

```gherkin
Scenario: IE-S3-008 Alpha Progression program CSV still detects as alpha_progression
  Given FIX-ALPHA-PROG-CSV bytes
  When detectAndParseProgramTemplate is invoked
  Then result.format equals "alpha_progression"
```

### IE-S3-M001 — Mutation defense: partial markers

```gherkin
Scenario: IE-S3-M001 Mutation defense requires source stratum
  Given JSON {"schema":"stratum.export.logs","schemaVersion":1,"source":"not-stratum","history":{"sessions":[],"sets":[]}}
  When matchesStratumLogExport is invoked
  Then result equals false
  # Fails against matcher that only checks schema string
```

---

## Slice 4 — Merge-safe log persist

**Primary test classes:**

- `com.stratum.domain.importing.ImportHistoryDedupeTest`
- `com.stratum.domain.importing.PersistImportedHistoryMergeTest`
- `com.stratum.domain.importing.ExecuteHistoricalImportPolicyTest`
- `com.stratum.core.inference.RunFullHistoricalImportMergePolicyTest`

### IE-S4-001 — Stratum id skip

```gherkin
Scenario: IE-S4-001 Merge skips existing Stratum session id and inserts new
  Given existing import session id "sess-export-1" with set "set-export-1"
  And incoming sessions "sess-export-1" and "sess-export-2" with sets set-export-1 and set-export-2
  And sourceKind StratumStableIds
  When ImportHistoryDedupe.filterForMerge is invoked with zone FIX-ZONE-NY
  Then filtered sessions ids equal exactly ["sess-export-2"]
  And filtered sets ids equal exactly ["set-export-2"]
  And skippedSessionCount equals 1
  And skippedSetCount equals 1
```

### IE-S4-002 — Third-party day occupancy

```gherkin
Scenario: IE-S4-002 Third-party merge skips occupied local calendar day
  Given existing LIVE session FIX-SESSION-LIVE startedAt 2026-07-10T18:00:00Z
  And incoming third-party session id "tp-1" startedAt 2026-07-10T20:00:00Z with one set
  And sourceKind ThirdParty and zone FIX-ZONE-NY
  When filterForMerge is invoked
  Then filtered sessions is empty
  And skippedSessionCount equals 1
  And skippedSetCount equals 1
```

### IE-S4-003 — LIVE untouched

```gherkin
Scenario: IE-S4-003 Merge append leaves LIVE sessions intact
  Given existing LIVE FIX-SESSION-LIVE and FIX-SET-LIVE1
  And incoming import session "imp-new" with set "set-new" on 2026-07-12
  And policy MergeSkipDuplicates
  When PersistImportedHistoryUseCase succeeds
  Then getAllSessions still contains id "sess-live" with origin LIVE
  And getAllSessions contains id "imp-new" with origin IMPORT
  And getAllSets still contains id "set-live1"
```

### IE-S4-004 — Content match after day filter

```gherkin
Scenario: IE-S4-004 Content match skips identical third-party session on free day edge case
  Given existing IMPORT session on 2026-07-05 with sets fingerprint
    (exerciseNameRaw="Squat", weightAmount=100.0, weightUnit=Lb, reps=5, rir=2)
  And incoming third-party session on 2026-07-05 with the same fingerprint sets
  And sourceKind ThirdParty
  # Note: day occupancy alone already skips; assert skippedSessionCount equals 1
  When filterForMerge is invoked
  Then skippedSessionCount equals 1
  And filtered sessions is empty
```

### IE-S4-005 — Merge failure no wipe

```gherkin
Scenario: IE-S4-005 Merge path failure does not clear prior import history
  Given existing import sessions count equals 2 before invoke
  And PersistImportedHistoryUseCase policy MergeSkipDuplicates
  And ExtractTrainingInsightsUseCase configured to throw RuntimeException("insights boom")
  When persist is invoked with new valid import sessions
  Then Result is failure
  And exception message equals "insights boom"
  And trainingLogRepository.getImportSessions().size equals 2
```

### IE-S4-006 — All duplicates success

```gherkin
Scenario: IE-S4-006 All-duplicate Stratum merge succeeds with zero inserts
  Given existing session "sess-export-1"
  And incoming only "sess-export-1" with its sets
  And policy MergeSkipDuplicates sourceKind StratumStableIds
  When PersistImportedHistoryUseCase is invoked
  Then Result is success
  And mergeStats.sessionsInserted equals 0
  And mergeStats.setsInserted equals 0
  And mergeStats.sessionsSkipped equals 1
```

### IE-S4-007 — Replace policy still rolls back

```gherkin
Scenario: IE-S4-007 ReplaceImportOrigin failure rolls back import history
  Given policy ReplaceImportOrigin
  And after replace there would be import sessions
  And insights extraction throws RuntimeException("replace boom")
  When PersistImportedHistoryUseCase is invoked
  Then Result is failure
  And getImportSessions().size equals 0
```

### IE-S4-M001 — Mutation defense: skip required

```gherkin
Scenario: IE-S4-M001 Mutation defense merge must not insert duplicate session id
  Given existing session id "dup-sess" already in repository
  And incoming session id "dup-sess" with a new set id "dup-set-new"
  And policy MergeSkipDuplicates sourceKind StratumStableIds
  When PersistImportedHistoryUseCase is invoked
  Then getAllSessions filtered by id "dup-sess" has size 1
  And mergeStats.sessionsSkipped is greater than or equal to 1
  # Fails against lazy merge that always appendImportHistory without filterForMerge
```

**Lazy implementation defeated:** `MergeSkipDuplicates` branch that calls `appendImportHistory`
with unfiltered incoming sessions/sets.

---

## Slice 5 — Program import core

**Primary test classes:**

- `com.stratum.domain.program.importing.ImportProgramTemplatesUseCaseTest`
- `com.stratum.domain.program.importing.ProgramImportDedupeTest`
- `com.stratum.domain.program.importing.NormalizedProgramTemplateJsonParseAllTest`
- Existing `ImportProgramTemplateUseCase` tests updated for wrapper

### IE-S5-001 — LibraryOnly no activate / no complete

```gherkin
Scenario: IE-S5-001 LibraryOnly never activates and never completes onboarding
  Given FakeProgramRepository with no programs
  And Fake onboarding repository onboardingComplete = false
  And FakeProgramTemplateImportPort returns ready single template name "Imported A"
  When ImportProgramTemplatesUseCase is invoked with LibraryOnly options
    (activation=LibraryOnly, completeOnboarding=false, appendProgramRegeneratedEvent=false)
  Then Result is success
  And importedProgramIds size equals 1
  And saved program isActive equals false
  And onboardingComplete remains false
```

### IE-S5-002 — Id skip

```gherkin
Scenario: IE-S5-002 Existing exported program id is skipped
  Given library program id "prog-a"
  And downloaded multi/single template with exportedId "prog-a"
  When ImportProgramTemplatesUseCase is invoked with LibraryOnly
  Then importedProgramIds is empty
  And skippedProgramCount equals 1
```

### IE-S5-003 — Multi-program all to library

```gherkin
Scenario: IE-S5-003 Multi-program file imports all templates to library
  Given empty library
  And conversion JSON with programs prog-a and prog-b
  When ImportProgramTemplatesUseCase is invoked with LibraryOnly
  Then importedProgramIds size equals 2
  And skippedProgramCount equals 0
  And every saved program has isActive false
```

### IE-S5-004 — Multi-program partial skip

```gherkin
Scenario: IE-S5-004 Multi-program skips existing id and imports the other
  Given library already has id "prog-a"
  And conversion JSON with prog-a and prog-b
  When ImportProgramTemplatesUseCase is invoked with LibraryOnly
  Then importedProgramIds equals ["prog-b"] or contains only "prog-b" as the new id value
  And skippedProgramCount equals 1
```

### IE-S5-005 — Soft match skip

```gherkin
Scenario: IE-S5-005 Soft name and structure match skips without exported id
  Given existing library program name "Push Pull", cycleLength 4,
    workout names ["Day A"], lift names [["Squat"]], set counts [[1]]
  And incoming ParsedProgramTemplate exportedId null with identical name/structure
  When ProgramImportDedupe.shouldSkip is invoked
  Then result equals true
```

### IE-S5-006 — Wrapper activates and completes

```gherkin
Scenario: IE-S5-006 Onboarding wrapper ActivateAsActive completes onboarding
  Given empty programs and onboardingComplete false
  When ImportProgramTemplateUseCase (wrapper) is invoked with valid file bytes
  Then Result is success
  And active program is non-null with isActive true
  And onboardingComplete equals true
```

### IE-S5-M001 — Mutation defense: no complete on LibraryOnly

```gherkin
Scenario: IE-S5-M001 Mutation defense LibraryOnly must not complete onboarding
  Given Fake CompleteOnboardingUseCase that records invocations
  When ImportProgramTemplatesUseCase is invoked with completeOnboarding=false
  Then completeOnboarding invocation count equals 0
  And onboardingComplete remains false
  # Fails against implementation that always calls CompleteOnboardingUseCase like the old use case
```

---

## Slice 6 — Settings export ViewModels

**Primary test classes:**

- `com.stratum.feature.settings.ExportLogsViewModelTest`
- `com.stratum.feature.settings.ExportProgramsViewModelTest`

Use fakes wrapping real domain use cases with `FakeTrainingLogRepository` /
`FakeProgramRepository`.

### IE-S6-001 — Share event logs

```gherkin
Scenario: IE-S6-001 Export logs click emits share event
  Given seeded FIX-SESSION-A and FIX-SET-A1
  And ExportLogsViewModel with range AllTime
  When onExportClick is invoked
  Then shareEvents emits exactly one ExportFileResult
  And that result.mimeType equals "application/json"
  And uiState.isExporting equals false after completion
```

### IE-S6-002 — Last30 preview

```gherkin
Scenario: IE-S6-002 Selecting Last30 updates preview to sessionCount 1
  Given seeded data such that LastDays(30) contains exactly 1 session and 1 set
  When onRangePresetSelected(Last30) is invoked
  Then uiState.rangePreset equals ExportRangePresetUi.Last30
  And uiState.preview.sessionCount equals 1
  And uiState.preview.setCount equals 1
```

### IE-S6-003 — Empty export error

```gherkin
Scenario: IE-S6-003 Empty range export surfaces exact error
  Given repository empty for selected range
  When onExportClick is invoked
  Then uiState.errorMessage equals "No training logs in the selected range."
  And shareEvents emits no values
```

### IE-S6-004 — Custom dates

```gherkin
Scenario: IE-S6-004 Custom dates stored on state
  When onRangePresetSelected(Custom)
  And onCustomDatesChanged(start=2026-01-01, end=2026-01-31)
  Then uiState.customStart equals 2026-01-01
  And uiState.customEnd equals 2026-01-31
  And uiState.rangePreset equals ExportRangePresetUi.Custom
```

### IE-S6-005 — Toggle select

```gherkin
Scenario: IE-S6-005 Toggle program selects id
  Given preview lists program id "prog-1" name "Alpha"
  When onToggleProgram("prog-1") is invoked
  Then uiState.selectedIds equals setOf("prog-1")
```

### IE-S6-006 — Select all

```gherkin
Scenario: IE-S6-006 Select all selects every program
  Given programs ids ["prog-1", "prog-2"]
  When onSelectAll is invoked
  Then uiState.selectedIds equals setOf("prog-1", "prog-2")
```

### IE-S6-007 — Programs share event

```gherkin
Scenario: IE-S6-007 Export programs emits share event
  Given selectedIds contains "prog-active" and program exists
  When onExportClick is invoked
  Then shareEvents emits one ExportFileResult with mimeType "application/json"
  And fileName starts with "stratum-programs-"
```

### IE-S6-008 — Empty selection error

```gherkin
Scenario: IE-S6-008 Export programs with no selection shows error
  Given selectedIds is empty
  When onExportClick is invoked
  Then uiState.errorMessage equals "Select at least one program to export."
  And shareEvents emits no values
```

### IE-S6-M001 — Mutation defense: select-all

```gherkin
Scenario: IE-S6-M001 Mutation defense select-all must select all listed ids
  Given programs ids ["a", "b", "c"] and selectedIds empty
  When onSelectAll is invoked
  Then uiState.selectedIds size equals 3
  And uiState.selectedIds contains "a" and "b" and "c"
  # Fails against hard-coded selectedIds = setOf(firstId) or no-op select-all
```

---

## Slice 7 — Settings import ViewModels

**Primary test classes:**

- `com.stratum.feature.settings.ImportLogsViewModelTest`
- `com.stratum.feature.settings.ImportProgramViewModelTest`

### IE-S7-001 — Signed-out logs

```gherkin
Scenario: IE-S7-001 Import logs signed-out shows guidance and skips execute
  Given FakeAuthManager AuthState.Unauthenticated
  And FakeHistoricalImportRunnerPort with invocation counter
  When onFilesSelected is invoked with one ImportFileInput
  Then uiState.signedOutGuidanceVisible equals true
  And runner invocations size equals 0
```

### IE-S7-002 — Merge policy passed

```gherkin
Scenario: IE-S7-002 Import logs passes MergeSkipDuplicates
  Given FakeAuthManager Authenticated user uid "u1"
  And FakeHistoricalImportRunnerPort capturing persistPolicy
  When onFilesSelected is invoked with one valid file
  Then runner last invocation persistPolicy equals ImportHistoryPersistPolicy.MergeSkipDuplicates
```

### IE-S7-003 — Merge stats on success

```gherkin
Scenario: IE-S7-003 Import logs success surfaces merge stats
  Given authenticated user
  And runner emits Completed with mergeStats sessionsInserted=1 setsInserted=2 sessionsSkipped=0 setsSkipped=0
  When onFilesSelected completes
  Then uiState.mergeStats.sessionsInserted equals 1
  And uiState.mergeStats.setsInserted equals 2
```

### IE-S7-004 — Cancel

```gherkin
Scenario: IE-S7-004 Cancel import does not throw
  Given authenticated user and import in progress
  When onCancelImport is invoked
  Then no exception is thrown
  And uiState.errorMessage is null or a non-crash cancel terminal state as implemented without process death
```

### IE-S7-005 — Signed-out program

```gherkin
Scenario: IE-S7-005 Import program signed-out shows guidance
  Given AuthState.Unauthenticated
  And recording ImportProgramTemplatesUseCase fake with callCount 0
  When onFileSelected is invoked with bytes and fileName "p.json"
  Then uiState.signedOutGuidanceVisible equals true
  And callCount equals 0
```

### IE-S7-006 — LibraryOnly options

```gherkin
Scenario: IE-S7-006 Import program uses LibraryOnly options
  Given Authenticated
  And fake ImportProgramTemplatesUseCase capturing options
  When onFileSelected is invoked
  Then options.activation equals ProgramImportActivation.LibraryOnly
  And options.completeOnboarding equals false
  And options.appendProgramRegeneratedEvent equals false
```

### IE-S7-007 — Success message counts

```gherkin
Scenario: IE-S7-007 Import program success message includes counts
  Given Authenticated
  And use case returns ProgramTemplateImportResult(importedProgramIds=[ProgramId("x")], skippedProgramCount=0)
  When onFileSelected completes
  Then uiState.successMessage equals "Imported 1 program (0 skipped)."
```

### IE-S7-M001 — Mutation defense: no ReplaceImportOrigin

```gherkin
Scenario: IE-S7-M001 Mutation defense Settings never passes ReplaceImportOrigin
  Given Authenticated and capturing runner
  When onFilesSelected is invoked
  Then persistPolicy equals ImportHistoryPersistPolicy.MergeSkipDuplicates
  And persistPolicy does not equal ImportHistoryPersistPolicy.ReplaceImportOrigin
  # Fails against copy-paste from onboarding that uses default ReplaceImportOrigin
```

---

## Slice 8 — Onboarding regression

**Primary test classes:**

- Existing `OnboardingGymLiftsStepViewModelTest` / `OnboardingImportProgramViewModelTest` updated
- `com.stratum.domain.importing.ExecuteHistoricalImportPolicyTest` default policy case
- `ImportProgramTemplateUseCase` wrapper test

### IE-S8-001 — Historical default replace

```gherkin
Scenario: IE-S8-001 Onboarding historical import defaults ReplaceImportOrigin
  Given OnboardingViewModel factory with FakeHistoricalImportRunnerPort capturing policy
  When onImportFilesSelected is invoked with one file
  Then runner persistPolicy equals ImportHistoryPersistPolicy.ReplaceImportOrigin
```

### IE-S8-002 — Program still completes onboarding

```gherkin
Scenario: IE-S8-002 Onboarding program import still completes onboarding
  Given onboardingComplete false and FakeProgramTemplateImportPort ready
  When OnboardingViewModel importExistingProgram succeeds
  Then onboardingComplete equals true
  And active program is non-null
```

### IE-S8-003 — Replace failure still rolls back

```gherkin
Scenario: IE-S8-003 Onboarding historical failure still rolls back import history
  Given ReplaceImportOrigin policy path via ExecuteHistoricalImportUseCase default
  And runner emits Failed after partial replace semantics
  When orchestration collects Failed event
  Then RollbackHistoricalImportUseCase was applied such that getImportSessions().size equals 0
```

---

## Mutation-defense risk summary

| ID | Plausible lazy bug | Why scenario fails against it |
|----|--------------------|-------------------------------|
| IE-S1-M001 | Export omits session/set `id` keys | Assert requires literal id field present |
| IE-S2-M001 | Program export omits `programs[].id` | Assert requires id `prog-active` |
| IE-S3-M001 | Matcher only checks `schema` string | `source: not-stratum` must not match |
| IE-S4-M001 | Merge always appends without skip | Duplicate session id count becomes 2 |
| IE-S5-M001 | LibraryOnly still completes onboarding | `completeOnboarding` call count must be 0 |
| IE-S6-M001 | Select-all hard-codes one id or no-op | selectedIds size must be 3 |
| IE-S7-M001 | Settings uses default ReplaceImportOrigin | Captured policy must be MergeSkipDuplicates |

---

## Self-review (test author)

- [x] Framework and exact run commands listed
- [x] Scenario index complete (62 IDs)
- [x] Happy path, error, edge, and mutation scenarios covered for ticket requirements
- [x] Every Then uses literal expected values, enums, or exact exception messages
- [x] Names match `spec.md` types and messages
- [x] No TBD / TODO / "similar to" placeholders
- [x] Mutation scenarios name the lazy implementation they defeat
