package com.stratum.domain.logging

import com.stratum.core.testing.logging.FakeLoggingPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class LogErrorUseCaseTest {
    private val fakeLogging = FakeLoggingPort()
    private val useCase = LogErrorUseCase(fakeLogging)

    @Test
    fun `invoke logs error with message and throwable`() {
        val throwable = IllegalStateException("Disk full")
        useCase("FileStorage", "Failed writing export", throwable)

        assertEquals(1, fakeLogging.errors.size)
        val entry = fakeLogging.errors.single()
        assertEquals("FileStorage", entry.tag)
        assertEquals("Failed writing export", entry.message)
        assertSame(throwable, entry.throwable)
    }

    @Test
    fun `invoke logs error without throwable when omitted`() {
        useCase("Sync", "Network timeout")

        assertEquals(1, fakeLogging.errors.size)
        val entry = fakeLogging.errors.single()
        assertEquals("Sync", entry.tag)
        assertEquals("Network timeout", entry.message)
        assertNull(entry.throwable)
    }
}
