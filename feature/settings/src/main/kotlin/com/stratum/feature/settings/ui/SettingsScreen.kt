package com.stratum.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumConfirmDialog
import com.stratum.core.ui.component.StratumPreferenceRow
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumSegmentedControl
import com.stratum.core.ui.component.StratumSwitch
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.StratumTopBarBackButton
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.settings.AppearanceUi
import com.stratum.feature.settings.PostWorkoutSuggestionUi
import com.stratum.feature.settings.R
import com.stratum.feature.settings.SettingsAccountActions
import com.stratum.feature.settings.SettingsUiState
import com.stratum.feature.settings.ThemeFamilyUi

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onBackClick: () -> Unit,
    onAppearanceSelected: (AppearanceUi) -> Unit,
    onThemeFamilySelected: (ThemeFamilyUi) -> Unit,
    onCheckInsEnabledChange: (Boolean) -> Unit = {},
    onPostWorkoutSuggestionEnabledChange: (PostWorkoutSuggestionUi, Boolean) -> Unit = { _, _ -> },
    onForceCheckInPending: () -> Unit = {},
    onLegalClick: () -> Unit = {},
    accountActions: SettingsAccountActions = SettingsAccountActions(),
    snackbarHostState: SnackbarHostState? = null,
) {
    val accountBusy = uiState.isLoggingOut || uiState.isDeletingAccount
    StratumScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            StratumTopBar(
                title = stringResource(R.string.settings_title),
                navigationIcon = {
                    StratumTopBarBackButton(
                        onClick = onBackClick,
                        contentDescription = stringResource(R.string.settings_navigate_back),
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SettingsSection(
                title = stringResource(R.string.settings_appearance_title),
                description = stringResource(R.string.settings_appearance_description),
            ) {
                val appearanceOptions = listOf(
                    AppearanceUi.System to stringResource(R.string.settings_appearance_system),
                    AppearanceUi.Light to stringResource(R.string.settings_appearance_light),
                    AppearanceUi.Dark to stringResource(R.string.settings_appearance_dark),
                )
                StratumSegmentedControl(
                    modifier = Modifier.fillMaxWidth(),
                    segments = appearanceOptions.map { it.second },
                    selectedIndex = appearanceOptions.indexOfFirst { it.first == uiState.appearance }
                        .takeIf { it >= 0 },
                    onSelectedIndexChange = { index ->
                        onAppearanceSelected(appearanceOptions[index].first)
                    },
                )
            }

            SettingsSection(
                title = stringResource(R.string.settings_theme_family_title),
                description = stringResource(R.string.settings_theme_family_description),
            ) {
                val familyOptions = listOf(
                    ThemeFamilyUi.Stratum to stringResource(R.string.settings_theme_family_stratum),
                    ThemeFamilyUi.NeoBrutalist to
                        stringResource(R.string.settings_theme_family_neo_brutalist),
                )
                StratumSegmentedControl(
                    modifier = Modifier.fillMaxWidth(),
                    segments = familyOptions.map { it.second },
                    selectedIndex = familyOptions.indexOfFirst { it.first == uiState.themeFamily }
                        .takeIf { it >= 0 },
                    onSelectedIndexChange = { index ->
                        onThemeFamilySelected(familyOptions[index].first)
                    },
                )
            }

            SettingsSection(
                title = stringResource(R.string.settings_checkin_title),
                description = stringResource(R.string.settings_checkin_description),
            ) {
                SettingsSwitchRow(
                    label = stringResource(R.string.settings_checkin_enabled),
                    checked = uiState.checkInsEnabled,
                    onCheckedChange = onCheckInsEnabledChange,
                )
            }

            SettingsSection(
                title = stringResource(R.string.settings_post_workout_title),
                description = stringResource(R.string.settings_post_workout_description),
            ) {
                SettingsSwitchRow(
                    label = stringResource(R.string.settings_equipment_proposals_enabled),
                    checked = uiState.equipmentProposalsEnabled,
                    onCheckedChange = { enabled ->
                        onPostWorkoutSuggestionEnabledChange(
                            PostWorkoutSuggestionUi.EquipmentSteps,
                            enabled,
                        )
                    },
                )
                SettingsSwitchRow(
                    label = stringResource(R.string.settings_rep_range_proposals_enabled),
                    checked = uiState.repRangeProposalsEnabled,
                    onCheckedChange = { enabled ->
                        onPostWorkoutSuggestionEnabledChange(
                            PostWorkoutSuggestionUi.RepRangeShift,
                            enabled,
                        )
                    },
                )
            }

            if (uiState.showDebugTools) {
                SettingsSection(
                    title = stringResource(R.string.settings_debug_title),
                    description = stringResource(R.string.settings_debug_description),
                ) {
                    StratumButton(
                        text = stringResource(R.string.settings_debug_force_checkin),
                        onClick = onForceCheckInPending,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    uiState.debugStatusMessage?.let { message ->
                        StratumText(
                            text = message,
                            muted = true,
                            style = StratumThemeAccessor.typography.sectionLabel,
                        )
                    }
                }
            }

            SettingsSection(
                title = stringResource(R.string.settings_account_title),
                description = stringResource(R.string.settings_account_description),
            ) {
                StratumButton(
                    text = stringResource(
                        if (uiState.isLoggingOut) {
                            R.string.settings_logout_in_progress
                        } else {
                            R.string.settings_logout
                        },
                    ),
                    onClick = accountActions.onLogoutClick,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !accountBusy,
                    destructive = true,
                )
                StratumButton(
                    text = stringResource(
                        if (uiState.isDeletingAccount) {
                            R.string.settings_delete_account_in_progress
                        } else {
                            R.string.settings_delete_account
                        },
                    ),
                    onClick = accountActions.onDeleteAccountClick,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !accountBusy,
                    destructive = true,
                )
            }

            StratumPreferenceRow(
                title = stringResource(R.string.settings_legal_row_title),
                subtitle = stringResource(R.string.settings_legal_row_subtitle),
                onClick = onLegalClick,
            )
        }
    }

    if (uiState.showLogoutConfirm) {
        StratumConfirmDialog(
            title = stringResource(R.string.settings_logout_confirm_title),
            body = stringResource(R.string.settings_logout_confirm_body),
            confirmLabel = stringResource(R.string.settings_logout_confirm),
            dismissLabel = stringResource(R.string.settings_logout_cancel),
            onConfirm = accountActions.onLogoutConfirmed,
            onDismiss = accountActions.onLogoutConfirmDismiss,
            destructiveConfirm = true,
            confirmEnabled = !accountBusy,
        )
    }

    if (uiState.showDeleteConfirm) {
        StratumConfirmDialog(
            title = stringResource(R.string.settings_delete_account_confirm_title),
            body = stringResource(R.string.settings_delete_account_confirm_body),
            confirmLabel = stringResource(R.string.settings_delete_account_confirm),
            dismissLabel = stringResource(R.string.settings_delete_account_cancel),
            onConfirm = accountActions.onDeleteAccountConfirmed,
            onDismiss = accountActions.onDeleteAccountConfirmDismiss,
            destructiveConfirm = true,
            confirmEnabled = !accountBusy,
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StratumText(
            text = title,
            style = StratumThemeAccessor.typography.titleMedium,
        )
        StratumText(
            text = description,
            muted = true,
            style = StratumThemeAccessor.typography.sectionLabel,
        )
        content()
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StratumText(
            text = label,
            style = StratumThemeAccessor.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        StratumSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
