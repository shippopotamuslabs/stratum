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

private val PreviewPadActions =
    SetLogKeyboardPadActions(
        onRirSelected = {},
        onDigit = {},
        onDecimal = {},
        onBackspace = {},
        onPrimaryAction = {},
        onDismiss = {},
    )

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
                state =
                    SetLogKeyboardPadState(
                        activeField = SetLogKeyboardField.Weight,
                        selectedRir = 2,
                        showWeightInput = true,
                        allowDecimal = true,
                        canComplete = false,
                    ),
                actions = PreviewPadActions,
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
private fun StratumSetLogKeyboardRepsPreview() {
    StratumTheme(themeId = StratumThemeId.Tonal) {
        Box(
            modifier =
                Modifier
                    .background(StratumThemeAccessor.colors.surfaceBase)
                    .padding(8.dp),
        ) {
            StratumSetLogKeyboardContent(
                state =
                    SetLogKeyboardPadState(
                        activeField = SetLogKeyboardField.Reps,
                        selectedRir = 1,
                        showWeightInput = true,
                        allowDecimal = false,
                        canComplete = true,
                    ),
                actions = PreviewPadActions,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(280.dp),
            )
        }
    }
}
