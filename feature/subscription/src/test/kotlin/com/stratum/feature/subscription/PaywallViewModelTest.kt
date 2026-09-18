package com.stratum.feature.subscription

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.subscription.FakeBillingPort
import com.stratum.core.testing.subscription.FakeEntitlementRepository
import com.stratum.core.testing.telemetry.FakeAnalyticsPort
import com.stratum.domain.logging.LogAnalyticsEventUseCase
import com.stratum.domain.logging.PARAM_ACTION
import com.stratum.domain.logging.PARAM_SCREEN
import com.stratum.domain.logging.USER_VISIBLE_ERROR_EVENT
import com.stratum.domain.subscription.BillingPurchaseResult
import com.stratum.domain.subscription.EntitlementSource
import com.stratum.domain.subscription.EntitlementState
import com.stratum.domain.subscription.GetSubscriptionProductsUseCase
import com.stratum.domain.subscription.ObserveBillingPurchaseResultsUseCase
import com.stratum.domain.subscription.ObserveEntitlementUseCase
import com.stratum.domain.subscription.PurchaseSubscriptionUseCase
import com.stratum.domain.subscription.RestorePurchasesUseCase
import com.stratum.domain.subscription.SubscriptionCatalog
import com.stratum.domain.subscription.annualSavingsPercent
import com.stratum.domain.subscription.monthlyEquivalentMicros
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
class PaywallViewModelTest {

    private lateinit var fakeBilling: FakeBillingPort
    private lateinit var fakeRepo: FakeEntitlementRepository
    private lateinit var viewModel: PaywallViewModel

    @BeforeEach
    fun setup() {
        fakeBilling = FakeBillingPort()
        fakeRepo = FakeEntitlementRepository()
        fakeRepo.setState(EntitlementState.NotEntitled())
        viewModel = createViewModel()
    }

    private fun createViewModel(
        analytics: FakeAnalyticsPort = FakeAnalyticsPort(),
    ): PaywallViewModel =
        PaywallViewModel(
            getSubscriptionProducts = GetSubscriptionProductsUseCase(fakeBilling),
            purchaseSubscription = PurchaseSubscriptionUseCase(fakeBilling),
            restorePurchases = RestorePurchasesUseCase(fakeBilling, fakeRepo),
            observeEntitlement = ObserveEntitlementUseCase(fakeRepo),
            observeBillingPurchaseResults = ObserveBillingPurchaseResultsUseCase(fakeBilling),
            logAnalytics = LogAnalyticsEventUseCase(analytics),
        )

    @Test
    fun `uiState loads product details with Annual plan selected by default`() = runTest {
        viewModel.uiState.test {
            val ready = awaitContent()
            val selected = requireNotNull(ready.selectedProduct)

            assertEquals(2, ready.products.size)
            assertEquals(SubscriptionCatalog.ANNUAL_PRODUCT_ID, ready.selectedProductId)
            assertEquals("$49.99", selected.formattedPrice)
            assertEquals(14, selected.freeTrialDays)
            assertEquals(expectedAnnualPricePerMonth(), selected.pricePerMonthText)
            assertEquals(expectedAnnualBadge(), selected.badge)
            assertFalse(selected.description.contains("after trial", ignoreCase = true))
            assertFalse(ready.isEntitled)
            assertFalse(ready.isComplimentary)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when onSelectProduct called with monthly id then selectedProduct updates`() = runTest {
        viewModel.uiState.test {
            val ready = awaitContent()
            assertEquals(SubscriptionCatalog.ANNUAL_PRODUCT_ID, ready.selectedProductId)

            viewModel.onSelectProduct(SubscriptionCatalog.MONTHLY_PRODUCT_ID)
            val updated = awaitItem() as PaywallUiState.Content
            val selected = requireNotNull(updated.selectedProduct)
            assertEquals(SubscriptionCatalog.MONTHLY_PRODUCT_ID, updated.selectedProductId)
            assertEquals("$7.99", selected.formattedPrice)
            assertEquals(0, selected.freeTrialDays)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when repo emits entitled then uiState updates to isEntitled true`() = runTest {
        fakeRepo.setState(EntitlementState.Entitled(EntitlementSource.GooglePlaySubscription))

        viewModel.uiState.test {
            val ready = awaitContent()
            assertTrue(ready.isEntitled)
            assertFalse(ready.isComplimentary)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when repo emits complimentary then uiState updates to isComplimentary true`() = runTest {
        fakeRepo.setState(EntitlementState.Entitled(EntitlementSource.ComplimentaryAccess))

        viewModel.uiState.test {
            val ready = awaitContent()
            assertTrue(ready.isEntitled)
            assertTrue(ready.isComplimentary)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `empty catalog is Error`() = runTest {
        fakeBilling.availableProducts = emptyList()
        viewModel = createViewModel()

        viewModel.uiState.test {
            val state = awaitErrorState()
            assertEquals(PaywallCopy.LOAD_ERROR, state.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `query failure is Error`() = runTest {
        fakeBilling.queryException = IllegalStateException("Play Billing unavailable")
        val analytics = FakeAnalyticsPort()
        viewModel = createViewModel(analytics)

        viewModel.uiState.test {
            val state = awaitErrorState()
            assertEquals(PaywallCopy.LOAD_ERROR, state.message)
            cancelAndIgnoreRemainingEvents()
        }
        val event = analytics.events.single()
        assertEquals(USER_VISIBLE_ERROR_EVENT, event.name)
        assertEquals("paywall", event.params[PARAM_SCREEN])
        assertEquals("load_offerings", event.params[PARAM_ACTION])
    }

    @Test
    fun `canceled purchaseResult clears isPurchasing`() = runTest {
        viewModel.uiState.test {
            awaitContent()
            viewModel.onPurchaseClicked()
            val purchasing = awaitItem() as PaywallUiState.Content
            assertTrue(purchasing.isPurchasing)

            fakeBilling.emitPurchaseResult(BillingPurchaseResult.Canceled)
            val cleared = awaitItem() as PaywallUiState.Content
            assertFalse(cleared.isPurchasing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when purchase clicked and entitlement becomes entitled then PurchaseSuccess is emitted`() = runTest {
        viewModel.events.test {
            viewModel.onPurchaseClicked()
            fakeRepo.setState(EntitlementState.Entitled(EntitlementSource.GooglePlaySubscription))
            assertEquals(PaywallUiEvent.PurchaseSuccess, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `restore none shows visible message`() = runTest {
        fakeRepo.setState(EntitlementState.NotEntitled())

        viewModel.uiState.test {
            awaitContent()
            viewModel.onRestorePurchasesClicked()
            val updated = awaitItem() as PaywallUiState.Content
            val restored = if (updated.isRestoring) {
                awaitItem() as PaywallUiState.Content
            } else {
                updated
            }
            assertFalse(restored.isRestoring)
            assertEquals(PaywallCopy.RESTORE_NONE, restored.userMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `restore failure records analytics and shows message`() = runTest {
        fakeBilling.restorePurchasesResult = Result.failure(IllegalStateException("Sync down"))
        val analytics = FakeAnalyticsPort()
        viewModel = createViewModel(analytics)

        viewModel.uiState.test {
            awaitContent()
            viewModel.onRestorePurchasesClicked()
            val updated = awaitItem() as PaywallUiState.Content
            val restored = if (updated.isRestoring) {
                awaitItem() as PaywallUiState.Content
            } else {
                updated
            }
            assertFalse(restored.isRestoring)
            cancelAndIgnoreRemainingEvents()
        }
        val event = analytics.events.single()
        assertEquals(USER_VISIBLE_ERROR_EVENT, event.name)
        assertEquals("paywall", event.params[PARAM_SCREEN])
        assertEquals("restore_purchases", event.params[PARAM_ACTION])
    }

    @Test
    fun `restore entitled emits PurchaseSuccess`() = runTest {
        fakeRepo.setState(EntitlementState.Entitled(EntitlementSource.GooglePlaySubscription))

        viewModel.events.test {
            viewModel.onRestorePurchasesClicked()
            assertEquals(PaywallUiEvent.PurchaseSuccess, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<PaywallUiState>.awaitContent(): PaywallUiState.Content {
        val initial = awaitItem()
        val ready = if (initial is PaywallUiState.Loading) {
            awaitItem()
        } else {
            initial
        }
        return assertInstanceOf(PaywallUiState.Content::class.java, ready)
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<PaywallUiState>.awaitErrorState(): PaywallUiState.Error {
        val initial = awaitItem()
        val ready = if (initial is PaywallUiState.Loading) {
            awaitItem()
        } else {
            initial
        }
        return assertInstanceOf(PaywallUiState.Error::class.java, ready)
    }

    private fun expectedAnnualPricePerMonth(): String {
        val annual = fakeBilling.availableProducts.first {
            it.productId == SubscriptionCatalog.ANNUAL_PRODUCT_ID
        }
        return formatPaywallPricePerMonth(
            formattedPrice = annual.formattedPrice,
            priceAmountMicros = annual.priceAmountMicros,
            currencyCode = annual.currencyCode,
            billingPeriodIso = annual.billingPeriodIso,
            billingPeriodText = "year",
        )
    }

    private fun expectedAnnualBadge(): String {
        val annual = fakeBilling.availableProducts.first {
            it.productId == SubscriptionCatalog.ANNUAL_PRODUCT_ID
        }
        val monthly = fakeBilling.availableProducts.first {
            it.productId == SubscriptionCatalog.MONTHLY_PRODUCT_ID
        }
        val monthlyEq = monthlyEquivalentMicros(annual.priceAmountMicros, annual.billingPeriodIso)
        assertNotNull(monthlyEq)
        val percent = annualSavingsPercent(monthlyEq!!, monthly.priceAmountMicros)
        assertNotNull(percent)
        assertTrue(percent!! > 0)
        return "SAVE $percent%"
    }
}
