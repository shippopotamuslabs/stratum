// STR-142 allowlist: dense set-log column insets (2.dp) pinned.
@file:Suppress("ForbiddenRawSpacingLiteralInUi")
package com.stratum.core.ui.component.workoutlog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stratum.core.ui.component.RirTargetBadge
import com.stratum.core.ui.component.RirTargetBottomSheet
import com.stratum.core.ui.component.StratumCheckbox
import com.stratum.core.ui.component.StratumNumber
import com.stratum.core.ui.component.StratumSwipeToRevealRow
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumSetTypeColors
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor

@Composable
fun StratumWorkoutLogSetRow(
    set: WorkoutLogSetUi,
    actions: WorkoutLogSetRowActions,
    modifier: Modifier = Modifier,
) {
    var showRirSheet by remember(set.setKey) { mutableStateOf(false) }
    val keyboardHost = LocalSetLogKeyboardHost.current
    val showTrailing = set.showCheckbox
    val weights = workoutLogColumnWeights(set.showWeightInput, showTrailing)
    val rowBg =
        if (set.isComplete) {
            StratumThemeAccessor.colors.setComplete
        } else {
            Color.Transparent
        }
    val setChip = resolveSetTypeChip(set.setTypeLabel, set.positionLabel)

    // Keep host drafts live while this row is composed (row may scroll but host stays mounted).
    // Also recompute live canComplete from current drafts (buffers may diverge mid-key).
    val weightRec =
        hasWeightRecommendation(
            showWeightInput = set.showWeightInput,
            recommendedWeightAmount = set.recommendedWeightAmount,
            weightPlaceholder = set.weightPlaceholder,
            assistMode = set.assistMode,
        )
    val repsRec =
        hasRepsRecommendation(
            recommendedReps = set.recommendedReps,
            repsPlaceholder = set.repsPlaceholder,
        )

    // Host owns weight/reps drafts while open; only sync RIR + rec chrome from the row.
    LaunchedEffect(
        set.setKey,
        set.rir,
        weightRec,
        repsRec,
        keyboardHost?.session?.setKey,
    ) {
        keyboardHost?.syncSessionChrome(
            setKey = set.setKey,
            rir = set.rir,
            hasWeightRecommendation = weightRec,
            hasRepsRecommendation = repsRec,
        )
    }

    // Dismiss when fields become disabled; completed rows stay editable so do not dismiss on
    // isComplete alone (checkbox / keyboard complete still dismiss on first commit).
    LaunchedEffect(set.fieldsEnabled) {
        if (!set.fieldsEnabled && keyboardHost?.isSetActive(set.setKey) == true) {
            keyboardHost.dismiss()
        }
    }

    val openKeyboard: (SetLogKeyboardField, RepsEntryMode) -> Unit = { field, initialMode ->
        if (set.fieldsEnabled) {
            showRirSheet = false
            keyboardHost?.open(
                setKey = set.setKey,
                field = field,
                weightText = set.weightText,
                repsText = set.repsText,
                partialRepsText = set.partialRepsText,
                rir = set.rir,
                showWeightInput = set.showWeightInput,
                hasWeightRecommendation = weightRec,
                hasRepsRecommendation = repsRec,
                onWeightChange = actions.onWeightChange,
                onRepsChange = actions.onRepsChange,
                onPartialRepsChange = actions.onPartialRepsChange,
                onRirChange = actions.onRirChange,
                onComplete =
                    actions.onCheckedChange?.let { onChecked ->
                        { onChecked(true) }
                    },
                isPlateLoaded = set.isPlateLoaded,
                recommendedWeightAmount = set.recommendedWeightAmount,
                unitLabel = set.unitLabel,
                isDoubleSided = set.isDoubleSided,
                initialRepsMode = initialMode,
            )
        }
    }

    val weightActive =
        keyboardHost?.isFieldActive(set.setKey, SetLogKeyboardField.Weight) == true
    val repsActive =
        keyboardHost?.isFieldActive(set.setKey, SetLogKeyboardField.Reps) == true
    val keyboardOpenForSet = keyboardHost?.isSetActive(set.setKey) == true
    val checkboxEnabled =
        actions.onCheckedChange != null &&
            isSetLogCheckboxEnabled(isComplete = set.isComplete, canComplete = set.canComplete)

    val rowContent: @Composable () -> Unit = {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(rowBg)
                    .padding(vertical = StratumSpacing.s4, horizontal = WorkoutLogTableHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(weights.label), contentAlignment = Alignment.CenterStart) {
                // Same 32.dp circle as the add-set + so the Set column lines up (Edit Workout pattern).
                WorkoutLogSetColumnBadge(
                    backgroundColor = setChip.background,
                    onClick = actions.onSetTypeClick,
                ) {
                    val labelStyle =
                        if (setChip.label.length > 1) {
                            StratumThemeAccessor.typography.sectionLabel.copy(
                                color = setChip.contentColor,
                                fontSize = 11.sp,
                                lineHeight = 12.sp,
                            )
                        } else {
                            StratumThemeAccessor.typography.sectionLabel.copy(
                                color = setChip.contentColor,
                            )
                        }
                    StratumNumber(
                        text = setChip.label,
                        style = labelStyle,
                    )
                }
            }

            Box(modifier = Modifier.weight(weights.prev), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Decimal weights (e.g. `42.5 × 13`) can overflow this narrow column;
                    // shrink-to-fit keeps the rep count visible instead of clipping it.
                    StratumText(
                        text = set.previousLabel,
                        muted = true,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        shrinkToFit = true,
                    )
                    val previousRirLabel = set.previousRirLabel
                    if (previousRirLabel != null) {
                        StratumText(
                            text = previousRirLabel,
                            style = StratumThemeAccessor.typography.sectionLabel,
                            muted = true,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
            }

            // While this set's pad is open, host drafts are SoT for both fields (not just the
            // active one) so weight stays correct while editing reps mid-session.
            val setSessionHost = keyboardHost.takeIf { keyboardOpenForSet }
            val livePartialRepsText = setSessionHost?.partialRepsText ?: set.partialRepsText
            val liveRepsMode = setSessionHost?.repsEntryMode ?: RepsEntryMode.Full
            val showSplitReps = shouldShowSplitRepsCell(liveRepsMode, livePartialRepsText)
            if (set.showWeightInput) {
                StratumInlineNumericCell(
                    value = setSessionHost?.weightText ?: set.weightText,
                    placeholder = set.weightPlaceholder,
                    onValueChange = actions.onWeightChange,
                    enabled = set.fieldsEnabled,
                    isActive = weightActive,
                    onActivate = { openKeyboard(SetLogKeyboardField.Weight, RepsEntryMode.Full) },
                    completed = set.isComplete,
                    cursorIndex = setSessionHost?.weightCursor ?: set.weightText.length,
                    selection = setSessionHost?.weightSelection ?: TextRange(set.weightText.length),
                    onCursorChange =
                        if (weightActive && setSessionHost != null) {
                            { index -> setSessionHost.setCursor(SetLogKeyboardField.Weight, index) }
                        } else {
                            null
                        },
                    modifier =
                        Modifier
                            .weight(weights.weight)
                            .padding(horizontal = 2.dp),
                )
            }

            RepsCellWithRirBadge(
                rir = set.rir,
                onBadgeClick = {
                    if (set.fieldsEnabled && !keyboardOpenForSet) {
                        showRirSheet = true
                    }
                },
                modifier =
                    Modifier
                        .weight(weights.reps)
                        .padding(horizontal = 2.dp),
            ) {
                if (showSplitReps) {
                    WorkoutLogSplitRepsField(
                        set = set,
                        sessionHost = setSessionHost,
                        livePartialRepsText = livePartialRepsText,
                        liveRepsMode = liveRepsMode,
                        repsActive = repsActive,
                        openKeyboard = openKeyboard,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    WorkoutLogSingleRepsField(
                        set = set,
                        actions = actions,
                        sessionHost = setSessionHost,
                        repsActive = repsActive,
                        openKeyboard = openKeyboard,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (showTrailing) {
                Box(modifier = Modifier.weight(weights.trailing), contentAlignment = Alignment.Center) {
                    if (actions.onCheckedChange != null) {
                        StratumCheckbox(
                            checked = set.isComplete,
                            enabled = checkboxEnabled,
                            onCheckedChange = { checked ->
                                if (checked && keyboardOpenForSet) {
                                    // Same race as keyboard Check: collapse restore must not
                                    // cancel post-log focus scroll.
                                    keyboardHost.overlay.skipNextCollapseScrollRestore = true
                                    keyboardHost.dismiss()
                                }
                                actions.onCheckedChange.invoke(checked)
                            },
                        )
                    } else {
                        Box(modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }

    // Historical edit (and any caller that supplies [onDelete]) uses swipe-to-reveal, matching
    // template Edit Workout. Live logging also supplies it whenever more than one set exists,
    // alongside the checkbox column — so the swipe clip is live on real screens.
    if (actions.onDelete != null) {
        val swipeBg =
            if (set.isComplete) {
                StratumThemeAccessor.colors.setComplete
            } else {
                StratumThemeAccessor.colors.surfaceRaised
            }
        StratumSwipeToRevealRow(
            onActionClick = actions.onDelete,
            actionContentDescription = actions.deleteContentDescription,
            enabled = true,
            contentBackgroundColor = swipeBg,
            // Lets the RIR corner badge overhang the row without being cut by the swipe clip.
            topOverflow = RirCornerBadgeOverflow,
            modifier = modifier,
        ) {
            rowContent()
        }
    } else {
        Box(modifier = modifier) {
            rowContent()
        }
    }

    if (showRirSheet && !keyboardOpenForSet) {
        RirTargetBottomSheet(
            selectedRirGoal = set.rir,
            onRirSelected = { rir ->
                actions.onRirChange(rir)
                showRirSheet = false
            },
            onDismiss = { showRirSheet = false },
        )
    }
}

/**
 * Reps cell with the RIR corner badge pinned to its top-right corner (STR-17 Choice D).
 * The badge is the existing [RirTargetBadge] at a smaller corner size, centered on the cell
 * corner so it stays clear of the reps digits (tap separation). It draws after [content],
 * so it sits above sunken (logged) and accent-bordered (active) cells alike.
 *
 * The badge runs with touch-target expansion OFF (STR-180, Sean 2026-10-23): the STR-145
 * 48dp outer box re-centered the 22dp visual 13dp inside the now-48dp-tall cell and covered
 * the cell's top-right with the badge's tap area, so tapping the field to edit reps opened
 * the RIR sheet instead. With the badge's layout box back to the visual circle, the
 * [RirCornerBadgeOffset] (half the circle) centers it exactly on the corner again —
 * floating half-out of the cell, as in the pre-STR-145 goldens — and only the badge's
 * corner quarter overlaps the cell.
 *
 * The overhang above the cell needs [RirCornerBadgeOverflow] of unclipped room, which the
 * row requests from [com.stratum.core.ui.component.StratumSwipeToRevealRow] — that wrapper
 * clips to the row rectangle and would otherwise cut the badge on every row.
 */
@Composable
internal fun RepsCellWithRirBadge(
    rir: Int,
    onBadgeClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth()) {
            content()
        }
        RirTargetBadge(
            rirGoal = rir,
            onClick = onBadgeClick,
            size = RirCornerBadgeSize,
            expandTouchTarget = false,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = RirCornerBadgeOffset, y = -RirCornerBadgeOffset),
        )
    }
}

internal val RirCornerBadgeSize = 22.dp

/**
 * Unclipped room the swipe row reserves above the content; covers the badge overhang
 * (half the badge size) plus rounding margin. Must stay >= half of [RirCornerBadgeSize].
 */
internal val RirCornerBadgeOverflow = 12.dp
private val RirCornerBadgeOffset = 11.dp

@Composable
private fun WorkoutLogSplitRepsField(
    set: WorkoutLogSetUi,
    sessionHost: SetLogKeyboardHostState?,
    livePartialRepsText: String,
    liveRepsMode: RepsEntryMode,
    repsActive: Boolean,
    openKeyboard: (SetLogKeyboardField, RepsEntryMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    StratumSplitRepsCell(
        fullRepsText = sessionHost?.repsText ?: set.repsText,
        partialRepsText = livePartialRepsText,
        fullPlaceholder = set.repsPlaceholder,
        selectedMode = liveRepsMode,
        enabled = set.fieldsEnabled,
        isActive = repsActive,
        completed = set.isComplete,
        fullSelection = sessionHost?.repsSelection ?: TextRange.Zero,
        partialSelection = sessionHost?.partialRepsSelection ?: TextRange.Zero,
        onSelectFull = {
            openKeyboard(SetLogKeyboardField.Reps, RepsEntryMode.Full)
        },
        onSelectPartial = {
            openKeyboard(SetLogKeyboardField.Reps, RepsEntryMode.Partial)
        },
        modifier = modifier,
    )
}

@Composable
private fun WorkoutLogSingleRepsField(
    set: WorkoutLogSetUi,
    actions: WorkoutLogSetRowActions,
    sessionHost: SetLogKeyboardHostState?,
    repsActive: Boolean,
    openKeyboard: (SetLogKeyboardField, RepsEntryMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    StratumInlineNumericCell(
        value = sessionHost?.repsText ?: set.repsText,
        placeholder = set.repsPlaceholder,
        onValueChange = actions.onRepsChange,
        enabled = set.fieldsEnabled,
        isActive = repsActive,
        onActivate = { openKeyboard(SetLogKeyboardField.Reps, RepsEntryMode.Full) },
        completed = set.isComplete,
        cursorIndex = sessionHost?.repsCursor ?: set.repsText.length,
        selection = sessionHost?.repsSelection ?: TextRange(set.repsText.length),
        onCursorChange =
            if (repsActive && sessionHost != null) {
                { index -> sessionHost.setCursor(SetLogKeyboardField.Reps, index) }
            } else {
                null
            },
        modifier = modifier,
    )
}

/**
 * Resolves the Set-column chip letter + color from [setTypeLabel].
 * Live workout maps domain types to `S` / `M` / `MM` / `D`; older history paths may still
 * pass enum names (`Standard`, `MyoRep`, `DropSet`) or `Warmup`.
 */
private data class SetTypeChip(
    val label: String,
    val background: Color,
    val contentColor: Color,
)

@Composable
private fun resolveSetTypeChip(
    setTypeLabel: String,
    positionFallback: String,
): SetTypeChip {
    val normalized = setTypeLabel.trim()
    return when (normalized) {
        "S", "Standard", "Working", "Work" ->
            SetTypeChip("S", StratumSetTypeColors.standard, StratumSetTypeColors.contentColor)
        "M", "MyoRep", "MyoRepFloor" ->
            SetTypeChip("M", StratumSetTypeColors.myoRep, StratumSetTypeColors.contentColor)
        "MM", "MyoRepMatch" ->
            SetTypeChip("MM", StratumSetTypeColors.myoRepMatch, StratumSetTypeColors.contentColor)
        "D", "DropSet" ->
            SetTypeChip("D", StratumSetTypeColors.dropSet, StratumSetTypeColors.contentColor)
        "W", "Warmup" ->
            SetTypeChip(
                "W",
                StratumThemeAccessor.colors.surfaceInset,
                StratumThemeAccessor.colors.textPrimary,
            )
        else ->
            // Unknown labels (or plain ordinals) fall back to position text.
            SetTypeChip(
                label = positionFallback.ifBlank { normalized }.ifBlank { "—" },
                background = StratumThemeAccessor.colors.surfaceInset,
                contentColor = StratumThemeAccessor.colors.textPrimary,
            )
    }
}
