package com.stratum.core.testing.importing

import com.stratum.domain.importing.ImportJob
import com.stratum.domain.importing.ImportJobRepository
import com.stratum.domain.importing.ImportJobStatus
import com.stratum.domain.importing.identity.ImportJobId
import java.time.Instant
import java.util.UUID

class FakeImportJobRepository : ImportJobRepository {
    private val jobs = mutableMapOf<ImportJobId, ImportJob>()

    fun allJobs(): List<ImportJob> = jobs.values.toList()

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
        jobs[job.id] = job
        return job
    }

    override suspend fun getJob(id: ImportJobId): ImportJob? = jobs[id]

    override suspend fun updateJob(job: ImportJob) {
        jobs[job.id] = job
    }

    override suspend fun getActiveJob(): ImportJob? =
        jobs.values.firstOrNull {
            it.status != ImportJobStatus.Completed && it.status != ImportJobStatus.Failed
        }
}
