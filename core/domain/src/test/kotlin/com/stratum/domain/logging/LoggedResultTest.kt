package com.stratum.domain.logging

import com.stratum.core.testing.logging.FakeLoggingPort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.stratum.domain.lift.Equipment

class LoggedResultTest {
    private val fakeLogging = FakeLoggingPort()

    @Test
    fun `runCatchingLogged returns success result and does not log`() = runTest {
        val result = runCatchingLogged(fakeLogging, "Repo", "Fetch failed") {
            42
        }

        assertTrue(result.isSuccess)
        assertEquals(42, result.getOrNull())
        assertTrue(fakeLogging.errors.isEmpty())
    }

    @Test
    fun `runCatchingLogged captures exception, logs error, and returns failure result`() = runTest {
        val failure = IllegalStateException("Connection refused")
        val result = runCatchingLogged(fakeLogging, "Repo", "Fetch failed") {
            throw failure
        }

        assertTrue(result.isFailure)
        assertSame(failure, result.exceptionOrNull())
        assertEquals(1, fakeLogging.errors.size)
        val entry = fakeLogging.errors.single()
        assertEquals("Repo", entry.tag)
        assertEquals("Fetch failed: Connection refused", entry.message)
        assertSame(failure, entry.throwable)
    }

    @Test
    fun `expectedFailure logs warning breadcrumb and returns failure without error`() {
        val failure = IllegalStateException("Training session 1 not found")
        val result: Result<String> = fakeLogging.expectedFailure("UseCase", failure)

        assertTrue(result.isFailure)
        assertSame(failure, result.exceptionOrNull())
        assertTrue(fakeLogging.errors.isEmpty())
        assertEquals(1, fakeLogging.warnings.size)
        val entry = fakeLogging.warnings.single()
        assertEquals("UseCase", entry.tag)
        assertEquals("Training session 1 not found", entry.message)
    }

    @Test
    fun `expectedFailure supports distinct breadcrumb message`() {
        val failure = IllegalArgumentException("Equipment not found")
        val result: Result<String> =
            fakeLogging.expectedFailure("UseCase", failure, "Equipment e1 not found for remedy")

        assertTrue(result.isFailure)
        assertSame(failure, result.exceptionOrNull())
        assertTrue(fakeLogging.errors.isEmpty())
        assertEquals("Equipment e1 not found for remedy", fakeLogging.warnings.single().message)
    }

    @Test
    fun `runCatchingLogged rethrows CancellationException without logging`() = runTest {
        org.junit.jupiter.api.assertThrows<kotlinx.coroutines.CancellationException> {
            runCatchingLogged(fakeLogging, "Repo", "Fetch failed") {
                throw kotlinx.coroutines.CancellationException("cancelled")
            }
        }

        assertTrue(fakeLogging.errors.isEmpty())
    }
}
