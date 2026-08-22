package com.stratum.core.ui.component.workoutlog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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

    val openKeyboard: (SetLogKeyboardField) -> Unit = { field ->
        if (set.fieldsEnabled) {
            showRirSheet = false
            keyboardHost?.open(
                setKey = set.setKey,
                field = field,
                weightText = set.weightText,
                repsText = set.repsText,
                rir = set.rir,
                showWeightInput = set.showWeightInput,
                hasWeightRecommendation = weightRec,
                hasRepsRecommendation = repsRec,
                onWeightChange = actions.onWeightChange,
                onRepsChange = actions.onRepsChange,
                onRirChange = actions.onRirChange,
                onComplete =
                    actions.onCheckedChange?.let { onChecked ->
                        { onChecked(true) }
                    },
                isPlateLoaded = set.isPlateLoaded,
                recommendedWeightAmount = set.recommendedWeightAmount,
                unitLabel = set.unitLabel,
                isDoubleSided = set.isDoubleSided,
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
                    .padding(vertical = 4.dp, horizontal = WorkoutLogTableHorizontalPadding),
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
                    StratumText(
                        text = set.previousLabel,
                        style = StratumThemeAccessor.typography.bodyLarge,
                        muted = true,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
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
            if (set.showWeightInput) {
                StratumInlineNumericCell(
                    value = setSessionHost?.weightText ?: set.weightText,
                    placeholder = set.weightPlaceholder,
                    onValueChange = actions.onWeightChange,
                    enabled = set.fieldsEnabled,
                    isActive = weightActive,
                    onActivate = { openKeyboard(SetLogKeyboardField.Weight) },
                    completed = set.isComplete,
                    cursorIndex = setSessionHost?.weightCursor ?: set.weightText.length,
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

            StratumInlineNumericCell(
                value = setSessionHost?.repsText ?: set.repsText,
                placeholder = set.repsPlaceholder,
                onValueChange = actions.onRepsChange,
                enabled = set.fieldsEnabled,
                isActive = repsActive,
                onActivate = { openKeyboard(SetLogKeyboardField.Reps) },
                completed = set.isComplete,
                cursorIndex = setSessionHost?.repsCursor ?: set.repsText.length,
                onCursorChange =
                    if (repsActive && setSessionHost != null) {
                        { index -> setSessionHost.setCursor(SetLogKeyboardField.Reps, index) }
                    } else {
                        null
                    },
                modifier =
                    Modifier
                        .weight(weights.reps)
                        .padding(horizontal = 2.dp),
            )

            Box(modifier = Modifier.weight(weights.rir), contentAlignment = Alignment.Center) {
                RirTargetBadge(
                    rirGoal = set.rir,
                    onClick = {
                        if (set.fieldsEnabled && !keyboardOpenForSet) {
                            showRirSheet = true
                        }
                    },
                )
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
    // template Edit Workout. Live logging leaves onDelete null and keeps the checkbox column.
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
