package com.stratum.telemetry

import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.subscription.FakeEntitlementRepository
import com.stratum.core.testing.telemetry.FakeCrashTelemetryPort
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.subscription.EntitlementSource
import com.stratum.domain.subscription.EntitlementState
import com.stratum.domain.subscription.ObserveEntitlementUseCase
import com.stratum.domain.telemetry.TIER_COMPLIMENTARY
import com.stratum.domain.telemetry.TIER_NOT_ENTITLED
import com.stratum.domain.training.ObserveActiveSessionUseCase
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
class TelemetryContextListenerTest {
    private val entitlementRepository = FakeEntitlementRepository()
    private val trainingLog = FakeTrainingLogRepository()
    private val fakeTelemetry = FakeCrashTelemetryPort()

    private fun createListener(scope: CoroutineScope) =
        TelemetryContextListener(
            observeEntitlement = ObserveEntitlementUseCase(entitlementRepository),
            observeActiveSession = ObserveActiveSessionUseCase(trainingLog),
            crashTelemetryPort = fakeTelemetry,
            loggingPort = FakeLoggingPort(),
            scope = scope,
        )

    @Test
    fun `entitlement changes set subscription tier on crashTelemetryPort`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val listener = createListener(scope)
            listener.start()

            entitlementRepository.setState(
                EntitlementState.Entitled(EntitlementSource.ComplimentaryAccess),
            )

            assertEquals(TIER_COMPLIMENTARY, fakeTelemetry.subscriptionTier)
            scope.cancel()
        }

    @Test
    fun `active session sets workout id and clears when session ends`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val listener = createListener(scope)
            listener.start()
            assertNull(fakeTelemetry.activeWorkoutId)

            val session = WorkoutLoggingFixtures.inProgressSession(id = "ts-1")
            trainingLog.seedSession(session)

            assertEquals(session.workoutId?.value, fakeTelemetry.activeWorkoutId)

            trainingLog.deleteSession(session.id)

            assertNull(fakeTelemetry.activeWorkoutId)
            scope.cancel()
        }

    @Test
    fun `not entitled maps to not_entitled tier`() =
        runTest {
            entitlementRepository.setState(EntitlementState.NotEntitled())
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val listener = createListener(scope)
            listener.start()

            assertEquals(TIER_NOT_ENTITLED, fakeTelemetry.subscriptionTier)
            scope.cancel()
        }

    @Test
    fun `repeated start calls do not launch duplicate collectors`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val listener = createListener(scope)
            listener.start()
            listener.start()

            entitlementRepository.setState(
                EntitlementState.Entitled(EntitlementSource.GooglePlaySubscription),
            )

            assertEquals("play_subscription", fakeTelemetry.subscriptionTier)
            scope.cancel()
        }
}
