package com.stratum.core.data.repository

import com.stratum.core.data.db.OnboardingProgressDao
import com.stratum.core.data.mapper.toDomain
import com.stratum.core.data.mapper.toEntity
import com.stratum.domain.onboarding.OnboardingProgress
import com.stratum.domain.onboarding.OnboardingRepository
import com.stratum.domain.onboarding.OnboardingStep
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomOnboardingRepository @Inject constructor(
    private val dao: OnboardingProgressDao,
) : OnboardingRepository {
    override fun observeProgress(): Flow<OnboardingProgress?> =
        dao.observe().map { it?.toDomain() }

    override suspend fun saveStep(step: OnboardingStep) {
        val startedAt = dao.getOrNull()?.startedAt ?: System.currentTimeMillis()
        dao.upsert(
            OnboardingProgress(
                currentStep = step,
                startedAt = startedAt,
                completedAt = null,
            ).toEntity(),
        )
    }

    override suspend fun markComplete() {
        val current = dao.getOrNull()
        val startedAt = current?.startedAt ?: System.currentTimeMillis()
        val step = current?.currentStep ?: OnboardingStep.Generate
        dao.upsert(
            OnboardingProgress(
                currentStep = step,
                startedAt = startedAt,
                completedAt = System.currentTimeMillis(),
            ).toEntity(),
        )
    }
}
