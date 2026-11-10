package com.stratum.navigation

import app.cash.turbine.test
import com.stratum.application.onboarding.ObserveOnboardingCompleteUseCase
import com.stratum.application.subscription.ObserveAppGateDestinationUseCase
import com.stratum.application.subscription.ObserveEntitlementUseCase
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.onboarding.FakeOnboardingRepository
import com.stratum.core.testing.onboarding.completeOnboardingProgress
import com.stratum.core.testing.onboarding.incompleteOnboardingProgress
import com.stratum.core.testing.subscription.FakeEntitlementRepository
import com.stratum.domain.subscription.EntitlementSource
import com.stratum.domain.subscription.EntitlementState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Thin wiring coverage for [EntitlementGateViewModel]. The gate combination matrix
 * (which onboarding/entitlement inputs produce which destination) is asserted in
 * ObserveAppGateDestinationUseCaseTest; this suite only proves the ViewModel starts
 * at Loading, republishes read models as destinations one-to-one, forwards retry to
 * the gate, and passes its loading timeout through.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class EntitlementGateViewModelTest {

    private val fakeOnboarding = FakeOnboardingRepository()
    private val fakeEntitlement = FakeEntitlementRepository(EntitlementState.Loading)
    private lateinit var subject: EntitlementGateViewModel

    @BeforeEach
    fun setup() {
        fakeOnboarding.seed(incompleteOnboardingProgress())
        fakeEntitlement.setState(EntitlementState.Loading)
        subject = buildViewModel()
    }

    private fun buildViewModel(
        loadingTimeoutMs: Long =
            ObserveAppGateDestinationUseCase.DEFAULT_GATE_LOADING_TIMEOUT_MS,
    ): EntitlementGateViewModel =
        EntitlementGateViewModel(
            observeAppGateDestination = ObserveAppGateDestinationUseCase(
                observeOnboardingComplete = ObserveOnboardingCompleteUseCase(fakeOnboarding),
                observeEntitlement = ObserveEntitlementUseCase(fakeEntitlement),
            ),
            loadingTimeoutMs = loadingTimeoutMs,
        )

    @Test
    @DisplayName("Destination starts at Loading")
    fun destinationStartsAtLoading() {
        assertEquals(AppGateDestination.Loading, subject.destination.value)
    }

    @Test
    @DisplayName("Read models are republished as the matching destinations")
    fun readModelsRepublishedAsDestinations() = runTest {
        fakeOnboarding.seed(completeOnboardingProgress())
        fakeEntitlement.setState(EntitlementState.NotEntitled())
        subject = buildViewModel()
        assertEquals(AppGateDestination.Paywall, subject.destination.value)

        fakeEntitlement.setState(
            EntitlementState.Entitled(EntitlementSource.GooglePlaySubscription),
        )
        assertEquals(AppGateDestination.Main, subject.destination.value)
    }

    @Test
    @DisplayName("Retry is forwarded to the gate and recovers")
    fun retryForwardsAndRecovers() = runTest {
        fakeOnboarding.seed(completeOnboardingProgress())
        fakeEntitlement.setState(
            EntitlementState.Entitled(EntitlementSource.GooglePlaySubscription),
        )
        fakeEntitlement.throwOnSubscribe = IllegalStateException("billing down")
        subject = buildViewModel()
        assertEquals(AppGateDestination.Failed, subject.destination.value)

        subject.retry()
        assertEquals(AppGateDestination.Main, subject.destination.value)
    }

    @Test
    @DisplayName("Loading timeout is passed through and maps to Failed")
    fun loadingTimeoutPassesThrough() = runTest {
        fakeOnboarding.seed(completeOnboardingProgress())
        fakeEntitlement.setState(EntitlementState.Loading)
        subject = buildViewModel(loadingTimeoutMs = 1_000L)

        subject.destination.test {
            assertEquals(AppGateDestination.Loading, awaitItem())
            advanceTimeBy(1_000L)
            assertEquals(AppGateDestination.Failed, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
