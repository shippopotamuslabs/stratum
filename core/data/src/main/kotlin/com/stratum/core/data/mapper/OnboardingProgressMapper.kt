package com.stratum.core.data.mapper

import com.stratum.core.data.db.OnboardingProgressEntity
import com.stratum.domain.onboarding.OnboardingProgress
import com.stratum.domain.onboarding.OnboardingStep

fun OnboardingProgressEntity.toDomain(): OnboardingProgress =
    OnboardingProgress(
        currentStep = OnboardingStep.valueOf(currentStep),
        startedAt = startedAt,
        completedAt = completedAt,
    )

fun OnboardingProgress.toEntity(): OnboardingProgressEntity =
    OnboardingProgressEntity(
        id = OnboardingProgressEntity.ID,
        currentStep = currentStep.name,
        startedAt = startedAt,
        completedAt = completedAt,
    )
