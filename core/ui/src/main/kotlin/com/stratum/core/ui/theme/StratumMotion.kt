package com.stratum.core.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Dp

/**
 * Stratum motion tokens (§12.5). The motion default is the M3 Expressive
 * scheme itself: [StratumTheme] wires `MotionScheme.expressive()` into
 * MaterialTheme (material3 pinned at 1.5.0-beta01 in the version catalog,
 * where the scheme is public; the BOM's managed 1.4.0 keeps it internal),
 * and components read spatial/effects springs from there. These tokens
 * name only what the scheme does not expose: the button press morph's
 * specs, which the contract fixes directly (§12.2 / §12.3 / §12.7).
 * Haptics live in [StratumHaptics].
 */
object StratumMotion {
    /** Pressed-state scale for buttons (§12.2: pill → 12dp + scale 0.96). */
    const val PressedScale: Float = 0.96f

    /** Corner morph spring for the button press (§12.7: [Spring.StiffnessHigh]). */
    val pressCornerSpec: AnimationSpec<Dp> = spring(stiffness = Spring.StiffnessHigh)

    /** Scale spring paired with [pressCornerSpec] for the press morph. */
    val pressScaleSpec: AnimationSpec<Float> = spring(stiffness = Spring.StiffnessHigh)
}
