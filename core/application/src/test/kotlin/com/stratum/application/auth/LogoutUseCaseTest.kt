package com.stratum.application.auth

import com.stratum.application.sync.CloudSyncDiagnostic
import com.stratum.core.testing.auth.FakeAuthManager
import com.stratum.core.testing.sync.FakeCloudSyncPort
import com.stratum.domain.sync.CloudSessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.application.sync.EndSessionAndWipeUseCase
import com.stratum.application.sync.StartCloudSyncUseCase
import com.stratum.application.sync.StopCloudSyncUseCase
import com.stratum.domain.auth.AuthState
import com.stratum.domain.auth.UserProfile

class LogoutUseCaseTest {

    @Test
    @DisplayName("Given authenticated user when logout then wipe then sign out")
    fun logoutWipesAndSignsOut() = runTest {
        val user = UserProfile("uid-1", "A", "a@example.com", null)
        val auth = FakeAuthManager(AuthState.Authenticated(user))
        val port = FakeCloudSyncPort()
        val subject = buildSubject(auth, port)

        val result = subject()

        assertEquals(LogoutResult.Success, result)
        assertEquals(listOf("wipe"), port.callOrder)
        assertEquals("uid-1", port.lastWipedUserId)
        assertEquals(1, port.wipeCount)
        assertEquals(1, auth.signOutCount)
        assertEquals(AuthState.Unauthenticated, auth.authState.value)
        assertEquals(CloudSessionState.Inactive, port.sessionState.value)
    }

    @Test
    @DisplayName("Given unauthenticated when logout then stop and sign out without wipe")
    fun logoutUnauthenticatedStopsOnly() = runTest {
        val auth = FakeAuthManager(AuthState.Unauthenticated)
        val port = FakeCloudSyncPort()
        val subject = buildSubject(auth, port)

        val result = subject()

        assertEquals(LogoutResult.Success, result)
        assertEquals(listOf("stop"), port.callOrder)
        assertEquals(0, port.wipeCount)
        assertEquals(1, auth.signOutCount)
    }

    @Test
    @DisplayName("Given wipe fails when logout then sign out is not called and sync restarts")
    fun wipeFailureRestartsSync() = runTest {
        val user = UserProfile("uid-1", "A", "a@example.com", null)
        val auth = FakeAuthManager(AuthState.Authenticated(user))
        val wipeError = IllegalStateException("wipe failed")
        val port = FakeCloudSyncPort().apply {
            wipeFailure = wipeError
        }
        val subject = buildSubject(auth, port)

        val result = subject()

        assertTrue(result is LogoutResult.WipeFailed)
        // Restart succeeded, so no restart diagnostics ride the branch.
        assertEquals(
            emptyList<CloudSyncDiagnostic>(),
            (result as LogoutResult.WipeFailed).restartDiagnostics,
        )
        // STR-238: the primary wipe failure's diagnostics ride the branch,
        // carrying the real cause for the boundary's exactly-once drain.
        val wipeDiagnostics = (result as LogoutResult.WipeFailed).wipeDiagnostics
        assertEquals(1, wipeDiagnostics.size)
        assertSame(
            wipeError,
            wipeDiagnostics.single().cause,
        )
        assertEquals(listOf("wipe", "start"), port.callOrder)
        assertEquals(0, auth.signOutCount)
        assertTrue(auth.authState.value is AuthState.Authenticated)
        assertTrue(port.sessionState.value is CloudSessionState.Ready)
    }

    @Test
    @DisplayName("Given sign-out fails after wipe when logout then sync restarts")
    fun signOutFailureRestartsSync() = runTest {
        val user = UserProfile("uid-1", "A", "a@example.com", null)
        val signOutError = IllegalStateException("sign-out failed")
        val auth = FakeAuthManager(AuthState.Authenticated(user)).apply {
            signOutFailure = signOutError
        }
        val port = FakeCloudSyncPort()
        val subject = buildSubject(auth, port)

        val result = subject()

        assertTrue(result is LogoutResult.SignOutFailed)
        assertEquals(
            emptyList<CloudSyncDiagnostic>(),
            (result as LogoutResult.SignOutFailed).restartDiagnostics,
        )
        // STR-238: the primary sign-out failure's diagnostics ride the
        // branch, carrying the real cause for the boundary's drain.
        val signOutDiagnostics = (result as LogoutResult.SignOutFailed).signOutDiagnostics
        assertEquals(1, signOutDiagnostics.size)
        assertSame(signOutError, signOutDiagnostics.single().cause)
        assertEquals(listOf("wipe", "start"), port.callOrder)
        assertEquals(1, auth.signOutCount)
        assertTrue(auth.authState.value is AuthState.Authenticated)
        assertTrue(port.sessionState.value is CloudSessionState.Ready)
    }

    @Test
    @DisplayName("Given wipe fails and restart fails when logout then branch carries restart diagnostic")
    fun wipeFailureAndRestartFailureCarriesDiagnostic() = runTest {
        val user = UserProfile("uid-1", "A", "a@example.com", null)
        val auth = FakeAuthManager(AuthState.Authenticated(user))
        val wipeError = IllegalStateException("wipe failed")
        val restartError = IllegalStateException("restart failed")
        val port = FakeCloudSyncPort().apply {
            wipeFailure = wipeError
            startFailure = restartError
        }
        val subject = buildSubject(auth, port)

        val result = subject()

        assertTrue(result is LogoutResult.WipeFailed)
        val diagnostics = (result as LogoutResult.WipeFailed).restartDiagnostics
        assertEquals(1, diagnostics.size)
        assertSame(
            restartError,
            (diagnostics.single() as CloudSyncDiagnostic.OperationFailed).cause,
        )
        // STR-238: the wipe failure's own diagnostics ride alongside the
        // restart's, each carrying its own real cause.
        val wipeDiagnostics = (result as LogoutResult.WipeFailed).wipeDiagnostics
        assertEquals(1, wipeDiagnostics.size)
        assertSame(
            wipeError,
            wipeDiagnostics.single().cause,
        )
        assertEquals(0, auth.signOutCount)
        assertTrue(auth.authState.value is AuthState.Authenticated)
    }

    @Test
    @DisplayName("Given sign-out fails and restart fails when logout then branch carries restart diagnostic")
    fun signOutFailureAndRestartFailureCarriesDiagnostic() = runTest {
        val user = UserProfile("uid-1", "A", "a@example.com", null)
        val signOutError = IllegalStateException("sign-out failed")
        val auth = FakeAuthManager(AuthState.Authenticated(user)).apply {
            signOutFailure = signOutError
        }
        val restartError = IllegalStateException("restart failed")
        val port = FakeCloudSyncPort().apply {
            startFailure = restartError
        }
        val subject = buildSubject(auth, port)

        val result = subject()

        assertTrue(result is LogoutResult.SignOutFailed)
        val diagnostics = (result as LogoutResult.SignOutFailed).restartDiagnostics
        assertEquals(1, diagnostics.size)
        assertSame(
            restartError,
            (diagnostics.single() as CloudSyncDiagnostic.OperationFailed).cause,
        )
        // STR-238: the sign-out failure's own diagnostics ride alongside
        // the restart's, each carrying its own real cause.
        val signOutDiagnostics = (result as LogoutResult.SignOutFailed).signOutDiagnostics
        assertEquals(1, signOutDiagnostics.size)
        assertSame(signOutError, signOutDiagnostics.single().cause)
        assertTrue(auth.authState.value is AuthState.Authenticated)
    }

    @Test
    @DisplayName("Given restart throws cancellation when logout then cancellation propagates")
    fun restartCancellationPropagates() = runTest {
        val user = UserProfile("uid-1", "A", "a@example.com", null)
        val auth = FakeAuthManager(AuthState.Authenticated(user))
        val port = FakeCloudSyncPort().apply {
            wipeFailure = IllegalStateException("wipe failed")
            startFailure = CancellationException("restart cancelled")
        }
        val subject = buildSubject(auth, port)

        var thrown: Throwable? = null
        try {
            subject()
        } catch (error: CancellationException) {
            thrown = error
        }

        assertTrue(thrown is CancellationException)
        assertEquals("restart cancelled", thrown?.message)
        assertEquals(0, auth.signOutCount)
    }

    private fun buildSubject(
        auth: FakeAuthManager,
        port: FakeCloudSyncPort,
    ): LogoutUseCase =
        LogoutUseCase(
            authManager = auth,
            endSessionAndWipe = EndSessionAndWipeUseCase(port),
            stopCloudSync = StopCloudSyncUseCase(port),
            startCloudSync = StartCloudSyncUseCase(port),
        )
}
