package com.stratum.domain.auth

import com.stratum.core.testing.auth.FakeAuthManager
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LoginWithGoogleUseCaseTest {
    @Test
    fun `sign in returns authenticated profile`() = runTest {
    val fake = FakeAuthManager()
    val subject = LoginWithGoogleUseCase(fake)
    val result = subject()
    assertTrue(result.isSuccess)
  }
}
