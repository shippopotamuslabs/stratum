package com.stratum.solver

import com.google.api.core.ApiFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Interrupt hygiene for Firestore futures (STR-52 review): interrupts
 * restore the flag and propagate unwrapped instead of being misclassified
 * as job failures. Other failures propagate untouched.
 */
class SynthesisJobStoreTest {
    @Test
    fun `value future returns`() {
        assertEquals("ok", getPreservingInterrupt(FakeFuture(value = "ok")))
    }

    @Test
    fun `interrupted future restores flag and propagates`() {
        Thread.interrupted() // Clear any pre-existing flag.
        assertThrows<InterruptedException> {
            getPreservingInterrupt(FakeFuture<String>(interrupt = true))
        }
        assertTrue(Thread.currentThread().isInterrupted, "interrupt flag must be restored")
        Thread.interrupted() // Clean up for other tests.
    }

    @Test
    fun `failed future propagates execution exception untouched`() {
        val cause = IllegalStateException("boom")
        val thrown =
            assertThrows<ExecutionException> {
                getPreservingInterrupt(FakeFuture<String>(failure = cause))
            }
        assertEquals(cause, thrown.cause)
    }

    private class FakeFuture<T>(
        private val value: T? = null,
        private val interrupt: Boolean = false,
        private val failure: Throwable? = null,
    ) : ApiFuture<T> {
        override fun get(): T {
            if (interrupt) throw InterruptedException("test interrupt")
            failure?.let { throw ExecutionException(it) }
            @Suppress("UNCHECKED_CAST")
            return value as T
        }

        override fun get(timeout: Long, unit: TimeUnit): T = get()

        override fun isDone(): Boolean = true

        override fun isCancelled(): Boolean = false

        override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false

        override fun addListener(listener: Runnable, executor: Executor): Unit =
            throw UnsupportedOperationException()
    }
}
