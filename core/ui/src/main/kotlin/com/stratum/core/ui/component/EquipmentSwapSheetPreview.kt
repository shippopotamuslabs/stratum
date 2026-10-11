package com.stratum.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.theme.StratumThemeId

@PreviewLightDark
@Composable
private fun EquipmentSwapSheetTonalPreview() {
    EquipmentSwapPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun EquipmentSwapSheetNeoPreview() {
    EquipmentSwapPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewLightDark
@Composable
private fun EquipmentSwapSheetLargeCatalogTonalPreview() {
    EquipmentSwapLargeCatalogPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun EquipmentSwapSheetLargeCatalogNeoPreview() {
    EquipmentSwapLargeCatalogPreviewContent(family = StratumThemeId.NeoBrutalist)
}

/**
 * Large-catalog regression: the list must scroll internally while the create-new
 * entry stays visible (the [LazyColumn][androidx.compose.foundation.lazy.LazyColumn]
 * is weighted, not greedy).
 */
@Composable
fun EquipmentSwapLargeCatalogPreviewContent(family: StratumThemeId) {
    val options = List(40) { index -> EquipmentSwapOption(id = "m-$index", name = "Machine %02d".format(index + 1)) }
    StratumTheme(themeId = family) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(StratumThemeAccessor.colors.surfaceBase)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            EquipmentSwapSheetContent(
                state =
                    EquipmentSwapSheetState(
                        current = options[0],
                        recent = listOf(options[1], options[2]),
                        all = options,
                    ),
                onQueryChange = {},
                onSelect = {},
                onCreateNew = {},
            )
        }
    }
}

/** Shared preview/screenshot content: subheader (assigned + unassigned) + full sheet. */
@Composable
fun EquipmentSwapPreviewContent(family: StratumThemeId) {
    val options =
        listOf(
            EquipmentSwapOption(id = "barbell", name = "Olympic Barbell"),
            EquipmentSwapOption(id = "ssb", name = "Safety Squat Bar"),
            EquipmentSwapOption(id = "cables", name = "Cables"),
            EquipmentSwapOption(id = "dumbbells", name = "Dumbbells"),
        )
    StratumTheme(themeId = family) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(StratumThemeAccessor.colors.surfaceBase)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StratumEquipmentSubheader(
                equipmentName = "Olympic Barbell",
                onSwapClick = {},
            )
            StratumEquipmentSubheader(
                equipmentName = null,
                onSwapClick = {},
            )
            EquipmentSwapSheetContent(
                state =
                    EquipmentSwapSheetState(
                        current = options[0],
                        recent = listOf(options[1], options[3]),
                        all = options,
                    ),
                onQueryChange = {},
                onSelect = {},
                onCreateNew = {},
            )
        }
    }
}
