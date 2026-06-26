# Response Profile — Codebase Research

## Research Questions

### Existing insight extraction (`TrainingInsightsExtractor`, models, persistence)

1. What symbols implement Tier 1 insight extraction, and where do they live?
2. What are the inputs and outputs of `TrainingInsightsExtractor.analyze`?
3. What thresholds and constants govern the analysis window, lift insights, supersets, and volume baselines?
4. How is e1RM calculated, and what fields on `LoggedSet` does the extractor use or ignore?
5. What domain models represent persisted insights, and what fields do they carry?
6. Which use cases orchestrate extraction and persistence, and when are they invoked?
7. What happens on import failure, rollback, or partial import (reconciliation)?
8. Is insight extraction re-runnable outside the import pipeline?

### Program generation payload consumption

9. What use case assembles the program-generation payload, and what repositories does it read?
10. What JSON structure does `ProgramGenerationPromptBuilder` emit, and which insight fields are included or omitted?
11. How are "proven" vs "available" lifts determined for the payload?
12. Does any feature module or inference adapter currently call `LoadProgramGenerationPayloadUseCase`?
13. Do `ProgramGeneratorPort`, `GenerateProgramUseCase`, or domain `Program`/`Workout` models exist in code?

### Historical session/set data model and repositories

14. What domain types represent imported history, and what fields do they expose?
15. What repository port abstracts training history, and what operations does it support?
16. How is imported history persisted in Room, and is there query-by-window support at the DAO level?
17. What sanitization runs before history is persisted?
18. How does server-normalized JSON become domain `HistoricalSession` / `LoggedSet` rows?

### Program/workout template models (Tier 4 prescription vs outcome linkage)

19. Do `Program`, `Workout`, `WorkoutLift`, or `Set` domain models or Room tables exist?
20. Is there any code linking prescribed program structure to logged outcomes?
21. What planned modules/docs describe post-onboarding workout logging and program storage?

### Test patterns and fakes in domain/testing

22. Which unit tests cover `TrainingInsightsExtractor` and `ExtractTrainingInsightsUseCase`, and what patterns do they use?
23. What shared fakes exist in `:core:testing` for import/insight ports?
24. Are there Robolectric DAO round-trip tests for import/insight tables?
25. What Gradle commands do contracts specify for domain and data tests?

### Schema/tables for insights

26. Which Room entities and tables store insights and import metadata, and what is the DB version?
27. What are the repository implementations and replace/upsert semantics for insight tables?
28. What schema-lock policy applies to new tables/columns?

### Onboarding vertical slice docs (analytics, OS-6, OS-7)

29. What do `docs/onboarding-vertical-slices.md` OS-6 and OS-7 specify about analytics scope, tables, and payload consumption?
30. Are there discrepancies between OS-6 documentation and current implementation?

### Progression/volume deterministic engines

31. Does a `ProgressionScheme` enum or progression engine exist in `:core:domain`?
32. How does `MuscleVolumeBaseline` relate to "hard sets," and do primary vs secondary muscles differ in counting?
33. What volume/specialization concepts exist in onboarding domain models?
34. Does WorkManager or periodic re-analysis exist for insights?

---

## Findings

### 1. Tier 1 extraction symbols and location

- `TrainingInsightsExtractor` — `core/domain/src/main/kotlin/com/stratum/domain/importing/TrainingInsightsExtractor.kt:15`
- `ExtractTrainingInsightsUseCase` — `core/domain/src/main/kotlin/com/stratum/domain/importing/ImportUseCases.kt:12`
- `EstimatedOneRm` — `core/domain/src/main/kotlin/com/stratum/domain/importing/LiftNameResolver.kt:126`
- Domain models — `core/domain/src/main/kotlin/com/stratum/domain/importing/ImportModels.kt`
- Repository ports — `core/domain/src/main/kotlin/com/stratum/domain/importing/ImportPorts.kt`

### 2. `TrainingInsightsExtractor` I/O

- **Input:** `AnalysisInput(sessions, sets, lifts, provenLiftIds = emptySet())` — `TrainingInsightsExtractor.kt:29-38`
- **Output:** `AnalysisOutput(metadata, liftInsights, supersetAffinities, volumeBaselines)` — `TrainingInsightsExtractor.kt:40-45`
- Pure analytics; comment at `TrainingInsightsExtractor.kt:12-14` states persistence is the use case's job.

### 3. Thresholds and constants

| Constant | Value | Location |
|----------|-------|----------|
| `MAX_WINDOW_DAYS` | 183 | `TrainingInsightsExtractor.kt:16` |
| `MIN_WEEKS` | 12 | `TrainingInsightsExtractor.kt:17` |
| `MIN_SESSIONS_FOR_FATIGUE` | 3 | `TrainingInsightsExtractor.kt:18` |
| `MIN_WEEKLY_POINTS_FOR_PROGRESS` | 4 | `TrainingInsightsExtractor.kt:19` |
| `SUPERSET_MAX_MEDIAN_SECONDS` | 90.0 | `TrainingInsightsExtractor.kt:20` |
| `SUPERSET_MIN_CO_OCCURRENCE` | 5 | `TrainingInsightsExtractor.kt:21` |
| `STAGNANT_SLOPE_THRESHOLD` | 0.25 | `TrainingInsightsExtractor.kt:22` |
| `MAJOR_MUSCLE_HARD_SET_THRESHOLD` | 30 | `TrainingInsightsExtractor.kt:25` |
| Superset confidence cap | `coOccurrenceCount / 20.0`, max 1.0 | `TrainingInsightsExtractor.kt:190` |

Window selection: `selectWindow` — `TrainingInsightsExtractor.kt:74-98`.

### 4. e1RM and `LoggedSet` field usage

- e1RM: Brzycki for reps ≤ 10, Epley otherwise — `LiftNameResolver.kt:123-138`
- Per-set e1RM from `weight.amount` and `reps`; sets with `reps <= 0` excluded from fatigue regression — `TrainingInsightsExtractor.kt:114-116`
- **Used:** `liftId`, `sessionId`, `setIndex`, `weight`, `reps`, `completedAt`, `exerciseNameRaw` (unmatched list)
- **Not used in extractor:** `rir` (field exists on `LoggedSet` — `ImportModels.kt:37`)
- Sets with `liftId == null` excluded from lift insights, supersets, volume baselines — `TrainingInsightsExtractor.kt:104,162,204`

### 5. Insight domain models

- `LiftTrainingInsights` — `liftId`, `estimatedOneRm`, `fatigueDecaySlope`, `fatigueDecayIntercept`, `sessionCount`, `progressTrend`, `lastPerformedAt` — `ImportModels.kt:58-66`
- `SupersetAffinity` — `liftA`, `liftB`, `medianRestSeconds`, `coOccurrenceCount`, `confidence` — `ImportModels.kt:68-74`
- `MuscleVolumeBaseline` — `muscle`, `meanWeeklyHardSets`, `p90WeeklyHardSets`, `trendSlope` — `ImportModels.kt:76-81`
- `ImportAnalysisMetadata` — `windowStart`, `windowEnd`, `provenLiftIds`, `unmatchedExerciseNames`, `totalSessions`, `totalSets` — `ImportModels.kt:83-96`
- `ProgressTrend` enum: `Improving`, `Stagnant`, `Regressing` — `ImportModels.kt:52-56`
- No `proven`/`hypothesis`/`insufficient` confidence labels on lift or muscle insight models.

### 6. Extraction orchestration and invocation

- `ExtractTrainingInsightsUseCase` reads all sessions/sets/lifts, calls `TrainingInsightsExtractor.analyze`, then `replaceAll`/`save` on four repositories — `ImportUseCases.kt:22-37`
- Invoked from `PersistImportedHistoryUseCase` after lift resolution and history persist, with `provenLiftIds` from import — `ImportUseCases.kt:374-415`
- `PersistImportedHistoryUseCase` is called from `RunFullHistoricalImportUseCase` — `core/inference/src/main/kotlin/com/stratum/core/inference/RunFullHistoricalImportUseCase.kt:72-77`
- On extract failure, history is rolled back (empty replace) — `ImportUseCases.kt:408-410`

### 7. Rollback and reconciliation

- `RollbackHistoricalImportUseCase` clears history + all four insight repositories + metadata; sets `usedHistoricalImport` false — `ImportUseCases.kt:418-433`
- `ReconcileHistoricalImportUseCase`: if `ImportAnalysisMetadataRepository.get()` is null, runs rollback — `ImportUseCases.kt:440-450`
- Import "complete" requires analysis metadata — comment at `ImportUseCases.kt:436-438`

### 8. Re-runnability outside import

- `ExtractTrainingInsightsUseCase.invoke(provenLiftIds = emptySet())` is public with default empty proven IDs — `ImportUseCases.kt:20-21`
- **Inference:** No other production caller besides `PersistImportedHistoryUseCase` (grep across worktree).
- No WorkManager or scheduled re-extraction in codebase.

### 9. Payload assembly use case

- `LoadProgramGenerationPayloadUseCase` — `core/domain/src/main/kotlin/com/stratum/domain/program/LoadProgramGenerationPayloadUseCase.kt:14`
- Reads: `UserTrainingProfileRepository`, `ObserveSpecializationUseCase`, `LiftRepository`, all four insight repositories, `ProgramGenerationPromptBuilder` — lines 15-22, 25-45

### 10. Payload JSON structure

- `ProgramGenerationPromptBuilder.buildPayload` emits: `profile`, `specialization`, `provenLifts`, `availableLifts`, optional `supersetAffinities`, optional `volumeBaselines`, optional `importAnalysis` — `ProgramGenerationPromptBuilder.kt:39-70`
- Per-lift `trainingInsights` JSON: `sessionCount`, optional `estimatedOneRm`, `fatigueDecaySlope`, `fatigueDecayIntercept`, `progressTrend` — `ProgramGenerationPromptBuilder.kt:75-88`
- `MuscleVolumeBaseline` JSON: `muscle`, `meanWeeklyHardSets`, `p90WeeklyHardSets`, optional `trendSlope` — `ProgramGenerationPromptBuilder.kt:102-106`
- `importAnalysis` snippet: `windowStart`, `windowEnd`, `totalSessions`, `totalSets` only — `ProgramGenerationPromptBuilder.kt:108-117`
- **Omitted from payload JSON:** `SupersetAffinity.confidence`, `ImportAnalysisMetadata.provenLiftIds`, `ImportAnalysisMetadata.unmatchedExerciseNames`
- Test confirms no `inferredGymProfile` — `ProgramGenerationPromptBuilderTest.kt:58-78`

### 11. Proven vs available lift partitioning

- Proven: enabled lift whose id ∈ `metadata.provenLiftIds` **and** has entry in `liftTrainingInsightsRepository` — `LoadProgramGenerationPayloadUseCase.kt:29-32`
- Available: remaining enabled lifts — lines 30-35
- `proven: true/false` flag in lift JSON — `ProgramGenerationPromptBuilder.kt:74,93`

### 12. Payload use case consumers

- **No production consumer** of `LoadProgramGenerationPayloadUseCase` outside its definition file (grep).
- OS-7 Generate step UI not present in `feature/onboarding` (no `*Generate*` files).

### 13. Program generation domain (OS-7)

- `ProgramGeneratorPort`, `GenerateProgramUseCase`, domain `Program`/`Workout`/`WorkoutLift`/`Set` — **not present in Kotlin source** (only in `docs/onboarding-vertical-slices.md`)
- `BuildProgramGenerationPayloadUseCase` (non-injected wrapper) exists — `ProgramGenerationPromptBuilder.kt:141-160`

### 14. Historical domain types

- `HistoricalSession(id, startedAt, endedAt, sourceApp)` — `ImportModels.kt:22-27`
- `LoggedSet(id, sessionId, exerciseNameRaw, liftId, setIndex, weight, reps, rir, completedAt)` — `ImportModels.kt:29-39`
- IDs: `HistoricalSessionId`, `LoggedSetId`, `ImportJobId` value classes — `core/domain/src/main/kotlin/com/stratum/domain/importing/identity/ImportIds.kt`

### 15. `TrainingHistoryRepository` port

- `replaceAll`, `getAllSessions`, `getAllSets`, `getSetsInWindow` — `ImportPorts.kt:98-106`

### 16. Room persistence and window queries

- Entities: `HistoricalSessionEntity`, `LoggedSetEntity` — `core/data/src/main/kotlin/com/stratum/core/data/db/ImportEntities.kt:20-40`
- `RoomTrainingHistoryRepository.replaceAll` transactional delete+insert — `RoomImportRepositories.kt:68-74`
- `getSetsInWindow` filters in memory over `getAllSets()` — `RoomImportRepositories.kt:83-88`; no indexed SQL time-range query on `LoggedSetDao` — `ImportDaos.kt:41-51`

### 17. Sanitization

- `ImportedHistorySanitizer.sanitize` drops sets with `reps <= 0`, negative weight, blank exercise name; drops sets referencing unknown session ids — `ImportedHistorySanitizer.kt:8-21`
- Called in `PersistImportedHistoryUseCase` before lift resolution — `ImportUseCases.kt:385`

### 18. JSON → domain parsing

- `ImportedHistoryJsonParser.parse` in `:core:inference` — `core/inference/src/main/kotlin/com/stratum/core/inference/ImportedHistoryJsonParser.kt:41-68`
- Parsed sets have `liftId = null`; resolution happens in `ResolveImportLiftsUseCase` — line 59

### 19. Program/workout template models

- **None in codebase.** `StratumDatabase` version 1 has no `programs`/`workouts` tables — `StratumDatabase.kt:7-24`
- OS-7 schema documented as future — `docs/onboarding-vertical-slices.md`

### 20. Prescription vs outcome linkage

- **None.** Docs explicitly separate historical logging from program templates.
- No experiment registry, epoch, anchor-lift, or stimulus/outcome descriptor types in domain.

### 21. Post-onboarding planned modules

- `:feature:workout`, `:feature:program`, `:feature:insights` planned — `contracts/ARCHITECTURE.md:45-49`
- Post-onboarding slice list — `docs/onboarding-vertical-slices.md`
- `:feature:insights` described as "What actually worked" dashboard — `ARCHITECTURE.md:49`

### 22. Extractor/use-case tests

- `TrainingInsightsExtractorTest` — synthetic 5-session fixture; asserts fatigue slope, session count, superset affinity, quad volume baseline — `core/domain/src/test/kotlin/com/stratum/domain/importing/TrainingInsightsExtractorTest.kt`
- `ExtractTrainingInsightsUseCaseTest` — module-local `InMemory*` fakes; asserts `liftsWithInsights > 0` — `ExtractTrainingInsightsUseCaseTest.kt`
- `ProgramGenerationPromptBuilderTest` — proven/available split, no `inferredGymProfile` — `ProgramGenerationPromptBuilderTest.kt`
- **No** `LoadProgramGenerationPayloadUseCaseTest`

### 23. `:core:testing` fakes

- Import-related: `FakeHistoricalImportPort`, `FakeHistoricalImportRunnerPort`, `FakeImportJobRepository`, etc. — `core/testing/src/main/kotlin/com/stratum/core/testing/importing/`
- Onboarding: `FakeLiftRepository`, `FakeUserTrainingProfileRepository`, etc.
- **No shared fakes** for insight repositories or `TrainingHistoryRepository` (tests use private `InMemory*` classes)

### 24. Robolectric DAO tests for import tables

- Existing DAO tests: `UserProfileDaoTest`, `OnboardingProgressDaoTest` only
- OS-6 doc lists Robolectric `logged_sets` DAO round-trip as planned; **not implemented**

### 25. Test run commands (contracts)

- Domain: `./gradlew :core:domain:test` — `AGENTS.md`, `contracts/UNIT_TESTING.md`
- Data (Android libs): `./gradlew :core:data:testDebugUnitTest`
- Full: `./gradlew testDebugUnitTest`

### 26. Insight Room schema

| Table | Entity | PK |
|-------|--------|-----|
| `lift_training_insights` | `LiftTrainingInsightsEntity` | `liftId` |
| `superset_affinities` | `SupersetAffinityEntity` | auto `rowId` |
| `muscle_volume_baselines` | `MuscleVolumeBaselineEntity` | `muscle` |
| `import_analysis_metadata` | `ImportAnalysisMetadataEntity` | singleton `id=0` |
| `historical_sessions` | `HistoricalSessionEntity` | `id` |
| `logged_sets` | `LoggedSetEntity` | `id` |
| `import_jobs` | `ImportJobEntity` | `id` |

- DB version: **1** — `StratumDatabase.kt:23`

### 27. Repository implementations and semantics

- All Room insight repos: `replaceAll` = delete-all then insert
- Metadata: singleton upsert
- DI bindings in `app/src/main/java/com/stratum/di/DataModule.kt:172-204`
- Mappers: `core/data/src/main/kotlin/com/stratum/core/data/mapper/ImportMapper.kt`

### 28. Schema lock policy

- `AGENTS.md:4.5` — no new tables/columns without explicit authorization
- `ARCHITECTURE.md:221-225` — version 1, no migrations pre-release

### 29. OS-6/OS-7 doc specifications

- OS-6: extract+persist only; no programming decisions
- Analytics pipeline steps 1–7 documented
- OS-7: first consumer of persisted insights; does not re-run analytics
- Planned domain: `Program`, `Workout`, `WorkoutLift`, `Set`, `ProgramGeneratorPort`

### 30. Doc vs implementation discrepancies

- OS-6 domain model list mentions "inferred gym profile" in metadata; **removed from code**
- OS-6 analytics step 4 "Gym inference"; equipment/gym are transient in import enablement, not persisted in metadata

### 31. Progression engine

- `ProgressionScheme { Linear, Double }` — **documentation only**
- **No Kotlin enum or progression engine** in `:core:domain`
- `TrainingAge` enum exists; assignment rules documented §2.3, not implemented in code

### 32. Volume / hard-set counting

- Window gate `countHardSetsByMuscle` counts **primary muscles only** — `TrainingInsightsExtractor.kt:238-249`
- `computeVolumeBaselines` counts **primary + secondary** muscles per set — `TrainingInsightsExtractor.kt:209-217`
- **Inference:** "Hard set" = any resolved set row; no RIR threshold or set-type filter in extractor

### 33. Onboarding volume/specialization models

- `MuscleGroup` enum (18 values) — `MuscleGroup.kt`
- `SpecializationLevel` (`Specialize`, `Maintenance`, `Omit`) — `SpecializationLevel.kt`
- `SpecializationPreference` consumed by payload builder
- MEV/MRV referenced in comments/docs only; no landmark constants in domain code

### 34. Periodic re-analysis

- WorkManager for "phase detection, insight generation" — guidance only — `ARCHITECTURE.md:227`
- **No WorkManager workers** or scheduled insight jobs in codebase

---

## Open Unknowns (for Design)

- Product rules for epoch splitting, anchor-lift selection, confidence thresholds (see `ticket.md`)
- Whether response profiles extend existing insight tables vs new tables (schema authorization required)
- Data source for Tier 4 once `:feature:workout` exists (only import history persisted today)
- Whether ongoing logging appends to `logged_sets` or uses separate tables (not implemented)
