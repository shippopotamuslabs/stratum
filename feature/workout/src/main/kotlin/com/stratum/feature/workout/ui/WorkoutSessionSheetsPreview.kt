package com.stratum.feature.workout.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.stratum.core.ui.component.LiftActionsSheetContent
import com.stratum.core.ui.component.SupersetLinkSheetContent
import com.stratum.core.ui.component.supersetLinkSheetOptions
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId

/**
 * Screenshot-test previews for session sheets and dialogs (STR-90).
 *
 * Sheet goldens pin content inline ([SheetContentPreview]): dialog windows are not
 * capturable (an open sheet blanks the golden) — see docs/screenshot-tests.md.
 * `darkTheme` is left null so `@PreviewLightDark` genuinely varies light/dark.
 */
@PreviewLightDark
@Composable
private fun WorkoutLiftActionsOptionsTonalPreview() {
    WorkoutLiftActionsOptionsPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun WorkoutLiftActionsOptionsNeoPreview() {
    WorkoutLiftActionsOptionsPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewLightDark
@Composable
private fun WorkoutCompleteSheetIncompleteTonalPreview() {
    WorkoutCompleteSheetPreviewContent(family = StratumThemeId.Tonal, phase = CompleteSheetPhase.Incomplete)
}

@PreviewLightDark
@Composable
private fun WorkoutCompleteSheetIncompleteNeoPreview() {
    WorkoutCompleteSheetPreviewContent(family = StratumThemeId.NeoBrutalist, phase = CompleteSheetPhase.Incomplete)
}

@PreviewLightDark
@Composable
private fun WorkoutCompleteSheetSaveChangesTonalPreview() {
    WorkoutCompleteSheetPreviewContent(family = StratumThemeId.Tonal, phase = CompleteSheetPhase.SaveChanges)
}

@PreviewLightDark
@Composable
private fun WorkoutCompleteSheetSaveChangesNeoPreview() {
    WorkoutCompleteSheetPreviewContent(family = StratumThemeId.NeoBrutalist, phase = CompleteSheetPhase.SaveChanges)
}

@PreviewLightDark
@Composable
private fun WorkoutCompleteSheetSaveChangesSummaryTonalPreview() {
    WorkoutCompleteSheetPreviewContent(
        family = StratumThemeId.Tonal,
        phase = CompleteSheetPhase.SaveChanges,
        templateChanges = previewTemplateChangeSummary(),
    )
}

@PreviewLightDark
@Composable
private fun WorkoutCompleteSheetSaveChangesSummaryNeoPreview() {
    WorkoutCompleteSheetPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        phase = CompleteSheetPhase.SaveChanges,
        templateChanges = previewTemplateChangeSummary(),
    )
}

@PreviewLightDark
@Composable
private fun TemplateChangeSummaryExpandedTonalPreview() {
    TemplateChangeSummaryPreviewContent(family = StratumThemeId.Tonal, expanded = true)
}

@PreviewLightDark
@Composable
private fun TemplateChangeSummaryExpandedNeoPreview() {
    TemplateChangeSummaryPreviewContent(family = StratumThemeId.NeoBrutalist, expanded = true)
}

@PreviewLightDark
@Composable
private fun WorkoutCompleteSheetAllSetsLoggedTonalPreview() {
    WorkoutCompleteSheetPreviewContent(family = StratumThemeId.Tonal, phase = CompleteSheetPhase.AllSetsLogged)
}

@PreviewLightDark
@Composable
private fun WorkoutCompleteSheetAllSetsLoggedNeoPreview() {
    WorkoutCompleteSheetPreviewContent(family = StratumThemeId.NeoBrutalist, phase = CompleteSheetPhase.AllSetsLogged)
}

@PreviewLightDark
@Composable
private fun WorkoutDiscardDialogTonalPreview() {
    WorkoutDiscardDialogPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun WorkoutDiscardDialogNeoPreview() {
    WorkoutDiscardDialogPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewLightDark
@Composable
private fun WorkoutSupersetSheetTonalPreview() {
    WorkoutSupersetSheetPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun WorkoutSupersetSheetNeoPreview() {
    WorkoutSupersetSheetPreviewContent(family = StratumThemeId.NeoBrutalist)
}

/** Lift options sheet body (Options mode, all workout rows visible). */
@Composable
internal fun WorkoutLiftActionsOptionsPreviewContent(family: StratumThemeId) {
    SheetContentPreview(family = family) {
        LiftActionsSheetContent(
            onConfigureSuperset = {},
            onSwapLift = {},
            onRemoveLift = {},
            onReorderLifts = {},
            onConfigureRestTimes = {},
            onManageNotes = {},
            onViewPastSessionNotes = {},
            onEditEquipment = {},
        )
    }
}

/** Complete-workout sheet body for the given phase. */
@Composable
internal fun WorkoutCompleteSheetPreviewContent(
    family: StratumThemeId,
    phase: CompleteSheetPhase,
    templateChanges: TemplateChangeSummaryUi? = null,
) {
    SheetContentPreview(family = family) {
        CompleteSheetContent(
            phase = phase,
            onContinueWorkout = {},
            onCompleteWorkout = {},
            onUpdateTemplate = {},
            onDiscardChanges = {},
            templateChanges = templateChanges,
        )
    }
}

/** Change-summary section body, expanded when [expanded] (STR-125). */
@Composable
internal fun TemplateChangeSummaryPreviewContent(family: StratumThemeId, expanded: Boolean) {
    SheetContentPreview(family = family) {
        TemplateChangeSummarySection(
            summary = previewTemplateChangeSummary(),
            initiallyExpanded = expanded,
        )
    }
}

/** Multi-category fake summary shared by Studio previews and screenshot goldens. */
internal fun previewTemplateChangeSummary(): TemplateChangeSummaryUi =
    TemplateChangeSummaryUi(
        groups =
            listOf(
                TemplateChangeGroupUi(
                    category = TemplateChangeCategoryUi.Lifts,
                    rows =
                        listOf(
                            TemplateChangeRowUi(label = "Barbell Row", oldText = null, newText = "3 sets"),
                            TemplateChangeRowUi(label = "Lift", oldText = "Lat Pulldown", newText = "Pull-Up"),
                        ),
                ),
                TemplateChangeGroupUi(
                    category = TemplateChangeCategoryUi.Order,
                    rows =
                        listOf(
                            TemplateChangeRowUi(label = "Back Squat", oldText = "Position 2", newText = "Position 1"),
                        ),
                ),
                TemplateChangeGroupUi(
                    category = TemplateChangeCategoryUi.Sets,
                    rows =
                        listOf(
                            TemplateChangeRowUi(
                                label = "Seated Cable Row",
                                oldText = "3 sets",
                                newText = "4 sets · 1 replaced",
                            ),
                            TemplateChangeRowUi(
                                label = "Bench Press · Set 2",
                                oldText = "Standard 6–10 · RIR 2",
                                newText = "Myo-Rep 6–10 · RIR 1",
                            ),
                        ),
                ),
                TemplateChangeGroupUi(
                    category = TemplateChangeCategoryUi.Rest,
                    rows =
                        listOf(
                            TemplateChangeRowUi(label = "Bench Press · Set 1", oldText = "2:00", newText = "1:30"),
                        ),
                ),
                TemplateChangeGroupUi(
                    category = TemplateChangeCategoryUi.Supersets,
                    rows =
                        listOf(
                            TemplateChangeRowUi(
                                label = "Overhead Press",
                                oldText = "Not grouped",
                                newText = "Grouped with Lateral Raise",
                            ),
                        ),
                ),
            ),
    )

/** Discard-workout dialog body (carries its own surface + border, no sheet container). */
@Composable
internal fun WorkoutDiscardDialogPreviewContent(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        DiscardWorkoutDialogContent(
            onDismiss = {},
            onConfirm = {},
        )
    }
}

/** Superset-link sheet body for the second lift (link with previous). */
@Composable
internal fun WorkoutSupersetSheetPreviewContent(family: StratumThemeId) {
    val lifts = previewWorkoutLoadedState().lifts
    SheetContentPreview(family = family) {
        SupersetLinkSheetContent(
            options = supersetLinkSheetOptions(lifts = lifts, workoutLiftId = "wl-2"),
            showRestBetweenLiftsToggle = false,
            useRestTimerBetweenLifts = false,
            onUseRestTimerBetweenLiftsChange = {},
            onOptionSelected = {},
        )
    }
}
