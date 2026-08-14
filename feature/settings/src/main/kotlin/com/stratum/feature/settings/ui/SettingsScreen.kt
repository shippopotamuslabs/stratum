package com.stratum.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
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
import com.stratum.feature.settings.HealthConnectPermissionFocus
import com.stratum.feature.settings.PostWorkoutSuggestionUi
import com.stratum.feature.settings.R
import com.stratum.feature.settings.SettingsAccountActions
import com.stratum.feature.settings.SettingsDataActions
import com.stratum.feature.settings.SettingsUiState
import com.stratum.feature.settings.ThemeFamilyUi
import com.stratum.feature.settings.WeightUnitUi

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onBackClick: () -> Unit,
    onAppearanceSelected: (AppearanceUi) -> Unit,
    onThemeFamilySelected: (ThemeFamilyUi) -> Unit,
    onCheckInsEnabledChange: (Boolean) -> Unit = {},
    onPostWorkoutSuggestionEnabledChange: (PostWorkoutSuggestionUi, Boolean) -> Unit = { _, _ -> },
    onWeightUnitSelected: (WeightUnitUi) -> Unit = {},
    onHealthConnectEnabledChange: (Boolean) -> Unit = {},
    onRequestHealthConnectPermissions: (HealthConnectPermissionFocus) -> Unit = {},
    onForceCheckInPending: () -> Unit = {},
    onLegalClick: () -> Unit = {},
    dataActions: SettingsDataActions = SettingsDataActions(),
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
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingsGroup(title = stringResource(R.string.settings_look_feel_section)) {
                SettingsControlBlock(
                    label = stringResource(R.string.settings_appearance_title),
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
                SettingsGroupDivider()
                SettingsControlBlock(
                    label = stringResource(R.string.settings_theme_family_title),
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
            }

            SettingsGroup(title = stringResource(R.string.settings_training_section)) {
                SettingsControlBlock(
                    label = stringResource(R.string.settings_weight_unit_title),
                ) {
                    val weightUnitOptions = listOf(
                        WeightUnitUi.Lbs to stringResource(R.string.settings_weight_unit_lbs),
                        WeightUnitUi.Kg to stringResource(R.string.settings_weight_unit_kg),
                    )
                    StratumSegmentedControl(
                        modifier = Modifier.fillMaxWidth(),
                        segments = weightUnitOptions.map { it.second },
                        selectedIndex = weightUnitOptions.indexOfFirst { it.first == uiState.weightUnit }
                            .takeIf { it >= 0 },
                        onSelectedIndexChange = { index ->
                            onWeightUnitSelected(weightUnitOptions[index].first)
                        },
                    )
                }
                SettingsGroupDivider()
                StratumPreferenceRow(
                    title = stringResource(R.string.settings_checkin_enabled),
                    subtitle = stringResource(R.string.settings_checkin_subtitle),
                    trailing = {
                        StratumSwitch(
                            checked = uiState.checkInsEnabled,
                            onCheckedChange = onCheckInsEnabledChange,
                        )
                    },
                )
                SettingsGroupDivider()
                StratumPreferenceRow(
                    title = stringResource(R.string.settings_equipment_proposals_enabled),
                    subtitle = stringResource(R.string.settings_equipment_proposals_subtitle),
                    trailing = {
                        StratumSwitch(
                            checked = uiState.equipmentProposalsEnabled,
                            onCheckedChange = { enabled ->
                                onPostWorkoutSuggestionEnabledChange(
                                    PostWorkoutSuggestionUi.EquipmentSteps,
                                    enabled,
                                )
                            },
                        )
                    },
                )
                SettingsGroupDivider()
                StratumPreferenceRow(
                    title = stringResource(R.string.settings_rep_range_proposals_enabled),
                    subtitle = stringResource(R.string.settings_rep_range_proposals_subtitle),
                    trailing = {
                        StratumSwitch(
                            checked = uiState.repRangeProposalsEnabled,
                            onCheckedChange = { enabled ->
                                onPostWorkoutSuggestionEnabledChange(
                                    PostWorkoutSuggestionUi.RepRangeShift,
                                    enabled,
                                )
                            },
                        )
                    },
                )
            }

            HealthConnectSettingsGroup(
                uiState = uiState,
                onEnabledChange = onHealthConnectEnabledChange,
                onRequestPermissions = onRequestHealthConnectPermissions,
            )

            SettingsGroup(title = stringResource(R.string.settings_data_section_title)) {
                StratumPreferenceRow(
                    title = stringResource(R.string.settings_import_data_row_title),
                    subtitle = stringResource(R.string.settings_import_data_row_subtitle),
                    onClick = dataActions.onImportDataClick,
                )
                SettingsGroupDivider()
                StratumPreferenceRow(
                    title = stringResource(R.string.settings_export_data_row_title),
                    subtitle = stringResource(R.string.settings_export_data_row_subtitle),
                    onClick = dataActions.onExportDataClick,
                )
                SettingsGroupDivider()
                StratumPreferenceRow(
                    title = stringResource(R.string.settings_rebuild_progress_row_title),
                    subtitle =
                        stringResource(
                            when {
                                uiState.isForceRefreshingDerived ->
                                    R.string.settings_rebuild_progress_row_subtitle_busy
                                uiState.isForceRefreshCooldown ->
                                    R.string.settings_rebuild_progress_row_subtitle_queued
                                else -> R.string.settings_rebuild_progress_row_subtitle
                            },
                        ),
                    onClick =
                        if (uiState.isRebuildProgressLocked) {
                            null
                        } else {
                            dataActions.onRebuildProgressClick
                        },
                )
            }

            SettingsGroup(title = stringResource(R.string.settings_legal_section)) {
                StratumPreferenceRow(
                    title = stringResource(R.string.settings_legal_row_title),
                    subtitle = stringResource(R.string.settings_legal_row_subtitle),
                    onClick = onLegalClick,
                )
            }

            if (uiState.showDebugTools) {
                SettingsGroup(title = stringResource(R.string.settings_debug_title)) {
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

            SettingsGroup(title = stringResource(R.string.settings_account_title)) {
                StratumText(
                    text = stringResource(R.string.settings_account_description),
                    muted = true,
                    style = StratumThemeAccessor.typography.sectionLabel,
                )
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
        }
    }

    if (uiState.showRebuildProgressConfirm) {
        StratumConfirmDialog(
            title = stringResource(R.string.settings_rebuild_progress_confirm_title),
            body = stringResource(R.string.settings_rebuild_progress_confirm_body),
            confirmLabel = stringResource(R.string.settings_rebuild_progress_confirm),
            dismissLabel = stringResource(R.string.settings_rebuild_progress_cancel),
            onConfirm = dataActions.onRebuildProgressConfirmed,
            onDismiss = dataActions.onRebuildProgressConfirmDismiss,
            destructiveConfirm = false,
            confirmEnabled = !uiState.isForceRefreshingDerived,
        )
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
private fun HealthConnectSettingsGroup(
    uiState: SettingsUiState,
    onEnabledChange: (Boolean) -> Unit,
    onRequestPermissions: (HealthConnectPermissionFocus) -> Unit,
) {
    SettingsGroup(title = stringResource(R.string.settings_health_connect_section)) {
        StratumPreferenceRow(
            title = stringResource(R.string.settings_health_connect_enabled),
            subtitle = stringResource(
                if (uiState.healthConnectEnabled) {
                    R.string.settings_health_connect_subtitle_on
                } else {
                    R.string.settings_health_connect_subtitle_off
                },
            ),
            trailing = {
                StratumSwitch(
                    checked = uiState.healthConnectEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            onRequestPermissions(HealthConnectPermissionFocus.All)
                        } else {
                            onEnabledChange(false)
                        }
                    },
                )
            },
        )
        if (uiState.healthConnectEnabled) {
            SettingsGroupDivider()
            StratumPreferenceRow(
                title = stringResource(R.string.settings_health_connect_write_title),
                subtitle = stringResource(
                    if (uiState.healthConnectWriteGranted) {
                        R.string.settings_health_connect_write_allowed
                    } else {
                        R.string.settings_health_connect_write_needed
                    },
                ),
                onClick = { onRequestPermissions(HealthConnectPermissionFocus.Write) },
            )
            SettingsGroupDivider()
            StratumPreferenceRow(
                title = stringResource(R.string.settings_health_connect_reads_title),
                subtitle = stringResource(
                    if (uiState.healthConnectReadsGranted) {
                        R.string.settings_health_connect_reads_allowed
                    } else {
                        R.string.settings_health_connect_reads_needed
                    },
                ),
                onClick = { onRequestPermissions(HealthConnectPermissionFocus.Reads) },
            )
        }
    }
}

/**
 * Muted section label above a raised bordered group card.
 * Matches import/export and review card chrome elsewhere in the app.
 */
@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StratumText(
            text = title,
            muted = true,
            style = StratumThemeAccessor.typography.sectionLabel,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(color = colors.surfaceRaised, shape = shapes.panel)
                .border(
                    width = shapes.borderWidth,
                    color = colors.borderSubtle,
                    shape = shapes.panel,
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsControlBlock(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StratumText(
            text = label,
            muted = true,
            style = StratumThemeAccessor.typography.sectionLabel,
        )
        content()
    }
}

@Composable
private fun SettingsGroupDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(StratumThemeAccessor.shapes.borderWidth)
            .background(StratumThemeAccessor.colors.borderSubtle),
    )
}
