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
    PlateDiagramLoadedPreviewContent(family = StratumThemeId.Tonal, targetWeight = 225.0)
}

@PreviewLightDark
@Composable
private fun StratumPlateBarbellDiagramEmptyPreview() {
    PlateDiagramEmptyPreviewContent(family = StratumThemeId.Tonal)
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

private const val PREVIEW_BAR_WEIGHT = 45.0
private const val PREVIEW_UNIT_LABEL = "lb"
private val previewAvailablePlates = PlateCalculator.DEFAULT_PLATES_LB

private fun previewPlateLoad(targetWeight: Double) =
    computePlateLoadUi(
        targetWeight = targetWeight,
        barWeight = PREVIEW_BAR_WEIGHT,
        availablePlates = previewAvailablePlates,
        unitLabel = PREVIEW_UNIT_LABEL,
    )

/** Loaded barbell diagram (full + mini) for an achievable target weight. */
@Composable
internal fun PlateDiagramLoadedPreviewContent(
    family: StratumThemeId,
    targetWeight: Double,
) {
    PlateDiagramPreviewContent(
        family = family,
        platesPerSide = previewPlateLoad(targetWeight).platesPerSide,
    )
}

/** Barbell diagram (full + mini) with no plates loaded. */
@Composable
internal fun PlateDiagramEmptyPreviewContent(family: StratumThemeId) {
    PlateDiagramPreviewContent(family = family, platesPerSide = emptyList())
}

@Composable
internal fun PlateDiagramPreviewContent(
    family: StratumThemeId,
    platesPerSide: List<PlateItemUi>,
) {
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
    val plateLoad = previewPlateLoad(targetWeight)
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
