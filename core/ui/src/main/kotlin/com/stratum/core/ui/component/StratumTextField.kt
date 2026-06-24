package com.stratum.core.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Stratum's text input. Wraps Material 3 [OutlinedTextField] using `StratumTheme`
 * semantic roles so feature code never imports raw M3 text-field APIs (UI_DESIGN.md §4).
 *
 * Set [isError] and [errorText] to surface inline validation; pass [keyboardOptions] for
 * numeric (age, session length) or capitalized entry.
 */
@Composable
fun StratumTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    errorText: String? = null,
    singleLine: Boolean = true,
    placeholder: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(text = label) },
        placeholder = placeholder?.let { { Text(text = it) } },
        singleLine = singleLine,
        isError = isError,
        supportingText = errorText?.let { { Text(text = it) } },
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        shape = StratumThemeAccessor.shapes.panel,
        colors = OutlinedTextFieldDefaults.colors(
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
            focusedSupportingTextColor = StratumThemeAccessor.colors.error,
            unfocusedSupportingTextColor = StratumThemeAccessor.colors.textMuted,
            disabledSupportingTextColor = StratumThemeAccessor.colors.textMuted,
            errorSupportingTextColor = StratumThemeAccessor.colors.error,
            focusedPlaceholderColor = StratumThemeAccessor.colors.textMuted,
            unfocusedPlaceholderColor = StratumThemeAccessor.colors.textMuted,
            disabledPlaceholderColor = StratumThemeAccessor.colors.textMuted,
        ),
    )
}
