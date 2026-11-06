package com.stratum.application.subscription

import app.cash.turbine.test
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.subscription.FakeBillingPort
import com.stratum.core.testing.subscription.FakeEntitlementRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.stratum.domain.subscription.BillingPurchaseResult
import com.stratum.domain.subscription.EntitlementDiagnostic
import com.stratum.domain.subscription.EntitlementSource
import com.stratum.domain.subscription.EntitlementState
import com.stratum.domain.subscription.SubscriptionCatalog

class SubscriptionUseCasesTest {
    private val fakeBilling = FakeBillingPort()
    private val fakeRepo = FakeEntitlementRepository()
    private val fakeLogging = FakeLoggingPort()

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
        val useCase = GetSubscriptionProductsUseCase(fakeBilling, fakeLogging)
        val result = useCase()
        val products = (result as GetSubscriptionProductsResult.Success).products
        assertEquals(2, products.size)
        assertEquals(SubscriptionCatalog.ANNUAL_PRODUCT_ID, products[0].productId)
        assertEquals("$49.99", products[0].formattedPrice)
        assertEquals(14, products[0].freeTrialDays)
        assertEquals(SubscriptionCatalog.MONTHLY_PRODUCT_ID, products[1].productId)
        assertEquals("$7.99", products[1].formattedPrice)
        assertEquals(0, products[1].freeTrialDays)
    }

    @Test
    fun `get subscription products failure returns Failed and logs once`() = runTest {
        val boom = IllegalStateException("Billing unavailable")
        fakeBilling.queryException = boom
        val useCase = GetSubscriptionProductsUseCase(fakeBilling, fakeLogging)

        val result = useCase()

        assertEquals(GetSubscriptionProductsResult.Failed, result)
        assertEquals(1, fakeLogging.errors.size)
        assertSame(boom, fakeLogging.errors.single().throwable)
    }

    @Test
    fun `purchase subscription launches billing flow for product`() = runTest {
        val useCase = PurchaseSubscriptionUseCase(fakeBilling, fakeLogging)
        val product = fakeBilling.availableProducts.first()
        val result = useCase(product)
        assertEquals(PurchaseSubscriptionResult.Success, result)
        assertEquals(product, fakeBilling.lastLaunchedProduct)
    }

    @Test
    fun `purchase subscription failure returns Failed carrying the message and logs once`() = runTest {
        fakeBilling.launchBillingFailure = IllegalStateException("Billing unavailable")
        val useCase = PurchaseSubscriptionUseCase(fakeBilling, fakeLogging)
        val product = fakeBilling.availableProducts.first()

        val result = useCase(product)

        assertEquals(PurchaseSubscriptionResult.Failed("Billing unavailable"), result)
        assertEquals(1, fakeLogging.errors.size)
    }

    @Test
    fun `restore purchases restores billing and refreshes repo state`() = runTest {
        fakeRepo.setState(EntitlementState.Entitled(EntitlementSource.GooglePlaySubscription))
        val useCase = RestorePurchasesUseCase(fakeBilling, fakeRepo, fakeLogging)
        val result = useCase()
        assertEquals(
            RestorePurchasesResult.Success(
                EntitlementState.Entitled(EntitlementSource.GooglePlaySubscription),
            ),
            result,
        )
    }

    @Test
    fun `restore purchases propagates billing failure`() = runTest {
        fakeBilling.restorePurchasesFailure = IllegalStateException("Billing unavailable")
        val useCase = RestorePurchasesUseCase(fakeBilling, fakeRepo, fakeLogging)
        val result = useCase()
        assertEquals(RestorePurchasesResult.Failed("Billing unavailable"), result)
        assertEquals(1, fakeLogging.errors.size)
    }

    @Test
    fun `restore purchases propagates repository refresh failure`() = runTest {
        fakeRepo.refreshFailure = IllegalStateException("Refresh failed")
        val useCase = RestorePurchasesUseCase(fakeBilling, fakeRepo, fakeLogging)
        val result = useCase()
        assertEquals(RestorePurchasesResult.Failed("Refresh failed"), result)
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

    @Test
    fun `observe entitlement diagnostics forwards repository values`() = runTest {
        val useCase = ObserveEntitlementDiagnosticsUseCase(fakeRepo)
        useCase().test {
            val boom = IllegalStateException("sync boom")
            fakeRepo.emitDiagnostic(EntitlementDiagnostic.PlaySyncFailed(boom))
            val diagnostic = awaitItem()
            assertTrue(diagnostic is EntitlementDiagnostic.PlaySyncFailed)
            assertEquals("sync boom", (diagnostic as EntitlementDiagnostic.PlaySyncFailed).cause.message)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
