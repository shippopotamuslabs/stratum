package com.stratum.core.ui.component.workoutlog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
private fun StratumRepRangeKeyboardMinPreview() {
    StratumTheme(themeId = StratumThemeId.Tonal) {
        Box(
            modifier =
                Modifier
                    .background(StratumThemeAccessor.colors.surfaceBase)
                    .padding(8.dp),
        ) {
            StratumRepRangeKeyboardContent(
                activeField = RepRangeKeyboardField.Min,
                onDigit = {},
                onBackspace = {},
                onOver = {},
                onDismiss = {},
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(280.dp),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun StratumRepRangeKeyboardMaxPreview() {
    StratumTheme(themeId = StratumThemeId.Tonal) {
        Box(
            modifier =
                Modifier
                    .background(StratumThemeAccessor.colors.surfaceBase)
                    .padding(8.dp),
        ) {
            StratumRepRangeKeyboardContent(
                activeField = RepRangeKeyboardField.Max,
                onDigit = {},
                onBackspace = {},
                onOver = {},
                onDismiss = {},
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(280.dp),
            )
        }
    }
}
