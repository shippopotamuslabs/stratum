package com.stratum.feature.workout.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.RirTargetBottomSheet
import com.stratum.core.ui.component.RirTargetBadge
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumCheckbox
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumOutlinedButton
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTextButton
import com.stratum.core.ui.component.StratumTopBarTextButton
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.LiftActionsBottomSheet
import com.stratum.core.ui.component.SupersetLinkBottomSheet
import com.stratum.core.ui.component.SupersetLinkDirection
import com.stratum.core.ui.component.supersetLinkSheetOptions
import com.stratum.core.ui.component.shouldKeepSupersetSheetOpenAfterLink
import com.stratum.core.ui.component.isSupersetPairMember
import com.stratum.core.ui.component.isLastInSupersetChain
import com.stratum.core.ui.component.SupersetGroupChip
import com.stratum.core.ui.theme.StratumSupersetGroupColors
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.domain.lift.Weight
import com.stratum.feature.workout.R

/** Rest seconds started after a set is logged. Shared by the weighted + bodyweight row branches. */
private const val DEFAULT_REST_SECONDS = 90

/** Upper bound for the rest timer. Matches the largest preset (5:00) so +10s cannot push past it. */
private const val MAX_REST_SECONDS = 300

/**
 * Column weight fractions for one set row. The weight column is present for weighted +
 * assisted lifts and absent for bodyweight, so the two layouts redistribute the same five
 * shared cells (label / prev / [weight] / reps / RIR / checkbox) across the row width.
 */
private data class SetRowWeights(
    val label: Float,
    val prev: Float,
    val weight: Float,
    val reps: Float,
    val rir: Float,
    val checkbox: Float,
)

private val weightedRowWeights = SetRowWeights(
    label = 0.10f,
    prev = 0.26f,
    weight = 0.22f,
    reps = 0.18f,
    rir = 0.12f,
    checkbox = 0.12f,
)

private val bodyweightRowWeights = SetRowWeights(
    label = 0.12f,
    prev = 0.30f,
    weight = 0.0f,
    reps = 0.28f,
    rir = 0.18f,
    checkbox = 0.12f,
)

@Composable
fun WorkoutScreen(
    state: WorkoutUiState.Loaded,
    onNavigateBack: () -> Unit,
    onCompleteWorkout: (saveTemplate: Boolean) -> Unit,
    onAbandon: () -> Unit,
    onLogSet: (String, Weight, Int, Int?) -> Unit,
    onUnlogSet: (String) -> Unit,
    onSwapLiftClick: (workoutId: String, workoutLiftId: String?, liftId: String?, liftName: String?) -> Unit,
    onLinkSupersetWithPrevious: (String) -> Unit,
    onLinkSupersetWithNext: (String) -> Unit,
    onRemoveFromSuperset: (String) -> Unit,
    onSetUseRestTimerBetweenLifts: (String, Boolean) -> Unit,
    onRemoveLift: (String) -> Unit,
    onReorderLiftsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var remainingRestSeconds by rememberSaveable { mutableIntStateOf(0) }
    var totalRestSeconds by rememberSaveable { mutableIntStateOf(0) }
    var isTimerActive by rememberSaveable { mutableStateOf(false) }
    var showRestSheet by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var liftActionsLiftId by remember { mutableStateOf<String?>(null) }
    var supersetSheetLiftId by remember { mutableStateOf<String?>(null) }
    var showCompleteSheet by remember { mutableStateOf(false) }
    var completeSheetPhase by remember { mutableStateOf(CompleteSheetPhase.Incomplete) }

    var elapsedTimeStr by remember(state.sessionStartedAtEpochMilli) { mutableStateOf("00:00") }
    LaunchedEffect(state.sessionStartedAtEpochMilli) {
        while (true) {
            val now = System.currentTimeMillis()
            val diffMs = now - state.sessionStartedAtEpochMilli
            val diffSec = (diffMs / 1000).coerceAtLeast(0)
            val hrs = diffSec / 3600
            val mins = (diffSec % 3600) / 60
            val secs = diffSec % 60
            elapsedTimeStr = if (hrs > 0) {
                String.format(java.util.Locale.US, "%d:%02d:%02d", hrs, mins, secs)
            } else {
                String.format(java.util.Locale.US, "%02d:%02d", mins, secs)
            }
            kotlinx.coroutines.delay(1000L)
        }
    }

    LaunchedEffect(isTimerActive, remainingRestSeconds) {
        if (isTimerActive && remainingRestSeconds > 0) {
            kotlinx.coroutines.delay(1000L)
            remainingRestSeconds -= 1
            if (remainingRestSeconds <= 0) {
                isTimerActive = false
            }
        }
    }

    val startRestTimer = { restSeconds: Int ->
        if (restSeconds > 0) {
            remainingRestSeconds = restSeconds
            totalRestSeconds = restSeconds
            isTimerActive = true
        }
    }

    StratumScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            StratumTopBar(
                title = elapsedTimeStr,
                navigationIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StratumIconButton(onClick = onNavigateBack) {
                            Icon(
                                painter = painterResource(CoreUiR.drawable.ic_chevron_down),
                                contentDescription = "Minimize Workout",
                                tint = StratumThemeAccessor.colors.textPrimary,
                            )
                        }
                        RestTimerTopBarIndicator(
                            isTimerActive = isTimerActive,
                            remainingSeconds = remainingRestSeconds,
                            totalSeconds = totalRestSeconds,
                            onClick = { showRestSheet = true }
                        )
                    }
                },
                actions = {
                    StratumTopBarTextButton(
                        text = stringResource(R.string.workout_screen_complete),
                        onClick = {
                            if (!state.hasIncompleteLifts && !state.hasDirtyEdits) {
                                onCompleteWorkout(false)
                            } else if (state.hasIncompleteLifts) {
                                completeSheetPhase = CompleteSheetPhase.Incomplete
                                showCompleteSheet = true
                            } else {
                                completeSheetPhase = CompleteSheetPhase.SaveChanges
                                showCompleteSheet = true
                            }
                        }
                    )
                }
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(state.lifts, key = { it.workoutLiftId }) { lift ->
                    LiftSection(
                        lift = lift,
                        onLogSet = onLogSet,
                        onUnlogSet = onUnlogSet,
                        onStartRestTimer = startRestTimer,
                        onShowLiftActions = { liftActionsLiftId = lift.workoutLiftId }
                    )
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        StratumTextButton(
                            text = "Discard Workout",
                            onClick = { showDiscardDialog = true },
                            contentColor = StratumThemeAccessor.colors.error
                        )
                    }
                }
            }
        }
    }

    if (showRestSheet) {
        RestTimerBottomSheet(
            isTimerActive = isTimerActive,
            remainingSeconds = remainingRestSeconds,
            totalSeconds = totalRestSeconds,
            onDismiss = { showRestSheet = false },
            onAdd10s = {
                remainingRestSeconds = (remainingRestSeconds + 10).coerceAtMost(MAX_REST_SECONDS)
                if (remainingRestSeconds > totalRestSeconds) {
                    totalRestSeconds = remainingRestSeconds
                }
                isTimerActive = true
            },
            onSubtract10s = {
                remainingRestSeconds = (remainingRestSeconds - 10).coerceAtLeast(0)
                if (remainingRestSeconds == 0) {
                    isTimerActive = false
                }
            },
            onSkip = {
                isTimerActive = false
                remainingRestSeconds = 0
                showRestSheet = false
            },
            onStartPreset = { seconds ->
                remainingRestSeconds = seconds
                totalRestSeconds = seconds
                isTimerActive = true
            }
        )
    }

    if (showDiscardDialog) {
        DiscardWorkoutDialog(
            onDismiss = { showDiscardDialog = false },
            onConfirm = {
                showDiscardDialog = false
                onAbandon()
                onNavigateBack()
            }
        )
    }

    liftActionsLiftId?.let { liftId ->
        val lift = state.lifts.firstOrNull { it.workoutLiftId == liftId }
        if (lift != null) {
            LiftActionsBottomSheet(
                onConfigureSuperset = {
                    liftActionsLiftId = null
                    supersetSheetLiftId = liftId
                },
                onSwapLift = {
                    liftActionsLiftId = null
                    onSwapLiftClick(state.workoutId, liftId, lift.liftId, lift.liftName)
                },
                onRemoveLift = {
                    liftActionsLiftId = null
                    onRemoveLift(liftId)
                },
                onReorderLifts = {
                    liftActionsLiftId = null
                    onReorderLiftsClick()
                },
                onDismiss = { liftActionsLiftId = null }
            )
        } else {
            liftActionsLiftId = null
        }
    }

    supersetSheetLiftId?.let { liftId ->
        val currentLift = state.lifts.firstOrNull { it.workoutLiftId == liftId }
        val options = supersetLinkSheetOptions(lifts = state.lifts, workoutLiftId = liftId)
        if (options.isNotEmpty() && currentLift != null) {
            SupersetLinkBottomSheet(
                options = options,
                showRestBetweenLiftsToggle =
                    isSupersetPairMember(currentLift, state.lifts) &&
                        !isLastInSupersetChain(currentLift, state.lifts),
                useRestTimerBetweenLifts = currentLift.useRestTimerBetweenLifts,
                onUseRestTimerBetweenLiftsChange = { enabled ->
                    onSetUseRestTimerBetweenLifts(liftId, enabled)
                },
                onOptionSelected = { direction ->
                    when (direction) {
                        SupersetLinkDirection.Previous -> onLinkSupersetWithPrevious(liftId)
                        SupersetLinkDirection.Next -> onLinkSupersetWithNext(liftId)
                        SupersetLinkDirection.Remove -> onRemoveFromSuperset(liftId)
                    }
                    if (!shouldKeepSupersetSheetOpenAfterLink(state.lifts, liftId, direction)) {
                        supersetSheetLiftId = null
                    }
                },
                onDismiss = { supersetSheetLiftId = null }
            )
        } else {
            supersetSheetLiftId = null
        }
    }

    if (showCompleteSheet) {
        CompleteWorkoutSheet(
            phase = completeSheetPhase,
            onContinueWorkout = { showCompleteSheet = false },
            onCompleteWorkout = {
                if (completeSheetPhase == CompleteSheetPhase.Incomplete && state.hasDirtyEdits) {
                    completeSheetPhase = CompleteSheetPhase.SaveChanges
                } else {
                    showCompleteSheet = false
                    onCompleteWorkout(false)
                }
            },
            onUpdateTemplate = {
                showCompleteSheet = false
                onCompleteWorkout(true)
            },
            onDiscardChanges = {
                showCompleteSheet = false
                onCompleteWorkout(false)
            },
            onDismiss = { showCompleteSheet = false },
        )
    }
}

@Composable
private fun RestTimerTopBarIndicator(
    isTimerActive: Boolean,
    remainingSeconds: Int,
    totalSeconds: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val barWidth by animateDpAsState(
        targetValue = if (isTimerActive) 80.dp else 0.dp,
        label = "restTimerWidth"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(end = 8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Timer,
            contentDescription = "Rest Timer",
            tint = StratumThemeAccessor.colors.textPrimary,
            modifier = Modifier.size(24.dp)
        )
        if (barWidth > 0.dp) {
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(24.dp)
                    .border(
                        width = 1.dp,
                        color = StratumThemeAccessor.colors.accent,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .clip(RoundedCornerShape(4.dp))
                    .background(StratumThemeAccessor.colors.surfaceSunken),
                contentAlignment = Alignment.Center
            ) {
                val progress = if (totalSeconds > 0) remainingSeconds.toFloat() / totalSeconds else 0f
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .align(Alignment.CenterStart)
                        .background(StratumThemeAccessor.colors.accent.copy(alpha = 0.3f))
                )
                StratumText(
                    text = formatRestDisplay(remainingSeconds),
                    style = StratumThemeAccessor.typography.sectionLabel,
                )
            }
        }
    }
}

@Composable
private fun LiftSection(
    lift: RenderedLiftUi,
    onLogSet: (String, Weight, Int, Int?) -> Unit,
    onUnlogSet: (String) -> Unit,
    onStartRestTimer: (Int) -> Unit,
    onShowLiftActions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(StratumThemeAccessor.shapes.panel)
            .background(
                color = StratumThemeAccessor.colors.surfaceRaised,
                shape = StratumThemeAccessor.shapes.panel
            )
            .border(
                width = StratumThemeAccessor.shapes.borderWidth,
                color = StratumThemeAccessor.colors.borderSubtle,
                shape = StratumThemeAccessor.shapes.panel
            )
            .padding(top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StratumText(
                text = lift.liftName,
                style = StratumThemeAccessor.typography.titleLarge,
                modifier = Modifier.weight(1f).padding(end = 20.dp),
            )
            StratumIconButton(
                onClick = onShowLiftActions,
                containerColor = Color.Transparent,
            ) {
                Icon(
                    painter = painterResource(CoreUiR.drawable.ic_more_vert),
                    contentDescription = "Lift Options",
                    tint = StratumThemeAccessor.colors.textMuted
                )
            }
        }

        lift.supersetGroupId?.let { groupId ->
            SupersetGroupChip(
                groupId = groupId,
                modifier = Modifier
                    .padding(vertical = 2.dp)
                    .padding(horizontal = 16.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val weights = if (lift.showWeightColumn) weightedRowWeights else bodyweightRowWeights
            TableHeaderCell("Set", Modifier.weight(weights.label), alignment = Alignment.CenterStart)
            TableHeaderCell("Prev", Modifier.weight(weights.prev))
            if (lift.showWeightColumn) {
                TableHeaderCell(lift.weightHeaderLabel, Modifier.weight(weights.weight))
            }
            TableHeaderCell("Reps", Modifier.weight(weights.reps))
            TableHeaderCell("RIR", Modifier.weight(weights.rir))
            TableHeaderCell("", Modifier.weight(weights.checkbox))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(StratumThemeAccessor.shapes.borderWidth)
                .background(StratumThemeAccessor.colors.borderSubtle)
        )

        lift.sets.forEach { set ->
            WorkoutSetRow(
                set = set,
                onLogSet = onLogSet,
                onUnlogSet = onUnlogSet,
                onStartRestTimer = onStartRestTimer
            )
        }
    }
}

@Composable
private fun TableHeaderCell(
    text: String,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
) {
    Box(
        modifier = modifier,
        contentAlignment = alignment,
    ) {
        StratumText(
            text = text,
            style = StratumThemeAccessor.typography.sectionLabel,
            muted = true,
        )
    }
}

@Composable
private fun WorkoutSetRow(
    set: RenderedSetUi,
    onLogSet: (String, Weight, Int, Int?) -> Unit,
    onUnlogSet: (String) -> Unit,
    onStartRestTimer: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val weightState = rememberSaveable(set.slotKey, set.loggedWeightLabel) {
        mutableStateOf(set.loggedWeightLabel ?: "")
    }
    val repsState = rememberSaveable(set.slotKey, set.loggedRepsLabel) {
        mutableStateOf(set.loggedRepsLabel ?: "")
    }
    val rirState = rememberSaveable(set.slotKey, set.loggedRir) {
        mutableIntStateOf(set.loggedRir ?: set.rirGoal)
    }
    var showRirSheet by remember { mutableStateOf(false) }

    val rowBg = if (set.isComplete) {
        StratumThemeAccessor.colors.setComplete
    } else {
        Color.Transparent
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(rowBg)
            .padding(vertical = 4.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val isWarmup = set.setTypeLabel == "Warmup"
        val isDrop = set.setTypeLabel == "DropSet"
        val setLabel = when {
            isWarmup -> "W"
            isDrop -> "D"
            else -> set.positionLabel
        }
        val weights = if (set.showWeightInput) weightedRowWeights else bodyweightRowWeights

        // Set label
        Box(modifier = Modifier.weight(weights.label), contentAlignment = Alignment.CenterStart) {
            StratumText(
                text = setLabel,
                style = StratumThemeAccessor.typography.bodyLarge,
            )
        }

        // Previous best
        Box(modifier = Modifier.weight(weights.prev), contentAlignment = Alignment.Center) {
            StratumText(
                text = set.previousWeightLabel ?: "—",
                style = StratumThemeAccessor.typography.bodyLarge,
                muted = true,
            )
        }

        // Weight input (omitted for bodyweight — weights.weight is 0f and the cell is skipped)
        if (set.showWeightInput) {
            InlineNumericCell(
                value = weightState.value,
                placeholder = set.recommendedWeightLabel,
                onValueChange = { weightState.value = it },
                enabled = !set.isComplete,
                modifier = Modifier
                    .weight(weights.weight)
                    .padding(horizontal = 2.dp)
            )
        }

        // Reps
        InlineNumericCell(
            value = repsState.value,
            placeholder = set.recommendedRepsLabel,
            onValueChange = { repsState.value = it },
            enabled = !set.isComplete,
            modifier = Modifier
                .weight(weights.reps)
                .padding(horizontal = 2.dp)
        )

        // RIR
        Box(modifier = Modifier.weight(weights.rir), contentAlignment = Alignment.Center) {
            RirTargetBadge(
                rirGoal = rirState.intValue,
                onClick = {
                    if (!set.isComplete) {
                        showRirSheet = true
                    }
                }
            )
        }

        // Checkbox + log. For bodyweight the weight is always 0; for assisted the
        // checkbox gates on |recommended| so a -50 lb suggestion still enables logging.
        Box(modifier = Modifier.weight(weights.checkbox), contentAlignment = Alignment.Center) {
            val weightMagnitude = if (set.assistMode) kotlin.math.abs(set.recommendedWeightAmount) else set.recommendedWeightAmount
            val isCheckboxEnabled = !set.showWeightInput ||
                weightMagnitude > 0.0 || weightState.value.isNotEmpty() || set.isComplete
            StratumCheckbox(
                checked = set.isComplete,
                enabled = isCheckboxEnabled,
                onCheckedChange = { checked ->
                    if (checked) {
                        val repsEmpty = repsState.value.isEmpty()
                        val finalReps = if (repsEmpty) set.repMin else (repsState.value.toIntOrNull() ?: 0)
                        val finalRir = rirState.intValue
                        val finalWeight = if (set.showWeightInput) {
                            val weightEmpty = weightState.value.isEmpty()
                            if (weightEmpty) set.recommendedWeightAmount else (weightState.value.toDoubleOrNull() ?: 0.0)
                        } else {
                            0.0
                        }

                        onLogSet(set.slotKey, Weight(finalWeight, Weight.WeightUnit.Lb), finalReps, finalRir)
                        onStartRestTimer(DEFAULT_REST_SECONDS)
                    } else {
                        onUnlogSet(set.slotKey)
                    }
                }
            )
        }
    }

    if (showRirSheet) {
        RirTargetBottomSheet(
            selectedRirGoal = rirState.intValue,
            onRirSelected = { rir -> rirState.intValue = rir },
            onDismiss = { showRirSheet = false }
        )
    }
}

@Composable
private fun InlineNumericCell(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusManager = LocalFocusManager.current
    var fieldValue by remember(value) {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }

    LaunchedEffect(value) {
        if (!isFocused) {
            fieldValue = TextFieldValue(text = value, selection = TextRange(value.length))
        }
    }

    BasicTextField(
        value = fieldValue,
        onValueChange = { updated ->
            val cleaned = updated.text.filter { char -> char.isDigit() || char == '.' }
            fieldValue = updated.copy(text = cleaned, selection = TextRange(cleaned.length))
            onValueChange(cleaned)
        },
        enabled = enabled,
        modifier = modifier
            .height(36.dp)
            .clip(StratumThemeAccessor.shapes.chip)
            .background(if (enabled) StratumThemeAccessor.colors.surfaceInset else StratumThemeAccessor.colors.surfaceSunken)
            .border(
                width = StratumThemeAccessor.shapes.borderWidth,
                color = if (isFocused) StratumThemeAccessor.colors.accent else StratumThemeAccessor.colors.borderSubtle,
                shape = StratumThemeAccessor.shapes.chip,
            )
            .padding(horizontal = 8.dp),
        textStyle = StratumThemeAccessor.typography.bodyLarge.copy(
            color = if (enabled) StratumThemeAccessor.colors.textPrimary else StratumThemeAccessor.colors.textMuted,
            textAlign = TextAlign.Center,
        ),
        singleLine = true,
        cursorBrush = SolidColor(StratumThemeAccessor.colors.accent),
        interactionSource = interactionSource,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(
            onDone = {
                focusManager.clearFocus()
            }
        ),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.Center) {
                if (fieldValue.text.isEmpty() && !isFocused) {
                    StratumText(text = placeholder, muted = true)
                }
                innerTextField()
            }
        },
    )
}



@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RestTimerBottomSheet(
    isTimerActive: Boolean,
    remainingSeconds: Int,
    totalSeconds: Int,
    onDismiss: () -> Unit,
    onAdd10s: () -> Unit,
    onSubtract10s: () -> Unit,
    onSkip: () -> Unit,
    onStartPreset: (Int) -> Unit,
) {
    StratumBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        StratumText(
            text = "Rest Timer",
            style = StratumThemeAccessor.typography.titleLarge,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 8.dp)
        )

        val progress = if (totalSeconds > 0) remainingSeconds.toFloat() / totalSeconds else 0f
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .background(StratumThemeAccessor.colors.surfaceSunken)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(StratumThemeAccessor.colors.accent)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isTimerActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StratumIconButton(onClick = onSubtract10s) {
                    Icon(
                        painter = painterResource(R.drawable.back_10),
                        contentDescription = "Reduce 10 seconds",
                        tint = StratumThemeAccessor.colors.textPrimary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = StratumThemeAccessor.colors.textPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                    StratumText(
                        text = formatRestDisplay(remainingSeconds),
                        style = StratumThemeAccessor.typography.titleLarge.copy(
                            fontSize = StratumThemeAccessor.typography.titleLarge.fontSize * 1.5
                        )
                    )
                }

                StratumIconButton(onClick = onAdd10s) {
                    Icon(
                        painter = painterResource(R.drawable.forward_10),
                        contentDescription = "Add 10 seconds",
                        tint = StratumThemeAccessor.colors.textPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            StratumButton(
                text = "Skip",
                onClick = onSkip,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        } else {
            StratumText(
                text = "Choose Rest Time to Start Timer:",
                style = StratumThemeAccessor.typography.bodyLarge,
                muted = true,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 8.dp)
            )

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val presets = (30..MAX_REST_SECONDS step 30).toList()
                presets.forEach { seconds ->
                    val min = seconds / 60
                    val sec = seconds % 60
                    val label = if (sec == 0) "$min:00" else "$min:$sec"
                    Box(
                        modifier = Modifier
                            .clip(StratumThemeAccessor.shapes.chip)
                            .background(StratumThemeAccessor.colors.surfaceInset)
                            .border(
                                width = StratumThemeAccessor.shapes.borderWidth,
                                color = StratumThemeAccessor.colors.borderSubtle,
                                shape = StratumThemeAccessor.shapes.chip
                            )
                            .clickable { onStartPreset(seconds) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        StratumText(
                            text = label,
                            style = StratumThemeAccessor.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscardWorkoutDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = StratumThemeAccessor.colors.surfaceRaised,
                    shape = StratumThemeAccessor.shapes.panel
                )
                .border(
                    width = StratumThemeAccessor.shapes.borderWidth,
                    color = StratumThemeAccessor.colors.borderStrong,
                    shape = StratumThemeAccessor.shapes.panel
                )
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StratumText(
                text = stringResource(R.string.workout_screen_discard_workout),
                style = StratumThemeAccessor.typography.titleLarge
            )
            StratumText(
                text = stringResource(R.string.workout_screen_discard_confirm_body),
                style = StratumThemeAccessor.typography.bodyLarge,
                muted = true
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
            ) {
                StratumOutlinedButton(
                    text = stringResource(R.string.workout_screen_cancel_discard),
                    onClick = onDismiss,
                )
                StratumButton(
                    text = stringResource(R.string.workout_screen_confirm_discard),
                    onClick = onConfirm,
                )
            }
        }
    }
}

private fun formatRestDisplay(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    val sStr = s.toString().padStart(2, '0')
    return "$m:$sStr"
}

enum class CompleteSheetPhase { Incomplete, SaveChanges }

@Composable
private fun CompleteWorkoutSheet(
    phase: CompleteSheetPhase,
    onContinueWorkout: () -> Unit,
    onCompleteWorkout: () -> Unit,
    onUpdateTemplate: () -> Unit,
    onDiscardChanges: () -> Unit,
    onDismiss: () -> Unit,
) {
    StratumBottomSheet(onDismissRequest = onDismiss) {
        when (phase) {
            CompleteSheetPhase.Incomplete -> {
                StratumText(
                    text = stringResource(R.string.workout_sheet_incomplete_title),
                    style = StratumThemeAccessor.typography.titleLarge,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                StratumText(
                    text = stringResource(R.string.workout_sheet_incomplete_message),
                    style = StratumThemeAccessor.typography.bodyLarge,
                    muted = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                StratumButton(
                    text = stringResource(R.string.workout_sheet_continue_workout),
                    onClick = onContinueWorkout,
                    modifier = Modifier.fillMaxWidth(),
                )
                StratumOutlinedButton(
                    text = stringResource(R.string.workout_sheet_complete_workout),
                    onClick = onCompleteWorkout,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            CompleteSheetPhase.SaveChanges -> {
                StratumText(
                    text = stringResource(R.string.workout_sheet_save_changes_title),
                    style = StratumThemeAccessor.typography.titleLarge,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                StratumText(
                    text = stringResource(R.string.workout_sheet_save_changes_message),
                    style = StratumThemeAccessor.typography.bodyLarge,
                    muted = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                StratumButton(
                    text = stringResource(R.string.workout_sheet_update_template),
                    onClick = onUpdateTemplate,
                    modifier = Modifier.fillMaxWidth(),
                )
                StratumOutlinedButton(
                    text = stringResource(R.string.workout_sheet_discard_changes),
                    onClick = onDiscardChanges,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
