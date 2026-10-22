package com.stratum.core.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R
import com.stratum.core.ui.theme.StratumThemeAccessor
import kotlin.math.roundToInt

private val SwipeDeleteIconSize = 24.dp
private val SwipeDeleteMinusSize = 12.dp

private const val SWIPE_SNAP_ANIMATION_MS = 180
private const val SWIPE_REVEAL_FRACTION = 0.25f
private const val SWIPE_REVEAL_SNAP_FRACTION = 0.5f

/**
 * Swipe-left row that snaps open to reveal a destructive action on an error-colored background.
 * Snaps to [SWIPE_REVEAL_FRACTION] of the row width; tapping the action invokes [onActionClick].
 */
@Composable
fun StratumSwipeToRevealRow(
    onActionClick: () -> Unit,
    actionContentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentBackgroundColor: Color = StratumThemeAccessor.colors.surfaceRaised,
    /**
     * Extra unclipped room above the row for content that intentionally overhangs it
     * (e.g. a corner badge). Horizontal and bottom clipping are unchanged, so the swipe
     * reveal still masks correctly. Defaults to 0 (exact row bounds, as before).
     */
    topOverflow: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val contentClip = remember(topOverflow) { rowClipWithTopOverflow(topOverflow) }
    if (!enabled) {
        Box(modifier = modifier.fillMaxWidth()) {
            content()
        }
        return
    }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(contentClip),
    ) {
        val density = LocalDensity.current
        val revealWidthPx = constraints.maxWidth * SWIPE_REVEAL_FRACTION
        val errorColor = StratumThemeAccessor.colors.error
        var dragOffsetPx by remember { mutableFloatStateOf(0f) }
        var isDragging by remember { mutableStateOf(false) }
        var isRevealed by remember { mutableStateOf(false) }

        val targetOffsetPx =
            when {
                isDragging -> dragOffsetPx
                isRevealed -> -revealWidthPx
                else -> 0f
            }
        val animatedOffsetPx by animateFloatAsState(
            targetValue = targetOffsetPx,
            animationSpec = tween(durationMillis = SWIPE_SNAP_ANIMATION_MS),
            label = "swipe_reveal_offset",
        )

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(contentClip),
        ) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(errorColor),
            ) {
                // §12.5: the whole revealed error band is the target — the
                // 24dp circle was never touchable on its own. The button
                // keeps the icon visual; the band carries the click.
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .width(with(density) { revealWidthPx.toDp() })
                            .fillMaxHeight()
                            .clickable(
                                role = Role.Button,
                                onClick = {
                                    isRevealed = false
                                    dragOffsetPx = 0f
                                    onActionClick()
                                },
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    SwipeDeleteActionButton(
                        minusTint = errorColor,
                        contentDescription = actionContentDescription,
                    )
                }
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(animatedOffsetPx.roundToInt(), 0) }
                        .background(contentBackgroundColor)
                        .pointerInput(revealWidthPx) {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    isDragging = true
                                    dragOffsetPx = if (isRevealed) -revealWidthPx else 0f
                                },
                                onDragEnd = {
                                    isDragging = false
                                    val revealThreshold = revealWidthPx * SWIPE_REVEAL_SNAP_FRACTION
                                    isRevealed = dragOffsetPx <= -revealThreshold
                                    dragOffsetPx = if (isRevealed) -revealWidthPx else 0f
                                },
                                onDragCancel = {
                                    isDragging = false
                                    isRevealed = dragOffsetPx <= -(revealWidthPx * SWIPE_REVEAL_SNAP_FRACTION)
                                    dragOffsetPx = if (isRevealed) -revealWidthPx else 0f
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    dragOffsetPx =
                                        (dragOffsetPx + dragAmount).coerceIn(-revealWidthPx, 0f)
                                },
                            )
                        },
            ) {
                content()
            }
        }
    }
}

/**
 * Row-bounds clip with extra unclipped room above ([topOverflow]) for content that
 * intentionally overhangs the row, such as a corner badge. Zero overflow produces the
 * exact row rectangle. Pure geometry — covered by [TopOverflowClipShapeTest].
 */
internal fun rowClipWithTopOverflow(topOverflow: Dp): Shape {
    if (topOverflow <= 0.dp) return RectangleShape
    return object : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density,
        ): Outline {
            val overflowPx = with(density) { topOverflow.toPx() }
            return Outline.Rectangle(
                Rect(left = 0f, top = -overflowPx, right = size.width, bottom = size.height),
            )
        }
    }
}

@Composable
private fun SwipeDeleteActionButton(
    minusTint: Color,
    contentDescription: String,
) {
    Box(
        modifier =
            Modifier
                .size(SwipeDeleteIconSize)
                .clip(CircleShape)
                .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_remove),
            contentDescription = contentDescription,
            tint = minusTint,
            modifier = Modifier.size(SwipeDeleteMinusSize),
        )
    }
}
