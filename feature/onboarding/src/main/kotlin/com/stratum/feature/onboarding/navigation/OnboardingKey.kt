package com.stratum.feature.onboarding.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.stratum.core.ui.component.StratumGlassHost
import com.stratum.feature.onboarding.OnboardingRoute
import kotlinx.serialization.Serializable

/** Nav3 key for the onboarding wizard. */
@Serializable
data object OnboardingKey : NavKey

/**
 * Registers the onboarding entry in a Nav3 [EntryProviderScope]. Completion is handled by
 * the app shell via [onComplete] (typically navigating to main tabs).
 * [onBuildFromScratch] opens Create Program in scratch mode without leaving the onboarding stack
 * underneath (back returns to Choose Path).
 */
fun EntryProviderScope<NavKey>.onboardingEntry(
    onComplete: () -> Unit,
    onBuildFromScratch: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    entry<OnboardingKey> {
        // Glass host (STR-161, §12.8): one HazeState for this route, provided
        // at the detail-route level — same host pattern as the other routes.
        StratumGlassHost {
            OnboardingRoute(
                onComplete = onComplete,
                onBuildFromScratch = onBuildFromScratch,
                modifier = modifier,
            )
        }
    }
}
