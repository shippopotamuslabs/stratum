package com.stratum.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId

@PreviewLightDark
@Composable
private fun StratumIconButtonTonalPreview() {
    IconButtonPreviewGrid(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun StratumIconButtonNeoPreview() {
    IconButtonPreviewGrid(family = StratumThemeId.NeoBrutalist)
}

@Composable
internal fun IconButtonPreviewGrid(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StratumIconButton(onClick = {}) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add")
            }
            StratumIconButton(onClick = {}) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
            }
            StratumIconButton(onClick = {}, enabled = false) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add disabled")
            }
        }
    }
}
