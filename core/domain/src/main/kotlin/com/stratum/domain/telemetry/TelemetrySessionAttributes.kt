package com.stratum.domain.telemetry

import com.stratum.domain.subscription.EntitlementSource
import com.stratum.domain.subscription.EntitlementState

/** Stable, non-PII Crashlytics custom-key values for subscription state. */
fun EntitlementState.toTelemetryTier(): String =
    when (this) {
        EntitlementState.Loading -> TIER_LOADING
        is EntitlementState.NotEntitled -> TIER_NOT_ENTITLED
        is EntitlementState.Entitled ->
            when {
                isFreeTrial -> TIER_TRIAL
                source == EntitlementSource.ComplimentaryAccess -> TIER_COMPLIMENTARY
                else -> TIER_PLAY_SUBSCRIPTION
            }
    }

const val TIER_LOADING = "loading"
const val TIER_NOT_ENTITLED = "not_entitled"
const val TIER_TRIAL = "trial"
const val TIER_COMPLIMENTARY = "complimentary"
const val TIER_PLAY_SUBSCRIPTION = "play_subscription"
