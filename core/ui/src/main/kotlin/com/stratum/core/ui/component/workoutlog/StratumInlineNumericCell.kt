package com.stratum.core.ui.component.workoutlog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor

/** Compact numeric cell for weight/reps in a set-log row. */
@Composable
fun StratumInlineNumericCell(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusManager = LocalFocusManager.current
    var fieldValue by remember(value) {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }

    LaunchedEffect(value) {
        if (!isFocused) {
            fieldValue = TextFieldValue(text = value, selection = TextRange(value.length))
        }
    }

    BasicTextField(
        value = fieldValue,
        onValueChange = { updated ->
            val cleaned = updated.text.filter { char -> char.isDigit() || char == '.' }
            fieldValue = updated.copy(text = cleaned, selection = TextRange(cleaned.length))
            onValueChange(cleaned)
        },
        enabled = enabled,
        modifier =
            modifier
                .height(36.dp)
                .clip(StratumThemeAccessor.shapes.chip)
                .background(
                    if (enabled) {
                        StratumThemeAccessor.colors.surfaceInset
                    } else {
                        StratumThemeAccessor.colors.surfaceSunken
                    },
                )
                .border(
                    width = StratumThemeAccessor.shapes.borderWidth,
                    color =
                        if (isFocused) {
                            StratumThemeAccessor.colors.accent
                        } else {
                            StratumThemeAccessor.colors.borderSubtle
                        },
                    shape = StratumThemeAccessor.shapes.chip,
                )
                .padding(horizontal = 8.dp),
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
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
        keyboardActions =
            KeyboardActions(
                onDone = { focusManager.clearFocus() },
            ),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.Center) {
                if (fieldValue.text.isEmpty() && !isFocused) {
                    StratumText(text = placeholder, muted = true)
                }
                innerTextField()
            }
        },
    )
}
