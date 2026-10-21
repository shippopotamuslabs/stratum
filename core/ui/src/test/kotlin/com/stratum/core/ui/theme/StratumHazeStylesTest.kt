package com.stratum.core.ui.theme

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
    fun styles_areStablePerSpecInstance() {
        val spec = StratumTonalSpec.resolve(dark = true)
        assertSame(spec.haze, spec.haze)
        assertSame(spec.haze.topBar, spec.haze.topBar)
    }
}
