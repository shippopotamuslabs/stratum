package com.stratum.feature.onboarding.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.stratum.feature.onboarding.OnboardingRoute
import kotlinx.serialization.Serializable

/** Nav3 key for the onboarding wizard. */
@Serializable
data object OnboardingKey : NavKey

/** Nav3 key for the home placeholder (owned here as the post-onboarding target). */
@Serializable
data object HomeKey : NavKey

/**
 * Registers the onboarding entry in a Nav3 [EntryProviderScope]. Completion navigates by
 * appending [HomeKey] to the back stack via [onComplete].
 */
fun EntryProviderScope<NavKey>.onboardingEntry(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    entry<OnboardingKey> {
        OnboardingRoute(
            onComplete = onComplete,
            modifier = modifier,
        )
    }
}
