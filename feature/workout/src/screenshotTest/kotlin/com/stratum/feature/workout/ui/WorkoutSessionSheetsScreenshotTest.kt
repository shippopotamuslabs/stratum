package com.stratum.feature.workout.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.stratum.core.ui.theme.StratumThemeId

/**
 * Screenshot tests for session sheet/dialog bodies (STR-90). Thin `@PreviewTest` wrappers
 * over the shared preview content — fake state lives in the main-source previews so
 * Studio `@Preview`s and goldens can never drift apart. Bodies render inline (dialog
 * windows are not capturable) — see docs/screenshot-tests.md.
 */
@PreviewTest
@PreviewLightDark
@Composable
fun workoutScreenLiftActionsOptionsTonal() {
    WorkoutLiftActionsOptionsPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun workoutScreenLiftActionsOptionsNeo() {
    WorkoutLiftActionsOptionsPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun workoutScreenLiftActionsManageNotesTonal() {
    ManageNotesSummaryPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun workoutScreenLiftActionsManageNotesNeo() {
    ManageNotesSummaryPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun workoutScreenCompleteSheetIncompleteTonal() {
    WorkoutCompleteSheetPreviewContent(family = StratumThemeId.Tonal, phase = CompleteSheetPhase.Incomplete)
}

@PreviewTest
@PreviewLightDark
@Composable
fun workoutScreenCompleteSheetIncompleteNeo() {
    WorkoutCompleteSheetPreviewContent(family = StratumThemeId.NeoBrutalist, phase = CompleteSheetPhase.Incomplete)
}

@PreviewTest
@PreviewLightDark
@Composable
fun workoutScreenCompleteSheetSaveChangesTonal() {
    WorkoutCompleteSheetPreviewContent(family = StratumThemeId.Tonal, phase = CompleteSheetPhase.SaveChanges)
}

@PreviewTest
@PreviewLightDark
@Composable
fun workoutScreenCompleteSheetSaveChangesNeo() {
    WorkoutCompleteSheetPreviewContent(family = StratumThemeId.NeoBrutalist, phase = CompleteSheetPhase.SaveChanges)
}

@PreviewTest
@PreviewLightDark
@Composable
fun workoutScreenCompleteSheetSaveChangesSummaryTonal() {
    WorkoutCompleteSheetPreviewContent(
        family = StratumThemeId.Tonal,
        phase = CompleteSheetPhase.SaveChanges,
        templateChanges = previewTemplateChangeSummary(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun workoutScreenCompleteSheetSaveChangesSummaryNeo() {
    WorkoutCompleteSheetPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        phase = CompleteSheetPhase.SaveChanges,
        templateChanges = previewTemplateChangeSummary(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun templateChangeSummaryExpandedTonal() {
    TemplateChangeSummaryPreviewContent(family = StratumThemeId.Tonal, expanded = true)
}

@PreviewTest
@PreviewLightDark
@Composable
fun templateChangeSummaryExpandedNeo() {
    TemplateChangeSummaryPreviewContent(family = StratumThemeId.NeoBrutalist, expanded = true)
}

@PreviewTest
@PreviewLightDark
@Composable
fun workoutScreenCompleteSheetAllSetsLoggedTonal() {
    WorkoutCompleteSheetPreviewContent(family = StratumThemeId.Tonal, phase = CompleteSheetPhase.AllSetsLogged)
}

@PreviewTest
@PreviewLightDark
@Composable
fun workoutScreenCompleteSheetAllSetsLoggedNeo() {
    WorkoutCompleteSheetPreviewContent(family = StratumThemeId.NeoBrutalist, phase = CompleteSheetPhase.AllSetsLogged)
}

@PreviewTest
@PreviewLightDark
@Composable
fun workoutScreenDiscardDialogTonal() {
    WorkoutDiscardDialogPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun workoutScreenDiscardDialogNeo() {
    WorkoutDiscardDialogPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun workoutScreenSupersetSheetTonal() {
    WorkoutSupersetSheetPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun workoutScreenSupersetSheetNeo() {
    WorkoutSupersetSheetPreviewContent(family = StratumThemeId.NeoBrutalist)
}
