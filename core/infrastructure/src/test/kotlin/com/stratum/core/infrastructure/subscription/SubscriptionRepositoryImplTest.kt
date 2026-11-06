package com.stratum.core.infrastructure.subscription

import app.cash.turbine.test
import com.stratum.core.testing.auth.FakeAuthManager
import com.stratum.core.testing.subscription.FakeBillingPort
import com.stratum.core.testing.subscription.FakePlayEntitlementSyncPort
import com.stratum.domain.auth.AuthState
import com.stratum.domain.auth.UserProfile
import com.stratum.domain.subscription.ComplimentaryAccess
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
        fakeComplimentary.access.value = ComplimentaryAccess.NotGranted
        fakeBilling.setPurchases(emptyList())
        fakePlaySync.syncedTokens.clear()
        fakePlaySync.syncFailure = null
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
        fakeComplimentary.access.value = ComplimentaryAccess.Granted

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
        fakeComplimentary.access.value = ComplimentaryAccess.NotGranted
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
        fakeComplimentary.access.value = ComplimentaryAccess.NotGranted
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
        assertEquals(EntitlementState.NotEntitled("Not authenticated"), result)
    }

    @Test
    @DisplayName("refresh propagates failure when billing restore fails")
    fun refreshFailsWhenBillingRestoreFails() = runTest {
        fakeBilling.restorePurchasesFailure = IllegalStateException("Billing service down")

        val thrown = org.junit.jupiter.api.assertThrows<IllegalStateException> {
            repository.refreshEntitlement()
        }
        assertEquals("Billing service down", thrown.message)
    }

    @Test
    @DisplayName("complimentary access wins over a known Play purchase")
    fun complimentaryWinsOverPlay() = runTest {
        fakeAuthManager.setState(authenticated("uid-dev"))
        fakeComplimentary.access.value = ComplimentaryAccess.Granted
        fakeBilling.setPurchases(listOf(knownPlayPurchase()))

        repository.observeEntitlement().test {
            assertEquals(
                EntitlementState.Entitled(EntitlementSource.ComplimentaryAccess),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }

        val refreshed = repository.refreshEntitlement()
        assertEquals(EntitlementState.Entitled(EntitlementSource.ComplimentaryAccess), refreshed)
    }

    @Test
    @DisplayName("a failed complimentary read is Loading, never the paywall state")
    fun unknownComplimentaryIsLoading() = runTest {
        fakeAuthManager.setState(authenticated("uid-dev"))
        fakeComplimentary.access.value = ComplimentaryAccess.Unknown

        repository.observeEntitlement().test {
            assertEquals(EntitlementState.Loading, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("refresh fails when the complimentary read failed and no Play purchase entitles")
    fun refreshUnknownComplimentaryFails() = runTest {
        fakeAuthManager.setState(authenticated("uid-dev"))
        fakeComplimentary.access.value = ComplimentaryAccess.Unknown

        org.junit.jupiter.api.assertThrows<IllegalStateException> {
            repository.refreshEntitlement()
        }
    }

    @Test
    @DisplayName("refresh still succeeds on a known Play purchase when the complimentary read failed")
    fun refreshUnknownComplimentaryKeepsPlayEntitlement() = runTest {
        fakeAuthManager.setState(authenticated("uid-subscriber"))
        fakeComplimentary.access.value = ComplimentaryAccess.Unknown
        fakeBilling.setPurchases(listOf(knownPlayPurchase()))

        val refreshed = repository.refreshEntitlement()
        assertEquals(EntitlementState.Entitled(EntitlementSource.GooglePlaySubscription), refreshed)
    }

    @Test
    @DisplayName("entitlement recovers when a failed complimentary read later succeeds")
    fun unknownComplimentaryRecovers() = runTest {
        fakeAuthManager.setState(authenticated("uid-dev"))
        fakeComplimentary.access.value = ComplimentaryAccess.Unknown

        repository.observeEntitlement().test {
            assertEquals(EntitlementState.Loading, awaitItem())
            fakeComplimentary.access.value = ComplimentaryAccess.Granted
            assertEquals(
                EntitlementState.Entitled(EntitlementSource.ComplimentaryAccess),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("play sync failure surfaces as a diagnostic value")
    fun syncFailureSurfacesAsDiagnostic() = runTest {
        val boom = IllegalStateException("sync boom")
        fakePlaySync.syncFailure = boom
        fakeAuthManager.setState(authenticated("uid-subscriber"))
        fakeBilling.setPurchases(listOf(knownPlayPurchase()))

        repository.observeEntitlementDiagnostics().test {
            repository.refreshEntitlement()
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
        fakePlaySync.syncFailure = boom
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
            repository.refreshEntitlement()
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
        val access = MutableStateFlow(ComplimentaryAccess.NotGranted)

        override fun observeComplimentaryAccess(userId: String): Flow<ComplimentaryAccess> = access.asStateFlow()

        override suspend fun getComplimentaryAccess(userId: String): ComplimentaryAccess = access.value
    }
}
