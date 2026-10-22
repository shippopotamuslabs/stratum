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
 * Stratum motion tokens (§12.5): the M3 Expressive motion scheme as
 * Stratum's own API, behind the scheme's member names.
 *
 * API availability (checked for STR-146): `MotionScheme.expressive()`
 * exists in the resolved material3 1.4.0 (Compose BOM 2026.09) but is
 * internal in that version; it turns public in material3 1.5. The specs
 * below are therefore spring-equivalents of the expressive scheme,
 * value-for-value (read from the 1.5.0-beta01 expressive implementation),
 * so adopting the real scheme later is a token swap, not a call-site
 * change. Spatial specs animate movement/size/shape; effects specs
 * animate color/alpha (damping 1.0, no bounce).
 */
object StratumMotion {
    /** Pressed-state scale for buttons (§12.2: pill → 12dp + scale 0.96). */
    const val PressedScale: Float = 0.96f

    /** Expressive default spatial spring. */
    fun <T> defaultSpatialSpec(): AnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)

    /** Expressive fast spatial spring. */
    fun <T> fastSpatialSpec(): AnimationSpec<T> = spring(dampingRatio = 0.6f, stiffness = 800f)

    /** Expressive slow spatial spring. */
    fun <T> slowSpatialSpec(): AnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 200f)

    /** Expressive default effects spring. */
    fun <T> defaultEffectsSpec(): AnimationSpec<T> = spring(stiffness = 1600f)

    /** Expressive fast effects spring. */
    fun <T> fastEffectsSpec(): AnimationSpec<T> = spring(stiffness = 3800f)

    /** Expressive slow effects spring. */
    fun <T> slowEffectsSpec(): AnimationSpec<T> = spring(stiffness = 800f)

    /**
     * Corner morph spring for the button press (§12.7 names
     * [Spring.StiffnessHigh] directly; the scheme has no equivalent).
     */
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
