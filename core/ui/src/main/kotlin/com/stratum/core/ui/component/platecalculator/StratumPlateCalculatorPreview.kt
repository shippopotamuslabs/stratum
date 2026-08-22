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
    val plateLoad = computePlateLoadUi(
        targetWeight = 225.0,
        barWeight = 45.0,
        availablePlates = PlateCalculator.DEFAULT_PLATES_LB,
        unitLabel = "lb",
    )
    StratumTheme(themeId = StratumThemeId.Tonal) {
        Column(
            modifier = Modifier
                .background(StratumThemeAccessor.colors.surfaceBase)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StratumPlateBarbellDiagram(
                platesPerSide = plateLoad.platesPerSide,
                isMini = false,
            )
            StratumPlateBarbellDiagram(
                platesPerSide = plateLoad.platesPerSide,
                isMini = true,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun StratumPlateBarbellDiagramEmptyPreview() {
    StratumTheme(themeId = StratumThemeId.Tonal) {
        Column(
            modifier = Modifier
                .background(StratumThemeAccessor.colors.surfaceBase)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StratumPlateBarbellDiagram(
                platesPerSide = emptyList(),
                isMini = false,
            )
            StratumPlateBarbellDiagram(
                platesPerSide = emptyList(),
                isMini = true,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun StratumPlateCalculatorMiniDrawerPreview() {
    val plateLoad = computePlateLoadUi(
        targetWeight = 225.0,
        barWeight = 45.0,
        availablePlates = PlateCalculator.DEFAULT_PLATES_LB,
        unitLabel = "lb",
    )
    StratumTheme(themeId = StratumThemeId.Tonal) {
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

@PreviewLightDark
@Composable
private fun StratumPlateCalculatorMiniDrawerMismatchPreview() {
    val plateLoad = computePlateLoadUi(
        targetWeight = 153.0,
        barWeight = 45.0,
        availablePlates = PlateCalculator.DEFAULT_PLATES_LB,
        unitLabel = "lb",
    )
    StratumTheme(themeId = StratumThemeId.Tonal) {
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

@PreviewLightDark
@Composable
private fun StratumPlateCalculatorSheetPreview() {
    StratumTheme(themeId = StratumThemeId.Tonal) {
        StratumPlateCalculatorSheet(
            initialTargetWeight = 225.0,
            unitLabel = "lb",
            onDismissRequest = {},
            onApplyWeight = { _, _ -> },
        )
    }
}
