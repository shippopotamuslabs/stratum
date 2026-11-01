package com.stratum.core.infrastructure.repository

import com.stratum.core.infrastructure.db.ImportAnalysisMetadataDao
import com.stratum.core.infrastructure.db.ImportJobDao
import com.stratum.core.infrastructure.db.LiftTrainingInsightsDao
import com.stratum.core.infrastructure.db.MuscleVolumeBaselineDao
import com.stratum.core.infrastructure.db.SupersetAffinityDao
import com.stratum.core.infrastructure.mapper.toDomain
import com.stratum.core.infrastructure.mapper.toEntity
import com.stratum.domain.importing.ImportAnalysisMetadata
import com.stratum.application.importing.ImportAnalysisMetadataRepository
import com.stratum.domain.importing.ImportJob
import com.stratum.application.importing.ImportJobRepository
import com.stratum.domain.importing.ImportJobStatus
import com.stratum.domain.importing.LiftTrainingInsights
import com.stratum.application.importing.LiftTrainingInsightsRepository
import com.stratum.domain.importing.MuscleVolumeBaseline
import com.stratum.application.importing.MuscleVolumeBaselineRepository
import com.stratum.domain.importing.SupersetAffinity
import com.stratum.application.importing.SupersetAffinityRepository
import com.stratum.domain.importing.identity.ImportJobId
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class RoomImportJobRepository @Inject constructor(
    private val importJobDao: ImportJobDao,
) : ImportJobRepository {
    override suspend fun createJob(): ImportJob {
        val now = Instant.now()
        val job = ImportJob(
            id = ImportJobId(UUID.randomUUID().toString()),
            status = ImportJobStatus.Pending,
            sampleRef = null,
            fullRef = null,
            scriptRef = null,
            errorMessage = null,
            createdAt = now,
            updatedAt = now,
        )
        importJobDao.upsert(job.toEntity())
        return job
    }

    override suspend fun getJob(id: ImportJobId): ImportJob? =
        importJobDao.getById(id.value)?.toDomain()

    override suspend fun updateJob(job: ImportJob) {
        importJobDao.upsert(job.toEntity())
    }

    override suspend fun getActiveJob(): ImportJob? =
        importJobDao.getActive()?.toDomain()
}

class RoomLiftTrainingInsightsRepository @Inject constructor(
    private val dao: LiftTrainingInsightsDao,
) : LiftTrainingInsightsRepository {
    override suspend fun replaceAll(insights: List<LiftTrainingInsights>) {
        dao.deleteAll()
        if (insights.isNotEmpty()) dao.upsertAll(insights.map { it.toEntity() })
    }

    override suspend fun getAll(): List<LiftTrainingInsights> =
        dao.getAll().map { it.toDomain() }

    override suspend fun getByLiftId(liftId: com.stratum.domain.lift.identity.LiftId): LiftTrainingInsights? =
        dao.getByLiftId(liftId)?.toDomain()
}

class RoomSupersetAffinityRepository @Inject constructor(
    private val dao: SupersetAffinityDao,
) : SupersetAffinityRepository {
    override suspend fun replaceAll(affinities: List<SupersetAffinity>) {
        dao.deleteAll()
        if (affinities.isNotEmpty()) dao.upsertAll(affinities.map { it.toEntity() })
    }

    override suspend fun getAll(): List<SupersetAffinity> =
        dao.getAll().map { it.toDomain() }
}

class RoomMuscleVolumeBaselineRepository @Inject constructor(
    private val dao: MuscleVolumeBaselineDao,
) : MuscleVolumeBaselineRepository {
    override suspend fun replaceAll(baselines: List<MuscleVolumeBaseline>) {
        dao.deleteAll()
        if (baselines.isNotEmpty()) dao.upsertAll(baselines.map { it.toEntity() })
    }

    override suspend fun getAll(): List<MuscleVolumeBaseline> =
        dao.getAll().map { it.toDomain() }
}

class RoomImportAnalysisMetadataRepository @Inject constructor(
    private val dao: ImportAnalysisMetadataDao,
) : ImportAnalysisMetadataRepository {
    override suspend fun save(metadata: ImportAnalysisMetadata) {
        dao.upsert(metadata.toEntity())
    }

    override suspend fun get(): ImportAnalysisMetadata? =
        dao.get()?.toDomain()

    override suspend fun clear() {
        dao.deleteAll()
    }
}
