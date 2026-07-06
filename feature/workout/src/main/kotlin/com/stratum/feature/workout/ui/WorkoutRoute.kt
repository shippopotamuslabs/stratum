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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumCheckbox
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumOutlinedButton
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.theme.StratumSupersetGroupColors
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.domain.lift.Weight
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.workout.SetSlotId
import com.stratum.feature.workout.WorkoutViewModel

/**
 * Compose route for the live workout screen (Slice 7). Navigation is hoisted to the
 * route level via callbacks — the navigation controller is never threaded into the
 * feature (ARCHITECTURE.md §4.5).
 */
@Composable
fun WorkoutRoute(
    workoutId: String,
    onNavigateBack: () -> Unit,
    onNavigateToFinalize: (sessionId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(workoutId) {
        viewModel.startSession(WorkoutId(workoutId))
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is WorkoutUiEvent.NavigateToFinalize -> onNavigateToFinalize(event.sessionId)
                is WorkoutUiEvent.ShowMessage -> Unit
            }
        }
    }

    when (val state = uiState) {
        is WorkoutUiState.Loading -> LoadingContent()
        is WorkoutUiState.NoActiveSession -> NoActiveSessionContent(onNavigateBack)
        is WorkoutUiState.Error -> ErrorContent(state.message, onNavigateBack)
        is WorkoutUiState.Loaded -> LoadedContent(
            state = state,
            onNavigateBack = onNavigateBack,
            onFinalize = viewModel::finalizeSession,
            onAbandon = viewModel::abandonSession,
            onLogSet = viewModel::logSet,
            onUnlogSet = viewModel::unlogSet,
            modifier = modifier
        )
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun NoActiveSessionContent(onNavigateBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StratumText(text = "No active session.")
        Spacer(modifier = Modifier.height(16.dp))
        StratumButton(text = "Back", onClick = onNavigateBack)
    }
}

@Composable
private fun ErrorContent(message: String, onNavigateBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StratumText(
            text = "Error: $message",
            style = StratumThemeAccessor.typography.bodyLarge.copy(color = StratumThemeAccessor.colors.error),
        )
        Spacer(modifier = Modifier.height(16.dp))
        StratumButton(text = "Back", onClick = onNavigateBack)
    }
}

@Composable
private fun LoadedContent(
    state: WorkoutUiState.Loaded,
    onNavigateBack: () -> Unit,
    onFinalize: () -> Unit,
    onAbandon: () -> Unit,
    onLogSet: (String, Weight, Int, Int?) -> Unit,
    onUnlogSet: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var remainingRestSeconds by rememberSaveable { mutableIntStateOf(0) }
    var totalRestSeconds by rememberSaveable { mutableIntStateOf(0) }
    var isTimerActive by rememberSaveable { mutableStateOf(false) }
    var showRestSheet by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

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
                title = state.workoutName,
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
                    StratumIconButton(onClick = { showDiscardDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Discard Workout",
                            tint = StratumThemeAccessor.colors.error,
                        )
                    }
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
                        onStartRestTimer = startRestTimer
                    )
                }
            }

            StratumButton(
                text = "Finalize Workout",
                onClick = onFinalize,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showRestSheet) {
        RestTimerBottomSheet(
            isTimerActive = isTimerActive,
            remainingSeconds = remainingRestSeconds,
            totalSeconds = totalRestSeconds,
            onDismiss = { showRestSheet = false },
            onAdd10s = {
                remainingRestSeconds = (remainingRestSeconds + 10).coerceAtMost(3600)
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
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(StratumThemeAccessor.colors.surfaceSunken)
            ) {
                val progress = if (totalSeconds > 0) remainingSeconds.toFloat() / totalSeconds else 0f
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .background(StratumThemeAccessor.colors.accent)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            StratumText(
                text = formatRestDisplay(remainingSeconds),
                style = StratumThemeAccessor.typography.sectionLabel,
            )
        }
    }
}

@Composable
private fun LiftSection(
    lift: RenderedLiftUi,
    onLogSet: (String, Weight, Int, Int?) -> Unit,
    onUnlogSet: (String) -> Unit,
    onStartRestTimer: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = StratumThemeAccessor.colors.surfaceRaised,
                shape = StratumThemeAccessor.shapes.panel
            )
            .border(
                width = StratumThemeAccessor.shapes.borderWidth,
                color = StratumThemeAccessor.colors.borderSubtle,
                shape = StratumThemeAccessor.shapes.panel
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Lift Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Lift image / first letter
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(StratumThemeAccessor.shapes.chip)
                        .background(StratumThemeAccessor.colors.surfaceInset),
                    contentAlignment = Alignment.Center
                ) {
                    StratumText(
                        text = lift.liftName.take(1).uppercase(),
                        style = StratumThemeAccessor.typography.titleLarge
                    )
                }
                Column {
                    StratumText(
                        text = lift.liftName,
                        style = StratumThemeAccessor.typography.titleLarge,
                    )
                    StratumText(
                        text = lift.progressionSchemeLabel,
                        style = StratumThemeAccessor.typography.sectionLabel,
                        muted = true,
                    )
                }
            }
            Icon(
                painter = painterResource(CoreUiR.drawable.ic_more_vert),
                contentDescription = "Lift Options",
                tint = StratumThemeAccessor.colors.textMuted
            )
        }

        // Superset Group Chip
        lift.supersetGroupId?.let { groupId ->
            WorkoutSupersetGroupChip(
                groupId = groupId,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Table Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TableHeaderCell("Set", Modifier.weight(0.10f), alignment = Alignment.CenterStart)
            TableHeaderCell("Prev", Modifier.weight(0.26f))
            TableHeaderCell("Weight", Modifier.weight(0.22f))
            TableHeaderCell("Reps", Modifier.weight(0.18f))
            TableHeaderCell("RPE", Modifier.weight(0.12f))
            TableHeaderCell("", Modifier.weight(0.12f))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(StratumThemeAccessor.shapes.borderWidth)
                .background(StratumThemeAccessor.colors.borderSubtle)
        )

        // Set Rows
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
    val weightState = rememberSaveable(set.slotKey, set.loggedWeightLabel, set.recommendedWeightLabel) {
        mutableStateOf(set.loggedWeightLabel ?: set.recommendedWeightLabel)
    }
    val repsState = rememberSaveable(set.slotKey, set.loggedRepsLabel, set.recommendedRepsLabel) {
        mutableStateOf(set.loggedRepsLabel ?: set.recommendedRepsLabel)
    }
    val rirState = rememberSaveable(set.slotKey, set.rirLabel) {
        val digits = set.rirLabel.filter { it.isDigit() }
        mutableStateOf(digits)
    }

    val rowBg = if (set.isComplete) {
        StratumThemeAccessor.colors.setComplete
    } else {
        Color.Transparent
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(rowBg, shape = RoundedCornerShape(8.dp))
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Set number / type
        val isWarmup = set.setTypeLabel == "Warmup"
        val isDrop = set.setTypeLabel == "DropSet"
        val setLabel = when {
            isWarmup -> "W"
            isDrop -> "D"
            else -> set.positionLabel
        }
        Box(modifier = Modifier.weight(0.10f), contentAlignment = Alignment.CenterStart) {
            StratumText(
                text = setLabel,
                style = StratumThemeAccessor.typography.bodyLarge,
            )
        }

        // Prev Performance
        Box(modifier = Modifier.weight(0.26f), contentAlignment = Alignment.Center) {
            StratumText(
                text = set.previousWeightLabel ?: "—",
                style = StratumThemeAccessor.typography.bodyLarge,
                muted = true,
            )
        }

        // Weight Input
        InlineNumericCell(
            value = weightState.value,
            placeholder = "0",
            onValueChange = { weightState.value = it },
            enabled = !set.isComplete,
            modifier = Modifier.weight(0.22f).padding(horizontal = 2.dp)
        )

        // Reps Input
        InlineNumericCell(
            value = repsState.value,
            placeholder = "0",
            onValueChange = { repsState.value = it },
            enabled = !set.isComplete,
            modifier = Modifier.weight(0.18f).padding(horizontal = 2.dp)
        )

        // RIR Input
        InlineNumericCell(
            value = rirState.value,
            placeholder = "0",
            onValueChange = { rirState.value = it },
            enabled = !set.isComplete,
            modifier = Modifier.weight(0.12f).padding(horizontal = 2.dp)
        )

        // Checkbox
        Box(modifier = Modifier.weight(0.12f), contentAlignment = Alignment.Center) {
            StratumCheckbox(
                checked = set.isComplete,
                onCheckedChange = { checked ->
                    if (checked) {
                        val wt = weightState.value.toDoubleOrNull() ?: 0.0
                        val rp = repsState.value.toIntOrNull() ?: 0
                        val rir = rirState.value.toIntOrNull()
                        onLogSet(set.slotKey, Weight(wt, Weight.WeightUnit.Lb), rp, rir)
                        // Trigger rest timer
                        // Use default 90 seconds rest time if not specified or 0
                        val restTime = if (rir != null) 90 else 60
                        onStartRestTimer(restTime)
                    } else {
                        onUnlogSet(set.slotKey)
                    }
                }
            )
        }
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

@Composable
private fun WorkoutSupersetGroupChip(
    groupId: String,
    modifier: Modifier = Modifier,
) {
    StratumText(
        text = "SUPERSET",
        style = StratumThemeAccessor.typography.sectionLabel.copy(
            color = StratumSupersetGroupColors.contentColor,
        ),
        modifier = modifier
            .background(
                color = StratumSupersetGroupColors.backgroundFor(groupId),
                shape = StratumThemeAccessor.shapes.chip,
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
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
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StratumIconButton(onClick = onSubtract10s) {
                    Icon(
                        painter = painterResource(CoreUiR.drawable.ic_remove),
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
                        painter = painterResource(CoreUiR.drawable.ic_add),
                        contentDescription = "Add 10 seconds",
                        tint = StratumThemeAccessor.colors.textPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            StratumButton(
                text = "Skip",
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
        } else {
            StratumText(
                text = "Choose Rest Time to Start Timer:",
                style = StratumThemeAccessor.typography.bodyLarge,
                muted = true,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val presets = (30..300 step 30).toList()
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
                text = "Abandon Workout?",
                style = StratumThemeAccessor.typography.titleLarge
            )
            StratumText(
                text = "This will permanently delete the current training session and all logged sets. Are you sure?",
                style = StratumThemeAccessor.typography.bodyLarge,
                muted = true
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
            ) {
                StratumOutlinedButton(
                    text = "Cancel",
                    onClick = onDismiss,
                )
                StratumButton(
                    text = "Abandon",
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
