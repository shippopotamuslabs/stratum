package com.stratum.domain.subscription

import com.stratum.domain.auth.AuthState
import com.stratum.domain.auth.UserProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class EntitlementStateTest {

    private val user = AuthState.Authenticated(
        UserProfile("uid-1", "Ada", "ada@example.com", null),
    )

    private val playPurchase = SubscriptionPurchase(
        orderId = "GPA.1234",
        purchaseToken = "token-should-not-affect-resolver",
        isAcknowledged = true,
        isAutoRenewing = true,
        purchaseTimeEpochMillis = 1_000L,
        productIds = listOf(SubscriptionCatalog.ANNUAL_PRODUCT_ID),
    )

    @Test
    @DisplayName("auth Loading resolves to Loading regardless of purchases")
    fun loading() {
        assertEquals(
            EntitlementState.Loading,
            EntitlementState.resolve(AuthState.Loading, complimentary = true, purchases = listOf(playPurchase)),
        )
    }

    @Test
    @DisplayName("signed-out and auth errors are never entitled")
    fun unauthenticatedNeverEntitled() {
        val expected = EntitlementState.NotEntitled("Not authenticated")
        assertEquals(
            expected,
            EntitlementState.resolve(AuthState.Unauthenticated, complimentary = true, purchases = listOf(playPurchase)),
        )
        assertEquals(
            expected,
            EntitlementState.resolve(AuthState.Error("nope"), complimentary = true, purchases = listOf(playPurchase)),
        )
    }

    @Test
    @DisplayName("complimentary access wins over a known Play purchase")
    fun complimentaryWins() {
        assertEquals(
            EntitlementState.Entitled(EntitlementSource.ComplimentaryAccess),
            EntitlementState.resolve(user, complimentary = true, purchases = listOf(playPurchase)),
        )
    }

    @Test
    @DisplayName("a known Play SKU entitles as Google Play membership")
    fun playEntitled() {
        assertEquals(
            EntitlementState.Entitled(EntitlementSource.GooglePlaySubscription),
            EntitlementState.resolve(user, complimentary = false, purchases = listOf(playPurchase)),
        )
    }

    @Test
    @DisplayName("unknown product ids do not entitle")
    fun unknownSkuNotEntitled() {
        val unknown = playPurchase.copy(productIds = listOf("some_other_sku"))
        assertEquals(
            EntitlementState.NotEntitled(),
            EntitlementState.resolve(user, complimentary = false, purchases = listOf(unknown)),
        )
    }

    @Test
    @DisplayName("authenticated with neither complimentary nor known purchase is not entitled")
    fun neither() {
        assertEquals(
            EntitlementState.NotEntitled(),
            EntitlementState.resolve(user, complimentary = false, purchases = emptyList()),
        )
    }
}
