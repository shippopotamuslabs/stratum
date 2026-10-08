package com.stratum.core.ui.component.workoutlog

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.stratum.core.ui.theme.StratumThemeId

/**
 * Screenshot tests for the workout-log kit (STR-94). Thin `@PreviewTest` wrappers over the
 * shared preview content.
 */
@PreviewTest
@PreviewLightDark
@Composable
fun workoutLogLiftPanelTonal() {
    WorkoutLogLiftPanelPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun workoutLogLiftPanelNeo() {
    WorkoutLogLiftPanelPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun setLogKeyboardWeightTonal() {
    SetLogKeyboardPreviewContent(
        family = StratumThemeId.Tonal,
        state = previewSetLogKeyboardWeight(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun setLogKeyboardWeightNeo() {
    SetLogKeyboardPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = previewSetLogKeyboardWeight(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun setLogKeyboardRepsTonal() {
    SetLogKeyboardPreviewContent(
        family = StratumThemeId.Tonal,
        state = previewSetLogKeyboardReps(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun setLogKeyboardRepsNeo() {
    SetLogKeyboardPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = previewSetLogKeyboardReps(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun setLogKeyboardBodyweightTonal() {
    SetLogKeyboardPreviewContent(
        family = StratumThemeId.Tonal,
        state = previewSetLogKeyboardBodyweight(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun setLogKeyboardBodyweightNeo() {
    SetLogKeyboardPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = previewSetLogKeyboardBodyweight(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun repRangeKeyboardMinTonal() {
    RepRangeKeyboardPreviewContent(
        family = StratumThemeId.Tonal,
        activeField = RepRangeKeyboardField.Min,
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun repRangeKeyboardMinNeo() {
    RepRangeKeyboardPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        activeField = RepRangeKeyboardField.Min,
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun repRangeKeyboardMaxTonal() {
    RepRangeKeyboardPreviewContent(
        family = StratumThemeId.Tonal,
        activeField = RepRangeKeyboardField.Max,
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun repRangeKeyboardMaxNeo() {
    RepRangeKeyboardPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        activeField = RepRangeKeyboardField.Max,
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun splitRepsCellFullTonal() {
    SplitRepsCellPreviewContent(
        family = StratumThemeId.Tonal,
        fullRepsText = "8",
        partialRepsText = "2",
        fullPlaceholder = "8",
        selectedMode = RepsEntryMode.Full,
        isActive = true,
        completed = false,
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun splitRepsCellFullNeo() {
    SplitRepsCellPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        fullRepsText = "8",
        partialRepsText = "2",
        fullPlaceholder = "8",
        selectedMode = RepsEntryMode.Full,
        isActive = true,
        completed = false,
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun splitRepsCellPartialTonal() {
    SplitRepsCellPreviewContent(
        family = StratumThemeId.Tonal,
        fullRepsText = "8",
        partialRepsText = "",
        fullPlaceholder = "8",
        selectedMode = RepsEntryMode.Partial,
        isActive = true,
        completed = false,
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun splitRepsCellPartialNeo() {
    SplitRepsCellPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        fullRepsText = "8",
        partialRepsText = "",
        fullPlaceholder = "8",
        selectedMode = RepsEntryMode.Partial,
        isActive = true,
        completed = false,
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun splitRepsCellCompletedTonal() {
    SplitRepsCellPreviewContent(
        family = StratumThemeId.Tonal,
        fullRepsText = "14",
        partialRepsText = "2",
        fullPlaceholder = "12",
        selectedMode = RepsEntryMode.Partial,
        isActive = false,
        completed = true,
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun splitRepsCellCompletedNeo() {
    SplitRepsCellPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        fullRepsText = "14",
        partialRepsText = "2",
        fullPlaceholder = "12",
        selectedMode = RepsEntryMode.Partial,
        isActive = false,
        completed = true,
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun repsModeToggleFullTonal() {
    RepsModeTogglePreviewContent(family = StratumThemeId.Tonal, mode = RepsEntryMode.Full)
}

@PreviewTest
@PreviewLightDark
@Composable
fun repsModeToggleFullNeo() {
    RepsModeTogglePreviewContent(family = StratumThemeId.NeoBrutalist, mode = RepsEntryMode.Full)
}

@PreviewTest
@PreviewLightDark
@Composable
fun repsModeTogglePartialTonal() {
    RepsModeTogglePreviewContent(family = StratumThemeId.Tonal, mode = RepsEntryMode.Partial)
}

@PreviewTest
@PreviewLightDark
@Composable
fun repsModeTogglePartialNeo() {
    RepsModeTogglePreviewContent(family = StratumThemeId.NeoBrutalist, mode = RepsEntryMode.Partial)
}
