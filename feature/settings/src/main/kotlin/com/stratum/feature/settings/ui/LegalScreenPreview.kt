package com.stratum.feature.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId

@PreviewLightDark
@Composable
private fun LegalScreenTonalPreview() {
    LegalScreenPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun LegalScreenNeoPreview() {
    LegalScreenPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@Composable
internal fun LegalScreenPreviewContent(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        LegalScreen(onBackClick = {})
    }
}
