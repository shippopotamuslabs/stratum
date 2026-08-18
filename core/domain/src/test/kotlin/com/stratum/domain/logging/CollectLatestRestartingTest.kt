package com.stratum.domain.logging

import com.stratum.core.testing.logging.FakeLoggingPort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CollectLatestRestartingTest {
    private val fakeLogging = FakeLoggingPort()

    @Test
    fun `collects emissions and stops when upstream completes`() = runTest {
        val values = mutableListOf<Int>()
        flow {
            emit(1)
            emit(2)
        }.collectLatestRestarting(fakeLogging, "Listener") { value ->
            values += value
        }

        assertEquals(listOf(1, 2), values)
        assertTrue(fakeLogging.errors.isEmpty())
    }

    @Test
    fun `logs and restarts after an unexpected throw`() = runTest {
        var attempts = 0
        val values = mutableListOf<String>()
        flow {
            attempts += 1
            if (attempts == 1) throw IllegalStateException("collector boom")
            emit("recovered")
        }.collectLatestRestarting(
            loggingPort = fakeLogging,
            tag = "Listener",
            restartDelayMs = 1_000L,
        ) { value ->
            values += value
        }

        assertEquals(listOf("recovered"), values)
        assertEquals(1, fakeLogging.errors.size)
        assertEquals("Listener", fakeLogging.errors.single().tag)
        assertTrue(fakeLogging.errors.single().message.contains("collector boom"))
    }
}
