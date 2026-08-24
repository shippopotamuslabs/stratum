package com.stratum.core.ui.component.workoutlog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.StratumNumber
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * 4×4 custom numeric pad for workout logging.
 *
 * ```
 * 1 2 3 [hide]
 * 4 5 6 [auxiliary | spacer]
 * 7 8 9 [elevated | spacer]
 * . 0 ⌫ [primary]
 * ```
 * When [allowDecimal] is false the decimal slot is an empty spacer.
 * [primaryContent] fills the bottom-right action key when both it and [onPrimaryAction]
 * are present; otherwise that cell is an empty spacer (e.g. bodyweight with no Check).
 * Optional [elevatedActionKey] fills the key above primary (field-nav chevron when a
 * weight field exists).
 * Optional [auxiliaryActionKey] fills the key above elevated (e.g. plate calculator).
 */
@Composable
internal fun StratumNumericPad(
    allowDecimal: Boolean,
    onDigit: (Char) -> Unit,
    onDecimal: () -> Unit,
    onBackspace: () -> Unit,
    onDismiss: () -> Unit,
    primaryContentDescription: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    primaryContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    elevatedActionKey: NumericPadActionKey? = null,
    auxiliaryActionKey: NumericPadActionKey? = null,
    auxiliaryContent: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(NumericPadKeyGap),
    ) {
        PadRow(Modifier.weight(1f)) {
            DigitKey('1', onDigit)
            DigitKey('2', onDigit)
            DigitKey('3', onDigit)
            IconKey(
                onClick = onDismiss,
                contentDescription = stringResource(CoreUiR.string.set_log_keyboard_hide),
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardHide,
                    contentDescription = null,
                    tint = StratumThemeAccessor.colors.textPrimary,
                    modifier = Modifier.size(NumericPadKeyIconSize),
                )
            }
        }
        PadRow(Modifier.weight(1f)) {
            DigitKey('4', onDigit)
            DigitKey('5', onDigit)
            DigitKey('6', onDigit)
            when {
                auxiliaryActionKey != null ->
                    IconKey(
                        onClick = auxiliaryActionKey.onAction,
                        contentDescription = auxiliaryActionKey.contentDescription,
                    ) {
                        auxiliaryActionKey.content()
                    }
                auxiliaryContent != null ->
                    PadHostKey(content = auxiliaryContent)
                else -> PadSpacerKey()
            }
        }
        PadRow(Modifier.weight(1f)) {
            DigitKey('7', onDigit)
            DigitKey('8', onDigit)
            DigitKey('9', onDigit)
            if (elevatedActionKey != null) {
                IconKey(
                    onClick = elevatedActionKey.onAction,
                    contentDescription = elevatedActionKey.contentDescription,
                ) {
                    elevatedActionKey.content()
                }
            } else {
                PadSpacerKey()
            }
        }
        PadRow(Modifier.weight(1f)) {
            if (allowDecimal) {
                TextKey(
                    label = ".",
                    contentDescription = stringResource(CoreUiR.string.set_log_keyboard_decimal),
                    onClick = onDecimal,
                )
            } else {
                PadSpacerKey()
            }
            DigitKey('0', onDigit)
            IconKey(
                onClick = onBackspace,
                contentDescription = stringResource(CoreUiR.string.set_log_keyboard_backspace),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = null,
                    tint = StratumThemeAccessor.colors.textPrimary,
                    modifier = Modifier.size(NumericPadKeyIconSize),
                )
            }
            if (onPrimaryAction != null && primaryContent != null && primaryContentDescription != null) {
                ActionKey(
                    onClick = onPrimaryAction,
                    contentDescription = primaryContentDescription,
                    content = primaryContent,
                )
            } else {
                PadSpacerKey()
            }
        }
    }
}

@Composable
private fun PadRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NumericPadKeyGap),
        content = content,
    )
}

@Composable
private fun RowScope.DigitKey(
    digit: Char,
    onClick: (Char) -> Unit,
) {
    TextKey(
        label = digit.toString(),
        contentDescription = stringResource(CoreUiR.string.set_log_keyboard_digit, digit),
        onClick = { onClick(digit) },
    )
}

@Composable
private fun RowScope.TextKey(
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
) {
    PadKeySurface(
        onClick = onClick,
        contentDescription = contentDescription,
        modifier = Modifier.weight(1f).fillMaxHeight(),
    ) {
        StratumNumber(
            text = label,
            style =
                StratumThemeAccessor.typography.bodyLarge.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = StratumThemeAccessor.colors.textPrimary,
                ),
        )
    }
}

@Composable
private fun RowScope.IconKey(
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    PadKeySurface(
        onClick = onClick,
        contentDescription = contentDescription,
        modifier = Modifier.weight(1f).fillMaxHeight(),
        content = content,
    )
}

@Composable
private fun RowScope.ActionKey(
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    PadKeySurface(
        onClick = onClick,
        contentDescription = contentDescription,
        modifier = Modifier.weight(1f).fillMaxHeight(),
        containerColor = StratumThemeAccessor.colors.accent,
        contentColor = StratumThemeAccessor.colors.onAccent,
        content = content,
    )
}

@Composable
private fun RowScope.PadSpacerKey() {
    Box(modifier = Modifier.weight(1f).fillMaxHeight())
}

@Composable
private fun RowScope.PadHostKey(
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(NumericPadKeyCornerRadius)
    Surface(
        modifier =
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(shape),
        shape = shape,
        color = StratumThemeAccessor.colors.surfaceRaised,
        contentColor = StratumThemeAccessor.colors.textPrimary,
        border = BorderStroke(1.dp, StratumThemeAccessor.colors.borderSubtle),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun PadKeySurface(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(NumericPadKeyCornerRadius),
    containerColor: Color = StratumThemeAccessor.colors.surfaceRaised,
    contentColor: Color = StratumThemeAccessor.colors.textPrimary,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val effectiveBg =
        if (isPressed && containerColor == StratumThemeAccessor.colors.surfaceRaised) {
            StratumThemeAccessor.colors.surfaceSunken
        } else {
            containerColor
        }
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier =
            modifier
                .clip(shape)
                .semantics {
                    role = Role.Button
                    this.contentDescription = contentDescription
                },
        shape = shape,
        color = effectiveBg,
        contentColor = contentColor,
        border = BorderStroke(1.dp, StratumThemeAccessor.colors.borderSubtle),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    }
}

private val NumericPadKeyGap = 6.dp
private val NumericPadKeyCornerRadius = 6.dp
internal val NumericPadKeyIconSize = 22.dp

@Immutable
internal data class NumericPadActionKey(
    val contentDescription: String,
    val onAction: () -> Unit,
    val content: @Composable () -> Unit,
)
