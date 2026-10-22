package com.stratum.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.theme.StratumThemeId

@PreviewLightDark
@Composable
private fun StratumSurfaceTonalPreview() {
    SurfacePreviewGrid(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun StratumSurfaceNeoPreview() {
    SurfacePreviewGrid(family = StratumThemeId.NeoBrutalist)
}

@Composable
internal fun SurfacePreviewGrid(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(StratumThemeAccessor.colors.surfaceBase)
                    .padding(StratumSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(StratumSpacing.s16),
        ) {
            StratumSurface(level = StratumSurfaceLevel.Rest) {
                StratumText(
                    text = "Rest surface",
                    modifier = Modifier.padding(StratumSpacing.s16),
                )
            }
            StratumSurface(level = StratumSurfaceLevel.Elevated) {
                StratumText(
                    text = "Elevated surface",
                    modifier = Modifier.padding(StratumSpacing.s16),
                )
            }
            StratumSurface(level = StratumSurfaceLevel.Sunken) {
                StratumText(
                    text = "Sunken surface",
                    modifier = Modifier.padding(StratumSpacing.s16),
                )
            }
            StratumSurface(
                level = StratumSurfaceLevel.Rest,
                onClick = {},
            ) {
                StratumText(
                    text = "Clickable surface",
                    modifier = Modifier.padding(StratumSpacing.s16),
                )
            }
        }
    }
}
