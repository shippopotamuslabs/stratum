package com.stratum.core.data.sync
import com.stratum.core.infrastructure.sync.SyncFlags

import com.stratum.core.infrastructure.mapper.toEntity
import com.stratum.domain.onboarding.OnboardingProgress
import com.stratum.domain.onboarding.OnboardingStep
import com.stratum.domain.onboarding.UserTrainingProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncFlagsTest {
    @Test
    fun `local profile write marks pending upsert`() {
        val entity = UserTrainingProfile.Empty.toEntity(nowMillis = 1_700_000_000_000L)
        assertEquals(SyncFlags.PENDING_UPSERT, entity.pendingSync)
        assertEquals(1_700_000_000_000L, entity.updatedAtEpochMillis)
        assertEquals("profile", entity.id.value)
    }

    @Test
    fun `local onboarding write marks pending upsert`() {
        val entity =
            OnboardingProgress(
                currentStep = OnboardingStep.Profile,
                startedAt = 10L,
                completedAt = null,
            ).toEntity(nowMillis = 99L)
        assertEquals(SyncFlags.PENDING_UPSERT, entity.pendingSync)
        assertEquals(99L, entity.updatedAtEpochMillis)
        assertTrue(entity.id.value.isNotBlank())
    }
}
