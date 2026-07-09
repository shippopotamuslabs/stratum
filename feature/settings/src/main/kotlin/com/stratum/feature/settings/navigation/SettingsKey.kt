package com.stratum.feature.settings.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.stratum.feature.settings.SettingsViewModel
import com.stratum.feature.settings.ui.SettingsScreen
import kotlinx.serialization.Serializable

/** Nav3 key for the Settings screen. */
@Serializable
data object SettingsKey : NavKey

/**
 * Registers the Settings entry in a Nav3 [EntryProviderScope]. Back is handled by the app
 * shell via [onBackClick].
 */
fun EntryProviderScope<NavKey>.settingsEntry(
    onBackClick: () -> Unit,
) {
    entry<SettingsKey> {
        SettingsRoute(onBackClick = onBackClick)
    }
}

@Composable
private fun SettingsRoute(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onAppearanceSelected = viewModel::onAppearanceSelected,
        onThemeFamilySelected = viewModel::onThemeFamilySelected,
    )
}
