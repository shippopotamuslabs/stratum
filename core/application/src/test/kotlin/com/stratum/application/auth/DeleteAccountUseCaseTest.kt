package com.stratum.application.auth

import com.stratum.application.sync.CloudSyncDiagnostic
import com.stratum.core.testing.auth.FakeAccountDeletionPort
import com.stratum.core.testing.auth.FakeAuthManager
import com.stratum.core.testing.sync.FakeCloudSyncPort
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

class DeleteAccountUseCaseTest {

    @Test
    @DisplayName("Given authenticated when delete then stop, data, wipe, auth, sign out")
    fun deleteSucceeds() = runTest {
        val user = UserProfile("uid-1", "A", "a@example.com", null)
        val auth = FakeAuthManager(AuthState.Authenticated(user))
        val remote = FakeAccountDeletionPort()
        val port = FakeCloudSyncPort()
        val subject = buildSubject(auth, remote, port)

        val result = subject()

        assertEquals(DeleteAccountResult.Success, result)
        assertEquals(1, remote.deleteDataCount)
        assertEquals(1, remote.deleteAuthCount)
        assertEquals(listOf("stop", "wipe"), port.callOrder)
        assertEquals("uid-1", port.lastWipedUserId)
        assertEquals(1, auth.signOutCount)
        assertEquals(AuthState.Unauthenticated, auth.authState.value)
    }

    @Test
    @DisplayName("Given unauthenticated when delete then not-signed-in branch without side effects")
    fun deleteUnauthenticatedFails() = runTest {
        val auth = FakeAuthManager(AuthState.Unauthenticated)
        val remote = FakeAccountDeletionPort()
        val port = FakeCloudSyncPort()
        val subject = buildSubject(auth, remote, port)

        val result = subject()

        assertEquals(DeleteAccountResult.NotSignedIn, result)
        assertEquals(0, remote.deleteDataCount)
        assertEquals(0, remote.deleteAuthCount)
        assertEquals(emptyList<String>(), port.callOrder)
        assertEquals(0, auth.signOutCount)
    }

    @Test
    @DisplayName("Given remote data fails when delete then no wipe/auth and sync restarts")
    fun remoteDataFailureLeavesLocalIntactAndRestartsSync() = runTest {
        val user = UserProfile("uid-1", "A", "a@example.com", null)
        val auth = FakeAuthManager(AuthState.Authenticated(user))
        val remote = FakeAccountDeletionPort().apply {
            deleteDataFailure = IllegalStateException("cloud failed")
        }
        val port = FakeCloudSyncPort()
        val subject = buildSubject(auth, remote, port)

        val result = subject()

        assertTrue(result is DeleteAccountResult.RemoteDataFailed)
        // Restart succeeded, so no restart diagnostics ride the branch.
        assertEquals(
            emptyList<CloudSyncDiagnostic>(),
            (result as DeleteAccountResult.RemoteDataFailed).restartDiagnostics,
        )
        assertEquals(1, remote.deleteDataCount)
        assertEquals(0, remote.deleteAuthCount)
        assertEquals(listOf("stop", "start"), port.callOrder)
        assertEquals(0, auth.signOutCount)
        assertTrue(auth.authState.value is AuthState.Authenticated)
    }

    @Test
    @DisplayName("Given remote data fails and restart fails when delete then branch carries restart diagnostic")
    fun remoteDataFailureAndRestartFailureCarriesDiagnostic() = runTest {
        val user = UserProfile("uid-1", "A", "a@example.com", null)
        val auth = FakeAuthManager(AuthState.Authenticated(user))
        val remote = FakeAccountDeletionPort().apply {
            deleteDataFailure = IllegalStateException("cloud failed")
        }
        val restartError = IllegalStateException("restart failed")
        val port = FakeCloudSyncPort().apply {
            startFailure = restartError
        }
        val subject = buildSubject(auth, remote, port)

        val result = subject()

        assertTrue(result is DeleteAccountResult.RemoteDataFailed)
        val diagnostics = (result as DeleteAccountResult.RemoteDataFailed).restartDiagnostics
        assertEquals(1, diagnostics.size)
        assertSame(
            restartError,
            (diagnostics.single() as CloudSyncDiagnostic.OperationFailed).cause,
        )
        assertEquals(1, remote.deleteDataCount)
        assertEquals(0, remote.deleteAuthCount)
        assertEquals(0, auth.signOutCount)
        assertTrue(auth.authState.value is AuthState.Authenticated)
    }

    @Test
    @DisplayName("Given restart throws cancellation when delete then cancellation propagates")
    fun restartCancellationPropagates() = runTest {
        val user = UserProfile("uid-1", "A", "a@example.com", null)
        val auth = FakeAuthManager(AuthState.Authenticated(user))
        val remote = FakeAccountDeletionPort().apply {
            deleteDataFailure = IllegalStateException("cloud failed")
        }
        val port = FakeCloudSyncPort().apply {
            startFailure = CancellationException("restart cancelled")
        }
        val subject = buildSubject(auth, remote, port)

        var thrown: Throwable? = null
        try {
            subject()
        } catch (error: CancellationException) {
            thrown = error
        }

        assertTrue(thrown is CancellationException)
        assertEquals("restart cancelled", thrown?.message)
        assertEquals(1, remote.deleteDataCount)
        assertEquals(0, remote.deleteAuthCount)
    }

    @Test
    @DisplayName("Given wipe fails after remote data when delete then local cleanup branch")
    fun wipeFailureAfterRemoteData() = runTest {
        val user = UserProfile("uid-1", "A", "a@example.com", null)
        val auth = FakeAuthManager(AuthState.Authenticated(user))
        val remote = FakeAccountDeletionPort()
        val port = FakeCloudSyncPort().apply {
            wipeFailure = IllegalStateException("wipe failed")
        }
        val subject = buildSubject(auth, remote, port)

        val result = subject()

        assertEquals(DeleteAccountResult.LocalCleanupFailed, result)
        assertEquals(1, remote.deleteDataCount)
        assertEquals(0, remote.deleteAuthCount)
        // Must not restart sync after cloud data is already gone (would re-upload).
        assertEquals(listOf("stop", "wipe"), port.callOrder)
        assertEquals(0, auth.signOutCount)
        assertTrue(auth.authState.value is AuthState.Authenticated)
    }

    @Test
    @DisplayName("Given auth delete fails after wipe when delete then auth branch and no sign out")
    fun authDeleteFailureAfterWipe() = runTest {
        val user = UserProfile("uid-1", "A", "a@example.com", null)
        val auth = FakeAuthManager(AuthState.Authenticated(user))
        val remote = FakeAccountDeletionPort().apply {
            deleteAuthFailure = IllegalStateException("auth delete failed")
        }
        val port = FakeCloudSyncPort()
        val subject = buildSubject(auth, remote, port)

        val result = subject()

        assertEquals(DeleteAccountResult.AuthDeleteFailed, result)
        assertEquals(1, remote.deleteDataCount)
        assertEquals(1, remote.deleteAuthCount)
        assertEquals(listOf("stop", "wipe"), port.callOrder)
        assertEquals(0, auth.signOutCount)
    }

    @Test
    @DisplayName("Given signOut fails after auth delete when delete then still success")
    fun signOutFailureAfterAuthDeleteIsBestEffort() = runTest {
        val user = UserProfile("uid-1", "A", "a@example.com", null)
        val auth = FakeAuthManager(AuthState.Authenticated(user)).apply {
            signOutFailure = IllegalStateException("credential clear failed")
        }
        val remote = FakeAccountDeletionPort()
        val port = FakeCloudSyncPort()
        val subject = buildSubject(auth, remote, port)

        val result = subject()

        assertEquals(DeleteAccountResult.Success, result)
        assertEquals(1, remote.deleteAuthCount)
        assertEquals(1, auth.signOutCount)
        assertEquals(listOf("stop", "wipe"), port.callOrder)
    }

    private fun buildSubject(
        auth: FakeAuthManager,
        remote: FakeAccountDeletionPort,
        port: FakeCloudSyncPort,
    ): DeleteAccountUseCase =
        DeleteAccountUseCase(
            authManager = auth,
            accountDeletionPort = remote,
            stopCloudSync = StopCloudSyncUseCase(port),
            startCloudSync = StartCloudSyncUseCase(port),
            endSessionAndWipe = EndSessionAndWipeUseCase(port),
        )
}
