// STR-142 allowlist: rest-timer bar gap (6.dp) pinned to bar width.
@file:Suppress("ForbiddenRawSpacingLiteralInUi")
package com.stratum.feature.workout.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumSpacing
import kotlinx.coroutines.Job
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.EquipmentSwapSheet
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumDetailScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.StratumTopBarTextButton
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.workout.R
import com.stratum.feature.workout.AfterSetLoggedDecision
import com.stratum.feature.workout.decideAfterSetLogged

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
    /** [weightAmountDisplay] is the UI magnitude; the ViewModel converts to storage Lb. */
    val onLogSet: (slotKey: String, weightAmountDisplay: Double, reps: Int, rir: Int?, partialReps: Int?) -> Unit,
    val onUnlogSet: (String) -> Unit,
    val onSwapLiftClick: (workoutId: String, workoutLiftId: String?, liftId: String?, liftName: String?) -> Unit,
    /**
     * Opens the shared-equipment increments editor for the equipment a lift uses.
     * No-op default until wired.
     */
    val onEditEquipmentClick: (EquipmentEditRequest) -> Unit = {},
    /**
     * Live "Adjust Equipment" pill tap: emits the tapped card's workoutLiftId. The route
     * resolves the seeded editor request (session read + inference) before navigating,
     * so the screen stays a pure function of state with no session reads.
     */
    val onAdjustEquipmentTap: (workoutLiftId: String) -> Unit = {},
    /**
     * Quiets the "Adjust Equipment" chip for one lift card ([RenderedLiftUi.workoutLiftId]).
     * The screen calls it only from the chip's X affordance.
     */
    val onDismissEquipmentAdjust: (String) -> Unit = {},
    /** Equipment subheader tap: opens the swap sheet for the card ([RenderedLiftUi.workoutLiftId]). */
    val onEquipmentSwapClick: (String) -> Unit = {},
    val onLinkSupersetWithPrevious: (String) -> Unit,
    val onLinkSupersetWithNext: (String) -> Unit,
    val onRemoveFromSuperset: (String) -> Unit,
    val onSetUseRestTimerBetweenLifts: (String, Boolean) -> Unit,
    val onRemoveLift: (String) -> Unit,
    val onReorderLiftsClick: () -> Unit,
    val onAddSet: (workoutLiftId: String) -> Unit = {},
    val onDeleteSet: (slotKey: String) -> Unit = {},
    val onAddLift: () -> Unit = {},
    /** Batch rest overrides for Configure Rest Times (slotKey → seconds). */
    val onSetRestSecondsForSlots: (Map<String, Int>) -> Unit = {},
    /** Change a set's type (Standard / Myo / Drop). Minis are blocked in the ViewModel. */
    val onSetSetKind: (slotKey: String, kind: WorkoutSetKind) -> Unit = { _, _ -> },
    val liftNotes: WorkoutLiftNotesActions = WorkoutLiftNotesActions(),
)

@Composable
fun WorkoutScreen(
    state: WorkoutUiState.Loaded,
    restTimer: RestTimerChrome,
    actions: WorkoutScreenActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
    /** Post-lifecycle [WorkoutUiEvent.SetLogged]; cleared via [onSetLoggedConsumed]. */
    pendingSetLogged: WorkoutUiEvent.SetLogged? = null,
    onSetLoggedConsumed: () -> Unit = {},
    /** Persist failed after optimistic draft materialise — reseed that slot. */
    failedLogSlotKey: String? = null,
    onFailedLogConsumed: () -> Unit = {},
    equipmentSwapHost: EquipmentSwapHost = EquipmentSwapHost(),
) {
    var showRestSheet by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var liftActionsLiftId by remember { mutableStateOf<String?>(null) }
    var liftSheetMode by remember { mutableStateOf<LiftOptionsSheetMode>(LiftOptionsSheetMode.Options) }
    var editingNotes by remember { mutableStateOf<EditingNotesTarget?>(null) }
    var pastSessionNotesLiftId by remember { mutableStateOf<String?>(null) }
    var configureRestLiftId by remember { mutableStateOf<String?>(null) }
    var showRestConfigDiscardDialog by remember { mutableStateOf(false) }
    var supersetSheetLiftId by remember { mutableStateOf<String?>(null) }
    var showCompleteSheet by remember { mutableStateOf(false) }
    var completeSheetPhase by remember { mutableStateOf(CompleteSheetPhase.Incomplete) }
    /** One auto-prompt per full-completion transition after a set log (#9). */
    var autoShownCompleteSheet by remember(state.workoutId) { mutableStateOf(false) }

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

    // Allow the finish sheet to auto-show again if the user unlogs a set; dismiss stale AllSetsLogged.
    LaunchedEffect(state.hasIncompleteLifts) {
        if (state.hasIncompleteLifts) {
            autoShownCompleteSheet = false
            if (completeSheetPhase == CompleteSheetPhase.AllSetsLogged) {
                showCompleteSheet = false
            }
        }
    }

    val scrollToLift: (String) -> Unit = { workoutLiftId ->
        val index = state.lifts.indexOfFirst { it.workoutLiftId == workoutLiftId }
        if (index >= 0) {
            selectedLiftId = workoutLiftId
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
        }
    }

    // Log only — rest timer, autoscroll, and complete sheet run after [pendingSetLogged]
    // is set by the route once the ViewModel confirms persistence (avoids optimistic chrome).
    val handleLogSet: (String, Double, Int, Int?, Int?) -> Unit =
        { slotKey, weightAmountDisplay, reps, rir, partialReps ->
            actions.onLogSet(slotKey, weightAmountDisplay, reps, rir, partialReps)
        }

    // Post-success side effects: rest (unless session done), focus next lift, complete sheet.
    // Clear pending only after work finishes so restarting this effect does not cancel mid-run.
    LaunchedEffect(pendingSetLogged) {
        val loggedEvent = pendingSetLogged ?: return@LaunchedEffect
        val slotKey = loggedEvent.slotKey
        // Rest duration is fully resolved in the session runtime (post-lifecycle).
        loggedEvent.restSeconds?.let { restTimer.onStart(it) }
        val decision =
            decideAfterSetLogged(
                lifts = state.lifts,
                slotKey = slotKey,
                alreadyAutoShownCompleteSheet = autoShownCompleteSheet,
            )
        // Keyboard Check dismisses the pad before SetLogged arrives. Give the list one frame
        // to apply collapsed dock padding so animateScrollToLiftTop measures the final layout
        // (collapse restore is suppressed on complete — see skipNextCollapseScrollRestore).
        if (decision is AfterSetLoggedDecision.FocusLift) {
            awaitFrame()
        }
        applyAfterSetLoggedDecision(
            decision = decision,
            onShowCompleteSheet = {
                autoShownCompleteSheet = true
                completeSheetPhase = CompleteSheetPhase.AllSetsLogged
                showCompleteSheet = true
            },
            onFocusLift = scrollToLift,
        )
        onSetLoggedConsumed()
    }

    // Pick the lift whose top edge is closest to the pin line (not "first fully pinned").
    // The pin line is beforeContentPadding (STR-193): item offsets are physical, so under
    // the navigator overlay the line sits one top-content-padding below zero.
    val primaryVisibleLiftId by remember(state.lifts) {
        derivedStateOf {
            val liftKeys = state.lifts.map { it.workoutLiftId }.toSet()
            val threshold = listState.layoutInfo.beforeContentPadding
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
    val underlinedLiftIds =
        remember(selectedLiftId, state.lifts) {
            underlinedLiftIdsForPrimary(selectedLiftId, state.lifts)
        }

    val elapsedTimeStr = rememberElapsedWorkoutTime(state.sessionStartedAtEpochMilli)

    BackHandler(enabled = editingNotes != null) {
        editingNotes = null
    }
    editingNotes?.let { target ->
        ManageNotesScreen(
            target = target,
            liftNotes = actions.liftNotes,
            onClose = { editingNotes = null },
            modifier = modifier,
        )
        return
    }

    // Glass (§12.8, STR-157/STR-193) DIRECTED GEOMETRY: under the route's glass host
    // the top bar overlays, the lift list fills the full viewport with the whole top
    // clearance as content padding, and the navigator row overlays the list — so panels
    // glaze beneath the glass AND beneath the navigator. Never haze behind set-logging
    // text: lift panels are opaque StratumEdgeToEdgeCard (surfaceRaised) containers, and
    // auto-scroll pins panels BELOW the navigator + bar (pin target = beforeContentPadding),
    // so editable set fields only cross the top edge transiently mid-scroll; the keyboard
    // dock is untouched. Solid hosts (Neo, previews) keep the pre-STR-193 in-flow
    // Column geometry exactly: the Box overlay makes the list viewport taller even
    // when glass is inactive, revealing the Add Lift footer, so solid must not share
    // the overlay path even though chrome.top is 0 there.
    val liftListActions =
        WorkoutLiftListActions(
            onLogSet = handleLogSet,
            onUnlogSet = actions.onUnlogSet,
            onAddSet = actions.onAddSet,
            onDeleteSet = actions.onDeleteSet,
            onSetSetKind = actions.onSetSetKind,
            onShowLiftActions = {
                liftSheetMode = LiftOptionsSheetMode.Options
                liftActionsLiftId = it
            },
            onOpenManageNotes = { workoutLiftId, tab ->
                liftSheetMode = LiftOptionsSheetMode.ManageNotes(tab)
                liftActionsLiftId = workoutLiftId
            },
            // Pill tap only emits the card id; the route resolves the seeded
            // request before navigating. Opening the editor leaves the chip
            // visible: only the X affordance or a real tier save quiets it.
            onAdjustEquipment = actions.onAdjustEquipmentTap,
            onDismissEquipmentAdjust = actions.onDismissEquipmentAdjust,
            onEquipmentSwapClick = actions.onEquipmentSwapClick,
            onAddLift = actions.onAddLift,
            onDiscardClick = { showDiscardDialog = true },
        )
    StratumDetailScaffold(
        modifier = modifier,
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
                            when (
                                val phase =
                                    completePhaseForTopBarClick(
                                        hasIncompleteLifts = state.hasIncompleteLifts,
                                        hasDirtyEdits = state.hasDirtyEdits,
                                    )
                            ) {
                                null -> actions.onCompleteWorkout(false)
                                else -> {
                                    completeSheetPhase = phase
                                    showCompleteSheet = true
                                }
                            }
                        }
                    )
                }
            )
        },
    ) { chrome ->
        if (!chrome.active) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = chrome.top)
                    .padding(vertical = StratumSpacing.s12),
                verticalArrangement = Arrangement.spacedBy(StratumSpacing.s12)
            ) {
                LiftNavigatorRow(
                    lifts = state.lifts,
                    primaryLiftId = selectedLiftId,
                    underlinedLiftIds = underlinedLiftIds,
                    onLiftClick = scrollToLift,
                    onAddLift = actions.onAddLift,
                    modifier = Modifier.fillMaxWidth(),
                )

                WorkoutLiftList(
                    lifts = state.lifts,
                    listState = listState,
                    liftHeightsPx = liftHeightsPx,
                    actions = liftListActions,
                    failedLogSlotKey = failedLogSlotKey,
                    onFailedLogConsumed = onFailedLogConsumed,
                    displayUnitEpoch = state.displayUnitEpoch,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
        } else {
            // Rest geometry, glass only: first panel top = chrome.top + s12 +
            // navigator height + s12 (the old column's top pad, navigator, and
            // inter-element gap, in that order).
            val navigatorTopPadding = chrome.top + StratumSpacing.s12
            val listTopPadding =
                navigatorTopPadding + LiftNavigatorRowHeight + StratumSpacing.s12
            Box(modifier = Modifier.fillMaxSize()) {
                WorkoutLiftList(
                    lifts = state.lifts,
                    listState = listState,
                    liftHeightsPx = liftHeightsPx,
                    actions = liftListActions,
                    failedLogSlotKey = failedLogSlotKey,
                    onFailedLogConsumed = onFailedLogConsumed,
                    displayUnitEpoch = state.displayUnitEpoch,
                    topContentPadding = listTopPadding,
                    modifier = Modifier.fillMaxSize(),
                )

                // Transparent row over scrolling panels (glaze-under-navigator is
                // intended: tabs are opaque surfaceSunken/surfaceRaised containers, never
                // raw text). Declared after the list with zIndex so it stays tappable above it.
                LiftNavigatorRow(
                    lifts = state.lifts,
                    primaryLiftId = selectedLiftId,
                    underlinedLiftIds = underlinedLiftIds,
                    onLiftClick = scrollToLift,
                    onAddLift = actions.onAddLift,
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .padding(top = navigatorTopPadding)
                            .zIndex(1f),
                )
            }
        }
    }

    WorkoutSessionOverlays(
        state = state,
        restTimer = restTimer,
        actions = actions,
        sheets =
            WorkoutOverlaySheetState(
                showRestSheet = showRestSheet,
                onShowRestSheetChange = { showRestSheet = it },
                showDiscardDialog = showDiscardDialog,
                onShowDiscardDialogChange = { showDiscardDialog = it },
                liftActionsLiftId = liftActionsLiftId,
                onLiftActionsLiftIdChange = {
                    liftActionsLiftId = it
                    if (it == null) liftSheetMode = LiftOptionsSheetMode.Options
                },
                notes =
                    LiftNotesSheetHostState(
                        liftSheetMode = liftSheetMode,
                        onLiftSheetModeChange = { liftSheetMode = it },
                        pastSessionNotesLiftId = pastSessionNotesLiftId,
                        onPastSessionNotesLiftIdChange = { pastSessionNotesLiftId = it },
                        onEditNotes = { editingNotes = it },
                    ),
                configureRestLiftId = configureRestLiftId,
                onConfigureRestLiftIdChange = { configureRestLiftId = it },
                showRestConfigDiscardDialog = showRestConfigDiscardDialog,
                onShowRestConfigDiscardDialogChange = { showRestConfigDiscardDialog = it },
                supersetSheetLiftId = supersetSheetLiftId,
                onSupersetSheetLiftIdChange = { supersetSheetLiftId = it },
                showCompleteSheet = showCompleteSheet,
                completeSheetPhase = completeSheetPhase,
                onShowCompleteSheetChange = { showCompleteSheet = it },
                onCompleteSheetPhaseChange = { completeSheetPhase = it },
            ),
    )

    equipmentSwapHost.sheet?.let { swap ->
        EquipmentSwapSheet(
            state = swap.sheet,
            onQueryChange = equipmentSwapHost.onQuery,
            onSelect = { equipmentSwapHost.onSelect(it.id) },
            onCreateNew = equipmentSwapHost.onCreateNew,
            onDismissRequest = equipmentSwapHost.onDismiss,
        )
    }

    equipmentSwapHost.rewritePrompt?.let { prompt ->
        EquipmentRewritePromptDialog(
            prompt = prompt,
            onRewrite = equipmentSwapHost.onRewrite,
            onKeepOld = equipmentSwapHost.onKeepOld,
            onDismiss = equipmentSwapHost.onDismissPrompt,
        )
    }
}

/** Applies a pure [AfterSetLoggedDecision] to UI callbacks (kept free of branching in Compose). */
internal fun applyAfterSetLoggedDecision(
    decision: AfterSetLoggedDecision,
    onShowCompleteSheet: () -> Unit,
    onFocusLift: (String) -> Unit,
) {
    when (decision) {
        AfterSetLoggedDecision.None -> Unit
        AfterSetLoggedDecision.ShowCompleteWorkoutSheet -> onShowCompleteSheet()
        is AfterSetLoggedDecision.FocusLift -> onFocusLift(decision.workoutLiftId)
    }
}

/**
 * Top-bar Complete: null means finish immediately; otherwise open the matching sheet phase.
 */
internal fun completePhaseForTopBarClick(
    hasIncompleteLifts: Boolean,
    hasDirtyEdits: Boolean,
): CompleteSheetPhase? =
    when {
        !hasIncompleteLifts && !hasDirtyEdits -> null
        hasIncompleteLifts -> CompleteSheetPhase.Incomplete
        else -> CompleteSheetPhase.SaveChanges
    }

/** Continuous underline span for the selected lift's superset / giant set (or itself). */
internal fun underlinedLiftIdsForPrimary(
    primaryLiftId: String?,
    lifts: List<RenderedLiftUi>,
): Set<String> {
    val primary = primaryLiftId ?: return emptySet()
    val primaryLift = lifts.firstOrNull { it.workoutLiftId == primary }
    val groupId = primaryLift?.supersetGroupId
    return if (groupId.isNullOrBlank()) {
        setOf(primary)
    } else {
        lifts.filter { it.supersetGroupId == groupId }.map { it.workoutLiftId }.toSet()
    }
}

@Composable
private fun rememberElapsedWorkoutTime(sessionStartedAtEpochMilli: Long): String {
    var elapsedTimeStr by remember(sessionStartedAtEpochMilli) { mutableStateOf("00:00") }
    LaunchedEffect(sessionStartedAtEpochMilli) {
        while (true) {
            val now = System.currentTimeMillis()
            val diffMs = now - sessionStartedAtEpochMilli
            val diffSec = (diffMs / 1000).coerceAtLeast(0)
            val hrs = diffSec / 3600
            val mins = (diffSec % 3600) / 60
            val secs = diffSec % 60
            elapsedTimeStr =
                if (hrs > 0) {
                    String.format(java.util.Locale.US, "%d:%02d:%02d", hrs, mins, secs)
                } else {
                    String.format(java.util.Locale.US, "%02d:%02d", mins, secs)
                }
            kotlinx.coroutines.delay(1000L)
        }
    }
    return elapsedTimeStr
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
        // §12.5: the rest-timer indicator is a mid-workout control — its
        // target floors at 48dp tall (the row's content is only 24dp).
        modifier = modifier
            .sizeIn(minHeight = 48.dp)
            .clickable(onClick = onClick)
            .padding(end = StratumSpacing.s8)
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
