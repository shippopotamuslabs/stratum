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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.RirTargetOptionRow
import com.stratum.core.ui.component.StratumNumber
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Set-log keyboard pad content (RIR row + numeric keys). Hosted by
 * [ProvideSetLogKeyboardHost] as an in-hierarchy bottom dock.
 */
@Composable
internal fun StratumSetLogKeyboardContent(
    activeField: SetLogKeyboardField,
    selectedRir: Int,
    showWeightInput: Boolean,
    allowDecimal: Boolean,
    onRirSelected: (Int) -> Unit,
    onDigit: (Char) -> Unit,
    onDecimal: () -> Unit,
    onBackspace: () -> Unit,
    onPrimaryAction: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    canComplete: Boolean = false,
    // Retained for call-site readability / future field chrome; icon uses [canComplete] only.
    @Suppress("UNUSED_PARAMETER") weightText: String = "",
    @Suppress("UNUSED_PARAMETER") repsText: String = "",
) {
    val primaryIcon =
        setLogKeyboardPrimaryIcon(
            active = activeField,
            canComplete = canComplete,
            showWeightInput = showWeightInput,
        )

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RirTargetOptionRow(
            selectedRirGoal = selectedRir,
            onRirSelected = onRirSelected,
            chipSize = KeyboardRirChipSize,
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(StratumThemeAccessor.colors.borderSubtle),
        )
        NumericPad(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            allowDecimal = allowDecimal,
            primaryIcon = primaryIcon,
            onDigit = onDigit,
            onDecimal = onDecimal,
            onBackspace = onBackspace,
            onPrimaryAction = onPrimaryAction,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun NumericPad(
    allowDecimal: Boolean,
    primaryIcon: SetLogKeyboardPrimaryIcon,
    onDigit: (Char) -> Unit,
    onDecimal: () -> Unit,
    onBackspace: () -> Unit,
    onPrimaryAction: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(KeyGap),
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
                    modifier = Modifier.size(KeyIconSize),
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
            PadSpacerKey()
        }
        PadRow(Modifier.weight(1f)) {
            if (allowDecimal) {
                TextKey(
                    label = ".",
                    contentDescription = stringResource(CoreUiR.string.set_log_keyboard_decimal),
                    onClick = onDecimal,
                )
            } else {
                // Reps: leave the decimal slot blank (no disabled key).
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
                    modifier = Modifier.size(KeyIconSize),
                )
            }
            PrimaryActionKey(icon = primaryIcon, onClick = onPrimaryAction)
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
        horizontalArrangement = Arrangement.spacedBy(KeyGap),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun RowScope.DigitKey(
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
private fun RowScope.TextKey(
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
private fun RowScope.IconKey(
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    PadKeyContainer(onClick = onClick, contentDescription = contentDescription) {
        content()
    }
}

@Composable
private fun RowScope.PrimaryActionKey(
    icon: SetLogKeyboardPrimaryIcon,
    onClick: () -> Unit,
) {
    val description =
        when (icon) {
            SetLogKeyboardPrimaryIcon.NextReps ->
                stringResource(CoreUiR.string.set_log_keyboard_next_reps)
            SetLogKeyboardPrimaryIcon.PrevWeight ->
                stringResource(CoreUiR.string.set_log_keyboard_prev_weight)
            SetLogKeyboardPrimaryIcon.Check ->
                stringResource(CoreUiR.string.set_log_keyboard_complete_set)
        }
    IconKey(onClick = onClick, contentDescription = description) {
        when (icon) {
            SetLogKeyboardPrimaryIcon.NextReps ->
                Icon(
                    painter = painterResource(CoreUiR.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = StratumThemeAccessor.colors.textPrimary,
                    modifier = Modifier.size(KeyIconSize),
                )
            SetLogKeyboardPrimaryIcon.PrevWeight ->
                Icon(
                    painter = painterResource(CoreUiR.drawable.ic_chevron_left),
                    contentDescription = null,
                    tint = StratumThemeAccessor.colors.textPrimary,
                    modifier = Modifier.size(KeyIconSize),
                )
            SetLogKeyboardPrimaryIcon.Check ->
                Icon(
                    painter = painterResource(CoreUiR.drawable.ic_check),
                    contentDescription = null,
                    tint = StratumThemeAccessor.colors.textPrimary,
                    modifier = Modifier.size(KeyIconSize),
                )
        }
    }
}

@Composable
private fun RowScope.PadKeyContainer(
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
private fun RowScope.PadSpacerKey() {
    Spacer(
        modifier =
            Modifier
                .weight(1f)
                .fillMaxHeight(),
    )
}

private val KeyGap = 4.dp
private val KeyIconSize = 20.dp
private val KeyboardRirChipSize = 32.dp
