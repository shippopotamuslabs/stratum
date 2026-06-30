package com.stratum.feature.program.navigation

import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.stratum.feature.program.ProgramsRoute
import kotlinx.serialization.Serializable

/** Nav3 key for the read-only Programs overview (post-onboarding landing, OS-7 D3). */
@Serializable
data object ProgramsKey : NavKey

/**
 * Registers the Programs entry in a Nav3 [EntryProviderScope]. Owned by `:feature:program`; the app
 * shell routes to [ProgramsKey] on onboarding completion.
 */
fun EntryProviderScope<NavKey>.programsEntry(
    onBackToOnboarding: () -> Unit,
    modifier: Modifier = Modifier,
) {
    entry<ProgramsKey> {
        ProgramsRoute(
            onBackToOnboarding = onBackToOnboarding,
            modifier = modifier,
        )
    }
}
