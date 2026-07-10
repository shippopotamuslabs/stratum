package com.stratum.feature.workout.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.LiftActionsBottomSheet
import com.stratum.core.ui.component.LiftThumbnail
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumOutlinedButton
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTextButton
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.StratumTopBarTextButton
import com.stratum.core.ui.component.SupersetLinkBottomSheet
import com.stratum.core.ui.component.SupersetLinkDirection
import com.stratum.core.ui.component.isLastInSupersetChain
import com.stratum.core.ui.component.isSupersetPairMember
import com.stratum.core.ui.component.shouldKeepSupersetSheetOpenAfterLink
import com.stratum.core.ui.component.supersetLinkSheetOptions
import com.stratum.core.ui.component.workoutlog.StratumWorkoutLogLiftPanel
import com.stratum.core.ui.component.workoutlog.WorkoutLogLiftUi
import com.stratum.core.ui.component.workoutlog.WorkoutLogSetRowActions
import com.stratum.core.ui.component.workoutlog.WorkoutLogSetUi
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.workout.R
import com.stratum.feature.workout.notification.RestTimerDefaults

/**
 * Rest-timer chrome + actions passed as one bag so [WorkoutScreen] stays under Detekt's
 * function parameter threshold.
 */
data class RestTimerChrome(
    val isActive: Boolean,
    val remainingSeconds: Int,
    val totalSeconds: Int,
    val onStart: (Int) -> Unit,
    val onAdd10s: () -> Unit,
    val onSubtract10s: () -> Unit,
    val onSkip: () -> Unit,
)

/** Session actions for [WorkoutScreen] — kept as one bag for Detekt [LongParameterList]. */
data class WorkoutScreenActions(
    val onNavigateBack: () -> Unit,
    val onCompleteWorkout: (saveTemplate: Boolean) -> Unit,
    val onAbandon: () -> Unit,
    /** [weightAmountLb] is the UI magnitude; the ViewModel applies load-type sign rules. */
    val onLogSet: (slotKey: String, weightAmountLb: Double, reps: Int, rir: Int?) -> Unit,
    val onUnlogSet: (String) -> Unit,
    val onSwapLiftClick: (workoutId: String, workoutLiftId: String?, liftId: String?, liftName: String?) -> Unit,
    val onLinkSupersetWithPrevious: (String) -> Unit,
    val onLinkSupersetWithNext: (String) -> Unit,
    val onRemoveFromSuperset: (String) -> Unit,
    val onSetUseRestTimerBetweenLifts: (String, Boolean) -> Unit,
    val onRemoveLift: (String) -> Unit,
    val onReorderLiftsClick: () -> Unit,
    val onAddSet: (workoutLiftId: String) -> Unit = {},
    val onDeleteSet: (slotKey: String) -> Unit = {},
    val onAddLift: () -> Unit = {},
)

/** 56.dp + 25% for denser, more tappable tabs. */
private val LiftNavigatorTabSize = 70.dp
private val LiftNavigatorTabSpacing = 4.dp
private val LiftNavigatorUnderlineGap = 6.dp
private val LiftNavigatorUnderlineHeight = 3.dp
private val LiftNavigatorRowHeight =
    LiftNavigatorTabSize + LiftNavigatorUnderlineGap + LiftNavigatorUnderlineHeight
private val LiftListSpacing = 16.dp
private const val LIFT_NAVIGATOR_INACTIVE_ALPHA = 0.4f

@Composable
fun WorkoutScreen(
    state: WorkoutUiState.Loaded,
    restTimer: RestTimerChrome,
    actions: WorkoutScreenActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
) {
    var showRestSheet by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var liftActionsLiftId by remember { mutableStateOf<String?>(null) }
    var supersetSheetLiftId by remember { mutableStateOf<String?>(null) }
    var showCompleteSheet by remember { mutableStateOf(false) }
    var completeSheetPhase by remember { mutableStateOf(CompleteSheetPhase.Incomplete) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val listSpacingPx = with(density) { LiftListSpacing.roundToPx() }
    /** Per-lift panel heights for single-shot pixel scrolling (avoids multi-phase animateScrollToItem). */
    val liftHeightsPx = remember { mutableStateMapOf<String, Int>() }
    var programmaticScroll by remember { mutableStateOf(false) }
    var tabScrollJob by remember { mutableStateOf<Job?>(null) }
    var selectedLiftId by remember(state.lifts.map { it.workoutLiftId }) {
        mutableStateOf(state.lifts.firstOrNull()?.workoutLiftId)
    }

    // Drop height cache entries for lifts no longer in the session.
    LaunchedEffect(state.lifts.map { it.workoutLiftId }) {
        val keys = state.lifts.map { it.workoutLiftId }.toSet()
        liftHeightsPx.keys.retainAll(keys)
    }

    // Pick the lift whose top edge is closest to the list viewport top (not "first fully pinned").
    val primaryVisibleLiftId by remember(state.lifts) {
        derivedStateOf {
            val liftKeys = state.lifts.map { it.workoutLiftId }.toSet()
            val threshold = listState.layoutInfo.viewportStartOffset
            primaryLiftIdFromScroll(
                visibleItems = listState.layoutInfo.visibleItemsInfo.mapNotNull { info ->
                    val key = info.key as? String ?: return@mapNotNull null
                    key to info.offset
                },
                liftKeys = liftKeys,
                threshold = threshold,
            )
        }
    }
    LaunchedEffect(primaryVisibleLiftId, programmaticScroll) {
        if (!programmaticScroll && primaryVisibleLiftId != null) {
            selectedLiftId = primaryVisibleLiftId
        }
    }

    // Continuous underline under every member of the selected lift's superset / giant set.
    val underlinedLiftIds = remember(selectedLiftId, state.lifts) {
        val primary = selectedLiftId ?: return@remember emptySet()
        val primaryLift = state.lifts.firstOrNull { it.workoutLiftId == primary }
        val groupId = primaryLift?.supersetGroupId
        if (groupId.isNullOrBlank()) {
            setOf(primary)
        } else {
            state.lifts
                .filter { it.supersetGroupId == groupId }
                .map { it.workoutLiftId }
                .toSet()
        }
    }

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

    StratumScaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHostState = snackbarHostState,
        topBar = {
            StratumTopBar(
                title = elapsedTimeStr,
                navigationIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StratumIconButton(onClick = actions.onNavigateBack) {
                            Icon(
                                painter = painterResource(CoreUiR.drawable.ic_chevron_down),
                                contentDescription = "Minimize Workout",
                                tint = StratumThemeAccessor.colors.textPrimary,
                            )
                        }
                        RestTimerTopBarIndicator(
                            isTimerActive = restTimer.isActive,
                            remainingSeconds = restTimer.remainingSeconds,
                            totalSeconds = restTimer.totalSeconds,
                            onClick = { showRestSheet = true }
                        )
                    }
                },
                actions = {
                    StratumTopBarTextButton(
                        text = stringResource(R.string.workout_screen_complete),
                        onClick = {
                            if (!state.hasIncompleteLifts && !state.hasDirtyEdits) {
                                actions.onCompleteWorkout(false)
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
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LiftNavigatorRow(
                lifts = state.lifts,
                primaryLiftId = selectedLiftId,
                underlinedLiftIds = underlinedLiftIds,
                onLiftClick = { workoutLiftId ->
                    val index = state.lifts.indexOfFirst { it.workoutLiftId == workoutLiftId }
                    if (index < 0) return@LiftNavigatorRow
                    selectedLiftId = workoutLiftId
                    // Cancel any in-flight tab scroll so stacked animations don't stutter.
                    tabScrollJob?.cancel()
                    tabScrollJob =
                        scope.launch {
                            programmaticScroll = true
                            try {
                                listState.animateScrollToLiftTop(
                                    targetIndex = index,
                                    liftIds = state.lifts.map { it.workoutLiftId },
                                    itemHeightsPx = liftHeightsPx,
                                    spacingPx = listSpacingPx,
                                )
                            } finally {
                                programmaticScroll = false
                            }
                        }
                },
                onAddLift = actions.onAddLift,
                modifier = Modifier.fillMaxWidth(),
            )

            WorkoutLiftList(
                lifts = state.lifts,
                listState = listState,
                liftHeightsPx = liftHeightsPx,
                onLogSet = actions.onLogSet,
                onUnlogSet = actions.onUnlogSet,
                onAddSet = actions.onAddSet,
                onDeleteSet = actions.onDeleteSet,
                onStartRestTimer = restTimer.onStart,
                onShowLiftActions = { liftActionsLiftId = it },
                onDiscardClick = { showDiscardDialog = true },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }
    }

    if (showRestSheet) {
        RestTimerBottomSheet(
            isTimerActive = restTimer.isActive,
            remainingSeconds = restTimer.remainingSeconds,
            totalSeconds = restTimer.totalSeconds,
            onDismiss = { showRestSheet = false },
            onAdd10s = restTimer.onAdd10s,
            onSubtract10s = restTimer.onSubtract10s,
            onSkip = {
                restTimer.onSkip()
                showRestSheet = false
            },
            onStartPreset = restTimer.onStart,
        )
    }

    if (showDiscardDialog) {
        DiscardWorkoutDialog(
            onDismiss = { showDiscardDialog = false },
            onConfirm = {
                showDiscardDialog = false
                actions.onAbandon()
                actions.onNavigateBack()
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
                    actions.onSwapLiftClick(state.workoutId, liftId, lift.liftId, lift.liftName)
                },
                onRemoveLift = {
                    liftActionsLiftId = null
                    actions.onRemoveLift(liftId)
                },
                onReorderLifts = {
                    liftActionsLiftId = null
                    actions.onReorderLiftsClick()
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
                    actions.onSetUseRestTimerBetweenLifts(liftId, enabled)
                },
                onOptionSelected = { direction ->
                    when (direction) {
                        SupersetLinkDirection.Previous -> actions.onLinkSupersetWithPrevious(liftId)
                        SupersetLinkDirection.Next -> actions.onLinkSupersetWithNext(liftId)
                        SupersetLinkDirection.Remove -> actions.onRemoveFromSuperset(liftId)
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
                    actions.onCompleteWorkout(false)
                }
            },
            onUpdateTemplate = {
                showCompleteSheet = false
                actions.onCompleteWorkout(true)
            },
            onDiscardChanges = {
                showCompleteSheet = false
                actions.onCompleteWorkout(false)
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

/**
 * Scrollable lift panels + discard. Bottom [contentPadding] is sized so max scroll pins the
 * last lift under the navigator — only the shortfall after last-lift + gap + discard.
 */
@Composable
private fun WorkoutLiftList(
    lifts: List<RenderedLiftUi>,
    listState: LazyListState,
    liftHeightsPx: MutableMap<String, Int>,
    onLogSet: (slotKey: String, weightAmountLb: Double, reps: Int, rir: Int?) -> Unit,
    onUnlogSet: (String) -> Unit,
    onAddSet: (workoutLiftId: String) -> Unit,
    onDeleteSet: (slotKey: String) -> Unit,
    onStartRestTimer: (Int) -> Unit,
    onShowLiftActions: (workoutLiftId: String) -> Unit,
    onDiscardClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var discardHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val listSpacingPx = with(density) { LiftListSpacing.roundToPx() }
    val viewportHeightPx = listState.layoutInfo.viewportSize.height
    // Prefer live LazyList measurements when the trailing items are composed.
    val measuredLastLiftPx =
        listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == lifts.lastOrNull()?.workoutLiftId }
            ?.size
            ?: lifts.lastOrNull()?.let { liftHeightsPx[it.workoutLiftId] }
            ?: 0
    val measuredDiscardPx =
        listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == "discard" }
            ?.size
            ?: discardHeightPx
    val bottomPinPaddingPx =
        bottomPinSpacerHeightPx(
            viewportHeightPx = viewportHeightPx,
            lastLiftHeightPx = measuredLastLiftPx,
            discardHeightPx = measuredDiscardPx,
            listSpacingPx = listSpacingPx,
        )

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding =
            PaddingValues(bottom = with(density) { bottomPinPaddingPx.toDp() }),
        verticalArrangement = Arrangement.spacedBy(LiftListSpacing),
    ) {
        itemsIndexed(lifts, key = { _, lift -> lift.workoutLiftId }) { _, lift ->
            LiveLiftSection(
                lift = lift,
                onLogSet = onLogSet,
                onUnlogSet = onUnlogSet,
                onAddSet = { onAddSet(lift.workoutLiftId) },
                onDeleteSet = onDeleteSet,
                onStartRestTimer = onStartRestTimer,
                onShowLiftActions = { onShowLiftActions(lift.workoutLiftId) },
                modifier =
                    Modifier.onSizeChanged { size ->
                        liftHeightsPx[lift.workoutLiftId] = size.height
                    },
            )
        }

        item(key = "discard") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { discardHeightPx = it.height },
                contentAlignment = Alignment.Center,
            ) {
                StratumTextButton(
                    text = "Discard Workout",
                    onClick = onDiscardClick,
                    contentColor = StratumThemeAccessor.colors.error,
                )
            }
        }
    }
}

/**
 * Sticky horizontal strip of lift tabs (image or initials) + add-lift control.
 * Consecutive supersets share one continuous underline; only [primaryLiftId] is full-opacity.
 */
@Composable
private fun LiftNavigatorRow(
    lifts: List<RenderedLiftUi>,
    primaryLiftId: String?,
    underlinedLiftIds: Set<String>,
    onLiftClick: (workoutLiftId: String) -> Unit,
    onAddLift: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val addLiftCd = stringResource(R.string.workout_screen_add_lift)
    val groups = remember(lifts) { groupLiftsForNavigator(lifts) }
    // Fixed row height so the underline slot is never clipped by LazyRow measurement.
    LazyRow(
        modifier = modifier.height(LiftNavigatorRowHeight),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(LiftNavigatorTabSpacing),
        verticalAlignment = Alignment.Top,
    ) {
        items(groups, key = { group -> group.joinToString("-") { it.workoutLiftId } }) { group ->
            LiftNavigatorGroup(
                lifts = group,
                primaryLiftId = primaryLiftId,
                showUnderline = group.any { it.workoutLiftId in underlinedLiftIds },
                onLiftClick = onLiftClick,
            )
        }
        item(key = "add-lift") {
            LiftNavigatorAddTab(
                contentDescription = addLiftCd,
                onClick = onAddLift,
            )
        }
    }
}

@Composable
private fun LiftNavigatorGroup(
    lifts: List<RenderedLiftUi>,
    primaryLiftId: String?,
    showUnderline: Boolean,
    onLiftClick: (workoutLiftId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    // Explicit width so the continuous underline has a real span (fillMaxWidth alone can
    // collapse when only Align-positioned children size the Box).
    val groupWidth =
        LiftNavigatorTabSize * lifts.size +
            LiftNavigatorTabSpacing * (lifts.size - 1).coerceAtLeast(0)
    Box(
        modifier = modifier
            .width(groupWidth)
            .height(LiftNavigatorRowHeight),
    ) {
        Row(
            modifier = Modifier.align(Alignment.TopStart),
            horizontalArrangement = Arrangement.spacedBy(LiftNavigatorTabSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            lifts.forEach { lift ->
                LiftNavigatorTab(
                    lift = lift,
                    highlighted = lift.workoutLiftId == primaryLiftId,
                    onClick = { onLiftClick(lift.workoutLiftId) },
                )
            }
        }
        if (showUnderline) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(LiftNavigatorUnderlineHeight)
                    .clip(RoundedCornerShape(1.5.dp))
                    // High-contrast bar (sample apps use a light line on dark chrome).
                    .background(colors.textPrimary),
            )
        }
    }
}

@Composable
private fun LiftNavigatorTab(
    lift: RenderedLiftUi,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val alpha = if (highlighted) 1f else LIFT_NAVIGATOR_INACTIVE_ALPHA
    // Prefer catalog image; otherwise two-letter initials (BB / SQ) for denser tabs.
    // Borderless thumbnails — selection is underline + opacity only.
    val hasImage = !lift.imageUrl.isNullOrBlank()
    Box(
        modifier = modifier
            .size(LiftNavigatorTabSize)
            .alpha(alpha)
            .clip(shapes.chip)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (hasImage) {
            LiftThumbnail(
                name = lift.liftName,
                imageUrl = lift.imageUrl,
                size = LiftNavigatorTabSize,
                bordered = false,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.surfaceSunken, shapes.chip),
                contentAlignment = Alignment.Center,
            ) {
                StratumText(
                    text = lift.navigatorInitials.ifBlank { "?" },
                    style = StratumThemeAccessor.typography.titleMedium,
                    muted = !highlighted,
                )
            }
        }
    }
}

@Composable
private fun LiftNavigatorAddTab(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    Box(
        modifier = modifier
            .height(LiftNavigatorRowHeight)
            .width(LiftNavigatorTabSize),
    ) {
        Box(
            modifier = Modifier
                .size(LiftNavigatorTabSize)
                .align(Alignment.TopCenter)
                .clip(shapes.chip)
                .background(colors.surfaceRaised)
                .border(
                    width = shapes.borderWidth,
                    color = colors.borderSubtle,
                    shape = shapes.chip,
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(CoreUiR.drawable.ic_add),
                contentDescription = contentDescription,
                tint = colors.textPrimary,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/**
 * Consecutive lifts that share a non-null [RenderedLiftUi.supersetGroupId] form one tab group
 * so a single underline can span the set.
 */
internal fun groupLiftsForNavigator(lifts: List<RenderedLiftUi>): List<List<RenderedLiftUi>> {
    if (lifts.isEmpty()) return emptyList()
    val groups = mutableListOf<MutableList<RenderedLiftUi>>()
    for (lift in lifts) {
        val current = groups.lastOrNull()
        val currentGroupId = current?.firstOrNull()?.supersetGroupId
        if (
            current != null &&
            !lift.supersetGroupId.isNullOrBlank() &&
            lift.supersetGroupId == currentGroupId
        ) {
            current += lift
        } else {
            groups += mutableListOf(lift)
        }
    }
    return groups
}

/**
 * Among visible lift list items, select the one whose top edge is closest to [threshold]
 * (typically the LazyColumn viewport start).
 */
internal fun primaryLiftIdFromScroll(
    visibleItems: List<Pair<String, Int>>,
    liftKeys: Set<String>,
    threshold: Int = 0,
): String? =
    visibleItems
        .filter { (key, _) -> key in liftKeys }
        .minByOrNull { (_, offset) -> kotlin.math.abs(offset - threshold) }
        ?.first

/**
 * Pixel delta to scroll so [toIndex] sits at the viewport top, given that [fromIndex] is
 * currently at [fromItemOffset] relative to the viewport top.
 *
 * Positive = scroll content up (reveal lower items); negative = scroll content down
 * (reveal earlier items). Used instead of [LazyListState.animateScrollToItem] for long
 * upward jumps, which otherwise multi-phase and hitch on intermediate lifts.
 */
internal fun scrollDeltaToItemTopPx(
    fromIndex: Int,
    fromItemOffset: Int,
    toIndex: Int,
    itemHeightPx: (Int) -> Int,
    spacingPx: Int,
): Int {
    if (fromIndex == toIndex) return fromItemOffset
    return if (toIndex > fromIndex) {
        var sum = 0
        for (i in fromIndex until toIndex) {
            sum += itemHeightPx(i) + spacingPx
        }
        sum + fromItemOffset
    } else {
        var sum = 0
        for (i in toIndex until fromIndex) {
            sum += itemHeightPx(i) + spacingPx
        }
        fromItemOffset - sum
    }
}

/**
 * Single continuous animated scroll that places lift [targetIndex] at the top of the list.
 * Prefers pixel [animateScrollBy] (one animation) over [LazyListState.animateScrollToItem]
 * which pauses mid-flight when scrolling up across uncomposed items.
 */
private suspend fun LazyListState.animateScrollToLiftTop(
    targetIndex: Int,
    liftIds: List<String>,
    itemHeightsPx: Map<String, Int>,
    spacingPx: Int,
) {
    if (targetIndex !in liftIds.indices) return

    val liftKeySet = liftIds.toSet()
    val visibleLiftItems =
        layoutInfo.visibleItemsInfo.filter { info ->
            (info.key as? String) in liftKeySet
        }

    // Already on-screen: pure pixel tweak, no composition phases.
    val targetVisible = visibleLiftItems.firstOrNull { it.index == targetIndex }
    if (targetVisible != null) {
        val delta = targetVisible.offset - layoutInfo.viewportStartOffset
        if (delta != 0) {
            animateScrollBy(delta.toFloat())
        }
        return
    }

    val anchor =
        visibleLiftItems.firstOrNull()
            ?: layoutInfo.visibleItemsInfo.firstOrNull()
            ?: run {
                // Nothing laid out yet — fall back to index scroll once.
                animateScrollToItem(targetIndex)
                return
            }

    val fromIndex = anchor.index.coerceIn(0, liftIds.lastIndex)
    val knownHeights = itemHeightsPx.values
    val estimatedHeight =
        if (knownHeights.isNotEmpty()) {
            knownHeights.sum() / knownHeights.size
        } else {
            DEFAULT_ESTIMATED_LIFT_HEIGHT_PX
        }

    fun heightFor(index: Int): Int {
        if (index !in liftIds.indices) return estimatedHeight
        itemHeightsPx[liftIds[index]]?.let { return it }
        layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.let { return it.size }
        return estimatedHeight
    }

    val delta =
        scrollDeltaToItemTopPx(
            fromIndex = fromIndex,
            fromItemOffset = anchor.offset,
            toIndex = targetIndex,
            itemHeightPx = ::heightFor,
            spacingPx = spacingPx,
        )
    if (delta != 0) {
        animateScrollBy(delta.toFloat())
    }

    // Snap correction once the target is composed (estimate error from unmeasured heights).
    val composed = layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }
    if (composed != null) {
        val correction = composed.offset - layoutInfo.viewportStartOffset
        if (kotlin.math.abs(correction) > 1) {
            animateScrollBy(correction.toFloat())
        }
    } else {
        // Still not composed (very large jump / cold heights) — index API as last resort.
        animateScrollToItem(targetIndex)
    }
}

/**
 * Bottom content-padding so max scroll places the last lift's top at the viewport top:
 * `lastLift + spacing + discard + padding == viewport` (padding is not a list item, so only
 * one inter-item gap after the last lift).
 */
internal fun bottomPinSpacerHeightPx(
    viewportHeightPx: Int,
    lastLiftHeightPx: Int,
    discardHeightPx: Int,
    listSpacingPx: Int,
): Int {
    if (viewportHeightPx <= 0 || lastLiftHeightPx <= 0) return 0
    return (viewportHeightPx - lastLiftHeightPx - discardHeightPx - listSpacingPx)
        .coerceAtLeast(0)
}

private const val DEFAULT_ESTIMATED_LIFT_HEIGHT_PX = 420

/**
 * Live session adapter over [StratumWorkoutLogLiftPanel]: local draft fields until checkbox
 * commits a log, then rest timer starts.
 */
@Composable
private fun LiveLiftSection(
    lift: RenderedLiftUi,
    onLogSet: (slotKey: String, weightAmountLb: Double, reps: Int, rir: Int?) -> Unit,
    onUnlogSet: (String) -> Unit,
    onAddSet: () -> Unit,
    onDeleteSet: (slotKey: String) -> Unit,
    onStartRestTimer: (Int) -> Unit,
    onShowLiftActions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Snapshot-state maps so edits recompose without mutating plain maps during composition.
    val weightDrafts = remember { androidx.compose.runtime.mutableStateMapOf<String, String>() }
    val repsDrafts = remember { androidx.compose.runtime.mutableStateMapOf<String, String>() }
    val rirDrafts = remember { androidx.compose.runtime.mutableStateMapOf<String, Int>() }

    // Seed incomplete drafts once; overwrite from logged values whenever a set completes.
    LaunchedEffect(lift.sets) {
        for (set in lift.sets) {
            if (set.isComplete) {
                weightDrafts[set.slotKey] = set.loggedWeightLabel.orEmpty()
                repsDrafts[set.slotKey] = set.loggedRepsLabel.orEmpty()
                rirDrafts[set.slotKey] = set.loggedRir ?: set.rirGoal
            } else {
                if (set.slotKey !in weightDrafts) {
                    weightDrafts[set.slotKey] = set.loggedWeightLabel.orEmpty()
                }
                if (set.slotKey !in repsDrafts) {
                    repsDrafts[set.slotKey] = set.loggedRepsLabel.orEmpty()
                }
                if (set.slotKey !in rirDrafts) {
                    rirDrafts[set.slotKey] = set.loggedRir ?: set.rirGoal
                }
            }
        }
    }

    val logLift =
        WorkoutLogLiftUi(
            key = lift.workoutLiftId,
            liftName = lift.liftName,
            showWeightColumn = lift.showWeightColumn,
            weightHeaderLabel = lift.weightHeaderLabel,
            supersetGroupId = lift.supersetGroupId,
            sets =
                lift.sets.map { set ->
                    WorkoutLogSetUi(
                        setKey = set.slotKey,
                        positionLabel = set.positionLabel,
                        setTypeLabel = set.setTypeLabel,
                        previousLabel = set.previousWeightLabel ?: "—",
                        weightText =
                            if (set.isComplete) {
                                set.loggedWeightLabel.orEmpty()
                            } else {
                                weightDrafts[set.slotKey].orEmpty()
                            },
                        weightPlaceholder = set.recommendedWeightLabel,
                        repsText =
                            if (set.isComplete) {
                                set.loggedRepsLabel.orEmpty()
                            } else {
                                repsDrafts[set.slotKey].orEmpty()
                            },
                        repsPlaceholder = set.recommendedRepsLabel,
                        rir =
                            if (set.isComplete) {
                                set.loggedRir ?: set.rirGoal
                            } else {
                                rirDrafts[set.slotKey] ?: set.rirGoal
                            },
                        isComplete = set.isComplete,
                        fieldsEnabled = !set.isComplete,
                        showWeightInput = set.showWeightInput,
                        showCheckbox = true,
                        assistMode = set.assistMode,
                        recommendedWeightAmount = set.recommendedWeightAmount,
                        recommendedReps = set.recommendedReps,
                        repMin = set.repMin,
                    )
                },
        )

    val addSetCd = stringResource(R.string.workout_screen_add_set)
    val deleteSetCd = stringResource(R.string.workout_screen_delete_set)
    val canDeleteSets = lift.sets.size > 1

    StratumWorkoutLogLiftPanel(
        lift = logLift,
        modifier = modifier,
        headerTrailing = {
            StratumIconButton(
                onClick = onShowLiftActions,
                containerColor = Color.Transparent,
            ) {
                Icon(
                    painter = painterResource(CoreUiR.drawable.ic_more_vert),
                    contentDescription = "Lift Options",
                    tint = StratumThemeAccessor.colors.textMuted,
                )
            }
        },
        onAddSet = onAddSet,
        addSetContentDescription = addSetCd,
        setRowActions = { setUi ->
            val source = lift.sets.first { it.slotKey == setUi.setKey }
            WorkoutLogSetRowActions(
                onWeightChange = { weightDrafts[setUi.setKey] = it },
                onRepsChange = { repsDrafts[setUi.setKey] = it },
                onRirChange = { rirDrafts[setUi.setKey] = it },
                onCheckedChange = { checked ->
                    if (checked) {
                        val weightText = weightDrafts[setUi.setKey].orEmpty()
                        val repsText = repsDrafts[setUi.setKey].orEmpty()
                        val rir = rirDrafts[setUi.setKey] ?: source.rirGoal
                        val (finalWeight, finalReps) =
                            resolveCheckboxLogValues(
                                enteredWeight = weightText,
                                enteredReps = repsText,
                                showWeightInput = source.showWeightInput,
                                recommendedWeightAmount = source.recommendedWeightAmount,
                                recommendedReps = source.recommendedReps,
                                repMin = source.repMin,
                            )
                        onLogSet(
                            source.slotKey,
                            finalWeight,
                            finalReps,
                            rir,
                        )
                        onStartRestTimer(RestTimerDefaults.DEFAULT_REST_SECONDS)
                    } else {
                        onUnlogSet(source.slotKey)
                    }
                },
                // Last remaining set on a lift: no swipe — remove the lift via ⋮ instead.
                onDelete = if (canDeleteSets) {
                    { onDeleteSet(setUi.setKey) }
                } else {
                    null
                },
                deleteContentDescription = deleteSetCd,
            )
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
                val presets = (30..RestTimerDefaults.MAX_REST_SECONDS step 30).toList()
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
