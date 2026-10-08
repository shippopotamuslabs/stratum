package com.stratum.core.ui.component.platecalculator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.theme.StratumThemeId
import com.stratum.domain.lift.PlateCalculator

@PreviewLightDark
@Composable
private fun StratumPlateBarbellDiagramLoadedPreview() {
    PlateDiagramPreviewContent(family = StratumThemeId.Tonal, targetWeight = 225.0)
}

@PreviewLightDark
@Composable
private fun StratumPlateBarbellDiagramEmptyPreview() {
    PlateDiagramPreviewContent(family = StratumThemeId.Tonal, targetWeight = null)
}

@PreviewLightDark
@Composable
private fun StratumPlateCalculatorMiniDrawerPreview() {
    PlateMiniDrawerPreviewContent(family = StratumThemeId.Tonal, targetWeight = 225.0)
}

@PreviewLightDark
@Composable
private fun StratumPlateCalculatorMiniDrawerMismatchPreview() {
    PlateMiniDrawerPreviewContent(family = StratumThemeId.Tonal, targetWeight = 153.0)
}

@PreviewLightDark
@Composable
private fun StratumPlateCalculatorSheetPreview() {
    PlateSheetPreviewContent(family = StratumThemeId.Tonal)
}

@Composable
internal fun PlateDiagramPreviewContent(
    family: StratumThemeId,
    targetWeight: Double?,
) {
    val platesPerSide =
        targetWeight?.let {
            computePlateLoadUi(
                targetWeight = it,
                barWeight = 45.0,
                availablePlates = PlateCalculator.DEFAULT_PLATES_LB,
                unitLabel = "lb",
            ).platesPerSide
        } ?: emptyList()
    StratumTheme(themeId = family) {
        Column(
            modifier = Modifier
                .background(StratumThemeAccessor.colors.surfaceBase)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StratumPlateBarbellDiagram(
                platesPerSide = platesPerSide,
                isMini = false,
            )
            StratumPlateBarbellDiagram(
                platesPerSide = platesPerSide,
                isMini = true,
            )
        }
    }
}

@Composable
internal fun PlateMiniDrawerPreviewContent(
    family: StratumThemeId,
    targetWeight: Double,
) {
    val plateLoad = computePlateLoadUi(
        targetWeight = targetWeight,
        barWeight = 45.0,
        availablePlates = PlateCalculator.DEFAULT_PLATES_LB,
        unitLabel = "lb",
    )
    StratumTheme(themeId = family) {
        Box(
            modifier = Modifier
                .background(StratumThemeAccessor.colors.surfaceBase)
                .padding(12.dp),
        ) {
            StratumPlateCalculatorMiniDrawer(
                plateLoad = plateLoad,
                onExpandClick = {},
                onCloseClick = {},
            )
        }
    }
}

/**
 * Pins the sheet body inline: dialog windows are uncapturable in screenshot goldens
 * (STR-89 pattern).
 */
@Composable
internal fun PlateSheetPreviewContent(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        StratumPlateCalculatorSheetContent(
            initialTargetWeight = 225.0,
            unitLabel = "lb",
            onDismissRequest = {},
            onApplyWeight = { _, _ -> },
        )
    }
}
