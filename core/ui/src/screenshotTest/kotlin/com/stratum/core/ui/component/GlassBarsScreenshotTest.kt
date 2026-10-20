package com.stratum.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId

/**
 * Screenshot tests for the Glass Bars shell (STR-153, §12.8). The pair pins the
 * migration's before/after: `glassBarsTonalSolid` renders both bars in their
 * pre-glass solid treatments; `glassBarsTonalGlass` renders the always-glass
 * bottom bar and the scrolled glass top bar over the same content.
 */
@PreviewTest
@PreviewLightDark
@Composable
fun glassBarsTonalSolid() {
    StratumTheme(themeId = StratumThemeId.Tonal) {
        GlassBarsPreviewContent(glass = false)
    }
}

@PreviewTest
@PreviewLightDark
@Composable
fun glassBarsTonalGlass() {
    StratumTheme(themeId = StratumThemeId.Tonal) {
        GlassBarsPreviewContent(glass = true)
    }
}
