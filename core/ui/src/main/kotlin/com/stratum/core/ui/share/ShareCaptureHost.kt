package com.stratum.core.ui.share

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density

/** Default Instagram-feed square edge length in device pixels. */
const val SHARE_CAPTURE_DEFAULT_PIXEL_SIZE: Int = 1080

private const val CAPTURE_FRAME_ATTEMPTS: Int = 3

/**
 * On-demand square capture host. When [active] becomes true, composes [content] at
 * exactly [pixelSize] device pixels (fontScale forced to 1), records into a
 * [androidx.compose.ui.graphics.layer.GraphicsLayer], and invokes [onCaptured] with a
 * non-blank [ImageBitmap] — or [onCaptureFailed] if the layer stays empty.
 *
 * Layout is in-bounds (top-start of the parent) but not drawn to the visible canvas
 * (record-only) so the user does not see the share card. Semantics are cleared so
 * TalkBack does not announce a second summary.
 *
 * Compose only while [active] is true so complete/proposal screens do not pay a permanent
 * 1080² measure/draw tax.
 */
@Composable
fun ShareCaptureHost(
    active: Boolean,
    onCaptured: (ImageBitmap) -> Unit,
    onCaptureFailed: () -> Unit,
    modifier: Modifier = Modifier,
    pixelSize: Int = SHARE_CAPTURE_DEFAULT_PIXEL_SIZE,
    content: @Composable () -> Unit,
) {
    if (!active) return

    val density = LocalDensity.current
    val captureDensity =
        remember(density.density) {
            Density(density = density.density, fontScale = 1f)
        }
    val sideDp =
        remember(captureDensity, pixelSize) {
            with(captureDensity) { pixelSize.toFloat().toDp() }
        }
    val graphicsLayer = rememberGraphicsLayer()
    val onCapturedState by rememberUpdatedState(onCaptured)
    val onFailedState by rememberUpdatedState(onCaptureFailed)

    CompositionLocalProvider(LocalDensity provides captureDensity) {
        Box(
            modifier =
                modifier
                    .requiredSize(sideDp)
                    .clearAndSetSemantics { }
                    .drawWithContent {
                        // Capture outer ContentDrawScope so record{} can re-issue child draws.
                        val contentDraw = this
                        graphicsLayer.record {
                            contentDraw.drawContent()
                        }
                        // Record only — do not drawLayer to the visible canvas.
                    },
        ) {
            content()
        }
    }

    LaunchedEffect(active, pixelSize) {
        if (!active) return@LaunchedEffect
        repeat(CAPTURE_FRAME_ATTEMPTS) {
            withFrameNanos { }
            val image = graphicsLayer.toImageBitmap()
            if (!image.isBlankShareCapture(expectedMinEdge = (pixelSize / 4).coerceAtLeast(1))) {
                onCapturedState(image)
                return@LaunchedEffect
            }
        }
        onFailedState()
    }
}

/**
 * Rejects zero/undersized bitmaps (empty GraphicsLayer records typically yield 0×0 or
 * tiny layers). Full pixel sampling is intentionally avoided — theme surfaces are never
 * pure empty at the expected Instagram edge length when draw succeeded.
 */
fun ImageBitmap.isBlankShareCapture(expectedMinEdge: Int = 1): Boolean =
    width < expectedMinEdge || height < expectedMinEdge
