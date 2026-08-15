package com.stratum.feature.subscription.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.stratum.feature.subscription.ui.PaywallRoute

fun EntryProviderScope<NavKey>.paywallEntry(
    onDismiss: () -> Unit,
    onNavigateToTerms: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
) {
    entry<PaywallKey> { key ->
        PaywallRoute(
            onDismiss = onDismiss,
            onNavigateToTerms = onNavigateToTerms,
            onNavigateToPrivacy = onNavigateToPrivacy,
            isDismissable = key.isDismissable,
        )
    }
}
