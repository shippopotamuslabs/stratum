package com.stratum.telemetry

import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.auth.FakeAuthManager
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.telemetry.FakeCrashTelemetryPort
import com.stratum.domain.auth.AuthState
import com.stratum.domain.auth.GetAuthStateUseCase
import com.stratum.domain.auth.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class TelemetryAuthListenerTest {
    private val authManager = FakeAuthManager()
    private val fakeTelemetry = FakeCrashTelemetryPort()

    private fun createListener(scope: CoroutineScope) =
        TelemetryAuthListener(
            getAuthState = GetAuthStateUseCase(authManager),
            crashTelemetryPort = fakeTelemetry,
            loggingPort = FakeLoggingPort(),
            scope = scope,
        )

    @Test
    fun `authenticated state sets user ID on crashTelemetryPort`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val listener = createListener(scope)
            listener.start()

            val user = UserProfile("user-abc", "Test User", "test@example.com", null)
            authManager.setState(AuthState.Authenticated(user))

            assertEquals("user-abc", fakeTelemetry.currentUserId)
            scope.cancel()
        }

    @Test
    fun `unauthenticated state clears user ID on crashTelemetryPort`() =
        runTest {
            val user = UserProfile("user-abc", "Test User", "test@example.com", null)
            authManager.setState(AuthState.Authenticated(user))
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val listener = createListener(scope)
            listener.start()
            assertEquals("user-abc", fakeTelemetry.currentUserId)

            authManager.setState(AuthState.Unauthenticated)

            assertNull(fakeTelemetry.currentUserId)
            scope.cancel()
        }

    @Test
    fun `error auth state clears user ID on crashTelemetryPort`() =
        runTest {
            val user = UserProfile("user-abc", "Test User", "test@example.com", null)
            authManager.setState(AuthState.Authenticated(user))
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val listener = createListener(scope)
            listener.start()
            assertEquals("user-abc", fakeTelemetry.currentUserId)

            authManager.setState(AuthState.Error("Auth failed"))

            assertNull(fakeTelemetry.currentUserId)
            scope.cancel()
        }

    @Test
    fun `repeated start calls do not launch duplicate collectors`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val listener = createListener(scope)
            listener.start()
            listener.start()

            val user = UserProfile("user-abc", "Test User", "test@example.com", null)
            authManager.setState(AuthState.Authenticated(user))

            assertEquals("user-abc", fakeTelemetry.currentUserId)
            scope.cancel()
        }
}
