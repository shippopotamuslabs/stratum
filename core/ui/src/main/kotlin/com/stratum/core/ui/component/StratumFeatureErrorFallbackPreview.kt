package com.stratum.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId

@PreviewLightDark
@Composable
private fun StratumFeatureErrorFallbackTonalPreview() {
    StratumTheme(themeId = StratumThemeId.Tonal) {
        StratumFeatureErrorFallback(onRetry = {})
    }
}

@PreviewLightDark
@Composable
private fun StratumFeatureErrorFallbackNeoPreview() {
    StratumTheme(themeId = StratumThemeId.NeoBrutalist) {
        StratumFeatureErrorFallback(onRetry = {})
    }
}
