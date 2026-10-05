package com.stratum

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.stratum.core.ui.theme.StratumTheme

/**
 * Health Connect disabled hero state (STR-67 slice 2): centered Settings prompt replacing the
 * pager. Lives outside [DashboardScreenPreview.kt] because that file is at the Detekt
 * function-count threshold.
 */
@PreviewLightDark
@Composable
private fun HealthDisabledPromptPreview() {
    StratumTheme {
        DashboardHealthDisabledPrompt(onNavigateToSettings = {})
    }
}
