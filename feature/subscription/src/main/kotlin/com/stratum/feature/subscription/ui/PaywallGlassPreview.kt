package com.stratum.feature.subscription.ui

import androidx.compose.runtime.Composable
import com.stratum.core.ui.component.StratumGlassHost
import com.stratum.core.ui.theme.StratumThemeId

/**
 * Glass-host preview content for the paywall (STR-161, §12.8): the existing
 * preview content hosted the way the paywall Nav3 entry hosts it — a shared
 * HazeState provided at the route level via [StratumGlassHost] — so the
 * golden pins the glazed top bar (floor + blur + hairline, content beneath)
 * instead of the solid fallback the unhosted previews render. Tonal only:
 * Neo keeps solid chrome under the carve-out, already pinned by the Neo
 * golden.
 */
@Composable
internal fun PaywallGlassPreviewContent() {
    StratumGlassHost {
        PaywallPreviewContent(
            family = StratumThemeId.Tonal,
            uiState = previewPaywallContent(),
        )
    }
}
