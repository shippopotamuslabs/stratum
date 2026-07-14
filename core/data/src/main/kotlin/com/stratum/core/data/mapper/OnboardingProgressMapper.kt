package com.stratum.core.data.mapper

import com.stratum.core.data.db.OnboardingProgressEntity
import com.stratum.core.data.sync.SyncFlags
import com.stratum.domain.onboarding.OnboardingProgress
import com.stratum.domain.onboarding.identity.OnboardingProgressId

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
        selectedPath = selectedPath,
    )

fun OnboardingProgress.toEntity(
    nowMillis: Long = System.currentTimeMillis(),
): OnboardingProgressEntity =
    OnboardingProgressEntity(
        id = OnboardingProgressId.Default,
        currentStep = currentStep,
        startedAt = startedAt,
        completedAt = completedAt,
        selectedPath = selectedPath,
        updatedAtEpochMillis = nowMillis,
        pendingSync = SyncFlags.PENDING_UPSERT,
    )
