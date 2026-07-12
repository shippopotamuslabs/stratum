package com.stratum.core.ui.component.workoutlog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.StratumNumber
import com.stratum.core.ui.theme.StratumThemeAccessor

internal val NumericPadKeyGap = 4.dp
internal val NumericPadKeyIconSize = 20.dp

/**
 * Shared 4×4 numeric pad used by set-log and rep-range keyboards.
 *
 * Layout:
 * ```
 * 1 2 3 [hide]
 * 4 5 6 [spacer]
 * 7 8 9 [elevated | spacer]
 * . 0 ⌫ [primary]
 * ```
 * When [allowDecimal] is false the decimal slot is an empty spacer.
 * [primaryContent] fills the bottom-right action key.
 * Optional [elevatedContent] fills the key above primary (e.g. field-nav chevron when primary is Check).
 */
@Composable
internal fun StratumNumericPad(
    allowDecimal: Boolean,
    onDigit: (Char) -> Unit,
    onDecimal: () -> Unit,
    onBackspace: () -> Unit,
    onDismiss: () -> Unit,
    primaryContentDescription: String,
    onPrimaryAction: () -> Unit,
    primaryContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    elevatedContentDescription: String? = null,
    onElevatedAction: (() -> Unit)? = null,
    elevatedContent: (@Composable () -> Unit)? = null,
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
            PadSpacerKey()
        }
        PadRow(Modifier.weight(1f)) {
            DigitKey('7', onDigit)
            DigitKey('8', onDigit)
            DigitKey('9', onDigit)
            if (elevatedContent != null && onElevatedAction != null && elevatedContentDescription != null) {
                IconKey(
                    onClick = onElevatedAction,
                    contentDescription = elevatedContentDescription,
                ) {
                    elevatedContent()
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
            IconKey(onClick = onPrimaryAction, contentDescription = primaryContentDescription) {
                primaryContent()
            }
        }
    }
}

@Composable
internal fun PadRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NumericPadKeyGap),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
internal fun RowScope.DigitKey(
    digit: Char,
    onDigit: (Char) -> Unit,
) {
    TextKey(
        label = digit.toString(),
        contentDescription = stringResource(CoreUiR.string.set_log_keyboard_digit, digit.toString()),
        onClick = { onDigit(digit) },
    )
}

@Composable
internal fun RowScope.TextKey(
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
) {
    PadKeyContainer(onClick = onClick, contentDescription = contentDescription) {
        StratumNumber(
            text = label,
            style = StratumThemeAccessor.typography.titleLarge,
        )
    }
}

@Composable
internal fun RowScope.IconKey(
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    PadKeyContainer(onClick = onClick, contentDescription = contentDescription) {
        content()
    }
}

@Composable
internal fun RowScope.PadKeyContainer(
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    val shape = StratumThemeAccessor.shapes.chip
    Box(
        modifier =
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(shape)
                .background(StratumThemeAccessor.colors.surfaceInset)
                .border(
                    width = StratumThemeAccessor.shapes.borderWidth,
                    color = StratumThemeAccessor.colors.borderSubtle,
                    shape = shape,
                )
                .semantics { this.contentDescription = contentDescription }
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
internal fun RowScope.PadSpacerKey() {
    Spacer(
        modifier =
            Modifier
                .weight(1f)
                .fillMaxHeight(),
    )
}
