package com.stratum.core.ui.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.stratum.core.ui.theme.StratumTheme
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Guards the notes-field keyboard collapse: the first character typed into an empty
 * [StratumTextField] with a clear button must not steal focus. The clear button appears on
 * the empty → non-empty transition; if the field moves composition slots when the button
 * appears, the focused node is disposed, focus is lost, and the IME collapses.
 */
@ExtendWith(RobolectricExtension::class)
@DisplayName("StratumTextField focus")
class StratumTextFieldFocusTest {

    @Test
    @OptIn(ExperimentalTestApi::class)
    @DisplayName("first character with clear button keeps focus")
    fun firstCharacterKeepsFocus() =
        runComposeUiTest {
            var text by mutableStateOf("")
            setContent {
                StratumTheme(darkTheme = true) {
                    StratumTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = "Notes",
                        singleLine = false,
                        options =
                            StratumTextFieldOptions(
                                minLines = 4,
                                maxLines = 8,
                                showClearButton = true,
                            ),
                    )
                }
            }

            onNodeWithText("Notes").performClick()
            onNodeWithText("Notes").performTextInput("a")

            onNodeWithText("Notes").assertIsFocused()
        }
}
