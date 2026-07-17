package com.stratum.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.theme.stratumCardHardShadow
import com.stratum.feature.onboarding.R

/**
 * The path-fork step. The user chooses how to finish onboarding:
 * - **Generate**: walk through Schedule → Specialization → Minimum Volume, then generate a program.
 * - **Build From Scratch**: skip generation prefs and open the Create Program editor (app navigation).
 * - **Import**: skip those steps and import an existing program file (CSV/XLSX) directly.
 *
 * Each option is a tappable card built with the same surface-raised + bordered-panel idiom the
 * Review step uses for its summary cards, so the choice cards and summary cards read as one system.
 */
@Composable
internal fun ChoosePathScreen(
    onSelectGenerate: () -> Unit,
    onSelectScratch: () -> Unit,
    onSelectImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StratumText(
            text = stringResource(R.string.choose_path_title),
            style = StratumThemeAccessor.typography.titleLarge,
        )

        ChoosePathCard(
            title = stringResource(R.string.choose_path_generate_title),
            description = stringResource(R.string.choose_path_generate_description),
            onClick = onSelectGenerate,
        )
        ChoosePathCard(
            title = stringResource(R.string.choose_path_scratch_title),
            description = stringResource(R.string.choose_path_scratch_description),
            onClick = onSelectScratch,
        )
        ChoosePathCard(
            title = stringResource(R.string.choose_path_import_title),
            description = stringResource(R.string.choose_path_import_description),
            onClick = onSelectImport,
        )
    }
}

@Composable
private fun ChoosePathCard(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val elevations = StratumThemeAccessor.elevations
    val borderColor =
        if (elevations.useHardShadow) colors.borderStrong else colors.borderSubtle
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .stratumCardHardShadow(shape = shapes.panel)
            .background(
                color = colors.surfaceRaised,
                shape = shapes.panel,
            )
            .border(
                width = shapes.borderWidth,
                color = borderColor,
                shape = shapes.panel,
            )
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PathArrow()
            Spacer(modifier = Modifier.size(12.dp))
            StratumText(text = title, style = StratumThemeAccessor.typography.titleLarge)
        }
        StratumText(text = description, muted = true)
    }
}

/** A small accent chevron marking the card as tappable. */
@Composable
private fun PathArrow() {
    Row(
        modifier = Modifier
            .size(28.dp)
            .background(
                color = StratumThemeAccessor.colors.accent,
                shape = StratumThemeAccessor.shapes.chip,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StratumText(
            text = "›",
            style = StratumThemeAccessor.typography.titleLarge.copy(
                color = StratumThemeAccessor.colors.onAccent,
            ),
        )
    }
}
