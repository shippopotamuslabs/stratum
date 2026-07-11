package com.stratum.feature.onboarding.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId

@Preview(name = "Welcome light", showBackground = true)
@Composable
private fun WelcomeScreenLightPreview() {
    StratumTheme(themeId = StratumThemeId.Tonal, darkTheme = false) {
        WelcomeScreen()
    }
}

@Preview(name = "Welcome dark", showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun WelcomeScreenDarkPreview() {
    StratumTheme(themeId = StratumThemeId.Tonal, darkTheme = true) {
        WelcomeScreen()
    }
}
