package com.stratum.feature.onboarding.ui

import com.stratum.feature.onboarding.OnboardingValidationConstants
import com.stratum.feature.onboarding.SpecializationDraftUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests for the specialization screen's UI state ([SpecializationDraftUi]).
 * Verifies the validation and cap-checking logic that controls widget enabling/disabling
 * and screen validation status.
 */
class SpecializationScreenTest {

    @Test
    @DisplayName("Given an empty selection when checking atCap then it returns false")
    fun emptySelectionNotAtCap() {
        val draft = SpecializationDraftUi(selectedMuscleLabels = emptySet())
        assertFalse(draft.atCap)
    }

    @Test
    @DisplayName("Given selections below MAX when checking atCap then it returns false")
    fun selectionsBelowMaxNotAtCap() {
        val draft = SpecializationDraftUi(selectedMuscleLabels = setOf("Quads", "Chest"))
        assertFalse(draft.atCap)
    }

    @Test
    @DisplayName("Given selections at MAX when checking atCap then it returns true")
    fun selectionsAtMaxIsAtCap() {
        val draft = SpecializationDraftUi(
            selectedMuscleLabels = List(OnboardingValidationConstants.MAX_SPECIALIZE) { "Muscle$it" }.toSet()
        )
        assertTrue(draft.atCap)
    }

    @Test
    @DisplayName("Given selections above MAX when checking atCap then it returns true")
    fun selectionsAboveMaxIsAtCap() {
        val draft = SpecializationDraftUi(
            selectedMuscleLabels = List(OnboardingValidationConstants.MAX_SPECIALIZE + 1) { "Muscle$it" }.toSet()
        )
        assertTrue(draft.atCap)
    }

    @Test
    @DisplayName("Given an empty selection when validating draft then it is valid")
    fun emptySelectionIsValid() {
        val draft = SpecializationDraftUi(selectedMuscleLabels = emptySet())
        assertTrue(draft.isValid)
    }

    @Test
    @DisplayName("Given selections at MAX when validating draft then it is valid")
    fun selectionsAtMaxIsValid() {
        val draft = SpecializationDraftUi(
            selectedMuscleLabels = List(OnboardingValidationConstants.MAX_SPECIALIZE) { "Muscle$it" }.toSet()
        )
        assertTrue(draft.isValid)
    }

    @Test
    @DisplayName("Given selections above MAX when validating draft then it is invalid")
    fun selectionsAboveMaxIsInvalid() {
        val draft = SpecializationDraftUi(
            selectedMuscleLabels = List(OnboardingValidationConstants.MAX_SPECIALIZE + 1) { "Muscle$it" }.toSet()
        )
        assertFalse(draft.isValid)
    }

    @Test
    @DisplayName("Verify MAX_SPECIALIZE matches the domain validation rules constraint")
    fun maxSpecializeLimitConstraint() {
        assertEquals(3, OnboardingValidationConstants.MAX_SPECIALIZE)
    }
}
