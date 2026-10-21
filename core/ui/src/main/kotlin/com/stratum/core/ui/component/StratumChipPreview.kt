package com.stratum.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.theme.StratumThemeId

@PreviewLightDark
@Composable
private fun StratumChipTonalPreview() {
    ChipPreviewGrid(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun StratumChipNeoPreview() {
    ChipPreviewGrid(family = StratumThemeId.NeoBrutalist)
}

@Composable
internal fun ChipPreviewGrid(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(StratumThemeAccessor.colors.surfaceBase)
                    .padding(StratumSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(StratumSpacing.s12),
        ) {
            StratumChip(label = "Assist chip", onClick = {})
            StratumChip(
                label = "With leading icon",
                onClick = {},
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
            StratumChip(
                label = "Input with avatar",
                onClick = {},
                avatar = {
                    Box(
                        modifier =
                            Modifier
                                .size(24.dp)
                                .background(StratumThemeAccessor.colors.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        StratumText(
                            text = "S",
                            style = StratumThemeAccessor.typography.labelLarge,
                            color = StratumThemeAccessor.colors.onAccent,
                        )
                    }
                },
            )
            StratumChip(label = "Selected input", onClick = {}, selected = true)
            StratumChip(label = "Suggestion chip")
            StratumChip(label = "Disabled", onClick = {}, enabled = false)
        }
    }
}
