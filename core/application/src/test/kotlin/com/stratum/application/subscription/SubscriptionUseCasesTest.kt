package com.stratum.application.subscription

import app.cash.turbine.test
import com.stratum.core.testing.subscription.FakeBillingPort
import com.stratum.core.testing.subscription.FakeEntitlementRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.stratum.domain.subscription.BillingPurchaseResult
import com.stratum.domain.subscription.EntitlementSource
import com.stratum.domain.subscription.EntitlementState
import com.stratum.domain.subscription.SubscriptionCatalog

class SubscriptionUseCasesTest {
    private val fakeBilling = FakeBillingPort()
    private val fakeRepo = FakeEntitlementRepository()

    @Test
    fun `observe entitlement emits current state`() = runTest {
        val useCase = ObserveEntitlementUseCase(fakeRepo)
        useCase().test {
            assertEquals(EntitlementState.NotEntitled(), awaitItem())
            fakeRepo.setState(EntitlementState.Entitled(EntitlementSource.ComplimentaryAccess))
            assertEquals(
                EntitlementState.Entitled(EntitlementSource.ComplimentaryAccess),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `get subscription products returns all available products`() = runTest {
        val useCase = GetSubscriptionProductsUseCase(fakeBilling)
        val result = useCase()
        assertTrue(result.isSuccess)
        val products = result.getOrNull() ?: emptyList()
        assertEquals(2, products.size)
        assertEquals(SubscriptionCatalog.ANNUAL_PRODUCT_ID, products[0].productId)
        assertEquals("$49.99", products[0].formattedPrice)
        assertEquals(14, products[0].freeTrialDays)
        assertEquals(SubscriptionCatalog.MONTHLY_PRODUCT_ID, products[1].productId)
        assertEquals("$7.99", products[1].formattedPrice)
        assertEquals(0, products[1].freeTrialDays)
    }

    @Test
    fun `purchase subscription launches billing flow for product`() = runTest {
        val useCase = PurchaseSubscriptionUseCase(fakeBilling)
        val product = fakeBilling.availableProducts.first()
        val result = useCase(product)
        assertTrue(result.isSuccess)
        assertEquals(product, fakeBilling.lastLaunchedProduct)
    }

    @Test
    fun `restore purchases restores billing and refreshes repo state`() = runTest {
        fakeRepo.setState(EntitlementState.Entitled(EntitlementSource.GooglePlaySubscription))
        val useCase = RestorePurchasesUseCase(fakeBilling, fakeRepo)
        val result = useCase()
        assertTrue(result.isSuccess)
        assertEquals(
            EntitlementState.Entitled(EntitlementSource.GooglePlaySubscription),
            result.getOrNull(),
        )
    }

    @Test
    fun `restore purchases propagates billing failure`() = runTest {
        fakeBilling.restorePurchasesResult = Result.failure(IllegalStateException("Billing unavailable"))
        val useCase = RestorePurchasesUseCase(fakeBilling, fakeRepo)
        val result = useCase()
        assertTrue(result.isFailure)
        assertEquals("Billing unavailable", result.exceptionOrNull()?.message)
    }

    @Test
    fun `restore purchases propagates repository refresh failure`() = runTest {
        fakeRepo.refreshResult = Result.failure(IllegalStateException("Refresh failed"))
        val useCase = RestorePurchasesUseCase(fakeBilling, fakeRepo)
        val result = useCase()
        assertTrue(result.isFailure)
        assertEquals("Refresh failed", result.exceptionOrNull()?.message)
    }

    @Test
    fun `observe billing purchase results forwards port emissions`() = runTest {
        val useCase = ObserveBillingPurchaseResultsUseCase(fakeBilling)
        useCase().test {
            fakeBilling.emitPurchaseResult(BillingPurchaseResult.Canceled)
            assertEquals(BillingPurchaseResult.Canceled, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
