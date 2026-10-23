package com.stratum.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumButtonVariant
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.theme.stratumButtonHardShadow

/**
 * Secondary call-to-action with transparent fill and outline — e.g. "Start Empty Workout".
 *
 * When [destructive] is true, border and label use the error semantic role (e.g. discard actions).
 * Neo Brutalist themes apply a hard-offset shadow from elevations; Tonal stays flat.
 * Press morph per §12.7 (StratumPressMorph), same as [StratumButton].
 */
@Composable
fun StratumOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: TextStyle = StratumThemeAccessor.typography.labelLarge,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    containerColor: Color = Color.Transparent,
    /** When true, uses error semantic colors for border and label. */
    destructive: Boolean = false,
    variant: StratumButtonVariant = StratumButtonVariant.Pill,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val elevations = StratumThemeAccessor.elevations
    val borderColor =
        when {
            destructive -> colors.error
            elevations.useHardShadow -> colors.borderStrong
            // STR-181: an outlined button's border is an interactive boundary,
            // not a hairline. §12.3's border-default (Stratum borderSubtle,
            // #2A2E39) measures 1.06:1 against the sheet container
            // (surfaceInset #272A33) and 1.42:1 on bg-base — the pill simply
            // vanishes, leaving a floating label. borderStrong is no fix in
            // dark (1.26:1 on sheets). textFaint is the ramp step that meets
            // the palette gospel's 3:1 interactive-boundary bar in both
            // themes (dark #6E7280: 2.99:1 on sheets, up to 4.03:1 on
            // bg-base; light #83868F: 3.17–3.64:1, effectively the light
            // borderStrong), and it already paints the sheet drag handle for
            // the same reason (STR-175).
            else -> colors.textFaint
        }
    val contentColor = if (destructive) colors.error else colors.accent
    val morph =
        rememberStratumPressMorph(
            restCorner = shapes.buttonRestCorner(variant),
            pressedCorner = shapes.buttonPressedCorner,
        )
    OutlinedButton(
        onClick = onClick,
        modifier =
            modifier
                .then(morph.scaleModifier)
                .heightIn(min = DefaultHeight)
                .stratumButtonHardShadow(enabled = enabled, shape = morph.shape, faceHeight = DefaultHeight),
        enabled = enabled,
        shape = morph.shape,
        interactionSource = morph.interactionSource,
        border = BorderStroke(shapes.borderWidth, borderColor),
        elevation =
            ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp,
                disabledElevation = 0.dp,
            ),
        colors =
            ButtonDefaults.outlinedButtonColors(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = containerColor,
                disabledContentColor = colors.textMuted,
            ),
        contentPadding = contentPadding,
    ) {
        Text(text = text, style = style)
    }
}

// §12.3 visual height; touch stays 48dp via M3 minimumInteractiveComponentSize.
private val DefaultHeight = 40.dp
