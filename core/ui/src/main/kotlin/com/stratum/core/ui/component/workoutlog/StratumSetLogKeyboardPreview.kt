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
    SetLogKeyboardPreviewContent(
        family = StratumThemeId.Tonal,
        state = previewSetLogKeyboardWeight(),
    )
}

@PreviewLightDark
@Composable
private fun StratumSetLogKeyboardRepsPreview() {
    SetLogKeyboardPreviewContent(
        family = StratumThemeId.Tonal,
        state = previewSetLogKeyboardReps(),
    )
}

@PreviewLightDark
@Composable
private fun StratumSetLogKeyboardBodyweightPreview() {
    SetLogKeyboardPreviewContent(
        family = StratumThemeId.Tonal,
        state = previewSetLogKeyboardBodyweight(),
    )
}

@Composable
internal fun SetLogKeyboardPreviewContent(
    family: StratumThemeId,
    state: SetLogKeyboardPadState,
) {
    StratumTheme(themeId = family) {
        Box(
            modifier =
                Modifier
                    .background(StratumThemeAccessor.colors.surfaceBase)
                    .padding(8.dp),
        ) {
            StratumSetLogKeyboardContent(
                state = state,
                actions = PreviewPadActions,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(280.dp),
            )
        }
    }
}

internal fun previewSetLogKeyboardWeight(): SetLogKeyboardPadState =
    SetLogKeyboardPadState(
        activeField = SetLogKeyboardField.Weight,
        selectedRir = 2,
        showWeightInput = true,
        allowDecimal = true,
        canComplete = false,
    )

internal fun previewSetLogKeyboardReps(): SetLogKeyboardPadState =
    SetLogKeyboardPadState(
        activeField = SetLogKeyboardField.Reps,
        selectedRir = 1,
        showWeightInput = true,
        allowDecimal = false,
        canComplete = true,
    )

internal fun previewSetLogKeyboardBodyweight(): SetLogKeyboardPadState =
    SetLogKeyboardPadState(
        activeField = SetLogKeyboardField.Reps,
        selectedRir = 1,
        showWeightInput = false,
        allowDecimal = false,
        canComplete = false,
    )
