package com.stratum.sync

import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.auth.FakeAuthManager
import com.stratum.core.testing.sync.FakeCloudSyncPort
import com.stratum.domain.auth.AuthState
import com.stratum.domain.auth.GetAuthStateUseCase
import com.stratum.domain.auth.UserProfile
import com.stratum.domain.sync.StartCloudSyncUseCase
import com.stratum.domain.sync.StopCloudSyncUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class CloudSyncAuthListenerTest {
    private val authManager = FakeAuthManager()
    private val cloudSyncPort = FakeCloudSyncPort()

    private fun createListener(scope: CoroutineScope) =
        CloudSyncAuthListener(
            getAuthState = GetAuthStateUseCase(authManager),
            startCloudSync = StartCloudSyncUseCase(cloudSyncPort),
            stopCloudSync = StopCloudSyncUseCase(cloudSyncPort),
            scope = scope,
        )

    @Test
    fun `authenticated state starts cloud sync with user ID`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val listener = createListener(scope)
            listener.start()

            val user = UserProfile("user-abc", "Test User", "test@example.com", null)
            authManager.setState(AuthState.Authenticated(user))

            assertEquals("user-abc", cloudSyncPort.startedUserId)
            scope.cancel()
        }

    @Test
    fun `unauthenticated state stops cloud sync`() =
        runTest {
            val user = UserProfile("user-abc", "Test User", "test@example.com", null)
            authManager.setState(AuthState.Authenticated(user))
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val listener = createListener(scope)
            listener.start()
            assertEquals("user-abc", cloudSyncPort.startedUserId)

            authManager.setState(AuthState.Unauthenticated)

            assertEquals(1, cloudSyncPort.stopCount)
            scope.cancel()
        }

    @Test
    fun `error auth state stops cloud sync`() =
        runTest {
            val user = UserProfile("user-abc", "Test User", "test@example.com", null)
            authManager.setState(AuthState.Authenticated(user))
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val listener = createListener(scope)
            listener.start()
            assertEquals("user-abc", cloudSyncPort.startedUserId)

            authManager.setState(AuthState.Error("Auth failed"))

            assertEquals(1, cloudSyncPort.stopCount)
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

            assertEquals("user-abc", cloudSyncPort.startedUserId)
            scope.cancel()
        }
}

