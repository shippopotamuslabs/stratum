package com.stratum.core.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp

/**
 * Stratum motion tokens (§12.5). The motion default is the M3 Expressive
 * scheme itself: [StratumTheme] wires `MotionScheme.expressive()` into
 * MaterialTheme (material3 pinned at 1.5.0-beta01 in the version catalog,
 * where the scheme is public; the BOM's managed 1.4.0 keeps it internal),
 * and components read spatial/effects springs from there. These tokens
 * name only what the scheme does not expose: the button press morph's
 * specs, which the contract fixes directly (§12.2 / §12.3 / §12.7).
 */
object StratumMotion {
    /** Pressed-state scale for buttons (§12.2: pill → 12dp + scale 0.96). */
    const val PressedScale: Float = 0.96f

    /** Corner morph spring for the button press (§12.7: [Spring.StiffnessHigh]). */
    val pressCornerSpec: AnimationSpec<Dp> = spring(stiffness = Spring.StiffnessHigh)

    /** Scale spring paired with [pressCornerSpec] for the press morph. */
    val pressScaleSpec: AnimationSpec<Float> = spring(stiffness = Spring.StiffnessHigh)
}

/**
 * Stratum haptic vocabulary (§12.5): a light click on toggles/switches, a
 * medium click on FAB press. Obtained via [rememberStratumHaptics]; the
 * [HapticFeedbackType] choices live here so components never pick their own.
 * No FAB ships in Stratum yet, so [mediumPress] is the token awaiting one.
 */
class StratumHaptics internal constructor(
    private val feedback: HapticFeedback,
) {
    /** Light toggle click, direction-aware per the platform toggle types. */
    fun toggle(checked: Boolean) {
        feedback.performHapticFeedback(
            if (checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
        )
    }

    /** Medium press click — the FAB press weight (§12.5). */
    fun mediumPress() {
        feedback.performHapticFeedback(HapticFeedbackType.ContextClick)
    }
}

@Composable
fun rememberStratumHaptics(): StratumHaptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { StratumHaptics(feedback) }
}
