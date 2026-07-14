package com.stratum.core.data.repository

import com.stratum.core.data.db.OnboardingProgressDao
import com.stratum.core.data.mapper.toDomain
import com.stratum.core.data.mapper.toEntity
import com.stratum.core.data.sync.SyncCollection
import com.stratum.core.data.sync.SyncTouch
import com.stratum.domain.onboarding.OnboardingPath
import com.stratum.domain.onboarding.OnboardingProgress
import com.stratum.domain.onboarding.OnboardingRepository
import com.stratum.domain.onboarding.OnboardingStep
import com.stratum.domain.onboarding.identity.OnboardingProgressId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomOnboardingRepository @Inject constructor(
    private val dao: OnboardingProgressDao,
    private val syncTouch: SyncTouch,
) : OnboardingRepository {
    override fun observeProgress(): Flow<OnboardingProgress?> =
        dao.observe().map { it?.toDomain() }

    override suspend fun saveStep(step: OnboardingStep, selectedPath: OnboardingPath?) {
        syncTouch.mutate {
            val existing = dao.getOrNull()?.toDomain()
            val startedAt = existing?.startedAt ?: System.currentTimeMillis()
            val path = selectedPath ?: existing?.selectedPath
            dao.upsert(
                OnboardingProgress(
                    currentStep = step,
                    startedAt = startedAt,
                    completedAt = null,
                    selectedPath = path,
                ).toEntity(),
            )
            syncTouch.cancelDelete(
                SyncCollection.OnboardingProgress,
                OnboardingProgressId.Default.value,
            )
        }
    }

    override suspend fun markComplete() {
        syncTouch.mutate {
            val current = dao.getOrNull()?.toDomain()
            val startedAt = current?.startedAt ?: System.currentTimeMillis()
            val step = current?.currentStep ?: OnboardingStep.Review
            dao.upsert(
                OnboardingProgress(
                    currentStep = step,
                    startedAt = startedAt,
                    completedAt = System.currentTimeMillis(),
                    selectedPath = current?.selectedPath,
                ).toEntity(),
            )
            syncTouch.cancelDelete(
                SyncCollection.OnboardingProgress,
                OnboardingProgressId.Default.value,
            )
        }
    }

    override suspend fun reopenWizard() {
        syncTouch.mutate {
            val current = dao.getOrNull()?.toDomain()
            val startedAt = current?.startedAt ?: System.currentTimeMillis()
            // Return to Choose Path; clear durable path so Review cannot mis-route.
            dao.upsert(
                OnboardingProgress(
                    currentStep = OnboardingStep.ChoosePath,
                    startedAt = startedAt,
                    completedAt = null,
                    selectedPath = null,
                ).toEntity(),
            )
            syncTouch.cancelDelete(
                SyncCollection.OnboardingProgress,
                OnboardingProgressId.Default.value,
            )
        }
    }
}
