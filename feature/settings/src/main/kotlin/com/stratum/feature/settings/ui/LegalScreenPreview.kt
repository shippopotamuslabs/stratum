package com.stratum.feature.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId

@PreviewLightDark
@Composable
private fun LegalScreenTonalPreview() {
    StratumTheme(themeId = StratumThemeId.Tonal, darkTheme = false) {
        LegalScreen(onBackClick = {})
    }
}

@PreviewLightDark
@Composable
private fun LegalScreenNeoPreview() {
    StratumTheme(themeId = StratumThemeId.NeoBrutalist, darkTheme = true) {
        LegalScreen(onBackClick = {})
    }
}
