package com.stratum.core.data.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * R2 F4: LWW-loss adopt must rethrow CancellationException and never map it to adopt-failure
 * (which would force-clean dirty local).
 */
class LwwAdoptGuardTest {
    @Test
    @DisplayName("successful adopt returns true")
    fun successReturnsTrue() = runTest {
        var errors = 0
        val ok =
            runLwwAdopt(onError = { errors++ }) {
                true
            }
        assertTrue(ok)
        assertEquals(0, errors)
    }

    @Test
    @DisplayName("adopt returning false does not call onError")
    fun falseWithoutError() = runTest {
        var errors = 0
        val ok =
            runLwwAdopt(onError = { errors++ }) {
                false
            }
        assertFalse(ok)
        assertEquals(0, errors)
    }

    @Test
    @DisplayName("non-cancel Exception maps to false and invokes onError")
    fun exceptionMapsToFalse() = runTest {
        var seen: Exception? = null
        val ok =
            runLwwAdopt(onError = { seen = it }) {
                throw IllegalStateException("decode boom")
            }
        assertFalse(ok)
        assertTrue(seen is IllegalStateException)
    }

    @Test
    @DisplayName("CancellationException is rethrown — caller must not force-clean")
    fun cancellationRethrown() = runTest {
        var errors = 0
        val outcome =
            runCatching {
                runLwwAdopt(onError = { errors++ }) {
                    throw CancellationException("cancelled mid-adopt")
                }
            }
        val thrown = outcome.exceptionOrNull()
        assertInstanceOf(CancellationException::class.java, thrown)
        assertTrue(thrown!!.message!!.contains("cancelled"))
        assertEquals(0, errors, "onError must not run for CancellationException")
    }
}
