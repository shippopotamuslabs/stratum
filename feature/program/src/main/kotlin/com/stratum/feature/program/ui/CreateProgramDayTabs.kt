package com.stratum.feature.program.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.program.CreateProgramDayUi
import com.stratum.feature.program.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

internal const val DAY_TAB_REORDER_THRESHOLD_DP = 72f
private val DayTabBarHeight = 48.dp
private val DayTabIndicatorHeight = 2.dp
/** Viewport edge band that triggers auto-scroll while reordering. */
private val DayTabAutoScrollEdge = 56.dp
/** Max scroll per frame (~60fps) when the finger is fully in the edge band. */
private val DayTabAutoScrollMaxSpeed = 14.dp
private const val DAY_TAB_AUTO_SCROLL_FRAME_MS = 16L

@Composable
internal fun DayTabsRow(
    days: List<CreateProgramDayUi>,
    selectedDayId: String?,
    canAddDay: Boolean,
    onSelectDay: (String) -> Unit,
    onAddDay: () -> Unit,
    onReorderDays: (fromIndex: Int, toIndex: Int) -> Unit,
) {
    val scrollState = rememberScrollState()
    var draggedDayId by remember { mutableStateOf<String?>(null) }
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var tabWidthsPx by remember { mutableStateOf(emptyMap<String, Float>()) }
    var viewportWidthPx by remember { mutableFloatStateOf(0f) }
    var pointerXInViewport by remember { mutableFloatStateOf(Float.NaN) }
    var viewportCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val density = LocalDensity.current
    val densityScale = density.density
    val edgeZonePx = with(density) { DayTabAutoScrollEdge.toPx() }
    val maxScrollSpeedPx = with(density) { DayTabAutoScrollMaxSpeed.toPx() }
    val colors = StratumThemeAccessor.colors
    val borderWidth = StratumThemeAccessor.shapes.borderWidth
    val latestDays by rememberUpdatedState(days)
    val latestOnReorderDays by rememberUpdatedState(onReorderDays)

    fun applyDragDelta(delta: Float) {
        if (draggedIndex < 0 || draggedDayId == null) return
        dragOffset += delta
        val currentWidth =
            tabWidthsPx[draggedDayId]
                ?: (DAY_TAB_REORDER_THRESHOLD_DP * densityScale)
        val thresholdPx = (currentWidth * 0.55f).coerceAtLeast(40f * densityScale)
        val dayCount = latestDays.size
        while (dragOffset > thresholdPx && draggedIndex < dayCount - 1) {
            latestOnReorderDays(draggedIndex, draggedIndex + 1)
            draggedIndex += 1
            dragOffset -= thresholdPx
        }
        while (dragOffset < -thresholdPx && draggedIndex > 0) {
            latestOnReorderDays(draggedIndex, draggedIndex - 1)
            draggedIndex -= 1
            dragOffset += thresholdPx
        }
    }

    // Edge auto-scroll while a day tab is being reordered.
    val isDragging = draggedDayId != null
    LaunchedEffect(isDragging) {
        if (!isDragging) return@LaunchedEffect
        while (isActive) {
            val x = pointerXInViewport
            val width = viewportWidthPx
            if (!x.isNaN() && width > 0f && edgeZonePx > 0f) {
                val scrollDelta =
                    when {
                        x < edgeZonePx -> {
                            val t = ((edgeZonePx - x) / edgeZonePx).coerceIn(0f, 1f)
                            -maxScrollSpeedPx * t
                        }
                        x > width - edgeZonePx -> {
                            val t = ((x - (width - edgeZonePx)) / edgeZonePx).coerceIn(0f, 1f)
                            maxScrollSpeedPx * t
                        }
                        else -> 0f
                    }
                if (scrollDelta != 0f) {
                    val before = scrollState.value
                    scrollState.scrollBy(scrollDelta)
                    val actual = (scrollState.value - before).toFloat()
                    if (actual != 0f) {
                        // Keep the dragged chip under the finger as content scrolls.
                        applyDragDelta(actual)
                    }
                }
            }
            delay(DAY_TAB_AUTO_SCROLL_FRAME_MS)
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(DayTabBarHeight)
                .onSizeChanged { size -> viewportWidthPx = size.width.toFloat() }
                .onGloballyPositioned { coords -> viewportCoordinates = coords }
                .drawBehind {
                    val y = size.height - borderWidth.toPx() / 2f
                    drawLine(
                        color = colors.borderSubtle,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = borderWidth.toPx(),
                    )
                },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .horizontalScroll(scrollState)
                    .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            days.forEachIndexed { index, day ->
                val selected = day.id == selectedDayId
                val tabDragging = day.id == draggedDayId
                DayTab(
                    name = day.name,
                    selected = selected,
                    isDragging = tabDragging,
                    dragOffsetX = if (tabDragging) dragOffset else 0f,
                    viewportCoordinates = { viewportCoordinates },
                    onSelect = { onSelectDay(day.id) },
                    onSizeChanged = { widthPx ->
                        tabWidthsPx = tabWidthsPx + (day.id to widthPx)
                    },
                    onDragStart = {
                        draggedDayId = day.id
                        draggedIndex = index
                        dragOffset = 0f
                    },
                    onDragEnd = {
                        draggedDayId = null
                        draggedIndex = -1
                        dragOffset = 0f
                        pointerXInViewport = Float.NaN
                    },
                    onHorizontalDrag = { dragAmount, pointerX ->
                        pointerXInViewport = pointerX
                        applyDragDelta(dragAmount)
                    },
                )
            }
            // Hide once the Mon–Sun week is full (multi-week chains deferred).
            if (canAddDay) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .clickable(onClick = onAddDay)
                            .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    StratumText(
                        text = stringResource(R.string.create_program_add_day) + " +",
                        style = StratumThemeAccessor.typography.bodyLarge,
                        muted = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayTab(
    name: String,
    selected: Boolean,
    isDragging: Boolean,
    dragOffsetX: Float,
    viewportCoordinates: () -> LayoutCoordinates?,
    onSelect: () -> Unit,
    onSizeChanged: (Float) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onHorizontalDrag: (dragAmount: Float, pointerXInViewport: Float) -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    val haptic = LocalHapticFeedback.current
    val dragDescription = stringResource(R.string.create_program_drag_day)
    val latestOnDragStart by rememberUpdatedState(onDragStart)
    val latestOnDragEnd by rememberUpdatedState(onDragEnd)
    val latestOnHorizontalDrag by rememberUpdatedState(onHorizontalDrag)
    val latestViewportCoordinates by rememberUpdatedState(viewportCoordinates)
    var handleCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Column(
        modifier =
            Modifier
                .zIndex(if (isDragging) 1f else 0f)
                .graphicsLayer {
                    translationX = dragOffsetX
                    alpha = if (isDragging) 0.92f else 1f
                }
                .fillMaxHeight()
                .defaultMinSize(minWidth = 72.dp)
                .onSizeChanged { size -> onSizeChanged(size.width.toFloat()) }
                .semantics {
                    role = Role.Tab
                    this.selected = selected
                }
                .clickable(onClick = onSelect)
                .drawBehind {
                    if (selected) {
                        val stroke = DayTabIndicatorHeight.toPx()
                        val y = size.height - stroke / 2f
                        drawLine(
                            color = colors.accent,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = stroke,
                        )
                    }
                }
                .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            StratumText(
                text = name,
                style = StratumThemeAccessor.typography.bodyLarge,
                muted = !selected,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Long-press (~system 500ms) arms reorder; before that, swipes scroll the day strip.
            // Near the strip edges while dragging, DayTabsRow auto-scrolls so long moves work.
            Box(
                modifier =
                    Modifier
                        .size(28.dp)
                        .onGloballyPositioned { coords -> handleCoordinates = coords }
                        .pointerInput(Unit) {
                            fun pointerXInViewport(localPosition: Offset): Float {
                                val handle = handleCoordinates?.takeIf { it.isAttached }
                                    ?: return Float.NaN
                                val viewport = latestViewportCoordinates()?.takeIf { it.isAttached }
                                    ?: return Float.NaN
                                val root = handle.localToRoot(localPosition)
                                val origin = viewport.localToRoot(Offset.Zero)
                                return root.x - origin.x
                            }
                            detectDragGesturesAfterLongPress(
                                onDragStart = { startOffset ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    latestOnDragStart()
                                    // Seed edge-scroll position before the first move event.
                                    latestOnHorizontalDrag(0f, pointerXInViewport(startOffset))
                                },
                                onDragEnd = { latestOnDragEnd() },
                                onDragCancel = { latestOnDragEnd() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    latestOnHorizontalDrag(
                                        dragAmount.x,
                                        pointerXInViewport(change.position),
                                    )
                                },
                            )
                        },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(CoreUiR.drawable.ic_drag_handle),
                    contentDescription = dragDescription,
                    tint = if (selected) colors.textPrimary else colors.textMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Preview(name = "Day tabs light", showBackground = true)
@Composable
internal fun DayTabsRowPreviewLight() {
    StratumTheme(darkTheme = false) {
        DayTabsRowPreviewContent()
    }
}

@Preview(name = "Day tabs dark", showBackground = true, backgroundColor = 0xFF121212)
@Composable
internal fun DayTabsRowPreviewDark() {
    StratumTheme(darkTheme = true) {
        DayTabsRowPreviewContent()
    }
}

@Composable
private fun DayTabsRowPreviewContent() {
    var days by remember {
        mutableStateOf(
            listOf(
                CreateProgramDayUi(id = "a", name = "Workout A", position = 0, isRest = false, editState = null),
                CreateProgramDayUi(id = "r1", name = "Rest", position = 1, isRest = true, editState = null),
                CreateProgramDayUi(id = "b", name = "Workout B", position = 2, isRest = false, editState = null),
                CreateProgramDayUi(id = "r2", name = "Rest", position = 3, isRest = true, editState = null),
            ),
        )
    }
    var selectedId by remember { mutableStateOf("a") }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(StratumThemeAccessor.colors.surfaceBase)
                .padding(vertical = 8.dp),
    ) {
        DayTabsRow(
            days = days,
            selectedDayId = selectedId,
            canAddDay = true,
            onSelectDay = { selectedId = it },
            onAddDay = {
                val next = days.size + 1
                days = days +
                    CreateProgramDayUi(
                        id = "new-$next",
                        name = "Workout $next",
                        position = days.size,
                        isRest = false,
                        editState = null,
                    )
            },
            onReorderDays = reorder@{ from, to ->
                if (from !in days.indices || to !in days.indices) return@reorder
                val mutable = days.toMutableList()
                val moved = mutable.removeAt(from)
                mutable.add(to, moved)
                days = mutable.mapIndexed { index, day -> day.copy(position = index) }
            },
        )
    }
}
