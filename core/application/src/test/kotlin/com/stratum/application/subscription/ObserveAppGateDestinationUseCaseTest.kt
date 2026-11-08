package com.stratum.application.subscription

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.stratum.application.onboarding.ObserveOnboardingCompleteUseCase
import com.stratum.core.testing.onboarding.FakeOnboardingRepository
import com.stratum.core.testing.subscription.FakeEntitlementRepository
import com.stratum.domain.onboarding.OnboardingProgress
import com.stratum.domain.onboarding.OnboardingStep
import com.stratum.domain.subscription.EntitlementSource
import com.stratum.domain.subscription.EntitlementState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveAppGateDestinationUseCaseTest {

    private val fakeOnboarding = FakeOnboardingRepository()
    private val fakeEntitlement = FakeEntitlementRepository(EntitlementState.Loading)
    private lateinit var subject: ObserveAppGateDestinationUseCase

    @BeforeEach
    fun setup() {
        fakeOnboarding.seed(incompleteProgress())
        fakeEntitlement.setState(EntitlementState.Loading)
        subject = buildUseCase()
    }

    private fun buildUseCase(): ObserveAppGateDestinationUseCase =
        ObserveAppGateDestinationUseCase(
            observeOnboardingComplete = ObserveOnboardingCompleteUseCase(fakeOnboarding),
            observeEntitlement = ObserveEntitlementUseCase(fakeEntitlement),
        )

    @Test
    @DisplayName("Loading entitlement maps to Loading even when onboarding is complete")
    fun loadingEntitlementMapsToLoading() = runTest {
        fakeOnboarding.seed(completeProgress())
        fakeEntitlement.setState(EntitlementState.Loading)

        subject(emptyFlow()).test {
            awaitDestination(AppGateReadModel.Loading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("NotEntitled maps to Paywall regardless of onboarding completeness")
    fun notEntitledMapsToPaywall() = runTest {
        fakeEntitlement.setState(EntitlementState.NotEntitled())

        subject(emptyFlow()).test {
            awaitDestination(AppGateReadModel.Paywall)
            fakeOnboarding.seed(completeProgress())
            awaitDestination(AppGateReadModel.Paywall)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Entitled and incomplete onboarding maps to Onboarding for both sources")
    fun entitledIncompleteMapsToOnboarding() = runTest {
        fakeOnboarding.seed(incompleteProgress())
        fakeEntitlement.setState(
            EntitlementState.Entitled(EntitlementSource.GooglePlaySubscription),
        )

        subject(emptyFlow()).test {
            awaitDestination(AppGateReadModel.Onboarding)
            cancelAndIgnoreRemainingEvents()
        }

        fakeEntitlement.setState(
            EntitlementState.Entitled(EntitlementSource.ComplimentaryAccess),
        )

        subject(emptyFlow()).test {
            awaitDestination(AppGateReadModel.Onboarding)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Entitled and complete onboarding maps to Main")
    fun entitledCompleteMapsToMain() = runTest {
        fakeOnboarding.seed(completeProgress())
        fakeEntitlement.setState(
            EntitlementState.Entitled(EntitlementSource.GooglePlaySubscription),
        )

        subject(emptyFlow()).test {
            awaitDestination(AppGateReadModel.Main)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Becoming entitled while incomplete routes from Paywall to Onboarding")
    fun purchaseWhileIncompleteRoutesToOnboarding() = runTest {
        fakeOnboarding.seed(incompleteProgress())
        fakeEntitlement.setState(EntitlementState.NotEntitled())

        subject(emptyFlow()).test {
            awaitDestination(AppGateReadModel.Paywall)
            fakeEntitlement.setState(
                EntitlementState.Entitled(EntitlementSource.ComplimentaryAccess),
            )
            awaitDestination(AppGateReadModel.Onboarding)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Losing entitlement while in the app routes to Paywall")
    fun losingEntitlementRoutesToPaywall() = runTest {
        fakeOnboarding.seed(completeProgress())
        fakeEntitlement.setState(
            EntitlementState.Entitled(EntitlementSource.GooglePlaySubscription),
        )

        subject(emptyFlow()).test {
            awaitDestination(AppGateReadModel.Main)
            fakeEntitlement.setState(EntitlementState.NotEntitled())
            awaitDestination(AppGateReadModel.Paywall)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Entitlement flow failure maps to Failed and retry recovers")
    fun entitlementFlowFailureMapsToFailedThenRetry() = runTest {
        fakeOnboarding.seed(completeProgress())
        fakeEntitlement.setState(
            EntitlementState.Entitled(EntitlementSource.GooglePlaySubscription),
        )
        fakeEntitlement.throwOnSubscribe = IllegalStateException("billing down")
        val retries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        subject(retries).test {
            awaitDestination(AppGateReadModel.Failed)
            retries.tryEmit(Unit)
            awaitDestination(AppGateReadModel.Main)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Loading timeout maps to Failed")
    fun loadingTimeoutMapsToFailed() = runTest {
        fakeOnboarding.seed(completeProgress())
        fakeEntitlement.setState(EntitlementState.Loading)

        subject(emptyFlow(), loadingTimeoutMs = 1_000L).test {
            awaitDestination(AppGateReadModel.Loading)
            advanceTimeBy(1_000L)
            awaitDestination(AppGateReadModel.Failed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Entitlement emission failure after Main maps to Failed")
    fun entitlementEmissionFailureAfterMainMapsToFailed() = runTest {
        fakeOnboarding.seed(completeProgress())
        fakeEntitlement.setState(
            EntitlementState.Entitled(EntitlementSource.GooglePlaySubscription),
        )

        subject(emptyFlow()).test {
            awaitDestination(AppGateReadModel.Main)
            fakeEntitlement.failCollector(IllegalStateException("billing dropped"))
            awaitDestination(AppGateReadModel.Failed)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

private suspend fun ReceiveTurbine<AppGateReadModel>.awaitDestination(
    expected: AppGateReadModel,
) {
    while (true) {
        val item = awaitItem()
        if (item == AppGateReadModel.Loading && expected != AppGateReadModel.Loading) {
            continue
        }
        assertEquals(expected, item)
        return
    }
}

private fun incompleteProgress(): OnboardingProgress =
    OnboardingProgress(
        currentStep = OnboardingStep.Profile,
        startedAt = FakeOnboardingRepository.STARTED_AT,
        completedAt = null,
    )

private fun completeProgress(): OnboardingProgress =
    OnboardingProgress(
        currentStep = OnboardingStep.Profile,
        startedAt = FakeOnboardingRepository.STARTED_AT,
        completedAt = FakeOnboardingRepository.COMPLETED_AT,
    )
