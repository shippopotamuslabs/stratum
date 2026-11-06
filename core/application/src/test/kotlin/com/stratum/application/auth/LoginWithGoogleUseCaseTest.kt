package com.stratum.application.auth

import com.stratum.application.diagnostics.OperationalDiagnostic
import com.stratum.core.testing.auth.FakeAuthManager
import com.stratum.domain.auth.AuthState
import com.stratum.domain.auth.SignInFailureKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LoginWithGoogleUseCaseTest {
    @Test
    fun `sign in returns authenticated profile`() = runTest {
        val fake = FakeAuthManager()
        val subject = LoginWithGoogleUseCase(fake)
        val result = subject()
        assertTrue(result is LoginWithGoogleResult.Success)
        assertEquals(
            "uid-test",
            (result as LoginWithGoogleResult.Success).profile.uid,
        )
        assertTrue(fake.authState.value is AuthState.Authenticated)
    }

    @Test
    fun `classified sign in failure returns failed branch with kind and message`() = runTest {
        val fake = FakeAuthManager().apply {
            signInFailureKind = SignInFailureKind.Network
            signInFailureMessage = "network unreachable"
        }
        val subject = LoginWithGoogleUseCase(fake)
        val result = subject()
        assertEquals(
            LoginWithGoogleResult.Failed(SignInFailureKind.Network, "network unreachable"),
            result,
        )
    }

    @Test
    fun `unclassified sign in failure returns other branch with message`() = runTest {
        val failure = IllegalStateException("unexpected boom")
        val fake = FakeAuthManager().apply {
            signInFailure = failure
        }
        val subject = LoginWithGoogleUseCase(fake)
        val result = subject()
        assertTrue(result is LoginWithGoogleResult.Failed)
        val failed = result as LoginWithGoogleResult.Failed
        assertEquals(SignInFailureKind.Other, failed.kind)
        assertEquals("unexpected boom", failed.message)
        // STR-238: the branch also carries the real cause as a §10
        // diagnostic for the boundary's exactly-once drain.
        val diagnostics = failed.diagnostics
        assertEquals(1, diagnostics.size)
        assertSame(failure, diagnostics.single().cause)
        assertEquals(
            OperationalDiagnostic(
                tag = "StratumGoogleAuth",
                message = "Google sign-in failed: unexpected boom",
                cause = failure,
            ),
            diagnostics.single(),
        )
    }

    @Test
    fun `sign in cancellation propagates`() = runTest {
        val fake = FakeAuthManager().apply {
            signInFailure = CancellationException("cancelled")
        }
        val subject = LoginWithGoogleUseCase(fake)
        var thrown: Throwable? = null
        try {
            subject()
        } catch (error: CancellationException) {
            thrown = error
        }
        assertTrue(thrown is CancellationException)
    }
}
