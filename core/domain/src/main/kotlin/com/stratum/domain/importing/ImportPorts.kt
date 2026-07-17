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
object HistoricalImportLimits {
    const val MAX_FILES = 5
    const val MAX_FILE_BYTES = 10 * 1024 * 1024
    const val MAX_FILE_SIZE_MB = 10
}

data class ImportFileInput(
    val bytes: ByteArray,
    val fileName: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ImportFileInput) return false
        return bytes.contentEquals(other.bytes) && fileName == other.fileName
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + fileName.hashCode()
        return result
    }
}

sealed class ImportCatalogProgressStep {
    data object LoadingConvertedData : ImportCatalogProgressStep()

    data class MergingImportedFiles(val fileCount: Int) : ImportCatalogProgressStep()

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
    /** 1-based index of the file currently being converted; set during multi-file import. */
    val fileIndex: Int? = null,
    val fileCount: Int? = null,
)

enum class RemoteImportJobStatus {
    PendingAgent,
    AgentRunning,
    Ready,
    Failed,
    /** Server rejected the file as not a training log (no agent spend). */
    Rejected,
}

/**
 * Distinguishes infra/agent failures from unsupported files so UI can show distinct copy
 * while multi-file resume still uses a single [HistoricalImportEvent.Failed] branch.
 */
enum class ImportFailureKind {
    Generic,
    UnsupportedFile,
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
    /** Deterministic format id when known (`"stratum"`, `"hevy"`, …). */
    val conversionFormat: String? = null,
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

    data class Completed(
        val summary: ExtractTrainingInsightsSummary,
        val mergeStats: MergeImportStats? = null,
    ) : HistoricalImportEvent

    data class Failed(
        val message: String,
        /** Present when one or more files converted successfully before a later file failed. */
        val resume: HistoricalImportResumeState? = null,
        val kind: ImportFailureKind = ImportFailureKind.Generic,
    ) : HistoricalImportEvent
}

/** Thrown when the remote job ends in [RemoteImportJobStatus.Rejected]. */
class UnsupportedImportFileException(
    message: String,
) : Exception(message)

/**
 * Checkpoint for resuming a multi-file import without re-converting files that already succeeded.
 * [files] is the full ordered selection; [completedChunks] holds normalized history per success.
 */
data class HistoricalImportResumeState(
    val files: List<ImportFileInput>,
    val completedChunks: List<ImportedHistoryChunk>,
) {
    val nextFileIndex: Int get() = completedChunks.size

    val totalFiles: Int get() = files.size

    val failedFileName: String?
        get() = files.getOrNull(nextFileIndex)?.fileName

    fun isResumable(): Boolean = completedChunks.isNotEmpty() && nextFileIndex < totalFiles
}

interface HistoricalImportRunnerPort {
    fun run(
        files: List<ImportFileInput> = emptyList(),
        resume: HistoricalImportResumeState? = null,
        /** Required — callers must choose Replace vs Merge; no default that could wipe history. */
        persistPolicy: ImportHistoryPersistPolicy,
    ): Flow<HistoricalImportEvent>
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
    suspend fun prepareBatch(exerciseNames: List<String>) {}

    fun resolve(exerciseNameRaw: String, catalogNames: Map<LiftId, String>): LiftId?

    fun evaluate(exerciseNameRaw: String, catalogNames: Map<LiftId, String>): LiftNameMatchEvaluation

    /**
     * Returns the last embedding batch failure message, or `null` when embeddings were fetched
     * successfully (or the resolver doesn't use embeddings). Callers surface this in the import
     * summary so silent degradation to pure BM25 is diagnosable.
     */
    fun embeddingBatchFailure(): String? = null
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
