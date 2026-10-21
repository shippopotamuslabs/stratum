package com.stratum.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.LocalStratumHazeState
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId
import dev.chrisbanes.haze.rememberHazeState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Pins the STR-156 bottomOverlay seam GEOMETRICALLY, not just by composition:
 * the regression this slot fixes was occlusion — the drawer composed fine but
 * rendered behind the shell's nav bar. Existence assertions would have passed
 * on the buggy structure, so this test pins where the overlay lands:
 *
 * - bottom-anchored to the scaffold's content area, horizontally centered;
 * - identical placement with and without a glass host;
 * - lifted by exactly the in-flow bottom bar's height when one is present
 *   (the slot shares the content column's bottom reservation).
 *
 * If the overlay ever moves back inside the source content column, it stacks
 * in flow after the content instead of anchoring, and these bounds fail.
 * Z-order against the real shell nav remains device-verified (the shell is
 * not composable in this module's test env).
 *
 * One [runComposeUiTest] environment for all scenarios (a second environment
 * in the same fork never reaches idle — see GlassHostActivationTest).
 */
@ExtendWith(RobolectricExtension::class)
@DisplayName("StratumTabScaffold bottomOverlay slot")
class StratumTabScaffoldTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    @DisplayName("bottom overlay anchors to the bottom edge in both host modes and clears an in-flow bottom bar")
    fun bottomOverlayAnchorsToBottomEdge() {
        runComposeUiTest {
            var provideHost by mutableStateOf(false)
            var withBottomBar by mutableStateOf(false)
            setContent {
                StratumTheme(themeId = StratumThemeId.Tonal, darkTheme = true) {
                    if (provideHost) {
                        CompositionLocalProvider(
                            LocalStratumHazeState provides rememberHazeState(),
                        ) {
                            ScaffoldUnderTest(withBottomBar = withBottomBar)
                        }
                    } else {
                        ScaffoldUnderTest(withBottomBar = withBottomBar)
                    }
                }
            }

            val root = onRoot().fetchSemanticsNode().boundsInRoot
            fun drawerBounds() = onNodeWithText("drawer pin").fetchSemanticsNode().boundsInRoot
            fun assertAnchored(expectedLiftPx: Float) {
                onNodeWithText("screen content").assertExists()
                onNodeWithText("drawer pin").assertIsDisplayed()
                val drawer = drawerBounds()
                assertEquals(
                    root.bottom - expectedLiftPx,
                    drawer.bottom,
                    2f,
                    "drawer must anchor to the content area's bottom edge (lifted by any in-flow bar)",
                )
                assertTrue(
                    drawer.center.x > root.center.x - 2f && drawer.center.x < root.center.x + 2f,
                    "drawer must stay horizontally centered",
                )
                assertTrue(
                    drawer.top > root.center.y,
                    "drawer must sit in the bottom half, not stacked in the content flow",
                )
            }

            // Solid host, no in-flow bar: flush with the bottom edge.
            assertAnchored(expectedLiftPx = 0f)
            // Glass host: same anchor — placement is host-independent.
            runOnIdle { provideHost = true }
            waitForIdle()
            assertAnchored(expectedLiftPx = 0f)
            // In-flow bottom bar present: the slot lifts by exactly its height.
            runOnIdle { withBottomBar = true }
            waitForIdle()
            assertAnchored(expectedLiftPx = with(density) { BottomBarHeight.toPx() })
        }
    }

    @Composable
    private fun ScaffoldUnderTest(withBottomBar: Boolean) {
        StratumTabScaffold(
            topBar = {},
            bottomBar = {
                if (withBottomBar) {
                    Box(modifier = Modifier.height(BottomBarHeight))
                }
            },
            bottomOverlay = { Text("drawer pin") },
        ) {
            Text("screen content")
        }
    }

    private companion object {
        val BottomBarHeight = 40.dp
    }
}
