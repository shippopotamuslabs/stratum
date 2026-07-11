package com.stratum.feature.onboarding.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.stratum.core.ui.component.StratumProgressTimeline

/**
 * Onboarding alias for the shared vertical status timeline (generation + import wrappers).
 */
@Composable
internal fun OnboardingProgressTimeline(
    stepLabels: List<String>,
    activeIndex: Int,
    activeDetail: String? = null,
    modifier: Modifier = Modifier,
) {
    StratumProgressTimeline(
        stepLabels = stepLabels,
        activeIndex = activeIndex,
        activeDetail = activeDetail,
        modifier = modifier,
    )
}
