package com.stratum.feature.workout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.theme.StratumThemeId

/**
 * Screenshot-test previews for rest-timer states (STR-89).
 *
 * Determinism rules: the elapsed clock pins `sessionStartedAtEpochMilli` to composition
 * time (`00:00`, via [previewWorkoutLoadedState]); the rest countdown is a fixed
 * remaining/total pair (no ticking clock, no in-flight animation). `darkTheme` is left
 * null so `@PreviewLightDark` genuinely varies light/dark.
 *
 * Sheet goldens pin content inline ([RestSheetContentPreview]): dialog windows are not
 * capturable (an open sheet blanks the golden) — see docs/screenshot-tests.md.
 */
@PreviewLightDark
@Composable
private fun WorkoutRestTimerActiveTonalPreview() {
    WorkoutRestTimerActivePreviewContent(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun WorkoutRestTimerActiveNeoPreview() {
    WorkoutRestTimerActivePreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewLightDark
@Composable
private fun WorkoutRestSheetTonalPreview() {
    WorkoutRestSheetPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun WorkoutRestSheetNeoPreview() {
    WorkoutRestSheetPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewLightDark
@Composable
private fun WorkoutRestSheetEditedTonalPreview() {
    WorkoutRestSheetEditedPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun WorkoutRestSheetEditedNeoPreview() {
    WorkoutRestSheetEditedPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewLightDark
@Composable
private fun WorkoutConfigureRestTonalPreview() {
    WorkoutConfigureRestPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun WorkoutConfigureRestNeoPreview() {
    WorkoutConfigureRestPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewLightDark
@Composable
private fun WorkoutRestConfigDiscardTonalPreview() {
    WorkoutRestConfigDiscardPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun WorkoutRestConfigDiscardNeoPreview() {
    WorkoutRestConfigDiscardPreviewContent(family = StratumThemeId.NeoBrutalist)
}

/** Active countdown in the top bar (mid-countdown progress), no sheet open. */
@Composable
internal fun WorkoutRestTimerActivePreviewContent(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        WorkoutScreen(
            state = previewWorkoutLoadedState(),
            restTimer = previewRestTimerChrome(
                isActive = true,
                remainingSeconds = 47,
                totalSeconds = 90,
            ),
            actions = previewWorkoutScreenActions(),
        )
    }
}

/** Rest bottom sheet body (view state: fresh countdown). */
@Composable
internal fun WorkoutRestSheetPreviewContent(family: StratumThemeId) {
    RestSheetContentPreview(family = family) {
        restTimerSheetBody(remainingSeconds = 75)
    }
}

/** Rest bottom sheet body after a −10s adjustment (edited countdown). */
@Composable
internal fun WorkoutRestSheetEditedPreviewContent(family: StratumThemeId) {
    RestSheetContentPreview(family = family) {
        restTimerSheetBody(remainingSeconds = 65)
    }
}

/** Shared rest-timer sheet body so view/edited previews differ only in countdown. */
@Composable
private fun ColumnScope.restTimerSheetBody(remainingSeconds: Int, totalSeconds: Int = 90) {
    RestTimerSheetContent(
        isTimerActive = true,
        remainingSeconds = remainingSeconds,
        totalSeconds = totalSeconds,
        onAdd10s = {},
        onSubtract10s = {},
        onSkip = {},
        onStartPreset = {},
    )
}

/** Configure Rest Times sheet body for the first lift (baseline per-set rows). */
@Composable
internal fun WorkoutConfigureRestPreviewContent(family: StratumThemeId) {
    val lift = previewConfigureRestLift()
    val baseline = remember(lift) { lift.sets.associate { it.slotKey to it.restSeconds } }
    val pending = remember(lift) { mutableStateMapOf<String, Int>().apply { putAll(baseline) } }
    RestSheetContentPreview(family = family) {
        ConfigureRestTimesSheetContent(
            lift = lift,
            baseline = baseline,
            pending = pending,
            onConfirm = {},
        )
    }
}

/** Rest-config discard dialog body. */
@Composable
internal fun WorkoutRestConfigDiscardPreviewContent(family: StratumThemeId) {
    RestSheetContentPreview(family = family) {
        RestConfigDiscardSheetContent(
            onConfirmDiscard = {},
            onDismiss = {},
        )
    }
}

/**
 * Inline stand-in for the StratumBottomSheet inner container: same background, padding,
 * and spacing the sheet body is designed for. Sheet chrome itself (scrim, drag handle,
 * window) is framework-owned and uncoverable by screenshot tests.
 *
 * Deliberately mirrors [com.stratum.core.ui.component.StratumBottomSheet]'s inner
 * Column — keep the background, paddings, and arrangement in sync with it. Sharing
 * the container with production would mean refactoring the ModalBottomSheet wrapper;
 * that production change is out of scope for screenshot wiring, so the values are
 * duplicated here with this pointer instead.
 */
@Composable
private fun RestSheetContentPreview(
    family: StratumThemeId,
    content: @Composable ColumnScope.() -> Unit,
) {
    StratumTheme(themeId = family) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(StratumThemeAccessor.colors.surfaceRaised)
                .padding(horizontal = 12.dp)
                .padding(top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

private fun previewConfigureRestLift(): RenderedLiftUi {
    val lift = previewWorkoutLoadedState().lifts.first { it.workoutLiftId == "wl-1" }
    return lift.copy(
        sets = lift.sets.mapIndexed { index, set ->
            set.copy(restSeconds = if (index == 0) 90 else 120)
        },
    )
}
