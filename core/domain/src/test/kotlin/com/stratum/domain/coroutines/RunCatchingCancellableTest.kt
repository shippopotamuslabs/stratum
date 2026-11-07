package com.stratum.domain.coroutines

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RunCatchingCancellableTest {

    @Test
    fun `success passes through as a success result`() = runTest {
        val result = runCatchingCancellable { 42 }

        assertTrue(result.isSuccess)
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun `non-cancellation failure returns a failure result with the same throwable`() = runTest {
        val failure = IllegalStateException("Connection refused")

        val result = runCatchingCancellable { throw failure }

        assertTrue(result.isFailure)
        assertSame(failure, result.exceptionOrNull())
    }

    @Test
    fun `CancellationException propagates as the same instance`() = runTest {
        val cancellation = CancellationException("cancelled")

        val thrown = assertThrows<CancellationException> {
            runCatchingCancellable { throw cancellation }
        }

        assertSame(cancellation, thrown)
    }
}
