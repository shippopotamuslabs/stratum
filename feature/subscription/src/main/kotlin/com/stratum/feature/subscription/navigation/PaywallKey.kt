package com.stratum.feature.subscription.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data class PaywallKey(
    val isDismissable: Boolean = true,
) : NavKey
