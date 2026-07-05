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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
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
    content: @Composable () -> Unit,
) {
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
                .clip(RectangleShape),
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
                    .clip(RectangleShape),
        ) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(errorColor),
            ) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .width(with(density) { revealWidthPx.toDp() })
                            .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    SwipeDeleteActionButton(
                        onClick = {
                            isRevealed = false
                            dragOffsetPx = 0f
                            onActionClick()
                        },
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

@Composable
private fun SwipeDeleteActionButton(
    onClick: () -> Unit,
    minusTint: Color,
    contentDescription: String,
) {
    Box(
        modifier =
            Modifier
                .size(SwipeDeleteIconSize)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(onClick = onClick),
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
