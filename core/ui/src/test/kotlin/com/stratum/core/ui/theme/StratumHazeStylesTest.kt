package com.stratum.core.ui.theme

import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.GLASS_BACKGROUND_FLOOR_ALPHA
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the Glass Bars family switch (§12.8 of `contracts/UI_DESIGN.md`): glass is a
 * Tonal treatment; Neo Brutalist keeps solid chrome under its sanctioned carve-out
 * (§11.3 ruling 1). `HazeBlurStyle` is opaque, so the style values themselves are
 * pinned by the token factory's construction against §12.8; what is testable here
 * is enablement and instance stability — the spec must hand out the SAME style
 * objects on every read, or every glass modifier downstream would churn.
 */
class StratumHazeStylesTest {

    // STR-155: the clearance an overlay screen must give its scrollable is the
    // status-bar inset PLUS the bar height. The shipped formula omitted the
    // inset, so content collided with the bar on device; goldens cannot catch
    // that (Robolectric runs with zero insets), so the formula is pinned here.
    // The clearance formula's inputs: screens pad scrollables by the bar heights,
    // so a height change without a padding review collides content with chrome.
    @Test
    fun glassChrome_heightsArePinned() {
        assertEquals(64.dp, StratumGlassChrome.topBarHeight)
        assertEquals(80.dp, StratumGlassChrome.bottomBarHeight)
    }

    // STR-155 floor: the glass top bar paints surface-base at this alpha UNDER the
    // blur so it never goes fully transparent. Must stay translucent (glass reads
    // as glass) but present (no invisible bar over an empty source).
    @Test
    fun topBarGlassFloor_isTranslucentButPresent() {
        assertEquals(0.65f, GLASS_BACKGROUND_FLOOR_ALPHA)
        assertTrue(GLASS_BACKGROUND_FLOOR_ALPHA > 0f)
        assertTrue(GLASS_BACKGROUND_FLOOR_ALPHA < 1f)
    }

    @Test
    fun glassTopClearance_addsStatusInsetToBarHeight() {
        assertEquals(StratumGlassChrome.topBarHeight, glassTopClearance(0.dp))
        assertEquals(StratumGlassChrome.topBarHeight + 24.dp, glassTopClearance(24.dp))
        assertEquals(StratumGlassChrome.topBarHeight + 35.dp, glassTopClearance(35.dp))
    }

    @Test
    fun tonal_enablesGlass() {
        assertTrue(StratumTonalSpec.resolve(dark = true).haze.enabled)
        assertTrue(StratumTonalSpec.resolve(dark = false).haze.enabled)
    }

    @Test
    fun neo_disablesGlass() {
        assertFalse(NeoBrutalistSpec.resolve(dark = true).haze.enabled)
        assertFalse(NeoBrutalistSpec.resolve(dark = false).haze.enabled)
    }

    @Test
    fun specs_areCachedSingletons_soStyleInstancesAreStable() {
        // Glass modifiers identity-compare style objects; per-instance caches
        // inside the spec would still churn across resolve() calls, so the
        // factories hand out one spec per family/darkness.
        assertSame(StratumTonalSpec.resolve(dark = true), StratumTonalSpec.resolve(dark = true))
        assertSame(StratumTonalSpec.resolve(dark = false), StratumTonalSpec.resolve(dark = false))
        assertSame(NeoBrutalistSpec.resolve(dark = true), NeoBrutalistSpec.resolve(dark = true))
        val spec = StratumTonalSpec.resolve(dark = true)
        assertSame(spec.haze.topBar, spec.haze.topBar)
    }
}
