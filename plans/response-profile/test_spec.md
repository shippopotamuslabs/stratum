# Response Profile — Test Spec

**Ticket:** `response-profile`  
**Phase:** Plan (Synthitect)  
**Companion:** `spec.md`  
**Framework:** JUnit 5 (`org.junit.jupiter.api`), Turbine for Flow/StateFlow, fakes from `:core:testing` or module-local in-memory implementations. No MockK on Stratum ports.

---

## Run Commands

| Scope | Command |
|-------|---------|
| Domain unit tests | `./gradlew :core:domain:test` |
| Single test class | `./gradlew :core:domain:test --tests "com.stratum.domain.response.TrainingSignalCleanerTest"` |
| Data DAO tests | `./gradlew :core:data:testDebugUnitTest` |
| Data single test | `./gradlew :core:data:testDebugUnitTest --tests "com.stratum.core.data.db.ResponseDaoTest"` |
| Full Android unit tests | `./gradlew testDebugUnitTest` |
| Compile smoke | `./gradlew :app:assembleDebug` |
| Pre-merge | `./gradlew ktlintCheck`, `./gradlew detekt`, `./gradlew testDebugUnitTest`, `./gradlew :core:domain:test` |

---

## Fixture Conventions

Fixtures live in `core/domain/src/test/kotlin/com/stratum/domain/response/fixtures/ResponseFixtures.kt`.

| Fixture ID | Description |
|------------|-------------|
| `FIX-S1-WORKING` | Single session, bench press 100 kg × 5 @ RIR 2 |
| `FIX-S1-WARMUP-RIR` | Same session top set plus prior set 60 kg × 10 @ RIR 6 |
| `FIX-S1-WARMUP-LOAD` | Same session top set plus prior set at 60% of top weight, RIR null |
| `FIX-S1-MIXED-UNIT` | Two sessions same lift with weight unit mismatch flagged |
| `FIX-S2-RIR-WEEK` | One calendar week, 4 working sets, 3 with RIR logged (75% coverage) |
| `FIX-S2-NO-RIR-WEEK` | One calendar week, 4 working sets, 0 with RIR |
| `FIX-S2-OUTLIER-WEEKLY` | 5 consecutive weeks e1RM series [100, 102, 200, 103, 104] kg |
| `FIX-S3-GAP-15D` | Two training blocks separated by 15 calendar days |
| `FIX-S3-JACCARD` | Primary lift set changes > 50% Jaccard over rolling 4-week window |
| `FIX-S3-DELOAD-VOL` | One week at 40% of epoch median weekly hard sets, no anchor PR |
| `FIX-S3-DELOAD-WGT` | One week anchor mean weight −20%, set count within ±20% of epoch median |
| `FIX-S4-SPARSE-QUAD` | Quad primary lifts in 5 sessions over 4 weeks |
| `FIX-S4-RICH-CHEST` | Chest primary lift in 10 sessions over 8 weeks |
| `FIX-S5-SINGLE-EPOCH` | One 10-week epoch, 5 weekly OLS points for Quad |
| `FIX-S5-DUAL-EPOCH` | Two 10-week epochs, positive slope in both for Quad |
| `FIX-S6-PATTERN-3` | HorizontalPush pattern with 3 lifts, 8 sessions each |
| `FIX-S6-SINGLE-LIFT` | HorizontalPush with 1 lift, 8 sessions |
| `FIX-S7-MULTI-EPOCH` | Composite fixture spanning slices 3–6 |
| `FIX-S9-IMPORT-OK` | Full import with Tier 1 + response success |
| `FIX-S10-PAYLOAD-MIX` | Observations covering all three `EvidenceLevel` values |

All `Then` clauses below use literals from these fixtures or explicit constants from `ResponseConstants.kt`. Expected values are **not** derived from production code under test.

---

## Slice 1 — Clean Training Signal

### RP-S1-001 — Working set with moderate RIR classified as working

```gherkin
Scenario: RP-S1-001 working set retained
  Given fixture FIX-S1-WORKING with weight 100.0 kg, reps 5, rir 2
  When TrainingSignalCleaner.clean is invoked
  Then the output contains exactly 1 CleanTrainingSet
  And isWorkingSet equals true
  And normalizedWeight equals 100.0
  And estimatedOneRm is greater than 100.0
  And qualityFlags does not contain HighRirWarmupExcluded
```

### RP-S1-002 — High RIR warmup excluded

```gherkin
Scenario: RP-S1-002 high RIR warmup excluded
  Given fixture FIX-S1-WARMUP-RIR with warmup rir 6 and working set rir 2
  When TrainingSignalCleaner.clean is invoked
  Then the output contains exactly 1 CleanTrainingSet
  And the excluded warmup set has isWorkingSet false
  And qualityFlags on excluded row contains HighRirWarmupExcluded
  And qualityFlags on excluded row contains value HighRirWarmupExcluded exactly once in the set
```

### RP-S1-003 — Low relative load warmup excluded without RIR

```gherkin
Scenario: RP-S1-003 low relative load warmup excluded
  Given fixture FIX-S1-WARMUP-LOAD with top set 100.0 kg and warmup 60.0 kg, rir null on warmup
  When TrainingSignalCleaner.clean is invoked
  Then exactly 1 row has isWorkingSet true
  And exactly 1 row has isWorkingSet false
  And the false row qualityFlags contains LowRelativeLoadWarmupExcluded
```

### RP-S1-004 — RIR-adjusted e1RM computed when RIR present

```gherkin
Scenario: RP-S1-004 RIR-adjusted e1RM populated
  Given fixture FIX-S1-WORKING with weight 100.0 kg, reps 5, rir 2
  When TrainingSignalCleaner.clean is invoked
  Then rirAdjustedEstimatedOneRm is not null
  And rirAdjustedEstimatedOneRm is greater than estimatedOneRm
```

### RP-S1-005 — Missing RIR quality flag

```gherkin
Scenario: RP-S1-005 missing RIR flagged
  Given a single set with weight 100.0 kg, reps 5, rir null
  When TrainingSignalCleaner.clean is invoked
  Then qualityFlags contains MissingRir
  And rirAdjustedEstimatedOneRm is null
```

### RP-S1-006 — Mixed units quality flag

```gherkin
Scenario: RP-S1-006 mixed units flagged
  Given fixture FIX-S1-MIXED-UNIT with lb-tagged and kg-tagged sets for same lift
  When TrainingSignalCleaner.clean is invoked
  Then at least 1 CleanTrainingSet qualityFlags contains MixedUnits
```

### RP-S1-M001 — Mutation defense: warmup threshold boundary

```gherkin
Scenario: RP-S1-M001 RIR exactly 5 is excluded not working
  Given a single set with weight 80.0 kg, reps 8, rir 5
  When TrainingSignalCleaner.clean is invoked
  Then isWorkingSet equals false
  And qualityFlags contains HighRirWarmupExcluded
```

**Test class:** `com.stratum.domain.response.TrainingSignalCleanerTest`

---

## Slice 2 — Weekly Series and Robustness

### RP-S2-001 — RIR coverage at threshold uses inverted mean RIR

```gherkin
Scenario: RP-S2-001 RIR-first intensity at 75 percent coverage
  Given fixture FIX-S2-RIR-WEEK with 4 sets, 3 sets rir values [1, 2, 3], 1 set rir null
  When IntensityProxyCalculator.forWeek is invoked for that lift
  Then intensityProxy equals 2.0
  And intensityProxy is computed as inverted mean RIR (6 minus mean of logged RIR values 1, 2, 3)
```

### RP-S2-002 — Below RIR coverage falls back to e1RM proxy

```gherkin
Scenario: RP-S2-002 e1RM proxy fallback below 70 percent RIR
  Given fixture FIX-S2-NO-RIR-WEEK with 4 working sets and 0 rir values
  When IntensityProxyCalculator.forWeek is invoked
  Then intensityProxy is not null
  And intensityProxy equals the week peak estimatedOneRm divided by 100.0 for normalized fixture scale
  And intensityProxy does not equal 2.0
```

### RP-S2-003 — Weekly hard sets count working sets only

```gherkin
Scenario: RP-S2-003 hard sets exclude warmups
  Given fixture FIX-S1-WARMUP-RIR spanning 1 calendar week
  When StimulusSeriesBuilder.weeklyHardSetsByMuscle is invoked
  Then hardSets for Chest equals 1
  And hardSets does not equal 2
```

### RP-S2-004 — Three-week rolling median smoothing

```gherkin
Scenario: RP-S2-004 rolling median smooths middle week
  Given weekly peak e1RM values [100.0, 200.0, 102.0] kg for consecutive weeks
  When OutlierGuard winsorizeWeeklySignals is applied after initial WeeklyTrainingSignal build
  Then smoothedEstimatedOneRm for week 2 equals 102.0
  And smoothedEstimatedOneRm for week 2 does not equal 200.0
```

### RP-S2-005 — MAD outlier winsorized

```gherkin
Scenario: RP-S2-005 outlier week winsorized
  Given fixture FIX-S2-OUTLIER-WEEKLY
  When OutlierGuard.winsorizeWeeklySignals is invoked
  Then week 3 qualityFlags contains OutlierWinsorized
  And week 3 peakEstimatedOneRm equals 200.0 before winsorization
  And week 3 smoothedEstimatedOneRm is less than 200.0
```

### RP-S2-006 — Suspicious e1RM jump flagged at 15 percent

```gherkin
Scenario: RP-S2-006 suspicious jump without gap
  Given consecutive weeks with peak e1RM 100.0 kg then 120.0 kg, no session gap
  When OutlierGuard.suspiciousJumpFlags is invoked
  Then the later weekStart is in suspiciousJumpFlags
  And suspiciousJumpFlags size equals 1
```

### RP-S2-007 — Outcome series uses top two hardest sets

```gherkin
Scenario: RP-S2-007 anchor outcome aggregates top sets
  Given one session for anchor lift with working sets e1RM [110.0, 115.0, 90.0] kg
  When OutcomeSeriesBuilder.weeklyAnchorOutcomes is invoked for that week
  Then peakEstimatedOneRm equals 115.0
  And smoothedEstimatedOneRm equals 112.5
```

**Test classes:** `StimulusSeriesBuilderTest`, `OutcomeSeriesBuilderTest`, `IntensityProxyCalculatorTest`, `OutlierGuardTest`

---

## Slice 3 — Epoch Splitting

### RP-S3-001 — Session gap splits epochs

```gherkin
Scenario: RP-S3-001 15 day gap yields 2 epochs
  Given fixture FIX-S3-GAP-15D with last session of block A and first session of block B 15 days apart
  When EpochSplitter.split is invoked
  Then epochs size equals 2
  And epochs[1].splitReason equals SessionGap
  And epochs[0].end is before epochs[1].start
```

### RP-S3-002 — Primary lift Jaccard change splits

```gherkin
Scenario: RP-S3-002 Jaccard above 0.5 splits
  Given fixture FIX-S3-JACCARD with primary lift identity change exceeding 0.5 Jaccard over 4 weeks
  When EpochSplitter.split is invoked
  Then epochs size equals 2
  And at least 1 epoch splitReason equals PrimaryLiftChange
```

### RP-S3-003 — Lift churn split

```gherkin
Scenario: RP-S3-003 persistent lift churn splits epoch
  Given 8 weeks where primary lift roster changes every 2 weeks with churn score above threshold
  When EpochSplitter.split is invoked
  Then at least 1 epoch splitReason equals LiftChurn
  And epochs size is greater than or equal to 2
```

### RP-S3-004 — Equipment shift flagged

```gherkin
Scenario: RP-S3-004 equipment shift boundary
  Given consecutive weeks with e1RM jump 18 percent and unit change on same lift name
  When EpochSplitter.split is invoked
  Then at least 1 epoch splitReason equals EquipmentShift
```

### RP-S3-005 — Binary segmentation respects minimum segment length

```gherkin
Scenario: RP-S3-005 binseg requires 6 week segments
  Given 10 weeks of smoothed volume with single change at week 4 and penalty multiplier 4.0
  When ChangePointDetector.binarySegment is invoked
  Then proposed boundaries size equals 0
  And no boundary falls between week 4 and week 5
```

### RP-S3-006 — Volume deload marked not split

```gherkin
Scenario: RP-S3-006 volume deload week excluded from fit
  Given fixture FIX-S3-DELOAD-VOL inside a single 12-week epoch
  When EpochSplitter.markDeloadWeeks is invoked
  Then deloadWeeksExcluded size equals 1
  And epochs size equals 1
```

### RP-S3-007 — Intensity deload marked not split

```gherkin
Scenario: RP-S3-007 weight deload with steady volume
  Given fixture FIX-S3-DELOAD-WGT inside a single 12-week epoch
  When EpochSplitter.markDeloadWeeks is invoked
  Then deloadWeeksExcluded size equals 1
  And epochs size equals 1
```

### RP-S3-M001 — Mutation defense: 14-day gap is boundary not split

```gherkin
Scenario: RP-S3-M001 gap exactly 14 days does not split
  Given two training blocks with last and first session exactly 14 calendar days apart
  When EpochSplitter.split is invoked
  Then epochs size equals 1
  And epochs[0].splitReason equals WindowEnd
```

**Test classes:** `EpochSplitterTest`, `ChangePointDetectorTest`

---

## Slice 4 — Anchor Lift Selection

### RP-S4-001 — Sparse muscle skipped

```gherkin
Scenario: RP-S4-001 insufficient sessions returns null
  Given fixture FIX-S4-SPARSE-QUAD with 5 sessions over 4 weeks for muscle Quad
  When AnchorLiftSelector.select is invoked for muscle Quad
  Then result is null
```

### RP-S4-002 — Sufficient history selects top lift

```gherkin
Scenario: RP-S4-002 anchor lift selected
  Given fixture FIX-S4-RICH-CHEST with barbell bench 10 sessions and dumbbell fly 6 sessions
  When AnchorLiftSelector.select is invoked for muscle Chest
  Then result equals LiftId for barbell bench press fixture id "lift-bench"
  And result does not equal LiftId for dumbbell fly fixture id "lift-fly"
```

### RP-S4-003 — Specialize muscle priority on tie

```gherkin
Scenario: RP-S4-003 specialize breaks session-count tie
  Given muscle Chest in specializeMuscles set
  And two chest lifts each with exactly 8 sessions in same recency window
  And lift "lift-bench" appears in provenLiftIds
  When AnchorLiftSelector.select is invoked for muscle Chest
  Then result equals LiftId "lift-bench"
```

**Test class:** `AnchorLiftSelectorTest`

---

## Slice 5 — Volume–Response

### RP-S5-001 — Single epoch never Proven

```gherkin
Scenario: RP-S5-001 single epoch caps at Hypothesis
  Given fixture FIX-S5-SINGLE-EPOCH with positive OLS slope 0.8 and confidenceScore 0.72
  When VolumeResponseAnalyzer.analyze is invoked for muscle Quad
  Then observation evidence equals Hypothesis
  And observation evidence does not equal Proven
```

### RP-S5-002 — Two agreeing epochs yield Proven

```gherkin
Scenario: RP-S5-002 dual epoch agreement
  Given fixture FIX-S5-DUAL-EPOCH with positive slope in epoch A and positive slope in epoch B
  When VolumeResponseAnalyzer.analyze is invoked twice and evidence aggregated
  Then merged evidence for muscle Quad equals Proven
  And confidenceScore is greater than or equal to 0.65
```

### RP-S5-003 — Fewer than 4 weekly points yields InsufficientData

```gherkin
Scenario: RP-S5-003 insufficient weekly points
  Given 1 epoch with 3 non-deload weekly OLS points for Quad
  When VolumeResponseAnalyzer.analyze is invoked
  Then result is null
```

### RP-S5-004 — Fewer than 8 epoch weeks yields InsufficientData

```gherkin
Scenario: RP-S5-004 short epoch rejected
  Given 1 epoch spanning 7 calendar weeks with 5 weekly points
  When VolumeResponseAnalyzer.analyze is invoked
  Then result is null
```

### RP-S5-005 — Deload weeks excluded from OLS

```gherkin
Scenario: RP-S5-005 deload excluded from fit
  Given fixture FIX-S5-SINGLE-EPOCH with 1 deload week in epoch and 5 non-deload weekly points
  When VolumeResponseAnalyzer.analyze is invoked
  Then weeklyPointsUsed equals 5
  And epochWeeksUsed equals 10
  And observation is not null
```

### RP-S5-M001 — Mutation defense: Proven requires 2 epochs not 1

```gherkin
Scenario: RP-S5-M001 hardcoded Proven on single epoch fails
  Given fixture FIX-S5-SINGLE-EPOCH with slope 2.0 and rSquared 0.95
  When VolumeResponseAnalyzer.analyze is invoked
  Then observation evidence equals Hypothesis
  And observation evidence does not equal Proven
  And observation weeklyPointsUsed equals 5
```

**Test class:** `VolumeResponseAnalyzerTest`

---

## Slice 6 — Lift Effectiveness

### RP-S6-001 — Lift below session gate excluded

```gherkin
Scenario: RP-S6-001 fewer than 6 sessions excluded
  Given lift "lift-incline" in pattern HorizontalPush with 5 sessions in epoch
  When LiftEffectivenessAnalyzer.analyze is invoked for pattern HorizontalPush
  Then observations for lift "lift-incline" size equals 0
```

### RP-S6-002 — Pattern with fewer than 2 peers skips ranking

```gherkin
Scenario: RP-S6-002 single peer no rank
  Given fixture FIX-S6-SINGLE-LIFT with 8 sessions
  When LiftEffectivenessAnalyzer.analyze is invoked
  Then observations size equals 1
  And observations[0].rank equals 1
  And observations[0].peerLiftCount equals 1
  And observations[0].evidence equals Hypothesis
```

### RP-S6-003 — Top must beat runner-up by 20 percent

```gherkin
Scenario: RP-S6-003 rank margin enforced
  Given fixture FIX-S6-PATTERN-3 where top effectivenessScore is 0.12 and runner-up is 0.11
  When LiftEffectivenessAnalyzer.analyze is invoked
  Then rank 1 lift effectivenessScore equals 0.12
  And rank 1 evidence equals Hypothesis
  And no lift receives rank 1 with evidence Proven
```

### RP-S6-004 — Rank stable across two epochs yields Proven

```gherkin
Scenario: RP-S6-004 cross-epoch rank stability
  Given lift "lift-bench" rank 1 in epoch A and rank 1 in epoch B for HorizontalPush
  And runner-up margin at least 0.20 in both epochs
  When LiftEffectivenessAnalyzer.analyze is invoked per epoch and evidence merged
  Then merged observation for "lift-bench" evidence equals Proven
  And merged observation rank equals 1
```

### RP-S6-005 — No cross-pattern comparison

```gherkin
Scenario: RP-S6-005 patterns ranked independently
  Given lifts in HorizontalPush and VerticalPull with equal effectivenessScore 0.15
  When LiftEffectivenessAnalyzer.analyze is invoked for each pattern separately
  Then HorizontalPush rank 1 liftId does not equal VerticalPull rank 1 liftId
  And each observation pattern equals its invoked pattern argument
```

**Test class:** `LiftEffectivenessAnalyzerTest`

---

## Slice 7 — Extractor and Use Case

### RP-S7-001 — Multi-epoch passive extract

```gherkin
Scenario: RP-S7-001 end-to-end extract output
  Given fixture FIX-S7-MULTI-EPOCH with provenLiftIds containing "lift-bench"
  When ResponseObservationExtractor.extract is invoked
  Then output.epochs size equals 2
  And output.volumeResponses size is greater than or equal to 1
  And output.liftEffectiveness size is greater than or equal to 1
  And output.deloadWeeksExcluded size equals 1
```

### RP-S7-002 — Use case persists to in-memory repositories

```gherkin
Scenario: RP-S7-002 extract use case round trip
  Given in-memory response repositories seeded empty
  And fixture FIX-S7-MULTI-EPOCH loaded into FakeTrainingHistoryRepository
  When ExtractResponseProfileUseCase.invoke is invoked
  Then result is success
  And responseEpochRepository.getAll size equals 2
  And responseProfileMetadataRepository.get epochsAnalyzed equals 2
```

### RP-S7-003 — Empty registry yields zero experiment blocks

```gherkin
Scenario: RP-S7-003 empty experiment registry
  Given EmptyExperimentRegistry
  When ExtractResponseProfileUseCase.invoke completes
  Then experimentBlockRepository.getAll size equals 0
  And summary experimentsRecorded equals 0
```

### RP-S7-004 — Summary counts match output

```gherkin
Scenario: RP-S7-004 summary fields
  Given fixture FIX-S7-MULTI-EPOCH producing 2 epochs, 1 Quad volume response, 1 HorizontalPush effectiveness row
  When ExtractResponseProfileUseCase.invoke completes
  Then summary.epochsSegmented equals 2
  And summary.musclesWithVolumeResponse equals 1
  And summary.patternsWithEffectiveness equals 1
  And summary.experimentsRecorded equals 0
```

**Test classes:** `ResponseObservationExtractorTest`, `ExtractResponseProfileUseCaseTest`

---

## Slice 8 — Room Persistence

### RP-S8-001 — Epoch entity round trip

```gherkin
Scenario: RP-S8-001 epoch persistence
  Given domain Epoch with id "epoch-1", splitReason SessionGap
  When responseEpochRepository.replaceAll listOf(epoch) then getAll
  Then returned list size equals 1
  And returned[0].id.value equals "epoch-1"
  And returned[0].splitReason equals SessionGap
```

### RP-S8-002 — Volume response observation round trip

```gherkin
Scenario: RP-S8-002 volume observation persistence
  Given VolumeResponseObservation for muscle Quad, evidence Hypothesis, confidenceScore 0.70
  When volumeResponseObservationRepository.replaceAll then getAll
  Then returned[0].muscle equals Quad
  And returned[0].evidence equals Hypothesis
  And returned[0].confidenceScore equals 0.70
```

### RP-S8-003 — Lift effectiveness observation round trip

```gherkin
Scenario: RP-S8-003 effectiveness observation persistence
  Given LiftEffectivenessObservation pattern HorizontalPush, rank 1, evidence Proven
  When liftEffectivenessObservationRepository.replaceAll then getAll
  Then returned[0].pattern equals HorizontalPush
  And returned[0].rank equals 1
  And returned[0].evidence equals Proven
```

### RP-S8-004 — Experiment block round trip

```gherkin
Scenario: RP-S8-004 experiment block persistence
  Given ExperimentBlock id "exp-1", status Scheduled
  When experimentBlockRepository.replaceAll then getAll
  Then returned[0].id.value equals "exp-1"
  And returned[0].status equals Scheduled
```

### RP-S8-005 — Metadata singleton round trip

```gherkin
Scenario: RP-S8-005 metadata singleton
  Given ResponseProfileMetadata epochsAnalyzed 2, lastExtractedAt fixed instant "2026-01-15T00:00:00Z"
  When responseProfileMetadataRepository.save then get
  Then get epochsAnalyzed equals 2
  And get lastExtractedAt equals "2026-01-15T00:00:00Z"
```

### RP-S8-006 — replaceAll clears prior rows

```gherkin
Scenario: RP-S8-006 replaceAll semantics
  Given responseEpochRepository seeded with 3 epochs
  When replaceAll with list of 1 epoch is invoked
  Then getAll size equals 1
```

### RP-S8-007 — Database version unchanged

```gherkin
Scenario: RP-S8-007 schema version 1
  Given StratumDatabase built in test
  Then database openHelper readable version equals 1
```

**Test class:** `com.stratum.core.data.db.ResponseDaoTest` (Robolectric)

---

## Slice 9 — Import Orchestration

### RP-S9-001 — Successful import runs Tier 1 then response

```gherkin
Scenario: RP-S9-001 import orchestration order
  Given fixture FIX-S9-IMPORT-OK with valid history and provenLiftIds ["lift-bench"]
  When PersistImportedHistoryUseCase.invoke completes successfully
  Then importAnalysisMetadataRepository.get is not null
  And liftTrainingInsightsRepository.getAll size is greater than 0
  And responseEpochRepository.getAll size equals 2
  And responseProfileMetadataRepository.get is not null
```

### RP-S9-002 — Response failure rolls back history

```gherkin
Scenario: RP-S9-002 response failure rollback
  Given ExtractResponseProfileUseCase configured to return failure
  When PersistImportedHistoryUseCase.invoke is invoked with valid history
  Then trainingHistoryRepository.getAllSessions size equals 0
  And responseEpochRepository.getAll size equals 0
```

### RP-S9-003 — Rollback clears response tables

```gherkin
Scenario: RP-S9-003 rollback clears response data
  Given response repositories seeded with 2 epochs and 1 volume observation
  When RollbackHistoricalImportUseCase.invoke is invoked
  Then responseEpochRepository.getAll size equals 0
  And volumeResponseObservationRepository.getAll size equals 0
  And liftEffectivenessObservationRepository.getAll size equals 0
  And experimentBlockRepository.getAll size equals 0
  And responseProfileMetadataRepository.get is null
```

### RP-S9-004 — Reconcile triggers rollback when metadata missing

```gherkin
Scenario: RP-S9-004 reconcile without metadata
  Given training history present and importAnalysisMetadataRepository.get is null
  When ReconcileHistoricalImportUseCase.invoke is invoked
  Then trainingHistoryRepository.getAllSessions size equals 0
  And responseEpochRepository.getAll size equals 0
```

### RP-S9-005 — Tier 1 failure rollback unchanged

```gherkin
Scenario: RP-S9-005 insight failure still rolls back
  Given ExtractTrainingInsightsUseCase configured to return failure
  When PersistImportedHistoryUseCase.invoke is invoked
  Then trainingHistoryRepository.getAllSessions size equals 0
  And responseEpochRepository.getAll size equals 0
```

**Test classes:** `PersistImportedHistoryUseCaseTest`, `RollbackHistoricalImportUseCaseTest`

---

## Slice 10 — Program Generation Payload

### RP-S10-001 — responseProfile key present when data exists

```gherkin
Scenario: RP-S10-001 JSON contains responseProfile
  Given fixture FIX-S10-PAYLOAD-MIX assembled into ResponseProfilePayload with epochsAnalyzed 2
  When ProgramGenerationPromptBuilder.buildPayload is invoked with responseProfile non-null
  Then JSON root contains key "responseProfile"
  And responseProfile.epochsAnalyzed equals 2
```

### RP-S10-002 — Proven observations in influence arrays

```gherkin
Scenario: RP-S10-002 Proven in volumeResponses influence
  Given VolumeResponseObservation muscle Quad, evidence Proven, confidenceScore 0.80
  When influence filter applied
  Then volumeResponses influence list size equals 1
  And volumeResponses[0].evidence equals Proven
  And volumeResponses[0].muscle equals Quad
```

### RP-S10-003 — High-confidence Hypothesis in influence

```gherkin
Scenario: RP-S10-003 Hypothesis at 0.65 in influence
  Given VolumeResponseObservation evidence Hypothesis, confidenceScore 0.65
  When influence filter applied
  Then volumeResponses influence list size equals 1
  And volumeResponses[0].confidenceScore equals 0.65
```

### RP-S10-004 — Low-confidence Hypothesis informational only

```gherkin
Scenario: RP-S10-004 Hypothesis below threshold excluded from influence
  Given VolumeResponseObservation evidence Hypothesis, confidenceScore 0.64
  When influence filter applied and JSON built
  Then volumeResponses influence list size equals 0
  And informational.volumeResponses size equals 1
  And informational.volumeResponses[0].confidenceScore equals 0.64
```

### RP-S10-005 — InsufficientData absent from JSON

```gherkin
Scenario: RP-S10-005 InsufficientData omitted entirely
  Given VolumeResponseObservation evidence InsufficientData, confidenceScore 0.10
  When JSON serialized
  Then volumeResponses influence list size equals 0
  And informational.volumeResponses size equals 0
  And JSON string does not contain "InsufficientData"
```

### RP-S10-006 — Empty profile omits key

```gherkin
Scenario: RP-S10-006 no observations omits responseProfile
  Given ResponseProfilePayload with epochsAnalyzed 0 and empty observation lists
  When ProgramGenerationPayloadBuilder.buildPayload is invoked
  Then JSON root does not contain key "responseProfile"
```

### RP-S10-007 — Load payload use case includes responseProfile

```gherkin
Scenario: RP-S10-007 load payload assembly includes responseProfile when persisted
  Given persisted volume response observations and response profile metadata
  When LoadProgramGenerationPayloadUseCase is invoked
  Then JSON string contains key "responseProfile"
```

**Test classes:** `ProgramGenerationPromptBuilderTest`, `LoadProgramGenerationPayloadUseCaseTest`

---

## Slice 11 — DI and Compile Smoke

### RP-S11-001 — Application compiles with bindings

```gherkin
Scenario: RP-S11-001 assembleDebug succeeds
  Given Hilt DataModule binds ResponseEpochRepository, VolumeResponseObservationRepository, LiftEffectivenessObservationRepository, ExperimentBlockRepository, ResponseProfileMetadataRepository, and ExperimentRegistry to EmptyExperimentRegistry
  When ./gradlew :app:assembleDebug is executed
  Then exit code equals 0
```

**Verification:** Gradle task outcome; no dedicated test class required.

---

## Mutation-Defense Summary

| Scenario ID | Would fail if |
|-------------|---------------|
| RP-S1-M001 | Warmup threshold hardcoded to exclude RIR > 5 instead of ≥ 5 |
| RP-S3-M001 | Gap split uses > 14 instead of > MAX_EPOCH_GAP_DAYS (14) |
| RP-S5-M001 | Single epoch incorrectly labeled Proven |

---

## Scenario Index

| Slice | Happy path | Edge / error | Mutation | Total |
|-------|------------|--------------|----------|-------|
| 1 | 4 | 2 | 1 | 7 |
| 2 | 4 | 3 | 0 | 7 |
| 3 | 5 | 2 | 1 | 8 |
| 4 | 2 | 1 | 0 | 3 |
| 5 | 3 | 2 | 1 | 6 |
| 6 | 2 | 3 | 0 | 5 |
| 7 | 4 | 0 | 0 | 4 |
| 8 | 5 | 2 | 0 | 7 |
| 9 | 2 | 3 | 0 | 5 |
| 10 | 3 | 3 | 0 | 6 |
| 11 | 1 | 0 | 0 | 1 |
| **Total** | **37** | **19** | **3** | **59** |

---

## Red-Phase Checklist

1. Write failing tests per scenario ID before production implementation.
2. Use `@DisplayName` including scenario ID (e.g. `"RP-S5-001 single epoch caps at Hypothesis"`).
3. Map each test method to exactly one scenario ID in KDoc or display name.
4. Domain tests: JVM + module-local or `:core:testing` fakes.
5. Data tests: Robolectric + in-memory Room.
6. Assert state (repository contents, returned domain objects, JSON keys) — not interaction counts.
7. Do not import production constants into expected-value calculations beyond enum case names; fixture literals define numeric expectations.
