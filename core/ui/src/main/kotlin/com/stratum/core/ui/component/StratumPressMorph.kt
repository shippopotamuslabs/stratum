package com.stratum.core.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.stratum.core.ui.theme.StratumMotion

/**
 * The whole gospel button press pattern (§12.2 / §12.3 / §12.7) in one
 * holder: the [interactionSource] the component hands to its Material
 * button, the animated [shape], and the [scaleModifier] it appends to
 * its modifier chain. While pressed, the corner animates from the rest
 * corner to the pressed corner and the scale to
 * [StratumMotion.PressedScale], both on the [StratumMotion] springs.
 * Families whose rest and pressed corners are equal (Neo Brutalist,
 * §11.3 ruling 1) get a no-op corner morph by construction; the scale
 * still applies. Modifier ORDER beyond the scale append stays with each
 * component — their chains differ deliberately (shadow and size
 * placement), so only the shared wiring is centralized here.
 */
internal class StratumPressMorph(
    val interactionSource: MutableInteractionSource,
    val shape: Shape,
    val scaleModifier: Modifier,
)

/** Press morph with a self-owned interaction source (the button default). */
@Composable
internal fun rememberStratumPressMorph(
    restCorner: Dp,
    pressedCorner: Dp,
): StratumPressMorph {
    val interactionSource = remember { MutableInteractionSource() }
    return rememberStratumPressMorph(
        interactionSource = interactionSource,
        restCorner = restCorner,
        pressedCorner = pressedCorner,
    )
}

@Composable
internal fun rememberStratumPressMorph(
    interactionSource: MutableInteractionSource,
    restCorner: Dp,
    pressedCorner: Dp,
): StratumPressMorph {
    val pressed by interactionSource.collectIsPressedAsState()
    val corner by
        animateDpAsState(
            targetValue = if (pressed) pressedCorner else restCorner,
            animationSpec = StratumMotion.pressCornerSpec,
            label = "StratumPressMorphCorner",
        )
    val scale by
        animateFloatAsState(
            targetValue = if (pressed) StratumMotion.PressedScale else 1f,
            animationSpec = StratumMotion.pressScaleSpec,
            label = "StratumPressMorphScale",
        )
    return StratumPressMorph(
        interactionSource = interactionSource,
        shape = RoundedCornerShape(corner),
        scaleModifier =
            Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    )
}
