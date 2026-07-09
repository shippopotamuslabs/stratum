package com.stratum.domain.onboarding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class MuscleGroupDisplayLabelTest {
    @Test
    @DisplayName("calf label is Calves, not PlantarFlexion or Calf")
    fun calfLabel() {
        assertEquals("Calves", MuscleGroup.Calf.displayLabel)
    }

    @Test
    @DisplayName("deltoid heads use human labels, not PascalCase enum names")
    fun deltoidLabels() {
        assertEquals("Side Delts", MuscleGroup.LateralDeltoid.displayLabel)
        assertEquals("Rear Delts", MuscleGroup.PosteriorDeltoid.displayLabel)
        assertEquals("Front Delts", MuscleGroup.AnteriorDeltoid.displayLabel)
    }

    @Test
    @DisplayName("no muscle displayLabel equals the raw enum name for multi-word groups")
    fun noRawEnumNamesForCompoundMembers() {
        val multiWord = listOf(
            MuscleGroup.LateralDeltoid,
            MuscleGroup.PosteriorDeltoid,
            MuscleGroup.AnteriorDeltoid,
            MuscleGroup.LowerBack,
        )
        multiWord.forEach { muscle ->
            assertFalse(
                muscle.displayLabel == muscle.name,
                "${muscle.name} should not surface as raw enum name",
            )
        }
    }

    @Test
    @DisplayName("every muscle has a non-blank displayLabel")
    fun allNonBlank() {
        MuscleGroup.entries.forEach { muscle ->
            assertFalse(muscle.displayLabel.isBlank(), muscle.name)
        }
    }
}
