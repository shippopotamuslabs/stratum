package com.stratum.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Stratum's text input. Wraps Material 3 [OutlinedTextField] using `StratumTheme`
 * semantic roles so feature code never imports raw M3 text-field APIs (UI_DESIGN.md §4).
 *
 * Set [isError] and [errorText] to surface inline validation; pass [keyboardOptions] for
 * numeric (age, session length) or capitalized entry.
 *
 * [StratumTextFieldOptions.showClearButton] draws a compact X in the top-end corner with no
 * filled background, so it stays inside the outline on multiline fields.
 *
 * [StratumTextFieldOptions.selectAllOnFocus] selects all text on the first focus acquisition
 * (quick-replace editing, e.g. note fields); later focuses place the cursor normally.
 *
 * Pass `label = null` for label-less fields (e.g. swipe-to-delete note rows, where the
 * floating label's top strip sits outside the field container); use [placeholder] for the
 * empty-state caption instead.
 */
@Composable
fun StratumTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    errorText: String? = null,
    singleLine: Boolean = true,
    placeholder: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    options: StratumTextFieldOptions = StratumTextFieldOptions(),
) {
    val maxLines = options.maxLines ?: if (singleLine) 1 else Int.MAX_VALUE
    // Shared chrome so the select-all and plain branches differ only in value wiring.
    val labelContent: (@Composable () -> Unit)? = label?.let { text -> { Text(text = text) } }
    val placeholderContent: (@Composable () -> Unit)? =
        placeholder?.let { { Text(text = it) } }
    val supportingContent = supportingLine(isError, errorText, options.supportingText)
    val fieldShape = StratumThemeAccessor.shapes.panel
    val fieldColors = stratumTextFieldColors(isError)
    // Selection state for select-all-on-focus. Unconditional hooks; only read when enabled.
    // External value changes (e.g. length truncation) adopt the text and park the cursor at
    // the end; keystrokes flow through onValueChange so typing never fights the parent.
    var selectValue by remember { mutableStateOf(TextFieldValue(text = value)) }
    var selectArmed by remember { mutableStateOf(true) }
    val selecting = options.selectAllOnFocus
    if (selecting && selectValue.text != value) {
        selectValue = TextFieldValue(text = value, selection = TextRange(value.length))
    }
    val field =
        @Composable { fieldModifier: Modifier ->
            if (selecting) {
                OutlinedTextField(
                    value = selectValue,
                    onValueChange = { next ->
                        selectValue = next
                        if (next.text != value) onValueChange(next.text)
                    },
                    modifier =
                        fieldModifier
                            .fillMaxWidth()
                            .onFocusChanged { focus ->
                                if (focus.isFocused && selectArmed) {
                                    selectArmed = false
                                    selectValue =
                                        selectValue.copy(
                                            selection = TextRange(0, selectValue.text.length),
                                        )
                                }
                            },
                    enabled = enabled,
                    label = labelContent,
                    placeholder = placeholderContent,
                    singleLine = singleLine,
                    minLines = options.minLines,
                    maxLines = maxLines,
                    isError = isError,
                    supportingText = supportingContent,
                    keyboardOptions = keyboardOptions,
                    visualTransformation = visualTransformation,
                    shape = fieldShape,
                    colors = fieldColors,
                )
            } else {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = fieldModifier.fillMaxWidth(),
                    enabled = enabled,
                    label = labelContent,
                    placeholder = placeholderContent,
                    singleLine = singleLine,
                    minLines = options.minLines,
                    maxLines = maxLines,
                    isError = isError,
                    supportingText = supportingContent,
                    keyboardOptions = keyboardOptions,
                    visualTransformation = visualTransformation,
                    shape = fieldShape,
                    colors = fieldColors,
                )
            }
        }

    if (!options.showClearButton) {
        field(modifier)
        return
    }

    // Keep the field at a stable composition slot: toggling the clear icon's presence
    // must not move the text field, or the focused node is disposed on the first
    // empty -> non-empty transition and the IME collapses.
    Box(modifier = modifier.fillMaxWidth()) {
        field(Modifier)
        if (value.isNotEmpty()) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.stratum_text_field_clear_cd),
                tint = StratumThemeAccessor.colors.textMuted,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = CLEAR_ICON_INSET, end = CLEAR_ICON_INSET)
                        .size(CLEAR_ICON_SIZE)
                        .clickable(
                            role = Role.Button,
                            onClick = { onValueChange("") },
                        ),
            )
        }
    }
}

@Composable
private fun stratumTextFieldColors(isError: Boolean): TextFieldColors =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = StratumThemeAccessor.colors.textPrimary,
        unfocusedTextColor = StratumThemeAccessor.colors.textPrimary,
        disabledTextColor = StratumThemeAccessor.colors.textMuted,
        focusedContainerColor = StratumThemeAccessor.colors.surfaceBase,
        unfocusedContainerColor = StratumThemeAccessor.colors.surfaceBase,
        disabledContainerColor = StratumThemeAccessor.colors.surfaceSunken,
        cursorColor = StratumThemeAccessor.colors.accent,
        errorCursorColor = StratumThemeAccessor.colors.error,
        selectionColors = null,
        focusedBorderColor = StratumThemeAccessor.colors.accent,
        unfocusedBorderColor = StratumThemeAccessor.colors.borderSubtle,
        disabledBorderColor = StratumThemeAccessor.colors.borderSubtle,
        errorBorderColor = StratumThemeAccessor.colors.error,
        focusedLabelColor = StratumThemeAccessor.colors.accent,
        unfocusedLabelColor = StratumThemeAccessor.colors.textMuted,
        disabledLabelColor = StratumThemeAccessor.colors.textMuted,
        errorLabelColor = StratumThemeAccessor.colors.error,
        focusedSupportingTextColor =
            if (isError) {
                StratumThemeAccessor.colors.error
            } else {
                StratumThemeAccessor.colors.textMuted
            },
        unfocusedSupportingTextColor =
            if (isError) {
                StratumThemeAccessor.colors.error
            } else {
                StratumThemeAccessor.colors.textMuted
            },
        disabledSupportingTextColor = StratumThemeAccessor.colors.textMuted,
        errorSupportingTextColor = StratumThemeAccessor.colors.error,
        focusedPlaceholderColor = StratumThemeAccessor.colors.textMuted,
        unfocusedPlaceholderColor = StratumThemeAccessor.colors.textMuted,
        disabledPlaceholderColor = StratumThemeAccessor.colors.textMuted,
    )

@Composable
private fun supportingLine(
    isError: Boolean,
    errorText: String?,
    supportingText: String?,
): (@Composable () -> Unit)? {
    val text =
        when {
            isError && !errorText.isNullOrBlank() -> errorText
            !supportingText.isNullOrBlank() -> supportingText
            else -> return null
        }
    return { Text(text = text) }
}

private val CLEAR_ICON_SIZE = 18.dp
private val CLEAR_ICON_INSET = 12.dp
