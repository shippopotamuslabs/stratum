package com.stratum.domain.importing

import com.stratum.core.testing.importing.FakeImportJobRepository
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class StartHistoricalImportUseCaseTest {
    @Test
    @DisplayName("Given no active job when started then creates a pending job")
    fun createsFreshJobWhenNoneActive() = runTest {
        val jobRepo = FakeImportJobRepository()
        val subject = StartHistoricalImportUseCase(jobRepo)

        val job = subject().getOrThrow()

        assertEquals(ImportJobStatus.Pending, job.status)
        assertEquals(job, jobRepo.getActiveJob())
    }

    @Test
    @DisplayName("Given stuck active job when started then fails it and creates a new job id")
    fun failsStuckActiveJobAndCreatesNew() = runTest {
        val jobRepo = FakeImportJobRepository()
        val stuck = jobRepo.createJob().copy(
            status = ImportJobStatus.AgentRunning,
            updatedAt = Instant.parse("2025-01-01T00:00:00Z"),
        )
        jobRepo.updateJob(stuck)
        val subject = StartHistoricalImportUseCase(jobRepo)

        val job = subject().getOrThrow()

        assertNotEquals(stuck.id, job.id)
        assertEquals(ImportJobStatus.Pending, job.status)
        assertEquals(ImportJobStatus.Failed, jobRepo.getJob(stuck.id)?.status)
        assertEquals("Superseded by a new import.", jobRepo.getJob(stuck.id)?.errorMessage)
        assertEquals(job.id, jobRepo.getActiveJob()?.id)
    }

    @Test
    @DisplayName("Given terminal jobs only when started then leaves them and creates a new job")
    fun ignoresTerminalJobs() = runTest {
        val jobRepo = FakeImportJobRepository()
        val completed = jobRepo.createJob().copy(status = ImportJobStatus.Completed)
        jobRepo.updateJob(completed)
        val subject = StartHistoricalImportUseCase(jobRepo)

        val job = subject().getOrThrow()

        assertNotEquals(completed.id, job.id)
        assertEquals(ImportJobStatus.Completed, jobRepo.getJob(completed.id)?.status)
        assertTrue(jobRepo.getActiveJob()?.id == job.id)
        assertNull(jobRepo.allJobs().firstOrNull { it.id == completed.id && it.status != ImportJobStatus.Completed })
    }
}
