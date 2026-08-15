package com.stratum.domain.subscription

enum class EntitlementSource {
    GooglePlaySubscription,
    ComplimentaryAccess,
}

sealed interface EntitlementState {
    data object Loading : EntitlementState

    data class Entitled(
        val source: EntitlementSource,
        val isFreeTrial: Boolean = false,
        val expiresAtEpochMillis: Long? = null,
    ) : EntitlementState

    data class NotEntitled(
        val reason: String? = null,
    ) : EntitlementState
}
