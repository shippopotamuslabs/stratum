package com.stratum.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.theme.StratumThemeId

@PreviewLightDark
@Composable
private fun StratumTextFieldTonalPreview() {
    TextFieldPreviewGrid(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun StratumTextFieldNeoPreview() {
    TextFieldPreviewGrid(family = StratumThemeId.NeoBrutalist)
}

@Composable
internal fun TextFieldPreviewGrid(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        Column(
            // §12.3 fields are transparent, so the grid paints the screen
            // background the fields would sit on in production.
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(StratumThemeAccessor.colors.surfaceBase)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StratumTextField(
                value = "28",
                onValueChange = {},
                label = "Age",
                placeholder = "e.g. 28",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            StratumTextField(
                value = "",
                onValueChange = {},
                label = "Age",
                placeholder = "e.g. 28",
            )
            StratumTextField(
                value = "5",
                onValueChange = {},
                label = "Age",
                isError = true,
                errorText = "Must be at least 13.",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            // Disabled state pinned: on sheets this is the last band's
            // "Ends at" field, the one whose fill diverged in STR-245.
            StratumTextField(
                value = "28",
                onValueChange = {},
                label = "Age",
                enabled = false,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            StratumTextField(
                value = "Keep elbows tucked on the bench.",
                onValueChange = {},
                label = "Notes",
                singleLine = false,
                options =
                    StratumTextFieldOptions(
                        minLines = 3,
                        showClearButton = true,
                        supportingText = "Note will be pinned to this lift within this workout",
                    ),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        }
    }
}
