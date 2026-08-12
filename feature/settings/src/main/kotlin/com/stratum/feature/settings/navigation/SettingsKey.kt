package com.stratum.feature.settings.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.health.connect.client.PermissionController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.stratum.core.ui.component.UserMessageEffect
import com.stratum.feature.settings.HealthConnectLaunchPermissions
import com.stratum.feature.settings.SettingsAccountActions
import com.stratum.feature.settings.SettingsDataActions
import com.stratum.feature.settings.SettingsViewModel
import com.stratum.feature.settings.ui.LegalScreen
import com.stratum.feature.settings.ui.SettingsScreen
import kotlinx.serialization.Serializable

/** Nav3 key for the Settings screen. */
@Serializable
data object SettingsKey : NavKey

/** Nav3 key for the Legal destinations list (Terms, Privacy, OSS licenses). */
@Serializable
data object LegalKey : NavKey

/**
 * Registers the Settings entry in a Nav3 [EntryProviderScope]. Back is handled by the app
 * shell via [onBackClick]; Legal / Import / Export hubs via navigation callbacks.
 */
fun EntryProviderScope<NavKey>.settingsEntry(
    onBackClick: () -> Unit,
    onNavigateToLegal: () -> Unit,
    onNavigateToImportData: () -> Unit = {},
    onNavigateToExportData: () -> Unit = {},
) {
    entry<SettingsKey> {
        SettingsRoute(
            onBackClick = onBackClick,
            onNavigateToLegal = onNavigateToLegal,
            onNavigateToImportData = onNavigateToImportData,
            onNavigateToExportData = onNavigateToExportData,
        )
    }
}

/** Registers the Legal list entry. */
fun EntryProviderScope<NavKey>.legalEntry(
    onBackClick: () -> Unit,
) {
    entry<LegalKey> {
        LegalScreen(onBackClick = onBackClick)
    }
}

@Composable
private fun SettingsRoute(
    onBackClick: () -> Unit,
    onNavigateToLegal: () -> Unit,
    onNavigateToImportData: () -> Unit,
    onNavigateToExportData: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val healthConnectRequestedPermissions = remember { HealthConnectLaunchPermissions.all }
    val healthConnectPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) {
        viewModel.onHealthConnectPermissionSheetClosed()
    }
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
        onPostWorkoutSuggestionEnabledChange = viewModel::onPostWorkoutSuggestionEnabledChange,
        onWeightUnitSelected = viewModel::onWeightUnitSelected,
        onHealthConnectEnabledChange = viewModel::onHealthConnectEnabledChange,
        onRequestHealthConnectEnable = {
            healthConnectPermissionLauncher.launch(healthConnectRequestedPermissions)
        },
        onForceCheckInPending = viewModel::forceCheckInPending,
        onLegalClick = onNavigateToLegal,
        dataActions =
            SettingsDataActions(
                onImportDataClick = onNavigateToImportData,
                onExportDataClick = onNavigateToExportData,
                onRebuildProgressClick = viewModel::onRebuildProgressClick,
                onRebuildProgressConfirmDismiss = viewModel::onRebuildProgressConfirmDismiss,
                onRebuildProgressConfirmed = viewModel::onRebuildProgressConfirmed,
            ),
        accountActions =
            SettingsAccountActions(
                onLogoutClick = viewModel::onLogoutClick,
                onLogoutConfirmDismiss = viewModel::onLogoutConfirmDismiss,
                onLogoutConfirmed = viewModel::onLogoutConfirmed,
                onDeleteAccountClick = viewModel::onDeleteAccountClick,
                onDeleteAccountConfirmDismiss = viewModel::onDeleteAccountConfirmDismiss,
                onDeleteAccountConfirmed = viewModel::onDeleteAccountConfirmed,
            ),
        snackbarHostState = snackbarHostState,
    )
}
