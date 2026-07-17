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
private fun StratumEdgeToEdgeCardTonalPreview() {
    EdgeToEdgeCardPreview(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun StratumEdgeToEdgeCardNeoPreview() {
    EdgeToEdgeCardPreview(family = StratumThemeId.NeoBrutalist)
}

@Composable
private fun EdgeToEdgeCardPreview(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(StratumThemeAccessor.colors.surfaceBase)
                    .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StratumEdgeToEdgeCard {
                StratumText(
                    text = "Back Squat",
                    style = StratumThemeAccessor.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                StratumText(
                    text = "Edge-to-edge panel — flush sides; Neo adds top/bottom bands",
                    muted = true,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}
