package com.stratum.core.data.subscription

import app.cash.turbine.test
import com.stratum.core.testing.auth.FakeAuthManager
import com.stratum.core.testing.subscription.FakeBillingPort
import com.stratum.core.testing.subscription.FakePlayEntitlementSyncPort
import com.stratum.domain.auth.AuthState
import com.stratum.domain.auth.UserProfile
import com.stratum.domain.subscription.EntitlementDiagnostic
import com.stratum.domain.subscription.EntitlementSource
import com.stratum.domain.subscription.EntitlementState
import com.stratum.domain.subscription.SubscriptionCatalog
import com.stratum.domain.subscription.SubscriptionPurchase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class SubscriptionRepositoryImplTest {

    private val fakeAuthManager = FakeAuthManager()
    private val fakeBilling = FakeBillingPort()
    private val fakeComplimentary = FakeComplimentaryAccessStore()
    private val fakePlaySync = FakePlayEntitlementSyncPort()
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private lateinit var repository: SubscriptionRepositoryImpl

    @BeforeEach
    fun setup() {
        fakeAuthManager.setState(AuthState.Unauthenticated)
        fakeComplimentary.access.value = false
        fakeBilling.setPurchases(emptyList())
        fakePlaySync.syncedTokens.clear()
        fakePlaySync.syncResult = Result.success(true)
        repository = SubscriptionRepositoryImpl(
            billingPort = fakeBilling,
            complimentaryAccessStore = fakeComplimentary,
            authManager = fakeAuthManager,
            playEntitlementSync = fakePlaySync,
            applicationScope = applicationScope,
        )
    }

    @Test
    @DisplayName("unauthenticated observe is NotEntitled even with Play purchases")
    fun unauthenticated() = runTest {
        fakeBilling.setPurchases(listOf(knownPlayPurchase()))

        repository.observeEntitlement().test {
            assertEquals(EntitlementState.NotEntitled("Not authenticated"), awaitItem())
            assertEquals(emptyList<String>(), fakePlaySync.syncedTokens)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("authenticated complimentary access is Entitled ComplimentaryAccess")
    fun complimentary() = runTest {
        fakeAuthManager.setState(authenticated("uid-dev"))
        fakeComplimentary.access.value = true

        repository.observeEntitlement().test {
            assertEquals(
                EntitlementState.Entitled(EntitlementSource.ComplimentaryAccess),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("authenticated known Play SKU is Entitled GooglePlaySubscription")
    fun playEntitled() = runTest {
        fakeAuthManager.setState(authenticated("uid-subscriber"))
        fakeComplimentary.access.value = false
        fakeBilling.setPurchases(listOf(knownPlayPurchase()))

        repository.observeEntitlement().test {
            assertEquals(
                EntitlementState.Entitled(EntitlementSource.GooglePlaySubscription),
                awaitItem(),
            )
            assertEquals(listOf("token-abc"), fakePlaySync.syncedTokens)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("authenticated with neither complimentary nor known Play SKU is NotEntitled")
    fun neither() = runTest {
        fakeAuthManager.setState(authenticated("uid-free"))
        fakeComplimentary.access.value = false
        fakeBilling.setPurchases(emptyList())

        repository.observeEntitlement().test {
            assertEquals(EntitlementState.NotEntitled(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("refresh while signed out stays NotEntitled even if Play has a known SKU")
    fun refreshSignedOutStaysNotEntitled() = runTest {
        fakeAuthManager.setState(AuthState.Unauthenticated)
        fakeBilling.setPurchases(listOf(knownPlayPurchase()))

        val result = repository.refreshEntitlement()
        assertEquals(
            Result.success(EntitlementState.NotEntitled("Not authenticated")),
            result,
        )
    }

    @Test
    @DisplayName("refresh propagates failure when billing restore fails")
    fun refreshFailsWhenBillingRestoreFails() = runTest {
        fakeBilling.restorePurchasesResult = Result.failure(IllegalStateException("Billing service down"))

        val result = repository.refreshEntitlement()
        org.junit.jupiter.api.Assertions.assertTrue(result.isFailure)
        assertEquals("Billing service down", result.exceptionOrNull()?.message)
    }

    @Test
    @DisplayName("complimentary access wins over a known Play purchase")
    fun complimentaryWinsOverPlay() = runTest {
        fakeAuthManager.setState(authenticated("uid-dev"))
        fakeComplimentary.access.value = true
        fakeBilling.setPurchases(listOf(knownPlayPurchase()))

        repository.observeEntitlement().test {
            assertEquals(
                EntitlementState.Entitled(EntitlementSource.ComplimentaryAccess),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }

        val refreshed = repository.refreshEntitlement()
        assertEquals(
            Result.success(EntitlementState.Entitled(EntitlementSource.ComplimentaryAccess)),
            refreshed,
        )
    }

    @Test
    @DisplayName("play sync failure surfaces as a diagnostic value")
    fun syncFailureSurfacesAsDiagnostic() = runTest {
        val boom = IllegalStateException("sync boom")
        fakePlaySync.syncResult = Result.failure(boom)
        fakeAuthManager.setState(authenticated("uid-subscriber"))
        fakeBilling.setPurchases(listOf(knownPlayPurchase()))

        repository.observeEntitlementDiagnostics().test {
            val refreshed = repository.refreshEntitlement()
            assertTrue(refreshed.isSuccess)
            val diagnostic = awaitItem()
            assertTrue(diagnostic is EntitlementDiagnostic.PlaySyncFailed)
            assertSame(boom, (diagnostic as EntitlementDiagnostic.PlaySyncFailed).cause)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("observe-triggered play sync failure surfaces as a diagnostic value")
    fun observeTriggeredSyncFailureSurfacesAsDiagnostic() = runTest {
        val boom = IllegalStateException("sync boom")
        fakePlaySync.syncResult = Result.failure(boom)
        fakeAuthManager.setState(authenticated("uid-subscriber"))
        fakeBilling.setPurchases(listOf(knownPlayPurchase()))

        repository.observeEntitlementDiagnostics().test {
            repository.observeEntitlement().test {
                assertEquals(
                    EntitlementState.Entitled(EntitlementSource.GooglePlaySubscription),
                    awaitItem(),
                )
                cancelAndIgnoreRemainingEvents()
            }
            val diagnostic = awaitItem()
            assertTrue(diagnostic is EntitlementDiagnostic.PlaySyncFailed)
            assertSame(boom, (diagnostic as EntitlementDiagnostic.PlaySyncFailed).cause)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("successful play sync emits no diagnostic")
    fun successfulSyncEmitsNoDiagnostic() = runTest {
        fakeAuthManager.setState(authenticated("uid-subscriber"))
        fakeBilling.setPurchases(listOf(knownPlayPurchase()))

        repository.observeEntitlementDiagnostics().test {
            val refreshed = repository.refreshEntitlement()
            assertTrue(refreshed.isSuccess)
            assertEquals(listOf("token-abc"), fakePlaySync.syncedTokens)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun authenticated(uid: String) = AuthState.Authenticated(
        UserProfile(uid, "User", "user@stratum.fit", null),
    )

    private fun knownPlayPurchase() = SubscriptionPurchase(
        orderId = "GPA.1234-5678-9012",
        purchaseToken = "token-abc",
        isAcknowledged = true,
        isAutoRenewing = true,
        purchaseTimeEpochMillis = 1000L,
        productIds = listOf(SubscriptionCatalog.ANNUAL_PRODUCT_ID),
    )

    private class FakeComplimentaryAccessStore : ComplimentaryAccessStore {
        val access = MutableStateFlow(false)

        override fun observeComplimentaryAccess(userId: String): Flow<Boolean> = access.asStateFlow()

        override suspend fun getComplimentaryAccess(userId: String): Boolean = access.value
    }
}
