package com.stratum.core.ui.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.stratum.core.ui.theme.LocalStratumHazeState
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId
import dev.chrisbanes.haze.rememberHazeState
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Pins the STR-156 bottomOverlay seam: the slot composes its content in BOTH
 * host modes (solid and glass-provided). The slot exists because an overlay
 * rendered inside the scaffold's hazeSource content subtree can neither blur
 * (STR-155) nor be trusted to stay visible over the shell's glass nav bar —
 * the active-workout drawer regression was the content-side copy disappearing
 * behind that bar. The slot is a Scaffold sibling of the source, like topBar.
 *
 * One [runComposeUiTest] environment for both scenarios (a second environment
 * in the same fork never reaches idle — see GlassHostActivationTest).
 */
@ExtendWith(RobolectricExtension::class)
@DisplayName("StratumTabScaffold bottomOverlay slot")
class StratumTabScaffoldTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    @DisplayName("bottom overlay composes with and without a glass host")
    fun bottomOverlayComposesInBothHostModes() {
        runComposeUiTest {
            var provideHost by mutableStateOf(false)
            setContent {
                StratumTheme(themeId = StratumThemeId.Tonal, darkTheme = true) {
                    if (provideHost) {
                        CompositionLocalProvider(
                            LocalStratumHazeState provides rememberHazeState(),
                        ) {
                            ScaffoldUnderTest()
                        }
                    } else {
                        ScaffoldUnderTest()
                    }
                }
            }
            onNodeWithText("screen content").assertExists()
            onNodeWithText("drawer pin").assertExists()
            runOnIdle { provideHost = true }
            waitForIdle()
            onNodeWithText("screen content").assertExists()
            onNodeWithText("drawer pin").assertExists()
        }
    }

    @Composable
    private fun ScaffoldUnderTest() {
        StratumTabScaffold(
            topBar = {},
            bottomOverlay = { Text("drawer pin") },
        ) {
            Text("screen content")
        }
    }
}
