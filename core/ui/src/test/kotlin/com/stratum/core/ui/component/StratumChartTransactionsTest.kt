package com.stratum.core.ui.component

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class StratumChartTransactionsTest {
    @Test
    fun `runCatchingChartModel reports failure and invokes onFailure`() = runTest {
        val reported = mutableListOf<Throwable>()
        val reporter = UiRenderErrorReporter { reported += it }
        val failure = IllegalStateException("bad series")
        var failed = false

        runCatchingChartModel(reporter, onFailure = { failed = true }) {
            throw failure
        }

        assertTrue(failed)
        assertEquals(1, reported.size)
        assertSame(failure, reported.single())
    }

    @Test
    fun `runCatchingChartModel rethrows cancellation without reporting`() = runTest {
        val reported = mutableListOf<Throwable>()
        val reporter = UiRenderErrorReporter { reported += it }
        var failed = false

        assertThrows<CancellationException> {
            runCatchingChartModel(reporter, onFailure = { failed = true }) {
                throw CancellationException("cancelled")
            }
        }

        assertTrue(reported.isEmpty())
        assertFalse(failed)
    }
}
