@file:Suppress("ForbiddenMockKInTests") // Firestore SDK boundary — DocumentReference is a concrete class, no fake available.

package com.stratum.solver

import com.google.api.core.ApiFuture
import com.google.cloud.firestore.DocumentReference
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.WriteResult
import io.mockk.every
import io.mockk.mockk
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
 * as job failures. Other failures propagate untouched. Covers both the
 * helper and the store callers (claim/updateIfPresent must not re-wrap).
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

    @Test
    fun `claim propagates interrupt unwrapped, not as job failure`() {
        val firestore = mockk<Firestore>()
        every { firestore.document(any()) } returns mockk()
        every {
            firestore.runTransaction<SynthesisJobStore.Claim>(any())
        } returns FakeFuture(interrupt = true)
        Thread.interrupted() // Clear any pre-existing flag.
        assertThrows<InterruptedException> {
            FirestoreSynthesisJobStore(firestore).claim("uid", "job")
        }
        assertTrue(Thread.currentThread().isInterrupted, "interrupt flag must be restored")
        Thread.interrupted() // Clean up for other tests.
    }

    @Test
    fun `updateIfPresent propagates interrupt unwrapped`() {
        val docRef = mockk<DocumentReference>()
        every { docRef.update(any<Map<String, Any>>()) } returns FakeFuture<WriteResult>(interrupt = true)
        val firestore = mockk<Firestore>()
        every { firestore.document(any()) } returns docRef
        Thread.interrupted() // Clear any pre-existing flag.
        assertThrows<InterruptedException> {
            FirestoreSynthesisJobStore(firestore).updateIfPresent("uid", "job", mapOf("a" to 1))
        }
        assertTrue(Thread.currentThread().isInterrupted, "interrupt flag must be restored")
        Thread.interrupted() // Clean up for other tests.
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
