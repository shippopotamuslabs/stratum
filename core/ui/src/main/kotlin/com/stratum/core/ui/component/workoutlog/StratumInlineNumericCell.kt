package com.stratum.core.ui.component.workoutlog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Compact numeric cell for weight/reps in a set-log row.
 *
 * When [onActivate] is non-null, the system IME is suppressed and taps open the custom
 * set-log keyboard via [onActivate]. [isActive] drives the accent border and requests
 * list scroll so the cell sits above the docked keyboard.
 *
 * [completed] is visual only: keeps the dark sunken chip on finished sets even when
 * [enabled] so lifters can still edit without losing the completed-row look.
 *
 * [onValueChange] is only used on the system-IME path ([onActivate] null). Custom-keyboard
 * hosts update values through the dock session instead.
 */
@Composable
fun StratumInlineNumericCell(
    value: String,
    placeholder: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit = {},
    isActive: Boolean = false,
    onActivate: (() -> Unit)? = null,
    completed: Boolean = false,
    /** When true, border uses error color (takes priority over [isActive] accent). */
    isError: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val useCustomKeyboard = onActivate != null
    var cellBoundsInWindow by remember { mutableStateOf(Rect.Zero) }
    var fieldValue by remember(value) {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }

    LaunchedEffect(value) {
        fieldValue = TextFieldValue(text = value, selection = TextRange(value.length))
    }

    if (useCustomKeyboard) {
        DockCellRevealEffect(
            isActive = isActive,
            cellBoundsInWindow = cellBoundsInWindow,
        )
    }

    val borderColor =
        when {
            isError -> StratumThemeAccessor.colors.error
            isActive -> StratumThemeAccessor.colors.accent
            else -> StratumThemeAccessor.colors.borderSubtle
        }

    // Completed rows keep sunken chips (dark on setComplete green); incomplete enabled
    // fields use inset; disabled incomplete uses sunken.
    val cellBackground =
        if (completed || !enabled) {
            StratumThemeAccessor.colors.surfaceSunken
        } else {
            StratumThemeAccessor.colors.surfaceInset
        }

    val cellModifier =
        modifier
            .onGloballyPositioned { coords ->
                cellBoundsInWindow = coords.boundsInWindow()
            }
            .height(36.dp)
            .clip(StratumThemeAccessor.shapes.chip)
            .background(cellBackground)
            .border(
                width = StratumThemeAccessor.shapes.borderWidth,
                color = borderColor,
                shape = StratumThemeAccessor.shapes.chip,
            )
            .then(
                if (enabled && onActivate != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onActivate,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 4.dp)

    if (useCustomKeyboard) {
        Box(
            modifier = cellModifier,
            contentAlignment = Alignment.Center,
        ) {
            // Keep the recommendation placeholder while focused and empty so the lifter
            // still sees the suggested weight/reps; typed digits replace it, and clearing
            // the buffer brings the placeholder back. Active state is the accent border.
            when {
                value.isEmpty() ->
                    StratumText(text = placeholder, muted = true, maxLines = 1)
                else ->
                    StratumText(
                        text = value,
                        // Primary text when interactive (including amend-complete); muted only
                        // when truly disabled.
                        muted = !enabled,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
            }
        }
    } else {
        // Legacy path (e.g. template edit) — system IME still available.
        BasicTextField(
            value = fieldValue,
            onValueChange = { updated ->
                val cleaned = updated.text.filter { char -> char.isDigit() || char == '.' }
                fieldValue = updated.copy(text = cleaned, selection = TextRange(cleaned.length))
                onValueChange(cleaned)
            },
            enabled = enabled,
            modifier = cellModifier,
            textStyle =
                StratumThemeAccessor.typography.bodyLarge.copy(
                    color =
                        if (enabled) {
                            StratumThemeAccessor.colors.textPrimary
                        } else {
                            StratumThemeAccessor.colors.textMuted
                        },
                    textAlign = TextAlign.Center,
                ),
            singleLine = true,
            cursorBrush = SolidColor(StratumThemeAccessor.colors.accent),
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.Center) {
                    if (fieldValue.text.isEmpty()) {
                        StratumText(text = placeholder, muted = true, maxLines = 1)
                    }
                    innerTextField()
                }
            },
        )
    }
}
