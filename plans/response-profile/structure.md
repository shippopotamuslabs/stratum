# Response Profile — Structure

**Ticket:** `response-profile` | **Phase:** Structure (Synthitect)  
**Grounding:** `research.md`, `design.md` (approved 2026-06-25)

---

## Slice Index

| # | Goal | Verifiable artifact |
|---|------|---------------------|
| 1 | Domain identity types, core models, constants, and clean training-signal foundation | `TrainingSignalCleaner` unit tests |
| 2 | Weekly stimulus/outcome series, RIR-first intensity, smoothing, and outlier flags | `StimulusSeriesBuilder`, `OutcomeSeriesBuilder`, `OutlierGuard` tests |
| 3 | Epoch splitting with gaps, lift churn, deload marking, equipment shifts, and binary segmentation | `EpochSplitter` + `ChangePointDetector` tests |
| 4 | Per-muscle anchor lift selection with sparse-history skip | `AnchorLiftSelector` tests |
| 5 | Tier 2 volume–response observations, reliability metrics, and evidence labeling | `VolumeResponseAnalyzer` tests |
| 6 | Tier 3 pattern effectiveness ranking and evidence labeling | `LiftEffectivenessAnalyzer` tests |
| 7 | Full passive-path extractor, experiment registry stub, extract use case (in-memory) | `ResponseObservationExtractorTest`, `ExtractResponseProfileUseCaseTest` |
| 8 | Room v1 schema, DAOs, mappers, repository implementations | `:core:data` DAO/repository tests |
| 9 | Import orchestration: Tier 1 unchanged, response after insights, rollback/reconcile | Extended import use-case tests |
| 10 | Program-generation `responseProfile` JSON with influence policy | `ProgramGenerationPromptBuilderTest`, payload use-case test |
| 11 | DI wiring, compile smoke, re-extraction extension point documentation | `:app:assembleDebug` |

---

## Dependencies Between Slices

```
1 ──► 2 ──► 3 ──► 4 ──► 5 ──► 6 ──► 7 ──► 8 ──► 9
                                      └──────────────► 10 ──► 11
```

- **Slice 1** has no upstream dependency.
- **Slice 2** depends on cleaned working-set rows from Slice 1.
- **Slice 3** depends on robust weekly series from Slice 2.
- **Slices 4–6** each extend the extractor pipeline; tests run against prior slices' types without persistence.
- **Slice 7** composes 1–6 and introduces ports + use case; still domain-only (in-memory fakes).
- **Slice 8** requires Slice 7 port signatures; enables real persistence.
- **Slice 9** requires Slice 8 repositories (or in-memory equivalents in domain tests mirroring production wiring).
- **Slice 10** requires Slice 7 observation models and Slice 8 read path (domain tests may use in-memory repos).
- **Slice 11** requires Slices 8–10 bindings.

---

## Public Contracts

### Domain types (`com.stratum.domain.response`)

```kotlin
// identity/ResponseIds.kt
@JvmInline value class EpochId(val value: String)
@JvmInline value class ExperimentId(val value: String)

// ResponseModels.kt
enum class EpochSplitReason { SessionGap, PrimaryLiftChange, LiftChurn, EquipmentShift, ChangePoint, WindowEnd }
enum class OutcomeMetric { EstimatedOneRmTrend, RirAdjustedE1rmTrend, WeeklyPeakE1rm, ProgressSlope }
enum class EvidenceLevel { Proven, Hypothesis, InsufficientData }
enum class ExperimentStatus { Scheduled, Active, Completed, Cancelled }
enum class TrainingSignalQualityFlag {
    MissingRir,
    HighRirWarmupExcluded,
    LowRelativeLoadWarmupExcluded,
    MixedUnits,
    OutlierWinsorized,
    SuspiciousE1rmJump,
    SparseWeek,
    EquipmentShiftSuspected,
}

data class CleanTrainingSet(
    val sourceSet: LoggedSet,
    val normalizedWeight: Double,
    val estimatedOneRm: Double,
    val rirAdjustedEstimatedOneRm: Double?,
    val isWorkingSet: Boolean,
    val qualityFlags: Set<TrainingSignalQualityFlag>,
)

data class WeeklyTrainingSignal(
    val weekStart: Instant,
    val liftId: LiftId?,
    val muscle: MuscleGroup?,
    val hardSets: Int,
    val sessions: Int,
    val meanRir: Double?,
    val intensityProxy: Double?,
    val peakEstimatedOneRm: Double?,
    val smoothedEstimatedOneRm: Double?,
    val qualityFlags: Set<TrainingSignalQualityFlag>,
)

data class RegressionReliability(
    val rSquared: Double?,
    val outlierCount: Int,
    val completenessScore: Double,
)

data class WeeklySetRange(
    val min: Double,
    val max: Double,
)

data class Epoch(
    val id: EpochId,
    val start: Instant,
    val end: Instant,
    val anchorLiftIds: Set<LiftId>,
    val splitReason: EpochSplitReason,
)

data class StimulusDescriptor(
    val epochId: EpochId?,
    val experimentId: ExperimentId?,
    val muscle: MuscleGroup?,
    val pattern: MovementPattern?,
    val liftId: LiftId?,
    val weeklyHardSets: Double,
    val sessionsPerWeek: Double,
    val intensityProxy: Double?,
)

data class OutcomeDescriptor(
    val anchorLiftId: LiftId,
    val metric: OutcomeMetric,
    val value: Double,
    val windowWeeks: Int,
    val reliability: RegressionReliability,
)

data class VolumeResponseObservation(
    val muscle: MuscleGroup,
    val epochId: EpochId,
    val anchorLiftId: LiftId,
    val stimulus: StimulusDescriptor,
    val outcome: OutcomeDescriptor,
    val correlationSlope: Double,
    val confidenceScore: Double,
    val evidence: EvidenceLevel,
    val weeklyPointsUsed: Int,
    val epochWeeksUsed: Int,
    val observedWeeklySetRange: WeeklySetRange,
    val bestObservedWeeklySetRange: WeeklySetRange?,
)

data class LiftEffectivenessObservation(
    val pattern: MovementPattern,
    val liftId: LiftId,
    val epochId: EpochId,
    val effectivenessScore: Double,
    val peerLiftCount: Int,
    val rank: Int,
    val evidence: EvidenceLevel,
    val confidenceScore: Double,
)

data class ExperimentBlock(
    val id: ExperimentId,
    val prescribed: StimulusDescriptor,
    val outcomeWindowStart: Instant,
    val outcomeWindowEnd: Instant,
    val status: ExperimentStatus,
)

data class ResponseExtractionInput(
    val sessions: List<HistoricalSession>,
    val sets: List<LoggedSet>,
    val lifts: List<Lift>,
    val provenLiftIds: Set<LiftId>,
    val specializeMuscles: Set<MuscleGroup>,
    val liftInsights: List<LiftTrainingInsights>,
    val volumeBaselines: List<MuscleVolumeBaseline>,
    val analysisWindowStart: Instant,
    val analysisWindowEnd: Instant,
    val activeExperiments: List<ExperimentBlock>,
)

data class ResponseExtractionOutput(
    val epochs: List<Epoch>,
    val volumeResponses: List<VolumeResponseObservation>,
    val liftEffectiveness: List<LiftEffectivenessObservation>,
    val deloadWeeksExcluded: Set<Instant>, // week-start markers
)

data class ExtractResponseProfileSummary(
    val epochsSegmented: Int,
    val musclesWithVolumeResponse: Int,
    val patternsWithEffectiveness: Int,
    val experimentsRecorded: Int,
)

data class ResponseProfileMetadata(
    val epochsAnalyzed: Int,
    val analysisWindowStart: Instant,
    val analysisWindowEnd: Instant,
    val lastExtractedAt: Instant,
)
```

### Constants (`ResponseConstants.kt`)

| Symbol | Value | Role |
|--------|-------|------|
| `MAX_EPOCH_GAP_DAYS` | 14 | Session-gap epoch split |
| `PRIMARY_JACCARD_CHANGE_THRESHOLD` | 0.5 | Primary-set change split |
| `ROLLING_PRIMARY_WINDOW_WEEKS` | 4 | Jaccard rolling window |
| `HIGH_RIR_WARMUP_THRESHOLD` | 5 | Exclude likely warmups when RIR is logged |
| `LOW_RELATIVE_LOAD_FRACTION` | 0.65 | Exclude low-load warmups when RIR is missing |
| `ROLLING_MEDIAN_SMOOTHING_WEEKS` | 3 | Smooth weekly e1RM / volume before CPD and regression |
| `MAD_OUTLIER_THRESHOLD` | 3.5 | Winsorize per-lift e1RM outliers |
| `SUSPICIOUS_E1RM_JUMP_FRACTION` | 0.15 | Flag large jumps without preceding break |
| `MIN_BINSEG_SEGMENT_WEEKS` | 6 | Prevent tiny change-point segments |
| `BINSEG_PENALTY_MULTIPLIER` | 4.0 | Conservative penalty over smoothed weekly signals |
| `DELOAD_VOLUME_FRACTION` | 0.50 | Volume deload vs epoch median |
| `DELOAD_WEIGHT_DROP_FRACTION` | 0.15 | Intensity deload vs 4-week mean |
| `DELOAD_VOLUME_STEADY_BAND` | 0.20 | ± band for steady volume deload |
| `MIN_ANCHOR_SESSIONS` | 8 | Anchor lift session gate |
| `MIN_ANCHOR_WEEKS` | 6 | Anchor lift week span gate |
| `MIN_LIFT_SESSIONS_FOR_EFFECTIVENESS` | 6 | Tier 3 per-lift gate |
| `MIN_EPOCH_WEEKS_FOR_CORRELATION` | 8 | Tier 2 epoch length gate |
| `MIN_WEEKLY_POINTS_FOR_OLS` | 4 | Reuse Tier 1 OLS minimum |
| `RIR_COVERAGE_THRESHOLD` | 0.70 | RIR-first intensity gate |
| `HYPOTHESIS_CONFIDENCE_PAYLOAD_MIN` | 0.65 | Payload influence floor |
| `EFFECTIVENESS_RANK_MARGIN` | 0.20 | Top vs runner-up margin |
| `MIN_PEERS_FOR_PATTERN_RANK` | 2 | Minimum lifts in pattern |
| `PROVEN_EPOCH_AGREEMENT_COUNT` | 2 | Cross-epoch Proven gate |
| `RE_EXTRACT_MIN_INTERVAL_DAYS` | 28 | Documented v1 extension point |

### Extractor and analyzers

```kotlin
interface ResponseObservationExtractor {
    fun extract(input: ResponseExtractionInput): ResponseExtractionOutput
}

object TrainingSignalCleaner {
    fun clean(input: ResponseExtractionInput): List<CleanTrainingSet>
}

object OutlierGuard {
    fun winsorizeWeeklySignals(signals: List<WeeklyTrainingSignal>): List<WeeklyTrainingSignal>
    fun suspiciousJumpFlags(signals: List<WeeklyTrainingSignal>): Set<Instant>
}

object EpochSplitter {
    fun split(input: ResponseExtractionInput): List<Epoch>
    fun markDeloadWeeks(epoch: Epoch, input: ResponseExtractionInput): Set<Instant>
}

object ChangePointDetector {
    fun binarySegment(signals: List<WeeklyTrainingSignal>): List<Instant>
}

object StimulusSeriesBuilder {
    fun weeklyHardSetsByMuscle(/* epoch, cleanSets, lifts */): Map<MuscleGroup, List<WeeklyTrainingSignal>>
    fun weeklyForLift(/* epoch, liftId, cleanSets */): List<WeeklyTrainingSignal>
}

object OutcomeSeriesBuilder {
    fun weeklyAnchorOutcomes(/* epoch, anchorLiftId, cleanSets */): List<WeeklyTrainingSignal>
}

object IntensityProxyCalculator {
    fun forWeek(cleanSets: List<CleanTrainingSet>, liftId: LiftId?): Double?
}

object AnchorLiftSelector {
    fun select(
        muscle: MuscleGroup,
        epochs: List<Epoch>,
        input: ResponseExtractionInput,
    ): LiftId?
}

object VolumeResponseAnalyzer {
    fun analyze(
        muscle: MuscleGroup,
        anchorLiftId: LiftId,
        epoch: Epoch,
        input: ResponseExtractionInput,
        deloadWeeks: Set<Instant>,
    ): VolumeResponseObservation?
}

object LiftEffectivenessAnalyzer {
    fun analyze(
        pattern: MovementPattern,
        epoch: Epoch,
        input: ResponseExtractionInput,
        deloadWeeks: Set<Instant>,
    ): List<LiftEffectivenessObservation>
}

data class WeeklyPoint(val weekStart: Instant, val value: Double)
```

### Domain ports (`ResponsePorts.kt`)

```kotlin
interface ResponseEpochRepository {
    suspend fun replaceAll(epochs: List<Epoch>)
    suspend fun getAll(): List<Epoch>
}

interface VolumeResponseObservationRepository {
    suspend fun replaceAll(observations: List<VolumeResponseObservation>)
    suspend fun getAll(): List<VolumeResponseObservation>
}

interface LiftEffectivenessObservationRepository {
    suspend fun replaceAll(observations: List<LiftEffectivenessObservation>)
    suspend fun getAll(): List<LiftEffectivenessObservation>
}

interface ExperimentBlockRepository {
    suspend fun replaceAll(blocks: List<ExperimentBlock>)
    suspend fun getAll(): List<ExperimentBlock>
}

interface ResponseProfileMetadataRepository {
    suspend fun save(metadata: ResponseProfileMetadata)
    suspend fun get(): ResponseProfileMetadata?
    suspend fun clear()
}

interface ExperimentRegistry {
    fun active(): List<ExperimentBlock>
    fun record(block: ExperimentBlock)
}

class EmptyExperimentRegistry @Inject constructor() : ExperimentRegistry {
    override fun active(): List<ExperimentBlock> = emptyList()
    override fun record(block: ExperimentBlock) = Unit
}
```

### Use cases

```kotlin
class ExtractResponseProfileUseCase @Inject constructor(
    private val trainingHistoryRepository: TrainingHistoryRepository,
    private val liftRepository: LiftRepository,
    private val liftTrainingInsightsRepository: LiftTrainingInsightsRepository,
    private val muscleVolumeBaselineRepository: MuscleVolumeBaselineRepository,
    private val importAnalysisMetadataRepository: ImportAnalysisMetadataRepository,
    private val responseEpochRepository: ResponseEpochRepository,
    private val volumeResponseObservationRepository: VolumeResponseObservationRepository,
    private val liftEffectivenessObservationRepository: LiftEffectivenessObservationRepository,
    private val experimentBlockRepository: ExperimentBlockRepository,
    private val responseProfileMetadataRepository: ResponseProfileMetadataRepository,
    private val extractor: ResponseObservationExtractor,
    private val experimentRegistry: ExperimentRegistry,
) {
    suspend operator fun invoke(
        provenLiftIds: Set<LiftId> = emptySet(),
    ): Result<ExtractResponseProfileSummary>
}
```

`ExtractResponseProfileUseCase` reads the same history snapshot as Tier 1, passes Tier 1 outputs into `ResponseExtractionInput`, calls `extractor.extract`, then `replaceAll` on all four response tables plus metadata. `activeExperiments` sourced from `experimentRegistry.active()` (empty in v1).

**Re-extraction extension point:** KDoc on `ExtractResponseProfileUseCase` documents that v1 runs only from import orchestration; future WorkManager worker should call `invoke()` when `Instant.now() - metadata.lastExtractedAt >= RE_EXTRACT_MIN_INTERVAL_DAYS`. No worker in v1.

### Import orchestration changes (signatures unchanged, behavior extended)

```kotlin
// PersistImportedHistoryUseCase — add ExtractResponseProfileUseCase dependency;
// after extractTrainingInsights succeeds, invoke extractResponseProfile(provenLiftIds);
// on response failure, same rollback as insight failure (clear history).

// RollbackHistoricalImportUseCase — add four response repos + metadata;
// replaceAll(empty) + metadata.clear() alongside existing insight clears.

// ReconcileHistoricalImportUseCase — unchanged gate (import metadata singleton);
// rollback path clears response tables via extended RollbackHistoricalImportUseCase.
```

`ExtractTrainingInsightsUseCase` and `TrainingInsightsExtractor` remain **unchanged**.

### Program-generation payload

```kotlin
// program/ResponseProfilePayload.kt
data class VolumeResponseInfluence(
    val muscle: MuscleGroup,
    val anchorLiftId: LiftId,
    val correlationSlope: Double,
    val confidenceScore: Double,
    val evidence: EvidenceLevel,
)

data class LiftEffectivenessInfluence(
    val pattern: MovementPattern,
    val liftId: LiftId,
    val effectivenessScore: Double,
    val rank: Int,
    val evidence: EvidenceLevel,
)

data class ResponseProfileInformational(
    val volumeResponses: List<VolumeResponseObservation>,
    val liftEffectiveness: List<LiftEffectivenessObservation>,
)

data class ResponseProfilePayload(
    val volumeResponses: List<VolumeResponseInfluence>,
    val liftEffectiveness: List<LiftEffectivenessInfluence>,
    val informational: ResponseProfileInformational,
    val epochsAnalyzed: Int,
    val analysisWindowStart: Instant,
    val analysisWindowEnd: Instant,
)
```

```kotlin
// ProgramGenerationPromptBuilder — extended signature
fun buildPayload(
    profile: UserTrainingProfile,
    specialization: SpecializationPreference,
    provenLifts: List<LiftWithInsights>,
    availableLifts: List<LiftWithInsights>,
    supersetAffinities: List<SupersetAffinity>,
    volumeBaselines: List<MuscleVolumeBaseline>,
    analysisMetadata: ImportAnalysisMetadata?,
    responseProfile: ResponseProfilePayload?,
): String
```

**JSON top-level key:** `"responseProfile"` (optional; omitted when no observations and zero epochs).

**Influence arrays:** include `Proven` always; include `Hypothesis` only when `confidenceScore >= HYPOTHESIS_CONFIDENCE_PAYLOAD_MIN`; omit `InsufficientData` entirely. Sub-threshold `Hypothesis` rows appear under `informational` only.

```kotlin
// LoadProgramGenerationPayloadUseCase — add response repo dependencies;
// assemble ResponseProfilePayload from persisted observations + metadata;
// pass to promptBuilder.buildPayload(..., responseProfile = payload).
```

### Room schema (v1, no migration)

| Table | Entity PK | Maps to |
|-------|-----------|---------|
| `response_epochs` | `id: String` | `Epoch` |
| `volume_response_observations` | auto `rowId` | `VolumeResponseObservation` |
| `lift_effectiveness_observations` | auto `rowId` | `LiftEffectivenessObservation` |
| `experiment_blocks` | `id: String` | `ExperimentBlock` |
| `response_profile_metadata` | singleton `id=0` | `ResponseProfileMetadata` |

All insight-like tables use `replaceAll` = delete-all + insert. `StratumDatabase` stays **version 1**.

---

## Per-Slice Checklist

### Slice 1 — Clean training signal foundation

**Components:** `ResponseIds.kt`, `ResponseModels.kt` (core enums + clean signal types), `ResponseConstants.kt`, `TrainingSignalCleaner.kt`

**Checkpoint:** Raw logged sets become normalized clean rows; high-RIR and low-relative-load warmups are excluded; RIR-adjusted e1RM is computed when RIR is plausible; quality flags are emitted for missing RIR, mixed units, and excluded warmups.

**Create:**
| File | Responsibility |
|------|----------------|
| `core/domain/.../response/identity/ResponseIds.kt` | `EpochId`, `ExperimentId` |
| `core/domain/.../response/ResponseModels.kt` | Core enums and descriptor data classes |
| `core/domain/.../response/ResponseConstants.kt` | Threshold constants |
| `core/domain/.../response/TrainingSignalCleaner.kt` | Unit normalization, working-set classification, RIR-adjusted e1RM |
| `core/domain/src/test/.../response/TrainingSignalCleanerTest.kt` | Warmup filtering, RIR adjustment, quality flags |
| `core/domain/src/test/.../response/fixtures/ResponseFixtures.kt` | Shared synthetic sessions/sets builder |

**Test checkpoint:** `./gradlew :core:domain:test --tests "com.stratum.domain.response.TrainingSignalCleanerTest"`

---

### Slice 2 — Weekly stimulus, outcome, and robustness series

**Components:** `StimulusSeriesBuilder.kt`, `OutcomeSeriesBuilder.kt`, `IntensityProxyCalculator.kt`, `OutlierGuard.kt`, `WeeklyTrainingSignal` in `ResponseModels.kt`

**Checkpoint:** Week with ≥70% RIR sets uses inverted mean RIR; sparse RIR week falls back to e1RM proxy; weekly hard sets are built from clean working sets only; 3-week rolling median smoothing reduces noisy e1RM spikes; MAD outliers are winsorized and quality-flagged.

**Create:**
| File | Responsibility |
|------|----------------|
| `core/domain/.../response/StimulusSeriesBuilder.kt` | Weekly muscle/lift series |
| `core/domain/.../response/OutcomeSeriesBuilder.kt` | Weekly anchor e1RM outcome series |
| `core/domain/.../response/IntensityProxyCalculator.kt` | RIR-first with fallback |
| `core/domain/.../response/OutlierGuard.kt` | Rolling smoothing, MAD winsorization, suspicious jump flags |
| `core/domain/src/test/.../response/StimulusSeriesBuilderTest.kt` | Hard-set weekly aggregation |
| `core/domain/src/test/.../response/OutcomeSeriesBuilderTest.kt` | Top/robust top-2 e1RM aggregation |
| `core/domain/src/test/.../response/IntensityProxyCalculatorTest.kt` | RIR vs fallback paths |
| `core/domain/src/test/.../response/OutlierGuardTest.kt` | MAD and suspicious-jump fixtures |

**Modify:** `ResponseFixtures.kt` — RIR-enriched set rows

**Test checkpoint:** `./gradlew :core:domain:test --tests "com.stratum.domain.response.StimulusSeriesBuilderTest,com.stratum.domain.response.OutcomeSeriesBuilderTest,com.stratum.domain.response.IntensityProxyCalculatorTest,com.stratum.domain.response.OutlierGuardTest"`

---

### Slice 3 — Epoch foundation and change-point detection

**Components:** `EpochSplitter.kt`, `ChangePointDetector.kt`

**Checkpoint:** Greedy split produces ≥2 epochs on gap fixture; Jaccard >0.5 fixture splits; persistent lift churn splits; equipment-shift fixture is flagged; binary segmentation proposes a boundary only when min-length and penalty gates pass; deload weeks are identified by volume and weight-drop rules without splitting epoch.

**Create:**
| File | Responsibility |
|------|----------------|
| `core/domain/.../response/EpochSplitter.kt` | Gap/Jaccard/lift-churn/equipment-shift split + deload marking |
| `core/domain/.../response/ChangePointDetector.kt` | Conservative binary segmentation over smoothed weekly signals |
| `core/domain/src/test/.../response/EpochSplitterTest.kt` | Gap, Jaccard, lift churn, equipment shift, deload fixtures |
| `core/domain/src/test/.../response/ChangePointDetectorTest.kt` | Binary segmentation min-length/penalty fixtures |

**Test checkpoint:** `./gradlew :core:domain:test --tests "com.stratum.domain.response.EpochSplitterTest,com.stratum.domain.response.ChangePointDetectorTest"`

---

### Slice 4 — Anchor lift selection

**Components:** `AnchorLiftSelector.kt`

**Checkpoint:** Muscle with <8 anchor sessions returns null (skipped); muscle with sufficient history returns top-ranked lift; `Specialize` muscles prioritized when tied; no attempt to cover all 18 `MuscleGroup` values.

**Create:**
| File | Responsibility |
|------|----------------|
| `core/domain/.../response/AnchorLiftSelector.kt` | Per-muscle anchor ranking |
| `core/domain/src/test/.../response/AnchorLiftSelectorTest.kt` | Gate, priority, skip sparse |

**Test checkpoint:** `./gradlew :core:domain:test --tests "com.stratum.domain.response.AnchorLiftSelectorTest"`

---

### Slice 5 — Tier 2 volume–response

**Components:** `VolumeResponseAnalyzer.kt`, observation fields on `VolumeResponseObservation`

**Checkpoint:** Single epoch → `Hypothesis` or `InsufficientData`, never `Proven`; two agreeing epochs → `Proven`; <4 weekly points or <8 epoch weeks → `InsufficientData`; deload weeks excluded from OLS fit; correlation slope, R², completeness, outlier count, observed set range, and optional best observed set range are populated.

**Create:**
| File | Responsibility |
|------|----------------|
| `core/domain/.../response/VolumeResponseAnalyzer.kt` | OLS slope + evidence |
| `core/domain/src/test/.../response/VolumeResponseAnalyzerTest.kt` | Evidence gating fixtures |

**Test checkpoint:** `./gradlew :core:domain:test --tests "com.stratum.domain.response.VolumeResponseAnalyzerTest"`

---

### Slice 6 — Tier 3 lift effectiveness

**Components:** `LiftEffectivenessAnalyzer.kt`

**Checkpoint:** Lift with <6 sessions excluded; pattern with <2 peers skips rank; top must beat runner-up by 20%; score uses progress rate per week per hard-set exposure; rank stable across ≥2 epochs → `Proven`; no cross-pattern comparison.

**Create:**
| File | Responsibility |
|------|----------------|
| `core/domain/.../response/LiftEffectivenessAnalyzer.kt` | Score, rank, evidence |
| `core/domain/src/test/.../response/LiftEffectivenessAnalyzerTest.kt` | Ranking and Proven gates |

**Test checkpoint:** `./gradlew :core:domain:test --tests "com.stratum.domain.response.LiftEffectivenessAnalyzerTest"`

---

### Slice 7 — Extractor composition and use case

**Components:** `ResponseObservationExtractor.kt` (default impl), `ResponsePorts.kt`, `ExtractResponseProfileUseCase.kt`, `EmptyExperimentRegistry`

**Checkpoint:** End-to-end `extract()` on multi-epoch fixture yields epochs + mixed evidence observations; use case persists to in-memory repos and returns summary counts; empty registry produces zero experiment blocks.

**Create:**
| File | Responsibility |
|------|----------------|
| `core/domain/.../response/ResponseObservationExtractor.kt` | Orchestrates splitter + analyzers |
| `core/domain/.../response/ResponsePorts.kt` | Repository + registry ports |
| `core/domain/.../response/ExtractResponseProfileUseCase.kt` | Read history/Tier1 → extract → replaceAll |
| `core/domain/.../response/EmptyExperimentRegistry.kt` | Tier 4 stub |
| `core/domain/src/test/.../response/ResponseObservationExtractorTest.kt` | Multi-epoch passive path |
| `core/domain/src/test/.../response/ExtractResponseProfileUseCaseTest.kt` | In-memory repo round-trip |

**Test checkpoint:** `./gradlew :core:domain:test --tests "com.stratum.domain.response.ResponseObservationExtractorTest,com.stratum.domain.response.ExtractResponseProfileUseCaseTest"`

---

### Slice 8 — Room persistence

**Components:** entities, DAOs, mappers, Room repositories, database registration

**Checkpoint:** Entity↔domain round-trip for each table; `replaceAll` clears prior rows; metadata singleton upsert; DB version remains 1.

**Create:**
| File | Responsibility |
|------|----------------|
| `core/data/.../db/ResponseEntities.kt` | Five Room entities |
| `core/data/.../db/ResponseDaos.kt` | DAO interfaces |
| `core/data/.../mapper/ResponseMapper.kt` | Entity ↔ domain |
| `core/data/.../repository/RoomResponseRepositories.kt` | Four repo impls + metadata |

**Modify:**
| File | Responsibility |
|------|----------------|
| `core/data/.../db/StratumDatabase.kt` | Register entities, DAO accessors, stay v1 |
| `core/data/.../db/EnumConverters.kt` | Converters for new enums if stored as strings |

**Test checkpoint:** `./gradlew :core:data:testDebugUnitTest --tests "com.stratum.core.data.db.ResponseDaoTest"`

---

### Slice 9 — Import orchestration

**Components:** wire response extraction into persist/rollback paths

**Checkpoint:** Successful import runs Tier 1 then response; insight failure rolls back history (unchanged); response failure rolls back history; rollback clears all response tables; reconcile still keyed on import analysis metadata.

**Modify:**
| File | Responsibility |
|------|----------------|
| `core/domain/.../importing/ImportUseCases.kt` | Inject response use case; extend rollback |
| `core/domain/src/test/.../importing/PersistImportedHistoryUseCaseTest.kt` | Response hook (new or extended) |
| `core/domain/src/test/.../importing/RollbackHistoricalImportUseCaseTest.kt` | Response table clears |
| `core/inference/src/test/.../RunFullHistoricalImportUseCaseTest.kt` | Wire updated constructors |

**Test checkpoint:** `./gradlew :core:domain:test --tests "com.stratum.domain.importing.PersistImportedHistoryUseCaseTest,com.stratum.domain.importing.RollbackHistoricalImportUseCaseTest"`

---

### Slice 10 — Program-generation payload

**Components:** `ResponseProfilePayload.kt`, builder + use case extensions, influence filtering

**Checkpoint:** Payload includes `responseProfile` key; `Proven` in influence arrays; `Hypothesis` with confidence ≥0.65 in influence; lower-confidence `Hypothesis` only under `informational`; `InsufficientData` absent from JSON entirely.

**Create:**
| File | Responsibility |
|------|----------------|
| `core/domain/.../program/ResponseProfilePayload.kt` | Payload DTOs + influence filter |
| `core/domain/src/test/.../program/LoadProgramGenerationPayloadUseCaseTest.kt` | Assembly with response repos |

**Modify:**
| File | Responsibility |
|------|----------------|
| `core/domain/.../program/ProgramGenerationPromptBuilder.kt` | Serialize `responseProfile` |
| `core/domain/.../program/LoadProgramGenerationPayloadUseCase.kt` | Read response repos |
| `core/domain/src/test/.../program/ProgramGenerationPromptBuilderTest.kt` | Omission/influence rules |

**Test checkpoint:** `./gradlew :core:domain:test --tests "com.stratum.domain.program.ProgramGenerationPromptBuilderTest,com.stratum.domain.program.LoadProgramGenerationPayloadUseCaseTest"`

---

### Slice 11 — DI and compile smoke

**Components:** Hilt bindings, optional `:core:testing` fakes promotion if reused across modules

**Checkpoint:** App compiles; all response ports bound to Room implementations; `EmptyExperimentRegistry` bound for `ExperimentRegistry`.

**Modify:**
| File | Responsibility |
|------|----------------|
| `app/src/main/java/com/stratum/di/DataModule.kt` | Bind five response repositories |
| `app/src/main/java/com/stratum/di/DomainModule.kt` (or existing domain module) | Bind extractor + registry if not `@Inject` constructor |

**Create (conditional):**
| File | Responsibility |
|------|----------------|
| `core/testing/.../response/FakeResponseRepositories.kt` | Shared fakes if ≥2 test modules need them |

**Test checkpoint:** `./gradlew :app:assembleDebug` and `./gradlew :core:domain:test`

---

## File Map Preview (Consolidated)

### Create — `:core:domain/response/`

| Path | Responsibility |
|------|----------------|
| `identity/ResponseIds.kt` | Value-class IDs |
| `ResponseModels.kt` | Domain aggregates and I/O bundles |
| `ResponseConstants.kt` | v1 thresholds + re-extract interval |
| `TrainingSignalCleaner.kt` | Working-set classification and RIR-adjusted e1RM |
| `OutcomeSeriesBuilder.kt` | Weekly anchor outcome series |
| `OutlierGuard.kt` | Rolling smoothing, MAD winsorization, suspicious jump flags |
| `EpochSplitter.kt` | Epoch segmentation + deload marking |
| `ChangePointDetector.kt` | Conservative binary segmentation |
| `StimulusSeriesBuilder.kt` | Weekly stimulus series |
| `IntensityProxyCalculator.kt` | RIR-first intensity |
| `AnchorLiftSelector.kt` | Per-muscle anchor with gates |
| `VolumeResponseAnalyzer.kt` | Tier 2 correlations |
| `LiftEffectivenessAnalyzer.kt` | Tier 3 rankings |
| `ResponseObservationExtractor.kt` | Passive-path orchestrator |
| `ResponsePorts.kt` | Repository + registry interfaces |
| `ExtractResponseProfileUseCase.kt` | Persist orchestration |
| `EmptyExperimentRegistry.kt` | Tier 4 no-op |

### Create — `:core:domain/program/`

| Path | Responsibility |
|------|----------------|
| `ResponseProfilePayload.kt` | Payload DTOs and influence filter |

### Create — `:core:data/`

| Path | Responsibility |
|------|----------------|
| `db/ResponseEntities.kt` | Room entities (5 tables) |
| `db/ResponseDaos.kt` | DAOs |
| `mapper/ResponseMapper.kt` | Mapping |
| `repository/RoomResponseRepositories.kt` | Repository impls |

### Create — tests

| Path | Responsibility |
|------|----------------|
| `core/domain/src/test/.../response/*.kt` | Per-slice domain tests + fixtures |
| `core/domain/src/test/.../program/LoadProgramGenerationPayloadUseCaseTest.kt` | Payload assembly |
| `core/data/src/test/.../db/ResponseDaoTest.kt` | Persistence round-trip |

### Modify

| Path | Responsibility |
|------|----------------|
| `core/domain/.../importing/ImportUseCases.kt` | Response hook + rollback |
| `core/domain/.../program/ProgramGenerationPromptBuilder.kt` | `responseProfile` JSON |
| `core/domain/.../program/LoadProgramGenerationPayloadUseCase.kt` | Read response repos |
| `core/data/.../db/StratumDatabase.kt` | Schema registration |
| `core/data/.../db/EnumConverters.kt` | Enum storage |
| `app/.../di/DataModule.kt` | Hilt bindings |
| `core/inference/src/test/.../RunFullHistoricalImportUseCaseTest.kt` | Constructor wiring |

### Unchanged (explicit)

| Path | Note |
|------|------|
| `TrainingInsightsExtractor.kt` | Tier 1 logic frozen |
| `ExtractTrainingInsightsUseCase` | No signature change |
| `:core:inference` production sources | No direct response coupling |

---

## Test Checkpoints Summary

| After slice | Command | Pass criterion |
|-------------|---------|----------------|
| 1 | `:core:domain:test --tests TrainingSignalCleanerTest` | Working-set classification + quality flags |
| 2 | `:core:domain:test --tests Stimulus*,Outcome*,Intensity*,Outlier*` | RIR-first + robust weekly series |
| 3 | `:core:domain:test --tests EpochSplitterTest,ChangePointDetectorTest` | Gap/Jaccard/churn/equipment/CPD + deload rules |
| 4 | `:core:domain:test --tests AnchorLiftSelectorTest` | Sparse skip + selection |
| 5 | `:core:domain:test --tests VolumeResponseAnalyzerTest` | Tier 2 evidence and reliability gates |
| 6 | `:core:domain:test --tests LiftEffectivenessAnalyzerTest` | Tier 3 rank gates |
| 7 | `:core:domain:test --tests ResponseObservation*,ExtractResponse*` | Full domain pipeline |
| 8 | `:core:data:testDebugUnitTest --tests ResponseDaoTest` | Room round-trip |
| 9 | `:core:domain:test --tests PersistImported*,RollbackHistorical*` | Import + rollback |
| 10 | `:core:domain:test --tests ProgramGeneration*` | Payload influence policy |
| 11 | `:app:assembleDebug` + `:core:domain:test` | DI + compile smoke |

Full regression before merge (unchanged from AGENTS.md): `./gradlew ktlintCheck`, `./gradlew detekt`, `./gradlew testDebugUnitTest`, `./gradlew :core:domain:test`.
