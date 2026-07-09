package com.stratum.domain.logging

import com.stratum.core.testing.logging.FakeLoggingPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ProcessErrorSafetyNetTest {
    private val fakeLogging = FakeLoggingPort()
    private val safetyNet = ProcessErrorSafetyNet(fakeLogging)
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    @BeforeEach
    fun captureHandler() {
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
    }

    @AfterEach
    fun restoreHandler() {
        Thread.setDefaultUncaughtExceptionHandler(previousHandler)
    }

    @Test
    @DisplayName("uncaught handler logs via LoggingPort then chains to previous handler")
    fun uncaughtHandlerLogsAndChains() {
        var chained: Throwable? = null
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            chained = throwable
        }

        safetyNet.installUncaughtExceptionHandler()
        val failure = IllegalStateException("boom")
        Thread.getDefaultUncaughtExceptionHandler()!!
            .uncaughtException(Thread.currentThread(), failure)

        assertEquals(1, fakeLogging.errors.size)
        assertEquals("Uncaught", fakeLogging.errors.single().tag)
        assertSame(failure, fakeLogging.errors.single().throwable)
        assertSame(failure, chained)
    }

    @Test
    @DisplayName("application CoroutineExceptionHandler logs unhandled child failures")
    fun applicationScopeHandlerLogs() = runTest {
        val scope = CoroutineScope(
            SupervisorJob() + safetyNet.applicationCoroutineExceptionHandler(),
        )
        val failure = RuntimeException("async boom")
        scope.launch { throw failure }.join()

        assertEquals(1, fakeLogging.errors.size)
        assertEquals("ApplicationScope", fakeLogging.errors.single().tag)
        assertSame(failure, fakeLogging.errors.single().throwable)
        assertTrue(fakeLogging.errors.single().message.contains("async boom"))
    }
}
