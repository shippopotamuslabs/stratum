# Response Profile — Implementation Spec

**Ticket:** `response-profile`  
**Phase:** Plan (Synthitect)  
**Grounding:** `research.md`, `design.md` (approved 2026-06-25), `structure.md` (approved 2026-06-25), `ticket.md`  
**Worktree:** `/home/sean/Code/Stratum-response-profile`

---

## Feature Summary

Stratum derives **response profiles** deterministically from longitudinal training history (import path in v1; ongoing logging later). The feature adds Tier 2–3 analytics alongside unchanged Tier 1 (`TrainingInsightsExtractor`), persists results in five new Room v1 tables, and exposes a `responseProfile` JSON block to program generation.

**Passive path (v1):**

1. Clean raw `LoggedSet` rows into working-set signals with RIR-first intensity and quality flags.
2. Build robust weekly stimulus/outcome series (smoothing, MAD winsorization).
3. Segment history into **epochs** (gap, primary-lift churn, equipment shift, change-point rules; deload weeks marked but not split).
4. Select per-muscle **anchor lifts** only when minimum-data gates pass (sparse muscles skipped).
5. Compute **volume–response** observations (OLS slope, evidence labeling) and **lift effectiveness** rankings within movement patterns.
6. Persist via replace-all repositories; orchestrate after Tier 1 on import; rollback clears response tables with insight tables.
7. Program-generation payload includes influence arrays for `Proven` and high-confidence `Hypothesis` only.

**Tier 4-ready (v1 types only):** `ExperimentBlock`, `ExperimentRegistry`, `experiment_blocks` table; passive path uses `EmptyExperimentRegistry`.

**Non-goals v1:** response UI, population ML, in-workout inference, WorkManager worker, schema migrations, changes to `TrainingInsightsExtractor`.

---

## Vertical Slice Table

| # | Goal | Verifiable artifact | Upstream |
|---|------|---------------------|----------|
| 1 | Domain identity types, core models, constants, clean training-signal foundation | `TrainingSignalCleanerTest` | — |
| 2 | Weekly stimulus/outcome series, RIR-first intensity, smoothing, outlier flags | `StimulusSeriesBuilderTest`, `OutcomeSeriesBuilderTest`, `IntensityProxyCalculatorTest`, `OutlierGuardTest` | 1 |
| 3 | Epoch splitting (gaps, lift churn, deload marking, equipment shifts, binary segmentation) | `EpochSplitterTest`, `ChangePointDetectorTest` | 2 |
| 4 | Per-muscle anchor lift selection with sparse-history skip | `AnchorLiftSelectorTest` | 3 |
| 5 | Tier 2 volume–response observations, reliability metrics, evidence labeling | `VolumeResponseAnalyzerTest` | 4 |
| 6 | Tier 3 pattern effectiveness ranking and evidence labeling | `LiftEffectivenessAnalyzerTest` | 4 |
| 7 | Full passive-path extractor, experiment registry stub, extract use case (in-memory) | `ResponseObservationExtractorTest`, `ExtractResponseProfileUseCaseTest` | 5, 6 |
| 8 | Room v1 schema, DAOs, mappers, repository implementations | `ResponseDaoTest` | 7 |
| 9 | Import orchestration: Tier 1 unchanged, response after insights, rollback/reconcile | `PersistImportedHistoryUseCaseTest`, `RollbackHistoricalImportUseCaseTest` | 8 |
| 10 | Program-generation `responseProfile` JSON with influence policy | `ProgramGenerationPromptBuilderTest`, `LoadProgramGenerationPayloadUseCaseTest` | 7 |
| 11 | DI wiring, compile smoke, re-extraction extension point documentation | `:app:assembleDebug` | 8, 9, 10 |

**Dependency chain:** `1 → 2 → 3 → 4 → {5, 6} → 7 → 8 → 9`; `7 → 10 → 11`.

---

## File Structure

Paths verified against worktree `/home/sean/Code/Stratum-response-profile` on 2026-06-25.

### Create — `:core:domain`

| Path | Action | Responsibility |
|------|--------|----------------|
| `core/domain/src/main/kotlin/com/stratum/domain/response/identity/ResponseIds.kt` | Create | `EpochId`, `ExperimentId` value classes |
| `core/domain/src/main/kotlin/com/stratum/domain/response/ResponseModels.kt` | Create | Enums, descriptors, I/O bundles, `WeeklyTrainingSignal`, `RegressionReliability`, `WeeklySetRange` |
| `core/domain/src/main/kotlin/com/stratum/domain/response/ResponseConstants.kt` | Create | v1 thresholds and `RE_EXTRACT_MIN_INTERVAL_DAYS` |
| `core/domain/src/main/kotlin/com/stratum/domain/response/TrainingSignalCleaner.kt` | Create | Unit normalization, working-set classification, RIR-adjusted e1RM, quality flags |
| `core/domain/src/main/kotlin/com/stratum/domain/response/StimulusSeriesBuilder.kt` | Create | Weekly muscle/lift hard-set and session series |
| `core/domain/src/main/kotlin/com/stratum/domain/response/OutcomeSeriesBuilder.kt` | Create | Weekly anchor e1RM outcome series |
| `core/domain/src/main/kotlin/com/stratum/domain/response/IntensityProxyCalculator.kt` | Create | RIR-first intensity with e1RM fallback |
| `core/domain/src/main/kotlin/com/stratum/domain/response/OutlierGuard.kt` | Create | Rolling median smoothing, MAD winsorization, suspicious jump flags |
| `core/domain/src/main/kotlin/com/stratum/domain/response/EpochSplitter.kt` | Create | Gap/Jaccard/lift-churn/equipment-shift split; deload week marking |
| `core/domain/src/main/kotlin/com/stratum/domain/response/ChangePointDetector.kt` | Create | Conservative binary segmentation over smoothed weekly signals |
| `core/domain/src/main/kotlin/com/stratum/domain/response/AnchorLiftSelector.kt` | Create | Per-muscle anchor ranking with sparse skip |
| `core/domain/src/main/kotlin/com/stratum/domain/response/VolumeResponseAnalyzer.kt` | Create | OLS slope, reliability metrics, evidence labeling |
| `core/domain/src/main/kotlin/com/stratum/domain/response/LiftEffectivenessAnalyzer.kt` | Create | Score, rank, cross-epoch Proven gate |
| `core/domain/src/main/kotlin/com/stratum/domain/response/ResponseObservationExtractor.kt` | Create | Default passive-path orchestrator implementing `ResponseObservationExtractor` |
| `core/domain/src/main/kotlin/com/stratum/domain/response/ResponsePorts.kt` | Create | Five repository ports + `ExperimentRegistry` interface |
| `core/domain/src/main/kotlin/com/stratum/domain/response/ExtractResponseProfileUseCase.kt` | Create | Read history/Tier 1 → extract → replaceAll; re-extract KDoc |
| `core/domain/src/main/kotlin/com/stratum/domain/response/EmptyExperimentRegistry.kt` | Create | Tier 4 no-op registry |
| `core/domain/src/main/kotlin/com/stratum/domain/program/ResponseProfilePayload.kt` | Create | Payload DTOs and influence filter |
| `core/domain/src/test/kotlin/com/stratum/domain/response/fixtures/ResponseFixtures.kt` | Create | Shared synthetic sessions/sets/lifts builder |
| `core/domain/src/test/kotlin/com/stratum/domain/response/TrainingSignalCleanerTest.kt` | Create | Slice 1 tests |
| `core/domain/src/test/kotlin/com/stratum/domain/response/StimulusSeriesBuilderTest.kt` | Create | Slice 2 tests |
| `core/domain/src/test/kotlin/com/stratum/domain/response/OutcomeSeriesBuilderTest.kt` | Create | Slice 2 tests |
| `core/domain/src/test/kotlin/com/stratum/domain/response/IntensityProxyCalculatorTest.kt` | Create | Slice 2 tests |
| `core/domain/src/test/kotlin/com/stratum/domain/response/OutlierGuardTest.kt` | Create | Slice 2 tests |
| `core/domain/src/test/kotlin/com/stratum/domain/response/EpochSplitterTest.kt` | Create | Slice 3 tests |
| `core/domain/src/test/kotlin/com/stratum/domain/response/ChangePointDetectorTest.kt` | Create | Slice 3 tests |
| `core/domain/src/test/kotlin/com/stratum/domain/response/AnchorLiftSelectorTest.kt` | Create | Slice 4 tests |
| `core/domain/src/test/kotlin/com/stratum/domain/response/VolumeResponseAnalyzerTest.kt` | Create | Slice 5 tests |
| `core/domain/src/test/kotlin/com/stratum/domain/response/LiftEffectivenessAnalyzerTest.kt` | Create | Slice 6 tests |
| `core/domain/src/test/kotlin/com/stratum/domain/response/ResponseObservationExtractorTest.kt` | Create | Slice 7 tests |
| `core/domain/src/test/kotlin/com/stratum/domain/response/ExtractResponseProfileUseCaseTest.kt` | Create | Slice 7 tests |
| `core/domain/src/test/kotlin/com/stratum/domain/program/LoadProgramGenerationPayloadUseCaseTest.kt` | Create | Slice 10 tests |

### Create — `:core:data`

| Path | Action | Responsibility |
|------|--------|----------------|
| `core/data/src/main/kotlin/com/stratum/core/data/db/ResponseEntities.kt` | Create | Five Room entities |
| `core/data/src/main/kotlin/com/stratum/core/data/db/ResponseDaos.kt` | Create | DAO interfaces for five tables |
| `core/data/src/main/kotlin/com/stratum/core/data/mapper/ResponseMapper.kt` | Create | Entity ↔ domain mapping |
| `core/data/src/main/kotlin/com/stratum/core/data/repository/RoomResponseRepositories.kt` | Create | Four observation/epoch repo impls + metadata |
| `core/data/src/test/kotlin/com/stratum/core/data/db/ResponseDaoTest.kt` | Create | Robolectric round-trip and replaceAll |

### Create — `:core:testing` (conditional)

| Path | Action | Responsibility |
|------|--------|----------------|
| `core/testing/src/main/kotlin/com/stratum/core/testing/response/FakeResponseRepositories.kt` | Create (if ≥2 modules need shared fakes) | In-memory response port fakes |

### Modify — verified existing

| Path | Action | Responsibility |
|------|--------|----------------|
| `core/domain/src/main/kotlin/com/stratum/domain/importing/ImportUseCases.kt` | Modify | Inject `ExtractResponseProfileUseCase`; invoke after Tier 1; extend rollback |
| `core/domain/src/main/kotlin/com/stratum/domain/program/ProgramGenerationPromptBuilder.kt` | Modify | Serialize optional `responseProfile` key |
| `core/domain/src/main/kotlin/com/stratum/domain/program/LoadProgramGenerationPayloadUseCase.kt` | Modify | Read response repos; assemble `ResponseProfilePayload` |
| `core/data/src/main/kotlin/com/stratum/core/data/db/StratumDatabase.kt` | Modify | Register five entities, DAO accessors; stay version 1 |
| `core/data/src/main/kotlin/com/stratum/core/data/db/EnumConverters.kt` | Modify | Converters for new stored enums |
| `app/src/main/java/com/stratum/di/DataModule.kt` | Modify | Bind five response repositories + `ExperimentRegistry` |
| `core/domain/src/test/kotlin/com/stratum/domain/program/ProgramGenerationPromptBuilderTest.kt` | Modify | Influence/omission rules for `responseProfile` |
| `core/domain/src/test/kotlin/com/stratum/domain/importing/PersistImportedHistoryUseCaseTest.kt` | Create or Modify | Response hook and failure rollback |
| `core/domain/src/test/kotlin/com/stratum/domain/importing/RollbackHistoricalImportUseCaseTest.kt` | Create | Response table clears |
| `core/inference/src/test/kotlin/com/stratum/core/inference/RunFullHistoricalImportUseCaseTest.kt` | Modify | Updated constructor wiring |

### Unchanged (explicit)

| Path | Note |
|------|------|
| `core/domain/src/main/kotlin/com/stratum/domain/importing/TrainingInsightsExtractor.kt` | Tier 1 logic frozen |
| `ExtractTrainingInsightsUseCase` in `ImportUseCases.kt` | No signature or behavior change |
| `:core:inference` production sources | No direct response coupling |

**Note:** `app/src/main/java/com/stratum/di/DomainModule.kt` does not exist; use `@Inject` constructors for use cases/extractor and `@Binds` in `DataModule.kt` `RepositoryModule` for ports requiring interface binding.

---

## API Contracts

Contracts match `structure.md` exactly.

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
    val deloadWeeksExcluded: Set<Instant>,
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

`ExtractResponseProfileUseCase` reads the same history snapshot as Tier 1, passes Tier 1 outputs into `ResponseExtractionInput`, calls `extractor.extract`, then `replaceAll` on all four response data tables plus metadata. `activeExperiments` sourced from `experimentRegistry.active()` (empty in v1).

**Re-extraction extension point:** KDoc on `ExtractResponseProfileUseCase` documents that v1 runs only from import orchestration; future WorkManager worker should call `invoke()` when `Instant.now() - metadata.lastExtractedAt >= RE_EXTRACT_MIN_INTERVAL_DAYS`. No worker in v1.

### Import orchestration (behavior extended, Tier 1 signatures unchanged)

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

---

## Room Schema (v1, no migration)

`StratumDatabase` remains **version 1**. Dev policy: uninstall/reinstall on schema change pre-release.

| Table | Entity PK | Maps to | Semantics |
|-------|-----------|---------|-----------|
| `response_epochs` | `id: String` | `Epoch` | replaceAll |
| `volume_response_observations` | auto `rowId` | `VolumeResponseObservation` | replaceAll |
| `lift_effectiveness_observations` | auto `rowId` | `LiftEffectivenessObservation` | replaceAll |
| `experiment_blocks` | `id: String` | `ExperimentBlock` | replaceAll |
| `response_profile_metadata` | singleton `id=0` | `ResponseProfileMetadata` | upsert + clear |

All insight-like tables use `replaceAll` = delete-all + insert.

---

## Implementation Tasks

Tasks are ordered within each slice. Scenario IDs reference `test_spec.md`.

### Slice 1 — Clean training signal foundation

| ID | Task | Scenarios |
|----|------|-----------|
| S1-T01 | Create `ResponseIds.kt` with `EpochId`, `ExperimentId` | RP-S1-001 |
| S1-T02 | Create `ResponseModels.kt` with core enums and `CleanTrainingSet` | RP-S1-001, RP-S1-004, RP-S1-005, RP-S1-006 |
| S1-T03 | Create `ResponseConstants.kt` with all v1 thresholds | RP-S1-002, RP-S1-003 |
| S1-T04 | Implement `TrainingSignalCleaner.clean`: unit normalization, working-set rules (RIR ≥ 5 excluded; relative load < 0.65 excluded when RIR missing), RIR-adjusted e1RM, quality flags | RP-S1-001, RP-S1-002, RP-S1-003, RP-S1-004, RP-S1-005, RP-S1-006, RP-S1-M001 |
| S1-T05 | Create `ResponseFixtures.kt` with baseline session/set builders | RP-S1-001 through RP-S1-006 |
| S1-T06 | Write `TrainingSignalCleanerTest` (TDD Red) | RP-S1-001 through RP-S1-M001 |

**Checkpoint:** `./gradlew :core:domain:test --tests "com.stratum.domain.response.TrainingSignalCleanerTest"`

### Slice 2 — Weekly series and robustness

| ID | Task | Scenarios |
|----|------|-----------|
| S2-T01 | Extend `ResponseModels.kt` with `WeeklyTrainingSignal`, `WeeklyPoint` | RP-S2-001, RP-S2-003 |
| S2-T02 | Implement `StimulusSeriesBuilder`: weekly hard sets and sessions from working sets only | RP-S2-003, RP-S2-004 |
| S2-T03 | Implement `OutcomeSeriesBuilder`: top valid e1RM or robust mean of hardest 1–2 sets | RP-S2-007 |
| S2-T04 | Implement `IntensityProxyCalculator`: inverted mean RIR when coverage ≥ 0.70; e1RM proxy fallback otherwise | RP-S2-001, RP-S2-002 |
| S2-T05 | Implement `OutlierGuard`: 3-week rolling median, MAD winsorization at 3.5, suspicious jump at 15% | RP-S2-004, RP-S2-005, RP-S2-006 |
| S2-T06 | Extend `ResponseFixtures.kt` with RIR-enriched rows | RP-S2-001, RP-S2-002 |
| S2-T07 | Write slice 2 test classes (TDD Red) | RP-S2-001 through RP-S2-007 |

**Checkpoint:** `./gradlew :core:domain:test --tests "com.stratum.domain.response.StimulusSeriesBuilderTest,com.stratum.domain.response.OutcomeSeriesBuilderTest,com.stratum.domain.response.IntensityProxyCalculatorTest,com.stratum.domain.response.OutlierGuardTest"`

### Slice 3 — Epoch splitting and change points

| ID | Task | Scenarios |
|----|------|-----------|
| S3-T01 | Extend `ResponseModels.kt` with `Epoch`, `EpochSplitReason` | RP-S3-001 through RP-S3-007 |
| S3-T02 | Implement `EpochSplitter.split`: 14-day gap, Jaccard > 0.5 over 4-week window, lift churn, equipment shift | RP-S3-001, RP-S3-002, RP-S3-003, RP-S3-004, RP-S3-M001 |
| S3-T03 | Implement `EpochSplitter.markDeloadWeeks`: volume < 50% epoch median; weight drop > 15% with volume ± 20% | RP-S3-006, RP-S3-007 |
| S3-T04 | Implement `ChangePointDetector.binarySegment`: min 6-week segments, penalty multiplier 4.0 | RP-S3-005 |
| S3-T05 | Write `EpochSplitterTest`, `ChangePointDetectorTest` (TDD Red) | RP-S3-001 through RP-S3-M001 |

**Checkpoint:** `./gradlew :core:domain:test --tests "com.stratum.domain.response.EpochSplitterTest,com.stratum.domain.response.ChangePointDetectorTest"`

### Slice 4 — Anchor lift selection

| ID | Task | Scenarios |
|----|------|-----------|
| S4-T01 | Implement `AnchorLiftSelector.select`: rank by sessions × recency; gates 8 sessions and 6 weeks; prioritize `Specialize` muscles on tie | RP-S4-001, RP-S4-002, RP-S4-003 |
| S4-T02 | Write `AnchorLiftSelectorTest` (TDD Red) | RP-S4-001 through RP-S4-003 |

**Checkpoint:** `./gradlew :core:domain:test --tests "com.stratum.domain.response.AnchorLiftSelectorTest"`

### Slice 5 — Tier 2 volume–response

| ID | Task | Scenarios |
|----|------|-----------|
| S5-T01 | Extend `ResponseModels.kt` with `VolumeResponseObservation`, `StimulusDescriptor`, `OutcomeDescriptor`, `RegressionReliability`, `WeeklySetRange`, `EvidenceLevel` | RP-S5-001 through RP-S5-005 |
| S5-T02 | Implement `VolumeResponseAnalyzer.analyze`: OLS when ≥ 4 weekly points and ≥ 8 epoch weeks; exclude deload weeks; evidence rules (never `Proven` on single epoch; `Proven` when 2 epochs agree in sign) | RP-S5-001, RP-S5-002, RP-S5-003, RP-S5-004, RP-S5-005, RP-S5-M001 |
| S5-T03 | Write `VolumeResponseAnalyzerTest` (TDD Red) | RP-S5-001 through RP-S5-M001 |

**Checkpoint:** `./gradlew :core:domain:test --tests "com.stratum.domain.response.VolumeResponseAnalyzerTest"`

### Slice 6 — Tier 3 lift effectiveness

| ID | Task | Scenarios |
|----|------|-----------|
| S6-T01 | Extend `ResponseModels.kt` with `LiftEffectivenessObservation` | RP-S6-001 through RP-S6-005 |
| S6-T02 | Implement `LiftEffectivenessAnalyzer.analyze`: score = Δe1RM_per_week / mean_weekly_hard_sets; rank within pattern when ≥ 2 peers; top beats runner-up by 20%; `Proven` when rank stable across ≥ 2 epochs; no cross-pattern comparison | RP-S6-001, RP-S6-002, RP-S6-003, RP-S6-004, RP-S6-005 |
| S6-T03 | Write `LiftEffectivenessAnalyzerTest` (TDD Red) | RP-S6-001 through RP-S6-005 |

**Checkpoint:** `./gradlew :core:domain:test --tests "com.stratum.domain.response.LiftEffectivenessAnalyzerTest"`

### Slice 7 — Extractor composition and use case

| ID | Task | Scenarios |
|----|------|-----------|
| S7-T01 | Define `ResponseObservationExtractor` interface and default implementation orchestrating slices 1–6 | RP-S7-001, RP-S7-004 |
| S7-T02 | Create `ResponsePorts.kt` with five repository interfaces and `ExperimentRegistry` | RP-S7-002, RP-S7-003 |
| S7-T03 | Create `EmptyExperimentRegistry` | RP-S7-003 |
| S7-T04 | Implement `ExtractResponseProfileUseCase` with re-extract KDoc | RP-S7-002, RP-S7-004 |
| S7-T05 | Extend `ResponseModels.kt` with `ResponseExtractionInput`, `ResponseExtractionOutput`, `ExtractResponseProfileSummary`, `ResponseProfileMetadata`, `ExperimentBlock`, `ExperimentStatus` | RP-S7-001 through RP-S7-004 |
| S7-T06 | Write `ResponseObservationExtractorTest`, `ExtractResponseProfileUseCaseTest` with module-local in-memory fakes (TDD Red) | RP-S7-001 through RP-S7-004 |

**Checkpoint:** `./gradlew :core:domain:test --tests "com.stratum.domain.response.ResponseObservationExtractorTest,com.stratum.domain.response.ExtractResponseProfileUseCaseTest"`

### Slice 8 — Room persistence

| ID | Task | Scenarios |
|----|------|-----------|
| S8-T01 | Create `ResponseEntities.kt` for five tables | RP-S8-001 through RP-S8-005 |
| S8-T02 | Create `ResponseDaos.kt` with delete-all + insert and singleton metadata accessors | RP-S8-006, RP-S8-007 |
| S8-T03 | Create `ResponseMapper.kt` entity ↔ domain | RP-S8-001 through RP-S8-005 |
| S8-T04 | Create `RoomResponseRepositories.kt` with replaceAll semantics | RP-S8-006 |
| S8-T05 | Modify `StratumDatabase.kt`: register entities and DAOs; version stays 1 | RP-S8-001 through RP-S8-007 |
| S8-T06 | Modify `EnumConverters.kt` for stored enums | RP-S8-001 through RP-S8-005 |
| S8-T07 | Write `ResponseDaoTest` Robolectric round-trip (TDD Red) | RP-S8-001 through RP-S8-007 |

**Checkpoint:** `./gradlew :core:data:testDebugUnitTest --tests "com.stratum.core.data.db.ResponseDaoTest"`

### Slice 9 — Import orchestration

| ID | Task | Scenarios |
|----|------|-----------|
| S9-T01 | Modify `PersistImportedHistoryUseCase`: inject `ExtractResponseProfileUseCase`; invoke after successful Tier 1; rollback history on response failure | RP-S9-001, RP-S9-002, RP-S9-005 |
| S9-T02 | Modify `RollbackHistoricalImportUseCase`: clear five response repositories | RP-S9-003 |
| S9-T03 | Verify `ReconcileHistoricalImportUseCase` rollback path clears response tables via extended rollback | RP-S9-004 |
| S9-T04 | Create or extend `PersistImportedHistoryUseCaseTest`, `RollbackHistoricalImportUseCaseTest` (TDD Red) | RP-S9-001 through RP-S9-005 |
| S9-T05 | Modify `RunFullHistoricalImportUseCaseTest` constructor wiring | RP-S9-001 |

**Checkpoint:** `./gradlew :core:domain:test --tests "com.stratum.domain.importing.PersistImportedHistoryUseCaseTest,com.stratum.domain.importing.RollbackHistoricalImportUseCaseTest"`

### Slice 10 — Program-generation payload

| ID | Task | Scenarios |
|----|------|-----------|
| S10-T01 | Create `ResponseProfilePayload.kt` with DTOs and influence filter (`Proven` always; `Hypothesis` ≥ 0.65; `InsufficientData` excluded) | RP-S10-002, RP-S10-003, RP-S10-004, RP-S10-005 |
| S10-T02 | Modify `ProgramGenerationPromptBuilder.buildPayload`: optional `"responseProfile"` JSON key | RP-S10-001, RP-S10-006 |
| S10-T03 | Modify `LoadProgramGenerationPayloadUseCase`: read response repos; assemble payload | RP-S10-001 through RP-S10-006 |
| S10-T04 | Extend `ProgramGenerationPromptBuilderTest`; create `LoadProgramGenerationPayloadUseCaseTest` (TDD Red) | RP-S10-001 through RP-S10-006 |

**Checkpoint:** `./gradlew :core:domain:test --tests "com.stratum.domain.program.ProgramGenerationPromptBuilderTest,com.stratum.domain.program.LoadProgramGenerationPayloadUseCaseTest"`

### Slice 11 — DI and compile smoke

| ID | Task | Scenarios |
|----|------|-----------|
| S11-T01 | Modify `DataModule.kt` `RepositoryModule`: bind five response repositories | RP-S11-001 |
| S11-T02 | Bind `ExperimentRegistry` to `EmptyExperimentRegistry` | RP-S11-001 |
| S11-T03 | Ensure `ResponseObservationExtractor` and `ExtractResponseProfileUseCase` are constructible via Hilt (`@Inject` or `@Provides`) | RP-S11-001 |
| S11-T04 | Promote `FakeResponseRepositories.kt` to `:core:testing` if import and program tests both need shared fakes | RP-S11-001 |
| S11-T05 | Run compile smoke and full domain regression | RP-S11-001 |

**Checkpoint:** `./gradlew :app:assembleDebug` and `./gradlew :core:domain:test`

**Full merge regression:** `./gradlew ktlintCheck`, `./gradlew detekt`, `./gradlew testDebugUnitTest`, `./gradlew :core:domain:test`

---

## Drift and Escalation Rules

1. **Contract drift:** If implementation requires changing any public signature, enum variant, constant value, table name, or JSON key documented in this spec or `structure.md`, stop and request a **Contract Waiver** or design re-approval before proceeding.

2. **Schema drift:** No new tables or columns beyond the five authorized response tables without explicit developer authorization (`AGENTS.md` §4.5). No Room version bump or migration in v1.

3. **Tier 1 isolation:** Do not modify `TrainingInsightsExtractor` or `ExtractTrainingInsightsUseCase` behavior. Response failures must not leave partial response rows when history is rolled back.

4. **Evidence labeling drift:** Single-epoch volume–response must never emit `Proven`. Passive path observations default to `Hypothesis` unless multi-epoch agreement or Tier 4 registry applies.

5. **Payload influence drift:** Only `Proven` and `Hypothesis` with `confidenceScore >= 0.65` belong in influence arrays. Sub-threshold hypothesis and all `InsufficientData` stay out of constraint arrays.

6. **Test drift:** Red-phase tests must assert literal expected values from fixtures (see `test_spec.md`), not values copied from production implementations. Mutation-defense scenarios (`RP-S*-M001`) must fail if thresholds are hardcoded incorrectly.

7. **Scope escalation:** UI dashboards, WorkManager workers, population ML, direct OpenRouter inference on raw logs, or `:core:inference` production changes require a new ticket and design phase.

8. **Fake promotion:** Module-local in-memory fakes are acceptable in slice 7–9; promote to `:core:testing` only when a second module needs the same fake (slice 11 gate).

9. **Human gate:** After TDD Red, Red Review loop (max 3) validates tests against this spec before Implementation. Final Review (max 3) before merge.

---

## Task and Scenario Index

| Metric | Count |
|--------|-------|
| Implementation tasks | 53 |
| Test scenarios (including mutation-defense) | 59 |
| Vertical slices | 11 |
| New Room tables | 5 |
