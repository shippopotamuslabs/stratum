package com.stratum.core.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.LocalStratumTheme
import com.stratum.core.ui.theme.StratumButtonVariant
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor

private val TopBarTextButtonPadding =
    PaddingValues(
        start = StratumSpacing.s8,
        top = StratumSpacing.s8,
        end = 0.dp,
        bottom = StratumSpacing.s8,
    )
private val TopBarTextButtonEndInset = 16.dp

/**
 * Text-only action for inline secondary actions. Press morph per §12.7
 * (StratumPressMorph), same as [StratumButton].
 */
@Composable
fun StratumTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: TextStyle = StratumThemeAccessor.typography.labelLarge,
    contentColor: Color = StratumThemeAccessor.colors.accent,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val interactionSource = remember { MutableInteractionSource() }
    val morph =
        rememberStratumPressMorph(
            interactionSource = interactionSource,
            restCorner = shapes.buttonRestCorner(StratumButtonVariant.Pill),
            pressedCorner = shapes.buttonPressedCorner,
        )
    TextButton(
        onClick = onClick,
        modifier = modifier.then(morph.scaleModifier),
        enabled = enabled,
        shape = morph.shape,
        interactionSource = interactionSource,
        colors =
            ButtonDefaults.textButtonColors(
                contentColor = contentColor,
                disabledContentColor = colors.textMuted,
            ),
        contentPadding = contentPadding,
    ) {
        Text(text = text, style = style)
    }
}

/**
 * Trailing top-bar action — compact end padding and a lighter accent on dark surfaces.
 */
@Composable
fun StratumTopBarTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: TextStyle = StratumThemeAccessor.typography.labelLarge,
) {
    val colors = StratumThemeAccessor.colors
    val spec = LocalStratumTheme.current
    val contentColor =
        if (spec.dark) {
            colors.accent.lighten(fraction = 0.35f)
        } else {
            colors.accent
        }
    StratumTextButton(
        text = text,
        onClick = onClick,
        modifier = modifier.padding(end = TopBarTextButtonEndInset),
        enabled = enabled,
        style = style,
        contentColor = contentColor,
        contentPadding = TopBarTextButtonPadding,
    )
}

private fun Color.lighten(fraction: Float): Color =
    Color(
        red = red + ((1f - red) * fraction),
        green = green + ((1f - green) * fraction),
        blue = blue + ((1f - blue) * fraction),
        alpha = alpha,
    )
