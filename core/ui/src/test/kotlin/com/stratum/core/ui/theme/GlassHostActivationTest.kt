package com.stratum.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.rememberHazeState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Pins the glass host-activation contract the STR-155 seam relies on: glass applies
 * if and only if a shared HazeState is provided AND the family enables it. Screens
 * must not re-derive this — [rememberGlassHazeState] is the single gate, so the
 * overlay request in `StratumTabScaffold` and the bar treatments in `StratumTopBar`
 * / `StratumNavigationBar` agree by construction.
 *
 * Robolectric runs with zero window insets, so the glass top clearance equals the
 * bar height here; the inset half of the formula is pinned in [StratumHazeStylesTest].
 *
 * All scenarios share ONE [runComposeUiTest] environment: a second environment in
 * the same fork never reaches Espresso idle (the main thread parks in
 * `Espresso.onIdle` forever), so each scenario recomposes inside the same block.
 */
@ExtendWith(RobolectricExtension::class)
@DisplayName("Glass host activation")
class GlassHostActivationTest {

    private data class Observed(
        val active: Boolean,
        val topPadding: Dp,
        val bottomPadding: Dp,
    )

    @Test
    @DisplayName("host and family matrix activates glass only for Tonal with a host")
    fun hostFamilyMatrix() {
        val (tonalHost, tonalSolid, neoHost) = observeMatrix()

        assertTrue(tonalHost.active, "Tonal with a host state must activate glass")
        assertEquals(
            StratumGlassChrome.topBarHeight,
            tonalHost.topPadding,
            "Tonal host top clearance must equal the bar height under zero insets",
        )
        assertEquals(
            StratumGlassChrome.bottomBarHeight,
            tonalHost.bottomPadding,
            "Tonal host bottom clearance must equal the nav bar height",
        )

        assertFalse(tonalSolid.active, "Tonal without a host state must stay solid")
        assertEquals(0.dp, tonalSolid.topPadding, "Solid top clearance must be zero")
        assertEquals(0.dp, tonalSolid.bottomPadding, "Solid bottom clearance must be zero")

        assertFalse(neoHost.active, "Neo with a host state must stay solid under the carve-out")
        assertEquals(0.dp, neoHost.topPadding, "Neo top clearance must be zero")
        assertEquals(0.dp, neoHost.bottomPadding, "Neo bottom clearance must be zero")
    }

    @OptIn(ExperimentalTestApi::class)
    private fun observeMatrix(): Triple<Observed, Observed, Observed> {
        lateinit var result: Triple<Observed, Observed, Observed>
        runComposeUiTest {
            var family by mutableStateOf(StratumThemeId.Tonal)
            var provideHost by mutableStateOf(true)
            lateinit var current: Observed
            setContent {
                StratumTheme(themeId = family, darkTheme = true) {
                    if (provideHost) {
                        val hazeState = rememberHazeState()
                        CompositionLocalProvider(LocalStratumHazeState provides hazeState) {
                            current = observeReads()
                        }
                    } else {
                        current = observeReads()
                    }
                }
            }
            val tonalHost = current
            runOnIdle { provideHost = false }
            // The state write above schedules recomposition; wait until the frame
            // lands before reading, otherwise the read races the recompose.
            waitForIdle()
            val tonalSolid = current
            runOnIdle {
                family = StratumThemeId.NeoBrutalist
                provideHost = true
            }
            waitForIdle()
            val neoHost = current
            result = Triple(tonalHost, tonalSolid, neoHost)
        }
        return result
    }

    @Composable
    private fun observeReads(): Observed {
        val chrome = rememberGlassChromePadding()
        return Observed(
            active = chrome.active,
            topPadding = chrome.top,
            bottomPadding = chrome.bottom,
        )
    }
}
