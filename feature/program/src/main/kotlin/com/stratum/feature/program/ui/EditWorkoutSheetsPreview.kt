package com.stratum.feature.program.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.LiftActionsSheetContent
import com.stratum.core.ui.component.RirTargetSheetContent
import com.stratum.core.ui.component.SupersetLinkSheetContent
import com.stratum.core.ui.component.supersetLinkSheetOptions
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId

/**
 * Screenshot-test previews for [EditWorkoutScreen] sheets, dialogs, and top-bar states
 * (STR-91).
 *
 * Sheet goldens pin content inline ([SheetContentPreview]): dialog windows are not
 * capturable (an open sheet blanks the golden) — see docs/screenshot-tests.md.
 * `darkTheme` is left null so `@PreviewLightDark` genuinely varies light/dark.
 */
@PreviewLightDark
@Composable
private fun EditWorkoutRirSheetTonalPreview() {
    EditWorkoutRirSheetPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun EditWorkoutRirSheetNeoPreview() {
    EditWorkoutRirSheetPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewLightDark
@Composable
private fun EditWorkoutSupersetSheetTonalPreview() {
    EditWorkoutSupersetSheetPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun EditWorkoutSupersetSheetNeoPreview() {
    EditWorkoutSupersetSheetPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewLightDark
@Composable
private fun EditWorkoutLiftActionsTonalPreview() {
    EditWorkoutLiftActionsPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun EditWorkoutLiftActionsNeoPreview() {
    EditWorkoutLiftActionsPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewLightDark
@Composable
private fun EditWorkoutDiscardSheetTonalPreview() {
    EditWorkoutDiscardSheetPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun EditWorkoutDiscardSheetNeoPreview() {
    EditWorkoutDiscardSheetPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewLightDark
@Composable
private fun EditWorkoutProgressionExplainerTonalPreview() {
    EditWorkoutProgressionExplainerPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun EditWorkoutProgressionExplainerNeoPreview() {
    EditWorkoutProgressionExplainerPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewLightDark
@Composable
private fun EditWorkoutDirtyStartDialogTonalPreview() {
    EditWorkoutDirtyStartDialogPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun EditWorkoutDirtyStartDialogNeoPreview() {
    EditWorkoutDirtyStartDialogPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewLightDark
@Composable
private fun EditWorkoutActiveSessionDiscardTonalPreview() {
    EditWorkoutActiveSessionDiscardPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun EditWorkoutActiveSessionDiscardNeoPreview() {
    EditWorkoutActiveSessionDiscardPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewLightDark
@Composable
private fun EditWorkoutDirtyTopBarTonalPreview() {
    EditWorkoutDirtyTopBarPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun EditWorkoutDirtyTopBarNeoPreview() {
    EditWorkoutDirtyTopBarPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewLightDark
@Composable
private fun EditWorkoutSavingTonalPreview() {
    EditWorkoutSavingPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun EditWorkoutSavingNeoPreview() {
    EditWorkoutSavingPreviewContent(family = StratumThemeId.NeoBrutalist)
}

/** RIR target sheet body (2 RIR selected). */
@Composable
internal fun EditWorkoutRirSheetPreviewContent(family: StratumThemeId) {
    SheetContentPreview(family = family) {
        RirTargetSheetContent(
            selectedRirGoal = 2,
            onRirSelected = {},
            onDismiss = {},
        )
    }
}

/** Superset-link sheet body for the second lift (link with previous). */
@Composable
internal fun EditWorkoutSupersetSheetPreviewContent(family: StratumThemeId) {
    val lifts = previewTwoLiftState()
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

/** Lift actions sheet body (edit-workout wiring: superset/swap/remove/reorder). */
@Composable
internal fun EditWorkoutLiftActionsPreviewContent(family: StratumThemeId) {
    SheetContentPreview(family = family) {
        SheetContentTitle(text = stringResource(CoreUiR.string.edit_workout_lift_menu))
        LiftActionsSheetContent(
            onConfigureSuperset = {},
            onSwapLift = {},
            onRemoveLift = {},
            onReorderLifts = {},
        )
    }
}

/** Discard-changes sheet body. */
@Composable
internal fun EditWorkoutDiscardSheetPreviewContent(family: StratumThemeId) {
    SheetContentPreview(family = family) {
        EditWorkoutDiscardSheetContent(
            onConfirmDiscard = {},
            onDismiss = {},
        )
    }
}

/** Progression-scheme explainer sheet body. */
@Composable
internal fun EditWorkoutProgressionExplainerPreviewContent(family: StratumThemeId) {
    SheetContentPreview(family = family) {
        ProgressionSchemeExplainerSheetContent()
    }
}

/** Dirty-start dialog body (carries its own surface + border, no sheet container). */
@Composable
internal fun EditWorkoutDirtyStartDialogPreviewContent(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        EditWorkoutDirtyStartDialogContent(
            onConfirmSaveAndStart = {},
            onDiscardAndStart = {},
            onDismiss = {},
        )
    }
}

/** Active-session discard dialog body (carries its own surface + border). */
@Composable
internal fun EditWorkoutActiveSessionDiscardPreviewContent(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        StartSessionDiscardDialogContent(
            onDismiss = {},
            onConfirm = {},
        )
    }
}

/** Loaded screen with unsaved edits (Confirm enabled in the top bar). */
@Composable
internal fun EditWorkoutDirtyTopBarPreviewContent(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        EditWorkoutScreen(
            showStartWorkout = true,
            state = previewEditWorkoutLoadedState().copy(isDirty = true),
            onLeave = {},
            // Explicit no-op keeps the subheader visible, matching the wired edit flow.
            callbacks = EditWorkoutScreenCallbacks(onEquipmentSwapClick = {}),
        )
    }
}

/** Loaded screen mid-save (Confirm disabled in the top bar). */
@Composable
internal fun EditWorkoutSavingPreviewContent(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        EditWorkoutScreen(
            showStartWorkout = true,
            state = previewEditWorkoutLoadedState().copy(isSaving = true),
            onLeave = {},
            // Explicit no-op keeps the subheader visible, matching the wired edit flow.
            callbacks = EditWorkoutScreenCallbacks(onEquipmentSwapClick = {}),
        )
    }
}

private fun previewTwoLiftState(): List<EditLiftState> {
    val first = previewEditWorkoutLoadedState().lifts.single()
    return listOf(
        first,
        EditLiftState(
            workoutLiftId = "wl-2",
            liftId = "barbell-row",
            name = "Barbell Row",
            position = 1,
            sets =
                listOf(
                    EditSetState(
                        setId = "s-3",
                        setNumber = 1,
                        repMin = 8,
                        repMax = 8,
                        restSeconds = 90,
                        rirGoal = 1,
                        isTopSet = false,
                    ),
                ),
        ),
    )
}
