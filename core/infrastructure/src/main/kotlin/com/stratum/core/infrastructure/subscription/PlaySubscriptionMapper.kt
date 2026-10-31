package com.stratum.core.infrastructure.subscription

import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.stratum.domain.subscription.SubscriptionProduct
import com.stratum.domain.subscription.SubscriptionPurchase
import com.stratum.domain.subscription.trialDaysFromPhase

internal data class PlayPricingPhaseSnapshot(
    val priceAmountMicros: Long,
    val formattedPrice: String,
    val currencyCode: String,
    val billingPeriodIso: String,
    val billingCycleCount: Int,
)

internal data class PlayOfferSnapshot(
    val basePlanId: String,
    val offerId: String?,
    val offerToken: String,
    val phases: List<PlayPricingPhaseSnapshot>,
)

object PlaySubscriptionMapper {
    fun toDomain(details: ProductDetails): SubscriptionProduct? {
        val offers = details.subscriptionOfferDetails ?: return null
        return mapSubscriptionProduct(
            productId = details.productId,
            title = details.title,
            description = details.description,
            offers = offers.map { offer ->
                PlayOfferSnapshot(
                    basePlanId = offer.basePlanId,
                    offerId = offer.offerId,
                    offerToken = offer.offerToken,
                    phases = offer.pricingPhases.pricingPhaseList.map { phase ->
                        PlayPricingPhaseSnapshot(
                            priceAmountMicros = phase.priceAmountMicros,
                            formattedPrice = phase.formattedPrice,
                            currencyCode = phase.priceCurrencyCode,
                            billingPeriodIso = phase.billingPeriod,
                            billingCycleCount = phase.billingCycleCount,
                        )
                    },
                )
            },
        )
    }

    fun toDomain(purchase: Purchase): SubscriptionPurchase = SubscriptionPurchase(
        orderId = purchase.orderId,
        purchaseToken = purchase.purchaseToken,
        isAcknowledged = purchase.isAcknowledged,
        isAutoRenewing = purchase.isAutoRenewing,
        purchaseTimeEpochMillis = purchase.purchaseTime,
        productIds = purchase.products.toList(),
    )

    internal fun mapSubscriptionProduct(
        productId: String,
        title: String,
        description: String,
        offers: List<PlayOfferSnapshot>,
    ): SubscriptionProduct? {
        val eligible = offers.filter { offer ->
            offer.offerToken.isNotBlank() &&
                offer.phases.any { it.priceAmountMicros > 0L }
        }
        if (eligible.isEmpty()) return null

        val chosen = eligible.firstOrNull { offer ->
            offer.phases.any { it.priceAmountMicros == 0L }
        } ?: eligible.first()

        val trialPhase = chosen.phases.firstOrNull { it.priceAmountMicros == 0L }
        val recurringPhase = chosen.phases.first { it.priceAmountMicros > 0L }
        val trialDays = if (trialPhase != null) {
            trialDaysFromPhase(trialPhase.billingPeriodIso, trialPhase.billingCycleCount)
        } else {
            0
        }

        return SubscriptionProduct(
            productId = productId,
            basePlanId = chosen.basePlanId,
            offerId = chosen.offerId,
            formattedPrice = recurringPhase.formattedPrice,
            priceAmountMicros = recurringPhase.priceAmountMicros,
            currencyCode = recurringPhase.currencyCode,
            billingPeriodIso = recurringPhase.billingPeriodIso,
            freeTrialDays = trialDays,
            title = title,
            description = description,
            offerToken = chosen.offerToken,
        )
    }
}
