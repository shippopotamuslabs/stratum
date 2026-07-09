package com.stratum.feature.settings.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.stratum.core.ui.component.UserMessageEffect
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
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    UserMessageEffect(
        userMessage = userMessage,
        snackbarHostState = snackbarHostState,
        onUserMessageShown = viewModel::userMessageShown,
    )
    SettingsScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onAppearanceSelected = viewModel::onAppearanceSelected,
        onThemeFamilySelected = viewModel::onThemeFamilySelected,
        onCheckInsEnabledChange = viewModel::onCheckInsEnabledChange,
        onForceCheckInPending = viewModel::forceCheckInPending,
        snackbarHostState = snackbarHostState,
    )
}
