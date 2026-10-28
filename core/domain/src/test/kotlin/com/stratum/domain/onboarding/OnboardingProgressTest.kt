package com.stratum.domain.onboarding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class OnboardingProgressTest {

    @Test
    @DisplayName("completion before the start timestamp is unrepresentable")
    fun completionBeforeStartRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            OnboardingProgress(OnboardingStep.Review, startedAt = 100L, completedAt = 99L)
        }
    }

    @Test
    @DisplayName("advancedTo moves the step, clears completion, and keeps the path by default")
    fun advancedToTransition() {
        val progress = OnboardingProgress(
            currentStep = OnboardingStep.Review,
            startedAt = 100L,
            completedAt = 200L,
            selectedPath = OnboardingPath.Generate,
        )
        val moved = progress.advancedTo(OnboardingStep.Schedule)
        assertEquals(OnboardingStep.Schedule, moved.currentStep)
        assertNull(moved.completedAt)
        assertFalse(moved.isComplete)
        assertEquals(OnboardingPath.Generate, moved.selectedPath)
        assertEquals(
            OnboardingPath.Import,
            progress.advancedTo(OnboardingStep.Review, OnboardingPath.Import).selectedPath,
        )
        // An explicit null also keeps the path; only reopened() clears it.
        assertEquals(
            OnboardingPath.Generate,
            progress.advancedTo(OnboardingStep.Schedule, null).selectedPath,
        )
    }

    @Test
    @DisplayName("completed clamps a regressed wall clock up to startedAt")
    fun completedClampsRegressedClock() {
        val progress = OnboardingProgress.started(OnboardingStep.Review, startedAtMillis = 100L)
        assertEquals(100L, progress.completed(50L).completedAt)
    }

    @Test
    @DisplayName("completed marks completion at the given instant, keeping step and path")
    fun completedTransition() {
        val progress = OnboardingProgress.started(OnboardingStep.Review, startedAtMillis = 100L)
        val done = progress.completed(250L)
        assertTrue(done.isComplete)
        assertEquals(250L, done.completedAt)
        assertEquals(OnboardingStep.Review, done.currentStep)
    }

    @Test
    @DisplayName("reopened returns to ChoosePath with completion and path cleared")
    fun reopenedTransition() {
        val progress = OnboardingProgress(
            currentStep = OnboardingStep.Review,
            startedAt = 100L,
            completedAt = 200L,
            selectedPath = OnboardingPath.Import,
        )
        val reopened = progress.reopened()
        assertEquals(OnboardingStep.ChoosePath, reopened.currentStep)
        assertNull(reopened.completedAt)
        assertNull(reopened.selectedPath)
        assertEquals(100L, reopened.startedAt)
    }
}
