package com.stratum.core.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Circular icon button for secondary actions in Stratum chrome. Press morph
 * per §12.3 (StratumPressMorph): the default circular shape morphs its
 * corner to the family's pressed corner and scales to 0.96; a caller-passed
 * custom [shape] keeps its geometry and only scales.
 */
@Composable
fun StratumIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = Color.Transparent,
    contentColor: Color = StratumThemeAccessor.colors.textPrimary,
    shape: Shape = CircleShape,
    content: @Composable () -> Unit,
) {
    val shapes = StratumThemeAccessor.shapes
    val circular = shape == CircleShape
    val morph =
        rememberStratumPressMorph(
            restCorner = shapes.iconButtonRestCorner(shape),
            pressedCorner = shapes.buttonPressedCorner,
        )
    IconButton(
        onClick = onClick,
        // §12.3: 40x40dp container. Appended after the caller's modifier so a
        // caller-set size (earlier in the chain) still wins deliberately.
        modifier = modifier.then(morph.scaleModifier).size(40.dp),
        enabled = enabled,
        shape = if (circular) morph.shape else shape,
        interactionSource = morph.interactionSource,
        colors =
            IconButtonDefaults.iconButtonColors(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = StratumThemeAccessor.colors.borderSubtle,
                disabledContentColor = StratumThemeAccessor.colors.textMuted,
            ),
    ) {
        content()
    }
}
