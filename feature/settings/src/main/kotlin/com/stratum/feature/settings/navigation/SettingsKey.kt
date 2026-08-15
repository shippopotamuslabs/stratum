package com.stratum.feature.settings.navigation

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.HealthConnectClient
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.stratum.core.ui.component.UserMessageEffect
import com.stratum.feature.settings.HealthConnectPermissionAction
import com.stratum.feature.settings.HealthConnectPermissionRequestContract
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
    onNavigateToPaywall: () -> Unit = {},
) {
    entry<SettingsKey> {
        SettingsRoute(
            onBackClick = onBackClick,
            onNavigateToLegal = onNavigateToLegal,
            onNavigateToImportData = onNavigateToImportData,
            onNavigateToExportData = onNavigateToExportData,
            onNavigateToPaywall = onNavigateToPaywall,
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
    onNavigateToPaywall: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val healthConnectPermissionContract = remember { HealthConnectPermissionRequestContract() }
    val healthConnectPermissionLauncher = rememberLauncherForActivityResult(
        contract = healthConnectPermissionContract,
    ) {
        viewModel.onHealthConnectPermissionSheetClosed()
    }
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshHealthConnectGrants()
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel) {
        viewModel.healthConnectPermissionAction.collect { action ->
            when (action) {
                is HealthConnectPermissionAction.Request -> {
                    healthConnectPermissionLauncher.launch(action.permissions)
                }
                HealthConnectPermissionAction.OpenSettings -> {
                    try {
                        context.startActivity(
                            Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS),
                        )
                    } catch (_: ActivityNotFoundException) {
                        viewModel.onHealthConnectSettingsUnavailable()
                    }
                }
            }
        }
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
        onRequestHealthConnectPermissions = viewModel::onRequestHealthConnectPermissions,
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
                onMembershipClick = onNavigateToPaywall,
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
