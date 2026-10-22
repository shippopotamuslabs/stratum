package com.stratum.core.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.stratum.core.ui.theme.StratumMotion

/**
 * The gospel button press morph (§12.2 / §12.3 / §12.7): while
 * [interactionSource] is pressed, the corner animates from [restCorner] to
 * [pressedCorner] and the scale to [StratumMotion.PressedScale], both on
 * the [StratumMotion] springs. Families whose rest and pressed corners are
 * equal (Neo Brutalist, §11.3 ruling 1) get a no-op corner morph by
 * construction; the scale still applies.
 */
internal class StratumPressMorph(
    val shape: Shape,
    val scaleModifier: Modifier,
)

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
        shape = RoundedCornerShape(corner),
        scaleModifier =
            Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    )
}
