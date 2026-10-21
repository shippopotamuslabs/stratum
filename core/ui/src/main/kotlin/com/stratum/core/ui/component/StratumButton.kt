package com.stratum.core.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumButtonVariant
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.theme.stratumButtonHardShadow

/**
 * Stratum's primary call-to-action button. Wraps Material 3 [Button] using
 * `StratumTheme` semantic roles so feature code never imports raw M3 button APIs.
 *
 * Neo Brutalist themes apply a hard-offset shadow and strong border from
 * [com.stratum.core.ui.theme.StratumElevations]; Tonal keeps a flat filled button.
 *
 * Shape follows the Button Shape Gospel (§12.7): [variant] picks the resting
 * shape and, while pressed, the corner morphs to the family's pressed shape
 * (12.dp under Tonal) for BOTH variants. Neo's 4.dp corner scale makes its
 * resting and pressed corners identical, so its geometry is untouched by
 * construction (sanctioned carve-out, §11.3 ruling 1).
 */
@Composable
fun StratumButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: TextStyle = StratumThemeAccessor.typography.labelLarge,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    /** When true, uses error semantic colors (destructive confirms). */
    destructive: Boolean = false,
    variant: StratumButtonVariant = StratumButtonVariant.Pill,
    /** When true, renders the 56dp primary-CTA size instead of the 40dp default (§12.3). */
    large: Boolean = false,
    /** Optional leading icon, rendered at 18dp with an 8dp gap (§12.3). */
    leadingIcon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val elevations = StratumThemeAccessor.elevations

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // Resting corner per variant, resolved by the theme layer (§12.7):
    // family knowledge (pill vs carve-out geometry) lives in StratumShapes.
    val restCorner = shapes.buttonRestCorner(variant)
    val corner by
        animateDpAsState(
            targetValue = if (pressed) shapes.buttonPressedCorner else restCorner,
            animationSpec = spring(stiffness = Spring.StiffnessHigh),
            label = "StratumButtonPressMorph",
        )
    Button(
        onClick = onClick,
        modifier =
            modifier
                .heightIn(min = if (large) LargeHeight else DefaultHeight)
                .stratumButtonHardShadow(enabled = enabled, shape = shapes.button),
        enabled = enabled,
        shape = RoundedCornerShape(corner),
        interactionSource = interactionSource,
        border =
            if (elevations.useHardShadow) {
                BorderStroke(shapes.borderWidth, colors.borderStrong)
            } else {
                null
            },
        elevation =
            ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp,
                disabledElevation = 0.dp,
            ),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = if (destructive) colors.error else colors.accent,
                contentColor = if (destructive) colors.onError else colors.onAccent,
                disabledContainerColor =
                    if (destructive) colors.error.copy(alpha = 0.5f) else colors.accentMuted,
                disabledContentColor = if (destructive) colors.onError else colors.onAccent,
            ),
        contentPadding =
            if (leadingIcon != null && contentPadding == ButtonDefaults.ContentPadding) {
                IconContentPadding
            } else {
                contentPadding
            },
    ) {
        if (leadingIcon != null) {
            Box(modifier = Modifier.size(18.dp)) { leadingIcon() }
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = text, style = style)
    }
}

// §12.3 visual heights; the 48dp touch minimum is carried by M3's
// minimumInteractiveComponentSize, not by the layout height (§12.5).
private val DefaultHeight = 40.dp
private val LargeHeight = 56.dp

// §12.3: 16dp start padding when a leading icon is present (24dp text-only).
private val IconContentPadding =
    PaddingValues(
        start = StratumSpacing.s16,
        top = StratumSpacing.s8,
        end = StratumSpacing.s24,
        bottom = StratumSpacing.s8,
    )
