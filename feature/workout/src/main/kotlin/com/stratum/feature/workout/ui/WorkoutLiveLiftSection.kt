package com.stratum.feature.workout.ui

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.StratumAssistChip
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumConfirmDialog
import com.stratum.core.ui.component.StratumEquipmentSubheader
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumOutlinedButton
import com.stratum.core.ui.component.workoutlog.LocalSetLogKeyboardHost
import com.stratum.core.ui.component.workoutlog.ProvideSetLogKeyboardHost
import com.stratum.core.ui.component.workoutlog.StratumWorkoutLogLiftPanel
import com.stratum.core.ui.component.workoutlog.WorkoutLogSetRowActions
import com.stratum.core.ui.component.workoutlog.rememberOverlayDockListBinding
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.workout.R

internal val LiftListSpacing = 16.dp
/** Matches lift panel header inset so footer CTAs align with log chrome. */
private val FooterHorizontalPadding = 16.dp
/** Vertical gap between Add Lift and Discard inside the session footer. */
private val FooterActionSpacing = 12.dp
/** Breathing room below Discard so it never sits flush against the bottom nav bar. */
private val FooterBottomPadding = 16.dp
private const val DEFAULT_ESTIMATED_LIFT_HEIGHT_PX = 420
/** LazyList key for the trailing Add Lift + Discard chrome (not a lift panel). */
internal const val WORKOUT_SESSION_FOOTER_KEY = "session-footer"
/** Extra gap kept between focused cell bottom and keyboard top when revealing. */
private val KeyboardRevealMargin = 8.dp

/** Set-log callbacks for [WorkoutLiftList] — bag avoids Detekt LongParameterList. */
internal data class WorkoutLiftListActions(
    val onLogSet: (slotKey: String, weightAmountDisplay: Double, reps: Int, rir: Int?, partialReps: Int?) -> Unit,
    val onUnlogSet: (String) -> Unit,
    val onAddSet: (workoutLiftId: String) -> Unit,
    val onDeleteSet: (slotKey: String) -> Unit,
    val onSetSetKind: (slotKey: String, kind: WorkoutSetKind) -> Unit = { _, _ -> },
    val onShowLiftActions: (workoutLiftId: String) -> Unit,
    val onOpenManageNotes: (workoutLiftId: String, tab: LiftNoteTab) -> Unit = { _, _ -> },
    val onAdjustEquipment: (workoutLiftId: String) -> Unit = {},
    val onDismissEquipmentAdjust: (workoutLiftId: String) -> Unit = {},
    val onEquipmentSwapClick: (workoutLiftId: String) -> Unit = {},
    val onAddLift: () -> Unit,
    val onDiscardClick: () -> Unit,
)

/**
 * Scrollable lift panels + session footer (Add Lift, Discard). Bottom [contentPadding] is sized
 * so max scroll pins the last lift under the navigator — only the shortfall after last-lift +
 * gap + footer. Footer is a **single** LazyList item so pin math stays one inter-item gap
 * after the last lift (keyboard dock padding is layered on top and stays independent).
 * The footer item carries its own trailing bottom padding below Discard — part of the
 * measured footer height, so pin math stays exact.
 *
 * When the set-log keyboard is open, dock height is **added** to contentPadding (pin padding
 * stays stable) so low fields can scroll above the overlay without jumping on dismiss.
 */
@Composable
internal fun WorkoutLiftList(
    lifts: List<RenderedLiftUi>,
    listState: LazyListState,
    liftHeightsPx: MutableMap<String, Int>,
    actions: WorkoutLiftListActions,
    modifier: Modifier = Modifier,
    failedLogSlotKey: String? = null,
    onFailedLogConsumed: () -> Unit = {},
    /** Bumps when Settings weight unit flips — clears per-lift drafts. */
    displayUnitEpoch: Int = 0,
) {
    var footerHeightPx by remember { mutableIntStateOf(0) }
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
    val measuredFooterPx =
        listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == WORKOUT_SESSION_FOOTER_KEY }
            ?.size
            ?: footerHeightPx
    // Pin padding always uses the full list viewport (keyboard overlays; does not shrink it).
    val bottomPinPaddingPx =
        bottomPinSpacerHeightPx(
            viewportHeightPx = viewportHeightPx,
            lastLiftHeightPx = measuredLastLiftPx,
            footerHeightPx = measuredFooterPx,
            listSpacingPx = listSpacingPx,
        )

    ProvideSetLogKeyboardHost(modifier = modifier) { listModifier ->
        val keyboardHost = LocalSetLogKeyboardHost.current
        val dockHeightDp =
            rememberOverlayDockListBinding(
                listState = listState,
                dock = keyboardHost?.overlay,
                revealMargin = KeyboardRevealMargin,
            )

        // Stable pin + keyboard dock as extra scroll room (overlay covers the dock zone).
        val bottomContentPadding = with(density) { bottomPinPaddingPx.toDp() } + dockHeightDp

        LazyColumn(
            state = listState,
            modifier = listModifier,
            contentPadding = PaddingValues(bottom = bottomContentPadding),
            verticalArrangement = Arrangement.spacedBy(LiftListSpacing),
        ) {
            itemsIndexed(lifts, key = { _, lift -> lift.workoutLiftId }) { _, lift ->
                LiveLiftSection(
                    lift = lift,
                    allLifts = lifts,
                    onLogSet = actions.onLogSet,
                    onUnlogSet = actions.onUnlogSet,
                    onAddSet = { actions.onAddSet(lift.workoutLiftId) },
                    onDeleteSet = actions.onDeleteSet,
                    onSetSetKind = actions.onSetSetKind,
                    onShowLiftActions = { actions.onShowLiftActions(lift.workoutLiftId) },
                    onOpenManageNotes = { tab ->
                        actions.onOpenManageNotes(lift.workoutLiftId, tab)
                    },
                    onAdjustEquipmentClick = { actions.onAdjustEquipment(lift.workoutLiftId) },
                    onDismissAdjustEquipmentClick = {
                        actions.onDismissEquipmentAdjust(lift.workoutLiftId)
                    },
                    onEquipmentSwapClick = { actions.onEquipmentSwapClick(lift.workoutLiftId) },
                    failedLogSlotKey = failedLogSlotKey,
                    onFailedLogConsumed = onFailedLogConsumed,
                    displayUnitEpoch = displayUnitEpoch,
                    modifier =
                        Modifier.onSizeChanged { size ->
                            liftHeightsPx[lift.workoutLiftId] = size.height
                        },
                )
            }

            // Single footer item: one gap after last lift; height feeds bottom pin padding.
            item(key = WORKOUT_SESSION_FOOTER_KEY) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .onSizeChanged { footerHeightPx = it.height }
                            .padding(
                                start = FooterHorizontalPadding,
                                end = FooterHorizontalPadding,
                                bottom = FooterBottomPadding,
                            ),
                    verticalArrangement = Arrangement.spacedBy(FooterActionSpacing),
                ) {
                    StratumButton(
                        text = stringResource(R.string.workout_screen_add_lift),
                        onClick = actions.onAddLift,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    StratumOutlinedButton(
                        text = stringResource(R.string.workout_screen_discard_workout_action),
                        onClick = actions.onDiscardClick,
                        modifier = Modifier.fillMaxWidth(),
                        destructive = true,
                    )
                }
            }
        }
    }
}

/**
 * Live session adapter over [StratumWorkoutLogLiftPanel].
 *
 * Drafts, seed rules, merge, and commit resolution live in [LiveSetLogDraftStore] /
 * pure helpers ([seedLiveSetLogDrafts], [mergeLiveLogLift], [resolveLiveSetLogCommit]).
 * This composable only wires keyboard focus + panel callbacks.
 *
 * Rest timer / autoscroll / complete sheet run after log success
 * (see [pendingSetLoggedSlotKey] on [WorkoutScreen]).
 */
@Suppress("LongParameterList")
@Composable
internal fun LiveLiftSection(
    lift: RenderedLiftUi,
    onLogSet: (slotKey: String, weightAmountDisplay: Double, reps: Int, rir: Int?, partialReps: Int?) -> Unit,
    onUnlogSet: (String) -> Unit,
    onAddSet: () -> Unit,
    onDeleteSet: (slotKey: String) -> Unit,
    onSetSetKind: (slotKey: String, kind: WorkoutSetKind) -> Unit = { _, _ -> },
    onShowLiftActions: () -> Unit,
    onOpenManageNotes: (LiftNoteTab) -> Unit = {},
    onAdjustEquipmentClick: () -> Unit = {},
    onDismissAdjustEquipmentClick: () -> Unit = {},
    onEquipmentSwapClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    allLifts: List<RenderedLiftUi> = emptyList(),
    failedLogSlotKey: String? = null,
    onFailedLogConsumed: () -> Unit = {},
    displayUnitEpoch: Int = 0,
) {
    val draftStore = remember(lift.workoutLiftId) { LiveSetLogDraftStore() }
    val keyboardHost = LocalSetLogKeyboardHost.current
    val focusedSetKey = keyboardHost?.session?.setKey
    val revision by draftStore.revision.collectAsStateWithLifecycle()

    var setKindPickerSlotKey by remember { mutableStateOf<String?>(null) }
    var pendingMyoDemote by remember {
        mutableStateOf<Pair<String, WorkoutSetKind>?>(null)
    }

    LaunchedEffect(displayUnitEpoch) {
        if (displayUnitEpoch > 0) {
            draftStore.clearForDisplayUnitChange()
        }
    }

    LaunchedEffect(lift.sets, focusedSetKey, displayUnitEpoch) {
        draftStore.syncFromRendered(lift.sets, focusedSetKey)
    }

    // Persist failed after optimistic materialise — restore durable drafts for that slot.
    LaunchedEffect(failedLogSlotKey) {
        val key = failedLogSlotKey ?: return@LaunchedEffect
        val source = lift.sets.firstOrNull { it.slotKey == key } ?: run {
            onFailedLogConsumed()
            return@LaunchedEffect
        }
        draftStore.forceReseedSlot(source)
        onFailedLogConsumed()
    }

    // revision forces re-merge when drafts change; lift.sets when durable state updates.
    val logLift =
        remember(lift, allLifts, revision) {
            mergeLiveLogLift(lift, draftStore.snapshot(), allLifts)
        }

    val addSetCd = stringResource(R.string.workout_screen_add_set)
    val deleteSetCd = stringResource(R.string.workout_screen_delete_set)
    val canDeleteSets = lift.sets.size > 1

    val applySetKind: (String, WorkoutSetKind) -> Unit = { slotKey, kind ->
        val source = lift.sets.firstOrNull { it.slotKey == slotKey }
        // Confirm whenever structure would be discarded — including Floor ↔ Match
        // (minis/backoffs strip), not only demote to Standard/Drop.
        if (source != null && source.hasGeneratedMyoMinis && kind != source.setKind) {
            pendingMyoDemote = slotKey to kind
        } else {
            onSetSetKind(slotKey, kind)
        }
    }

    StratumWorkoutLogLiftPanel(
        lift = logLift,
        modifier = modifier,
        headerUnderTitle = {
            Column {
                StratumEquipmentSubheader(
                    equipmentName = lift.sessionEquipmentName,
                    onSwapClick = onEquipmentSwapClick,
                )
                if (lift.showAdjustEquipmentChip) {
                    StratumAssistChip(
                        label = stringResource(R.string.workout_live_adjust_equipment),
                        onClick = onAdjustEquipmentClick,
                        onDismissClick = onDismissAdjustEquipmentClick,
                        dismissContentDescription =
                            stringResource(R.string.workout_live_adjust_equipment_dismiss_cd),
                        modifier = Modifier.padding(horizontal = StratumSpacing.s16),
                    )
                }
            }
        },
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
        onWorkoutPinnedNoteClick = { onOpenManageNotes(LiftNoteTab.Workout) },
        onLiftPinnedNoteClick = { onOpenManageNotes(LiftNoteTab.Lift) },
        onAddSet = onAddSet,
        addSetContentDescription = addSetCd,
        setRowActions = { setUi ->
            val source = lift.sets.first { it.slotKey == setUi.setKey }
            val commitLog: (EmptyFieldFallback) -> Unit = { emptyFallback ->
                val commit = draftStore.resolveCommit(source, emptyFallback)
                // Materialize placeholders into draft text before/while VM persists so the row
                // never sits empty+placeholder after confirm.
                draftStore.applyResolvedCommit(source, commit)
                onLogSet(
                    commit.slotKey,
                    commit.weightAmountDisplay,
                    commit.reps,
                    commit.rir,
                    commit.partialReps,
                )
            }
            WorkoutLogSetRowActions(
                onWeightChange = { draftStore.setWeight(setUi.setKey, it) },
                onRepsChange = { draftStore.setReps(setUi.setKey, it) },
                onPartialRepsChange = { draftStore.setPartialReps(setUi.setKey, it) },
                onRirChange = { rir ->
                    draftStore.setRir(setUi.setKey, rir)
                    // RIR-only: keep previous weight/reps if fields were cleared mid-edit.
                    if (source.isComplete) {
                        commitLog(EmptyFieldFallback.KeepPreviousLogged)
                    }
                },
                onCheckedChange = { checked ->
                    if (checked) {
                        // Explicit complete: empty fields accept visible placeholders.
                        commitLog(EmptyFieldFallback.AcceptPlaceholders)
                    } else {
                        onUnlogSet(source.slotKey)
                    }
                },
                onDelete = if (canDeleteSets) {
                    { onDeleteSet(setUi.setKey) }
                } else {
                    null
                },
                deleteContentDescription = deleteSetCd,
                onSetTypeClick =
                    if (source.canChangeSetType) {
                        { setKindPickerSlotKey = source.slotKey }
                    } else {
                        null
                    },
            )
        },
    )

    val pickerSource = lift.sets.firstOrNull { it.slotKey == setKindPickerSlotKey }
    if (pickerSource != null) {
        WorkoutSetTypePickerSheet(
            selected = pickerSource.setKind,
            allowedKinds = pickerSource.allowedSetKinds.ifEmpty {
                listOf(pickerSource.setKind)
            },
            onSelect = { kind ->
                setKindPickerSlotKey = null
                if (kind != pickerSource.setKind) {
                    applySetKind(pickerSource.slotKey, kind)
                }
            },
            onDismiss = { setKindPickerSlotKey = null },
        )
    }

    pendingMyoDemote?.let { (slotKey, kind) ->
        StratumConfirmDialog(
            title = stringResource(R.string.workout_set_type_change_myo_title),
            body = stringResource(R.string.workout_set_type_change_myo_body),
            confirmLabel = stringResource(R.string.workout_set_type_change_myo_confirm),
            dismissLabel = stringResource(R.string.workout_set_type_change_myo_cancel),
            onConfirm = {
                pendingMyoDemote = null
                onSetSetKind(slotKey, kind)
            },
            onDismiss = { pendingMyoDemote = null },
            destructiveConfirm = true,
        )
    }
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
internal suspend fun LazyListState.animateScrollToLiftTop(
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
 * `lastLift + spacing + footer + padding == viewport` (padding is not a list item, so only
 * one inter-item gap after the last lift). [footerHeightPx] is the full session footer
 * (Add Lift + Discard) measured as a single LazyList item.
 */
internal fun bottomPinSpacerHeightPx(
    viewportHeightPx: Int,
    lastLiftHeightPx: Int,
    footerHeightPx: Int,
    listSpacingPx: Int,
): Int {
    if (viewportHeightPx <= 0 || lastLiftHeightPx <= 0) return 0
    return (viewportHeightPx - lastLiftHeightPx - footerHeightPx - listSpacingPx)
        .coerceAtLeast(0)
}
