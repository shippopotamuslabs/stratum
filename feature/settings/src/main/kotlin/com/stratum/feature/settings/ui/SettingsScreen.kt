package com.stratum.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumSegmentedControl
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.StratumTopBarBackButton
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.settings.AppearanceUi
import com.stratum.feature.settings.R
import com.stratum.feature.settings.SettingsUiState
import com.stratum.feature.settings.ThemeFamilyUi

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onBackClick: () -> Unit,
    onAppearanceSelected: (AppearanceUi) -> Unit,
    onThemeFamilySelected: (ThemeFamilyUi) -> Unit,
) {
    StratumScaffold(
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
        }
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
