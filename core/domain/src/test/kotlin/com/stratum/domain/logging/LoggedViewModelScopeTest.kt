package com.stratum.domain.logging

import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.logging.FakeLoggingPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
class LoggedViewModelScopeTest {
    @Test
    fun `loggedViewModelScope records unhandled child failures without cancelling siblings`() = runTest {
        val fakeLogging = FakeLoggingPort()
        val logError = LogErrorUseCase(fakeLogging)
        val scope = loggedViewModelScope("WorkoutViewModel", logError)

        val failure = IllegalStateException("boom")
        scope.launch { throw failure }.join()
        var siblingRan = false
        scope.launch { siblingRan = true }.join()

        assertTrue(siblingRan)
        assertEquals(1, fakeLogging.errors.size)
        assertEquals("WorkoutViewModel", fakeLogging.errors.single().tag)
        assertSame(failure, fakeLogging.errors.single().throwable)
        assertTrue(fakeLogging.errors.single().message.contains("boom"))
    }

    @Test
    fun `launchLogged logs unexpected failures`() = runTest {
        val fakeLogging = FakeLoggingPort()
        val logError = LogErrorUseCase(fakeLogging)
        val scope = CoroutineScope(coroutineContext)
        val failure = IllegalStateException("disk")

        scope.launchLogged(logError, "Export", "Write failed") {
            throw failure
        }.join()

        assertEquals(1, fakeLogging.errors.size)
        assertEquals("Export", fakeLogging.errors.single().tag)
        assertEquals("Write failed: disk", fakeLogging.errors.single().message)
        assertSame(failure, fakeLogging.errors.single().throwable)
    }
}
