package com.stratum.application.importing

import com.stratum.domain.importing.identity.ImportJobId
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import kotlinx.coroutines.flow.Flow
import com.stratum.domain.importing.HistoricalImportEvent
import com.stratum.domain.importing.HistoricalImportResumeState
import com.stratum.domain.importing.ImportFileInput
import com.stratum.domain.importing.ImportJobSnapshot
import com.stratum.domain.importing.LiftNameMatchEvaluation
import com.stratum.domain.importing.UploadRefs
import com.stratum.domain.importing.GymEquipment
import com.stratum.domain.importing.ImportAnalysisMetadata
import com.stratum.domain.importing.ImportHistoryPersistPolicy
import com.stratum.domain.importing.ImportJob
import com.stratum.domain.importing.LiftTrainingInsights
import com.stratum.domain.importing.MuscleVolumeBaseline
import com.stratum.application.importing.ResidualLiftNameResolution
import com.stratum.domain.importing.SupersetAffinity
import com.stratum.domain.lift.residual

interface HistoricalImportPort {
    suspend fun uploadRawAndSample(
        jobId: ImportJobId,
        bytes: ByteArray,
        fileName: String,
    ): Result<UploadRefs>

    suspend fun startImport(
        jobId: ImportJobId,
        refs: UploadRefs,
        /** When conversion omits weightUnit, agent/server should stamp this unit. */
        fallbackWeightUnit: Weight.WeightUnit = Weight.WeightUnit.Lb,
    ): Result<Unit>

    suspend fun pollJobStatus(jobId: ImportJobId): Result<ImportJobSnapshot>

    suspend fun downloadConversion(storagePath: String): Result<String>
}

interface HistoricalImportRunnerPort {
    fun run(
        files: List<ImportFileInput> = emptyList(),
        resume: HistoricalImportResumeState? = null,
        /** Required — callers must choose Replace vs Merge; no default that could wipe history. */
        persistPolicy: ImportHistoryPersistPolicy,
        /**
         * When a converted set has no explicit weight unit, stamp this unit.
         * Ignored for sets that already carry a unit. Prefer the user's import prompt choice;
         * defaults to Lb only for test/legacy callers.
         */
        fallbackWeightUnit: Weight.WeightUnit = Weight.WeightUnit.Lb,
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
    /**
     * Prefetch query embeddings for residual exercise names that still need semantic rerank after
     * local exact/BM25 matching. Callers must **not** pass already high-confidence matched names —
     * that forces unnecessary cloud MiniLM work on the happy path.
     *
     * Empty [exerciseNames] clears prior batch state without network (required for singleton
     * resolvers before a local-only pass). Prefer [ResidualLiftNameResolution] for the full
     * clear → local → residual embed → re-resolve contract.
     */
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

interface LiftEquipmentCatalogPort {
    fun equipmentFor(liftId: LiftId): Set<GymEquipment>

    /** All catalog entries that declare equipment, as id -> equipment set. */
    fun all(): Map<LiftId, Set<GymEquipment>>
}
