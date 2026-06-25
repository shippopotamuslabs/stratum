package com.stratum.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.onboarding.GymLiftsDraftUi
import com.stratum.feature.onboarding.GymPresetOptionUi
import com.stratum.feature.onboarding.R

/**
 * Step 5 of the wizard. The user picks a gym preset which bulk-enables a bundled lift set.
 */
@Composable
fun GymAndLiftsScreen(
    draft: GymLiftsDraftUi,
    presetOptions: List<GymPresetOptionUi>,
    onPresetSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StratumText(
            text = stringResource(R.string.gym_lifts_intro),
            muted = true,
        )

        presetOptions.forEach { preset ->
            GymPresetCard(
                option = preset,
                selected = draft.selectedPresetLabel == preset.label,
                onClick = { onPresetSelected(preset.label) },
            )
        }

        if (draft.showNoPresetError) {
            StratumText(
                text = stringResource(R.string.gym_lifts_error_no_preset),
                muted = true,
            )
        }
    }
}

@Composable
private fun GymPresetCard(
    option: GymPresetOptionUi,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (selected) {
        StratumThemeAccessor.colors.accent
    } else {
        StratumThemeAccessor.colors.borderSubtle
    }
    val borderWidth = if (selected) SELECTED_BORDER_WIDTH else StratumThemeAccessor.shapes.borderWidth

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                color = StratumThemeAccessor.colors.surfaceRaised,
                shape = StratumThemeAccessor.shapes.panel,
            )
            .border(
                width = borderWidth,
                color = borderColor,
                shape = StratumThemeAccessor.shapes.panel,
            )
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StratumText(
            text = option.label,
            style = StratumThemeAccessor.typography.titleLarge,
        )
        StratumText(
            text = option.description,
            muted = true,
        )
    }
}

private val SELECTED_BORDER_WIDTH = 2.dp
