package com.stratum.domain.lift

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Pins friendlier [MovementPattern.displayName] copy used by create-lift, lift info, and
 * program display-name fallbacks.
 */
class MovementPatternDisplayNameTest {

    @Test
    @DisplayName("lower-body patterns use short gym-friendly labels")
    fun lowerBodyLabels() {
        assertEquals("Squat", MovementPattern.SquatPattern.displayName)
        assertEquals("Hinge", MovementPattern.HipHinge.displayName)
        assertEquals("Leg extension", MovementPattern.KneeExtension.displayName)
        assertEquals("Leg curl", MovementPattern.KneeFlexion.displayName)
        assertEquals("Calf raise", MovementPattern.PlantarFlexion.displayName)
        assertEquals("Hip thrust", MovementPattern.HipExtension.displayName)
        assertEquals("Hip abduction", MovementPattern.HipAbduction.displayName)
        assertEquals("Hip adduction", MovementPattern.HipAdduction.displayName)
    }

    @Test
    @DisplayName("upper-body compound patterns use short gym-friendly labels")
    fun upperCompoundLabels() {
        assertEquals("Horizontal push", MovementPattern.HorizontalPress.displayName)
        assertEquals("Incline push", MovementPattern.InclinePress.displayName)
        assertEquals("Vertical push", MovementPattern.VerticalPress.displayName)
        assertEquals("Horizontal pull", MovementPattern.HorizontalPull.displayName)
        assertEquals("Vertical pull", MovementPattern.VerticalPull.displayName)
        assertEquals("Chest fly", MovementPattern.ShoulderAdduction.displayName)
    }

    @Test
    @DisplayName("isolation patterns use short gym-friendly labels")
    fun isolationLabels() {
        assertEquals("Triceps extension", MovementPattern.ElbowExtension.displayName)
        assertEquals("Biceps curl", MovementPattern.ElbowFlexion.displayName)
        assertEquals("Lateral raise", MovementPattern.ShoulderAbduction.displayName)
        assertEquals("Rear delt", MovementPattern.ShoulderHorizontalAbduction.displayName)
        assertEquals("Front raise", MovementPattern.ShoulderFlexion.displayName)
        assertEquals("Forearm / wrist", MovementPattern.WristFlexionExtension.displayName)
        assertEquals("Shrug", MovementPattern.ScapularElevation.displayName)
        assertEquals("Core / abs", MovementPattern.SpinalFlexion.displayName)
    }

    @Test
    @DisplayName("every MovementPattern has a non-blank displayName")
    fun everyPatternHasLabel() {
        MovementPattern.entries.forEach { pattern ->
            assertTrue(pattern.displayName.isNotBlank(), pattern.name)
        }
    }
}
