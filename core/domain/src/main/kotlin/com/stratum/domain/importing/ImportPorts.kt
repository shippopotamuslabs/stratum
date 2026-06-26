package com.stratum.domain.importing

import com.stratum.domain.importing.identity.ImportJobId
import com.stratum.domain.lift.identity.LiftId
import kotlinx.coroutines.flow.Flow

enum class ImportProgressPhase {
    Uploading,
    ReadingData,
    CreatingConverter,
    ValidatingSample,
    ConvertingFull,
    EnrichingCustomLifts,
    ExtractingInsights,
    Complete,
    Failed,
}

/**
 * Client-side sub-step shown as a muted subtitle under the phase title during historical import.
 * Mapped to user-facing copy in `:feature:onboarding` — domain stays string-free for i18n.
 */
sealed class ImportCatalogProgressStep {
    data object LoadingConvertedData : ImportCatalogProgressStep()

    data class MatchingCatalog(val exerciseCount: Int) : ImportCatalogProgressStep()

    data class IdentifyingCustomLifts(val exerciseCount: Int) : ImportCatalogProgressStep()

    data class InferringEquipment(val exerciseCount: Int) : ImportCatalogProgressStep()

    data object EnablingAvailableLifts : ImportCatalogProgressStep()

    data object SavingHistory : ImportCatalogProgressStep()

    data object AnalyzingPatterns : ImportCatalogProgressStep()
}

typealias ImportProgressCallback = suspend (ImportProgress) -> Unit

data class ImportProgress(
    val phase: ImportProgressPhase,
    val detail: String? = null,
    val catalogStep: ImportCatalogProgressStep? = null,
)

enum class RemoteImportJobStatus {
    PendingAgent,
    AgentRunning,
    Ready,
    Failed,
}

data class UploadRefs(
    val rawStoragePath: String,
    val sampleStoragePath: String,
)

data class ImportJobSnapshot(
    val status: RemoteImportJobStatus,
    val progressPhase: ImportProgressPhase?,
    val progressMessage: String?,
    val conversionStoragePath: String?,
    val errorMessage: String?,
)

data class ImportConversionResult(
    val sessionsImported: Int,
    val setsImported: Int,
)

interface HistoricalImportPort {
    suspend fun uploadRawAndSample(
        jobId: ImportJobId,
        bytes: ByteArray,
        fileName: String,
    ): Result<UploadRefs>

    suspend fun startImport(jobId: ImportJobId, refs: UploadRefs): Result<Unit>

    suspend fun pollJobStatus(jobId: ImportJobId): Result<ImportJobSnapshot>

    suspend fun downloadConversion(storagePath: String): Result<String>
}

sealed interface HistoricalImportEvent {
    data class Progress(val progress: ImportProgress) : HistoricalImportEvent

    data class Completed(val summary: ExtractTrainingInsightsSummary) : HistoricalImportEvent

    data class Failed(val message: String) : HistoricalImportEvent
}

interface HistoricalImportRunnerPort {
    fun run(fileBytes: ByteArray, fileName: String): Flow<HistoricalImportEvent>
}

interface TrainingHistoryRepository {
    suspend fun replaceAll(sessions: List<HistoricalSession>, sets: List<LoggedSet>)

    suspend fun getAllSessions(): List<HistoricalSession>

    suspend fun getAllSets(): List<LoggedSet>

    suspend fun getSetsInWindow(windowStart: java.time.Instant, windowEnd: java.time.Instant): List<LoggedSet>
}

interface ImportJobRepository {
    suspend fun createJob(): ImportJob

    suspend fun getJob(id: ImportJobId): ImportJob?

    suspend fun updateJob(job: ImportJob)

    suspend fun getActiveJob(): ImportJob?
}

interface LiftTrainingInsightsRepository {
    suspend fun replaceAll(insights: List<LiftTrainingInsights>)

    suspend fun getAll(): List<LiftTrainingInsights>

    suspend fun getByLiftId(liftId: LiftId): LiftTrainingInsights?
}

interface SupersetAffinityRepository {
    suspend fun replaceAll(affinities: List<SupersetAffinity>)

    suspend fun getAll(): List<SupersetAffinity>
}

interface MuscleVolumeBaselineRepository {
    suspend fun replaceAll(baselines: List<MuscleVolumeBaseline>)

    suspend fun getAll(): List<MuscleVolumeBaseline>
}

interface ImportAnalysisMetadataRepository {
    suspend fun save(metadata: ImportAnalysisMetadata)

    suspend fun get(): ImportAnalysisMetadata?

    suspend fun clear()
}

interface LiftNameResolverPort {
    fun resolve(exerciseNameRaw: String, catalogNames: Map<LiftId, String>): LiftId?

    fun evaluate(exerciseNameRaw: String, catalogNames: Map<LiftId, String>): LiftNameMatchEvaluation
}

data class LiftNameMatchEvaluation(
    val matchedLiftId: LiftId?,
    val bestScore: Double,
    val bestCatalogName: String?,
    val matched: Boolean,
)

/**
 * Read-only access to the bundled `lift-equipment-tags.json` asset: a map from catalog lift id to
 * the [GymEquipment] required to perform that lift. Used transiently during historical import to
 * drive Tier B enablement (equipment closure) and the sparse-import gym-type fallback.
 *
 * **Not backed by Room** — the data lives in the APK asset and is read on demand at import time
 * only. Post-onboarding the app is lift-first and never queries equipment (ARCHITECTURE.md §2.1).
 */
interface LiftEquipmentCatalogPort {
    fun equipmentFor(liftId: LiftId): Set<GymEquipment>

    /** All catalog entries that declare equipment, as id -> equipment set. */
    fun all(): Map<LiftId, Set<GymEquipment>>
}
