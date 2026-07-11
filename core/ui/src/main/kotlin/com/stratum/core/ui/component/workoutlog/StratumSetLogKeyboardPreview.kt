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
private fun StratumSetLogKeyboardWeightPreview() {
    StratumTheme(themeId = StratumThemeId.Tonal) {
        Box(
            modifier =
                Modifier
                    .background(StratumThemeAccessor.colors.surfaceBase)
                    .padding(8.dp),
        ) {
            StratumSetLogKeyboardContent(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                activeField = SetLogKeyboardField.Weight,
                selectedRir = 2,
                weightText = "135",
                repsText = "",
                showWeightInput = true,
                allowDecimal = true,
                onRirSelected = {},
                onDigit = {},
                onDecimal = {},
                onBackspace = {},
                onPrimaryAction = {},
                onDismiss = {},
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun StratumSetLogKeyboardRepsPreview() {
    StratumTheme(themeId = StratumThemeId.Tonal) {
        Box(
            modifier =
                Modifier
                    .background(StratumThemeAccessor.colors.surfaceBase)
                    .padding(8.dp),
        ) {
            StratumSetLogKeyboardContent(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                activeField = SetLogKeyboardField.Reps,
                selectedRir = 1,
                weightText = "135",
                repsText = "5",
                showWeightInput = true,
                allowDecimal = false,
                onRirSelected = {},
                onDigit = {},
                onDecimal = {},
                onBackspace = {},
                onPrimaryAction = {},
                onDismiss = {},
            )
        }
    }
}
