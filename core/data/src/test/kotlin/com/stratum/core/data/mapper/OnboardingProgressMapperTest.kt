package com.stratum.core.data.mapper

import com.stratum.domain.onboarding.OnboardingProgress
import com.stratum.domain.onboarding.OnboardingStep
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class OnboardingProgressMapperTest {

    @Test
    @DisplayName("Given in-progress onboarding when mapped then it round-trips")
    fun inProgressRoundTrips() {
        val progress = OnboardingProgress(
            currentStep = OnboardingStep.Profile,
            startedAt = 100L,
            completedAt = null,
        )

        val roundTripped = progress.toEntity().toDomain()

        assertEquals(progress, roundTripped)
        assertNull(roundTripped.completedAt)
    }

    @Test
    @DisplayName("Given completed onboarding when mapped then it round-trips")
    fun completedRoundTrips() {
        val progress = OnboardingProgress(
            currentStep = OnboardingStep.Review,
            startedAt = 100L,
            completedAt = 200L,
        )

        assertEquals(progress, progress.toEntity().toDomain())
    }

    @Test
    @DisplayName("Given a legacy row completed before it started when mapped then completion clamps to the start")
    fun legacyRegressedCompletionHeals() {
        val entity = OnboardingProgress(
            currentStep = OnboardingStep.Review,
            startedAt = 200L,
            completedAt = 200L,
        ).toEntity().copy(completedAt = 150L)

        assertEquals(200L, entity.toDomain().completedAt)
    }
}
