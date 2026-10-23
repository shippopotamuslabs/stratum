package com.stratum.feature.subscription.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.stratum.core.ui.component.StratumGlassHost
import com.stratum.feature.subscription.ui.PaywallRoute

fun EntryProviderScope<NavKey>.paywallEntry(
    onDismiss: () -> Unit,
    onNavigateToTerms: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
) {
    entry<PaywallKey> { key ->
        // Glass host (STR-161, §12.8): one HazeState for this route, provided
        // at the detail-route level — same host pattern as the other routes.
        StratumGlassHost {
            PaywallRoute(
                onDismiss = onDismiss,
                onNavigateToTerms = onNavigateToTerms,
                onNavigateToPrivacy = onNavigateToPrivacy,
                isDismissable = key.isDismissable,
            )
        }
    }
}
