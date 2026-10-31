package com.stratum.core.infrastructure.subscription

import com.stratum.domain.subscription.SubscriptionCatalog
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class PlaySubscriptionMapperTest {

    @Test
    @DisplayName("trial days come from a zero-price phase period times cycle count")
    fun trialDaysFromZeroPricePhase() {
        val product = PlaySubscriptionMapper.mapSubscriptionProduct(
            productId = SubscriptionCatalog.ANNUAL_PRODUCT_ID,
            title = "Annual",
            description = "Membership",
            offers = listOf(
                PlayOfferSnapshot(
                    basePlanId = "annual",
                    offerId = "trial",
                    offerToken = "annual-offer-token",
                    phases = listOf(
                        PlayPricingPhaseSnapshot(
                            priceAmountMicros = 0L,
                            formattedPrice = "Free",
                            currencyCode = "USD",
                            billingPeriodIso = "P2W",
                            billingCycleCount = 1,
                        ),
                        PlayPricingPhaseSnapshot(
                            priceAmountMicros = 49_990_000L,
                            formattedPrice = "$49.99",
                            currencyCode = "USD",
                            billingPeriodIso = "P1Y",
                            billingCycleCount = 0,
                        ),
                    ),
                ),
            ),
        )

        assertEquals(14, product?.freeTrialDays)
        assertEquals(49_990_000L, product?.priceAmountMicros)
        assertEquals("USD", product?.currencyCode)
        assertEquals("P1Y", product?.billingPeriodIso)
        assertEquals("$49.99", product?.formattedPrice)
        assertEquals("annual-offer-token", product?.offerToken)
    }

    @Test
    @DisplayName("annual without a zero-price phase does not invent a 14-day trial")
    fun annualWithoutTrialIsZeroDays() {
        val product = PlaySubscriptionMapper.mapSubscriptionProduct(
            productId = SubscriptionCatalog.ANNUAL_PRODUCT_ID,
            title = "Annual",
            description = "Membership",
            offers = listOf(
                PlayOfferSnapshot(
                    basePlanId = "annual",
                    offerId = null,
                    offerToken = "annual-base",
                    phases = listOf(
                        PlayPricingPhaseSnapshot(
                            priceAmountMicros = 49_990_000L,
                            formattedPrice = "$49.99",
                            currencyCode = "USD",
                            billingPeriodIso = "P1Y",
                            billingCycleCount = 0,
                        ),
                    ),
                ),
            ),
        )

        assertEquals(0, product?.freeTrialDays)
        assertEquals("P1Y", product?.billingPeriodIso)
    }

    @Test
    @DisplayName("offers without a recurring paid phase are skipped")
    fun skipsOffersWithoutRecurringPhase() {
        val product = PlaySubscriptionMapper.mapSubscriptionProduct(
            productId = SubscriptionCatalog.MONTHLY_PRODUCT_ID,
            title = "Monthly",
            description = "Membership",
            offers = listOf(
                PlayOfferSnapshot(
                    basePlanId = "monthly",
                    offerId = "prepaid",
                    offerToken = "prepaid-token",
                    phases = listOf(
                        PlayPricingPhaseSnapshot(
                            priceAmountMicros = 0L,
                            formattedPrice = "Free",
                            currencyCode = "USD",
                            billingPeriodIso = "P1W",
                            billingCycleCount = 1,
                        ),
                    ),
                ),
            ),
        )
        assertNull(product)
    }

    @Test
    @DisplayName("blank offer tokens are skipped")
    fun skipsBlankOfferToken() {
        val product = PlaySubscriptionMapper.mapSubscriptionProduct(
            productId = SubscriptionCatalog.MONTHLY_PRODUCT_ID,
            title = "Monthly",
            description = "Membership",
            offers = listOf(
                PlayOfferSnapshot(
                    basePlanId = "monthly",
                    offerId = null,
                    offerToken = "  ",
                    phases = listOf(
                        PlayPricingPhaseSnapshot(
                            priceAmountMicros = 7_990_000L,
                            formattedPrice = "$7.99",
                            currencyCode = "USD",
                            billingPeriodIso = "P1M",
                            billingCycleCount = 0,
                        ),
                    ),
                ),
            ),
        )
        assertNull(product)
    }

    @Test
    @DisplayName("prefers the offer that includes a free trial when one exists")
    fun prefersTrialOffer() {
        val product = PlaySubscriptionMapper.mapSubscriptionProduct(
            productId = SubscriptionCatalog.ANNUAL_PRODUCT_ID,
            title = "Annual",
            description = "Membership",
            offers = listOf(
                PlayOfferSnapshot(
                    basePlanId = "annual",
                    offerId = null,
                    offerToken = "base-token",
                    phases = listOf(
                        PlayPricingPhaseSnapshot(
                            priceAmountMicros = 49_990_000L,
                            formattedPrice = "$49.99",
                            currencyCode = "USD",
                            billingPeriodIso = "P1Y",
                            billingCycleCount = 0,
                        ),
                    ),
                ),
                PlayOfferSnapshot(
                    basePlanId = "annual",
                    offerId = "trial",
                    offerToken = "trial-token",
                    phases = listOf(
                        PlayPricingPhaseSnapshot(
                            priceAmountMicros = 0L,
                            formattedPrice = "Free",
                            currencyCode = "USD",
                            billingPeriodIso = "P1D",
                            billingCycleCount = 7,
                        ),
                        PlayPricingPhaseSnapshot(
                            priceAmountMicros = 49_990_000L,
                            formattedPrice = "$49.99",
                            currencyCode = "USD",
                            billingPeriodIso = "P1Y",
                            billingCycleCount = 0,
                        ),
                    ),
                ),
            ),
        )

        assertEquals("trial-token", product?.offerToken)
        assertEquals(7, product?.freeTrialDays)
    }
}
