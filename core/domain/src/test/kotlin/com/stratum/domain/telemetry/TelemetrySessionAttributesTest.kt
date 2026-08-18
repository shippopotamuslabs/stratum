package com.stratum.domain.telemetry

import com.stratum.domain.subscription.EntitlementSource
import com.stratum.domain.subscription.EntitlementState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TelemetrySessionAttributesTest {
    @Test
    fun `maps entitlement states to stable non-PII tiers`() {
        assertEquals(TIER_LOADING, EntitlementState.Loading.toTelemetryTier())
        assertEquals(TIER_NOT_ENTITLED, EntitlementState.NotEntitled().toTelemetryTier())
        assertEquals(
            TIER_TRIAL,
            EntitlementState.Entitled(
                source = EntitlementSource.GooglePlaySubscription,
                isFreeTrial = true,
            ).toTelemetryTier(),
        )
        assertEquals(
            TIER_COMPLIMENTARY,
            EntitlementState.Entitled(EntitlementSource.ComplimentaryAccess).toTelemetryTier(),
        )
        assertEquals(
            TIER_PLAY_SUBSCRIPTION,
            EntitlementState.Entitled(EntitlementSource.GooglePlaySubscription).toTelemetryTier(),
        )
    }
}
