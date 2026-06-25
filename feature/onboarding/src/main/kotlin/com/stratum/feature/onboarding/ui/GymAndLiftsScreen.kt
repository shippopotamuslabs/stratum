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
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.domain.lift.GymPreset

private val PRESET_OPTIONS = GymPreset.entries

/**
 * Step 5 of the wizard. The user picks a gym preset (Commercial gym or Garage gym) which
 * bulk-enables a bundled lift set for generation. Two large tappable cards with accent border
 * highlighting when selected.
 *
 * No lift list, search, or custom-lift form — full catalog management is deferred to a
 * post-onboarding settings screen (OS-4b design decision: 619 lifts is too many to audit
 * mid-onboarding; the edit loop in OS-7/OS-8 handles gym-specific substitutions).
 *
 * The step is valid once a preset is selected. The preset is applied immediately through the
 * ViewModel, so navigation back/forward composes correctly.
 */
@Composable
fun GymAndLiftsScreen(
    selectedPresetLabel: String?,
    onPresetSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StratumText(
            text = "Pick your gym type. You can fine-tune lifts later.",
            muted = true,
        )

        PRESET_OPTIONS.forEach { preset ->
            GymPresetCard(
                preset = preset,
                selected = selectedPresetLabel == preset.displayName,
                onClick = { onPresetSelected(preset.displayName) },
            )
        }
    }
}

/**
 * A large tappable card for one gym preset. When selected, the border switches to the accent
 * color at 2dp to clearly indicate the active choice; unselected cards use the subtle border at
 * the theme's default border width.
 */
@Composable
private fun GymPresetCard(
    preset: GymPreset,
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
            text = preset.displayName,
            style = StratumThemeAccessor.typography.titleLarge,
        )
        StratumText(
            text = presetDescription(preset),
            muted = true,
        )
    }
}

private fun presetDescription(preset: GymPreset): String = when (preset) {
    GymPreset.CommercialGym -> "Full equipment: barbells, dumbbells, cables, and machines."
    GymPreset.GarageGym -> "Home setup: barbells, dumbbells, and bodyweight. No cables or machines."
}

private val SELECTED_BORDER_WIDTH = 2.dp
