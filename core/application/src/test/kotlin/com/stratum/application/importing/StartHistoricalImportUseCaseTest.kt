package com.stratum.application.importing

import com.stratum.core.testing.importing.FakeImportJobRepository
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.importing.ImportJob
import com.stratum.domain.importing.ImportJobStatus
import kotlinx.coroutines.CancellationException

class StartHistoricalImportUseCaseTest {
    @Test
    @DisplayName("Given no active job when started then creates a pending job")
    fun createsFreshJobWhenNoneActive() = runTest {
        val jobRepo = FakeImportJobRepository()
        val subject = StartHistoricalImportUseCase(jobRepo)

        val job = (subject() as StartHistoricalImportOutcome.Started).job

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

        val job = (subject() as StartHistoricalImportOutcome.Started).job

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

        val job = (subject() as StartHistoricalImportOutcome.Started).job

        assertNotEquals(completed.id, job.id)
        assertEquals(ImportJobStatus.Completed, jobRepo.getJob(completed.id)?.status)
        assertTrue(jobRepo.getActiveJob()?.id == job.id)
        assertNull(jobRepo.allJobs().firstOrNull { it.id == completed.id && it.status != ImportJobStatus.Completed })
    }

    @Test
    @DisplayName("Given a failing job repository when started then Failed carries the failure diagnostic")
    fun returnsFailedWhenRepositoryThrows() = runTest {
        val failure = IllegalStateException("room down")
        val jobRepo = object : ImportJobRepository by FakeImportJobRepository() {
            override suspend fun createJob(): ImportJob = throw failure
        }
        val subject = StartHistoricalImportUseCase(jobRepo)

        // STR-237: the record rides the branch as a diagnostic (legacy tag +
        // rendered message, real throwable as cause); the boundary drains it
        // exactly once.
        val failed = subject() as StartHistoricalImportOutcome.Failed
        assertEquals(1, failed.diagnostics.size)
        assertEquals("StartHistoricalImport", failed.diagnostics.single().tag)
        assertEquals("Failed to start historical import: room down", failed.diagnostics.single().message)
        assertEquals(failure, failed.diagnostics.single().cause)
    }

    @Test
    @DisplayName("Given a cancellation from the repository when started then it propagates")
    fun cancellationPropagates() = runTest {
        val jobRepo = object : ImportJobRepository by FakeImportJobRepository() {
            override suspend fun createJob(): ImportJob = throw CancellationException("cancelled")
        }
        val subject = StartHistoricalImportUseCase(jobRepo)

        var thrown: Throwable? = null
        try {
            subject()
        } catch (cancelled: CancellationException) {
            thrown = cancelled
        }

        assertEquals("cancelled", thrown?.message)
    }
}
