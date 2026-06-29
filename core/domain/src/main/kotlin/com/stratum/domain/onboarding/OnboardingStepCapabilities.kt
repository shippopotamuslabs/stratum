package com.stratum.domain.onboarding

data class OnboardingStepCapabilities(
    val stepValid: Boolean,
    val gymLiftsValid: Boolean,
    val canGoBack: Boolean,
)
