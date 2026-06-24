package com.stratum.core.data.mapper

import com.stratum.core.data.db.OnboardingProgressEntity
import com.stratum.domain.onboarding.OnboardingProgress

/**
 * Maps [OnboardingProgressEntity] ⇄ domain [OnboardingProgress]. [currentStep] is a typed
 * [com.stratum.domain.onboarding.OnboardingStep] decoded by
 * [com.stratum.core.data.db.EnumConverters], so this is now a plain field copy.
 */
fun OnboardingProgressEntity.toDomain(): OnboardingProgress =
    OnboardingProgress(
        currentStep = currentStep,
        startedAt = startedAt,
        completedAt = completedAt,
    )

fun OnboardingProgress.toEntity(): OnboardingProgressEntity =
    OnboardingProgressEntity(
        id = OnboardingProgressEntity.ID,
        currentStep = currentStep,
        startedAt = startedAt,
        completedAt = completedAt,
    )
