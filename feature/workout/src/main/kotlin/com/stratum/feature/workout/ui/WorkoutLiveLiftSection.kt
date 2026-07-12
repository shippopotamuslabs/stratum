package com.stratum.feature.workout.ui

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumTextButton
import com.stratum.core.ui.component.workoutlog.LocalSetLogKeyboardHost
import com.stratum.core.ui.component.workoutlog.ProvideSetLogKeyboardHost
import com.stratum.core.ui.component.workoutlog.StratumWorkoutLogLiftPanel
import com.stratum.core.ui.component.workoutlog.WorkoutLogSetRowActions
import com.stratum.core.ui.component.workoutlog.rememberOverlayDockListBinding
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.workout.R

internal val LiftListSpacing = 16.dp
private const val DEFAULT_ESTIMATED_LIFT_HEIGHT_PX = 420
/** Extra gap kept between focused cell bottom and keyboard top when revealing. */
private val KeyboardRevealMargin = 8.dp

/**
 * Scrollable lift panels + discard. Bottom [contentPadding] is sized so max scroll pins the
 * last lift under the navigator — only the shortfall after last-lift + gap + discard.
 *
 * When the set-log keyboard is open, dock height is **added** to contentPadding (pin padding
 * stays stable) so low fields can scroll above the overlay without jumping on dismiss.
 */
@Composable
internal fun WorkoutLiftList(
    lifts: List<RenderedLiftUi>,
    listState: LazyListState,
    liftHeightsPx: MutableMap<String, Int>,
    onLogSet: (slotKey: String, weightAmountLb: Double, reps: Int, rir: Int?) -> Unit,
    onUnlogSet: (String) -> Unit,
    onAddSet: (workoutLiftId: String) -> Unit,
    onDeleteSet: (slotKey: String) -> Unit,
    onShowLiftActions: (workoutLiftId: String) -> Unit,
    onDiscardClick: () -> Unit,
    modifier: Modifier = Modifier,
    failedLogSlotKey: String? = null,
    onFailedLogConsumed: () -> Unit = {},
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
    // Pin padding always uses the full list viewport (keyboard overlays; does not shrink it).
    val bottomPinPaddingPx =
        bottomPinSpacerHeightPx(
            viewportHeightPx = viewportHeightPx,
            lastLiftHeightPx = measuredLastLiftPx,
            discardHeightPx = measuredDiscardPx,
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
                    onLogSet = onLogSet,
                    onUnlogSet = onUnlogSet,
                    onAddSet = { onAddSet(lift.workoutLiftId) },
                    onDeleteSet = onDeleteSet,
                    onShowLiftActions = { onShowLiftActions(lift.workoutLiftId) },
                    failedLogSlotKey = failedLogSlotKey,
                    onFailedLogConsumed = onFailedLogConsumed,
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
@Composable
internal fun LiveLiftSection(
    lift: RenderedLiftUi,
    onLogSet: (slotKey: String, weightAmountLb: Double, reps: Int, rir: Int?) -> Unit,
    onUnlogSet: (String) -> Unit,
    onAddSet: () -> Unit,
    onDeleteSet: (slotKey: String) -> Unit,
    onShowLiftActions: () -> Unit,
    modifier: Modifier = Modifier,
    failedLogSlotKey: String? = null,
    onFailedLogConsumed: () -> Unit = {},
) {
    val draftStore = remember(lift.workoutLiftId) { LiveSetLogDraftStore() }
    val keyboardHost = LocalSetLogKeyboardHost.current
    val focusedSetKey = keyboardHost?.session?.setKey
    val revision by draftStore.revision.collectAsStateWithLifecycle()

    LaunchedEffect(lift.sets, focusedSetKey) {
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
        remember(lift, revision) {
            mergeLiveLogLift(lift, draftStore.snapshot())
        }

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
            val commitLog: (EmptyFieldFallback) -> Unit = { emptyFallback ->
                val commit = draftStore.resolveCommit(source, emptyFallback)
                // Materialize placeholders into draft text before/while VM persists so the row
                // never sits empty+placeholder after confirm.
                draftStore.applyResolvedCommit(source, commit)
                onLogSet(commit.slotKey, commit.weightAmountLb, commit.reps, commit.rir)
            }
            WorkoutLogSetRowActions(
                onWeightChange = { draftStore.setWeight(setUi.setKey, it) },
                onRepsChange = { draftStore.setReps(setUi.setKey, it) },
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
            )
        },
    )
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
