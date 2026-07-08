package com.stratum.domain.onboarding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OnboardingStepTest {

    @Test
    fun `next on common prefix with null path walks ordinally`() {
        assertEquals(OnboardingStep.Profile, OnboardingStep.Welcome.next())
        assertEquals(OnboardingStep.GymAndLifts, OnboardingStep.Profile.next())
        assertEquals(OnboardingStep.ChoosePath, OnboardingStep.GymAndLifts.next())
    }

    @Test
    fun `next on ChoosePath with Import path goes to Review`() {
        assertEquals(OnboardingStep.Review, OnboardingStep.ChoosePath.next(OnboardingPath.Import))
    }

    @Test
    fun `next on ChoosePath with Generate path goes to Schedule`() {
        assertEquals(OnboardingStep.Schedule, OnboardingStep.ChoosePath.next(OnboardingPath.Generate))
    }

    @Test
    fun `next on ChoosePath with null path returns null`() {
        assertNull(OnboardingStep.ChoosePath.next(null))
    }

    @Test
    fun `next on Review returns null (terminal)`() {
        assertNull(OnboardingStep.Review.next(OnboardingPath.Generate))
        assertNull(OnboardingStep.Review.next(OnboardingPath.Import))
    }

    @Test
    fun `previous on common prefix walks ordinally`() {
        assertEquals(OnboardingStep.Profile, OnboardingStep.GymAndLifts.previous())
        assertEquals(OnboardingStep.Welcome, OnboardingStep.Profile.previous())
    }

    @Test
    fun `previous on Review with Import path goes to ChoosePath`() {
        assertEquals(OnboardingStep.ChoosePath, OnboardingStep.Review.previous(OnboardingPath.Import))
    }

    @Test
    fun `previous on Review with Generate path goes to MinimumVolume`() {
        assertEquals(OnboardingStep.MinimumVolume, OnboardingStep.Review.previous(OnboardingPath.Generate))
    }

    @Test
    fun `previous on Schedule goes to ChoosePath`() {
        assertEquals(OnboardingStep.ChoosePath, OnboardingStep.Schedule.previous(OnboardingPath.Generate))
    }

    @Test
    fun `previous on Welcome returns null`() {
        assertNull(OnboardingStep.Welcome.previous())
    }

    @Test
    fun `isLast is Review`() {
        assertEquals(OnboardingStep.Review, OnboardingStep.entries.last { it.isLast })
    }

    @Test
    fun `isFirst is Welcome`() {
        assertEquals(OnboardingStep.Welcome, OnboardingStep.entries.last { it.isFirst })
    }
}
