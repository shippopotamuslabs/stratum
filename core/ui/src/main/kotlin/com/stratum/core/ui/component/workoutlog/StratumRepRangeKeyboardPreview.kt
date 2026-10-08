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
    RepRangeKeyboardPreviewContent(
        family = StratumThemeId.Tonal,
        activeField = RepRangeKeyboardField.Min,
    )
}

@PreviewLightDark
@Composable
private fun StratumRepRangeKeyboardMaxPreview() {
    RepRangeKeyboardPreviewContent(
        family = StratumThemeId.Tonal,
        activeField = RepRangeKeyboardField.Max,
    )
}

@Composable
internal fun RepRangeKeyboardPreviewContent(
    family: StratumThemeId,
    activeField: RepRangeKeyboardField,
) {
    StratumTheme(themeId = family) {
        Box(
            modifier =
                Modifier
                    .background(StratumThemeAccessor.colors.surfaceBase)
                    .padding(8.dp),
        ) {
            StratumRepRangeKeyboardContent(
                activeField = activeField,
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
