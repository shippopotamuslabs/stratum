package com.stratum.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Search input with a leading magnifier and a trailing clear button. Sibling of
 * [StratumTextField] for the swap sheet's filter-within-list search. Same
 * `StratumThemeAccessor` color/shape scheme — kept inside `:core:ui` (Detekt-exempt) so
 * feature code never imports raw M3 text fields.
 *
 * Single-line, capitalises words (lift names), and dismisses the keyboard when the trailing
 * clear `StratumIconButton` is tapped. The trailing control is only rendered when there is text
 * to clear.
 */
@Composable
fun StratumSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.lift_swap_search_placeholder),
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val colors = StratumThemeAccessor.colors

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        label = null,
        placeholder = { Text(text = placeholder, color = colors.textMuted) },
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
                tint = colors.textMuted,
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                StratumIconButton(
                    onClick = {
                        onValueChange("")
                        keyboard?.hide()
                    },
                    modifier = Modifier.size(SEARCH_TRAILING_SIZE),
                    containerColor = colors.surfaceSunken,
                    contentColor = colors.textMuted,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_filter_reset),
                        contentDescription = stringResource(R.string.lift_swap_search_clear_content_description),
                        tint = colors.textMuted,
                    )
                }
            } else {
                Box(Modifier.size(SEARCH_TRAILING_SIZE))
            }
        },
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Search,
            ),
        shape = StratumThemeAccessor.shapes.panel,
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                disabledTextColor = colors.textMuted,
                focusedContainerColor = colors.surfaceBase,
                unfocusedContainerColor = colors.surfaceBase,
                disabledContainerColor = colors.surfaceSunken,
                cursorColor = colors.accent,
                errorCursorColor = colors.error,
                selectionColors = null,
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.borderSubtle,
                disabledBorderColor = colors.borderSubtle,
                errorBorderColor = colors.error,
                focusedLeadingIconColor = colors.textMuted,
                unfocusedLeadingIconColor = colors.textMuted,
                disabledLeadingIconColor = colors.textMuted,
            ),
    )
}

private val SEARCH_TRAILING_SIZE = 36.dp
