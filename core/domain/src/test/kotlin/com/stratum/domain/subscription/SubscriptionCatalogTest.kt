package com.stratum.domain.subscription

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class SubscriptionCatalogTest {

    @Test
    @DisplayName("known annual and monthly product ids are accepted")
    fun knownIds() {
        assertTrue(SubscriptionCatalog.isKnownProductId(SubscriptionCatalog.ANNUAL_PRODUCT_ID))
        assertTrue(SubscriptionCatalog.isKnownProductId(SubscriptionCatalog.MONTHLY_PRODUCT_ID))
        assertEquals(
            setOf("stratum_membership_annual", "stratum_membership_monthly"),
            SubscriptionCatalog.knownProductIds,
        )
    }

    @Test
    @DisplayName("unknown or blank product ids are rejected")
    fun unknownIds() {
        assertFalse(SubscriptionCatalog.isKnownProductId("premium_feature_sku"))
        assertFalse(SubscriptionCatalog.isKnownProductId(""))
        assertFalse(SubscriptionCatalog.isKnownProductId("stratum_membership_annual_extra"))
    }
}
