package com.stratum.core.data.work

import androidx.work.ListenableWorker.Result
import com.stratum.core.testing.logging.FakeLoggingPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkerRetryPolicyTest {
    private val fakeLogging = FakeLoggingPort()

    @Test
    fun `runAttemptCount below maxRetries logs warning and returns retry`() {
        val error = RuntimeException("Temporary network timeout")
        val result =
            retryOrGiveUp(
                runAttemptCount = 1,
                maxRetries = 3,
                tag = "TestWorker",
                taskDescription = "Test task",
                error = error,
                loggingPort = fakeLogging,
            )

        assertEquals(Result.retry(), result)
        assertEquals(1, fakeLogging.warnings.size)
        assertEquals("TestWorker", fakeLogging.warnings.single().tag)
        assertTrue(fakeLogging.warnings.single().message.contains("Test task attempt 1 failed"))
        assertTrue(fakeLogging.warnings.single().message.contains("Temporary network timeout"))
        assertTrue(fakeLogging.errors.isEmpty())
    }

    @Test
    fun `runAttemptCount at maxRetries logs error without warning and returns failure`() {
        val error = RuntimeException("Permanent failure")
        val result =
            retryOrGiveUp(
                runAttemptCount = 3,
                maxRetries = 3,
                tag = "TestWorker",
                taskDescription = "Test task",
                error = error,
                loggingPort = fakeLogging,
            )

        assertEquals(Result.failure(), result)
        assertTrue(fakeLogging.warnings.isEmpty())
        assertEquals(1, fakeLogging.errors.size)
        assertEquals("TestWorker", fakeLogging.errors.single().tag)
        assertTrue(fakeLogging.errors.single().message.contains("Test task exceeded max retries (3)"))
        assertTrue(fakeLogging.errors.single().message.contains("Permanent failure"))
        assertSame(error, fakeLogging.errors.single().throwable)
    }

    @Test
    fun `runAttemptCount above maxRetries logs error and returns failure`() {
        val error = RuntimeException("Exceeded failure")
        val result =
            retryOrGiveUp(
                runAttemptCount = 4,
                maxRetries = 3,
                tag = "TestWorker",
                taskDescription = "Test task",
                error = error,
                loggingPort = fakeLogging,
            )

        assertEquals(Result.failure(), result)
        assertTrue(fakeLogging.warnings.isEmpty())
        assertEquals(1, fakeLogging.errors.size)
    }
}
