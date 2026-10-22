package com.stratum.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

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
