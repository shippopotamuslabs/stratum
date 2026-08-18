package com.stratum.navigation

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.onboarding.FakeOnboardingRepository
import com.stratum.core.testing.subscription.FakeEntitlementRepository
import com.stratum.domain.onboarding.ObserveOnboardingCompleteUseCase
import com.stratum.domain.onboarding.OnboardingProgress
import com.stratum.domain.onboarding.OnboardingStep
import com.stratum.domain.subscription.EntitlementSource
import com.stratum.domain.subscription.EntitlementState
import com.stratum.domain.subscription.ObserveEntitlementUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class EntitlementGateViewModelTest {

    private val fakeOnboarding = FakeOnboardingRepository()
    private val fakeEntitlement = FakeEntitlementRepository(EntitlementState.Loading)
    private lateinit var subject: EntitlementGateViewModel

    @BeforeEach
    fun setup() {
        fakeOnboarding.seed(incompleteProgress())
        fakeEntitlement.setState(EntitlementState.Loading)
        subject = buildViewModel()
    }

    private fun buildViewModel(
        loadingTimeoutMs: Long = EntitlementGateViewModel.DEFAULT_GATE_LOADING_TIMEOUT_MS,
    ): EntitlementGateViewModel =
        EntitlementGateViewModel(
            observeEntitlement = ObserveEntitlementUseCase(fakeEntitlement),
            observeOnboardingComplete = ObserveOnboardingCompleteUseCase(fakeOnboarding),
            loadingTimeoutMs = loadingTimeoutMs,
        )

    @Test
    @DisplayName("Loading entitlement maps to Loading even when onboarding is complete")
    fun loadingEntitlementMapsToLoading() = runTest {
        fakeOnboarding.seed(completeProgress())
        fakeEntitlement.setState(EntitlementState.Loading)
        subject = buildViewModel()

        subject.destination.test {
            awaitDestination(AppGateDestination.Loading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("NotEntitled maps to Paywall regardless of onboarding completeness")
    fun notEntitledMapsToPaywall() = runTest {
        fakeOnboarding.seed(completeProgress())
        fakeEntitlement.setState(EntitlementState.NotEntitled())
        subject = buildViewModel()

        subject.destination.test {
            awaitDestination(AppGateDestination.Paywall)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Entitled and incomplete onboarding maps to Onboarding")
    fun entitledIncompleteMapsToOnboarding() = runTest {
        fakeOnboarding.seed(incompleteProgress())
        fakeEntitlement.setState(
            EntitlementState.Entitled(EntitlementSource.GooglePlaySubscription),
        )
        subject = buildViewModel()

        subject.destination.test {
            awaitDestination(AppGateDestination.Onboarding)
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
        subject = buildViewModel()

        subject.destination.test {
            awaitDestination(AppGateDestination.Main)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Becoming entitled while incomplete routes from Paywall to Onboarding")
    fun purchaseWhileIncompleteRoutesToOnboarding() = runTest {
        fakeOnboarding.seed(incompleteProgress())
        fakeEntitlement.setState(EntitlementState.NotEntitled())
        subject = buildViewModel()

        subject.destination.test {
            awaitDestination(AppGateDestination.Paywall)
            fakeEntitlement.setState(
                EntitlementState.Entitled(EntitlementSource.ComplimentaryAccess),
            )
            awaitDestination(AppGateDestination.Onboarding)
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
        subject = buildViewModel()

        subject.destination.test {
            awaitDestination(AppGateDestination.Main)
            fakeEntitlement.setState(EntitlementState.NotEntitled())
            awaitDestination(AppGateDestination.Paywall)
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
        subject = buildViewModel()

        subject.destination.test {
            awaitDestination(AppGateDestination.Failed)
            subject.retry()
            awaitDestination(AppGateDestination.Main)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Loading timeout maps to Failed")
    fun loadingTimeoutMapsToFailed() = runTest {
        fakeOnboarding.seed(completeProgress())
        fakeEntitlement.setState(EntitlementState.Loading)
        subject = buildViewModel(loadingTimeoutMs = 1_000L)

        subject.destination.test {
            awaitDestination(AppGateDestination.Loading)
            advanceTimeBy(1_000L)
            awaitDestination(AppGateDestination.Failed)
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
        subject = buildViewModel()

        subject.destination.test {
            awaitDestination(AppGateDestination.Main)
            fakeEntitlement.failCollector(IllegalStateException("billing dropped"))
            awaitDestination(AppGateDestination.Failed)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

private suspend fun ReceiveTurbine<AppGateDestination>.awaitDestination(
    expected: AppGateDestination,
) {
    while (true) {
        val item = awaitItem()
        if (item == AppGateDestination.Loading && expected != AppGateDestination.Loading) {
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
