package com.stratum.subscription

import com.stratum.application.subscription.ObserveEntitlementDiagnosticsUseCase
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.subscription.FakeEntitlementRepository
import com.stratum.domain.subscription.EntitlementDiagnostic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class EntitlementDiagnosticsListenerTest {
    private val entitlementRepository = FakeEntitlementRepository()
    private val loggingPort = FakeLoggingPort()

    private fun createListener(scope: CoroutineScope) =
        EntitlementDiagnosticsListener(
            observeDiagnostics = ObserveEntitlementDiagnosticsUseCase(entitlementRepository),
            loggingPort = loggingPort,
            scope = scope,
        )

    @Test
    fun `play sync failure drains to error log exactly once`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val listener = createListener(scope)
            listener.start()

            val boom = IllegalStateException("sync boom")
            entitlementRepository.emitDiagnostic(EntitlementDiagnostic.PlaySyncFailed(boom))

            assertEquals(1, loggingPort.errors.size)
            assertEquals("SubscriptionRepo", loggingPort.errors.single().tag)
            assertEquals("Play entitlement sync failed", loggingPort.errors.single().message)
            assertSame(boom, loggingPort.errors.single().throwable)
            scope.cancel()
        }

    @Test
    fun `repeated start calls do not double-drain`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val listener = createListener(scope)
            listener.start()
            listener.start()

            val boom = IllegalStateException("sync boom")
            entitlementRepository.emitDiagnostic(EntitlementDiagnostic.PlaySyncFailed(boom))

            assertEquals(1, loggingPort.errors.size)
            scope.cancel()
        }
}
