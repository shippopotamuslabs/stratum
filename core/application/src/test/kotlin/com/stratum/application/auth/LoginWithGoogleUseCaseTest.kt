package com.stratum.application.auth

import com.stratum.core.testing.auth.FakeAuthManager
import com.stratum.domain.auth.AuthState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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
            signInFailure =
                GoogleSignInException(SignInFailureKind.Network, "network unreachable")
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
        val fake = FakeAuthManager().apply {
            signInFailure = IllegalStateException("unexpected boom")
        }
        val subject = LoginWithGoogleUseCase(fake)
        val result = subject()
        assertEquals(
            LoginWithGoogleResult.Failed(SignInFailureKind.Other, "unexpected boom"),
            result,
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
