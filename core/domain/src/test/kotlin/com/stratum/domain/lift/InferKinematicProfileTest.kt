package com.stratum.domain.lift

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class InferKinematicProfileTest {

    @Test
    @DisplayName("isolation patterns → Isolation")
    fun isolation() {
        assertEquals(
            KinematicProfile.Isolation,
            inferKinematicProfile(MovementPattern.ElbowExtension, "Skull Crusher"),
        )
        assertEquals(
            KinematicProfile.Isolation,
            inferKinematicProfile(MovementPattern.PlantarFlexion, "Calf Raise"),
        )
    }

    @Test
    @DisplayName("compound pull/press → NonAxialCompound")
    fun nonAxial() {
        assertEquals(
            KinematicProfile.NonAxialCompound,
            inferKinematicProfile(MovementPattern.VerticalPull, "Overhand Grip Cable Lat Pulldown"),
        )
        assertEquals(
            KinematicProfile.NonAxialCompound,
            inferKinematicProfile(MovementPattern.HorizontalPull, "Iso-Lateral High Row"),
        )
        assertEquals(
            KinematicProfile.NonAxialCompound,
            inferKinematicProfile(MovementPattern.HorizontalPress, "Machine Chest Press"),
        )
    }

    @Test
    @DisplayName("free-weight hinge/squat → AxialCompound")
    fun axial() {
        assertEquals(
            KinematicProfile.AxialCompound,
            inferKinematicProfile(MovementPattern.HipHinge, "Barbell Romanian Deadlift"),
        )
        assertEquals(
            KinematicProfile.AxialCompound,
            inferKinematicProfile(MovementPattern.SquatPattern, "Barbell Back Squat"),
        )
    }

    @Test
    @DisplayName("machine squat/hinge → NonAxialCompound")
    fun machineNotAxial() {
        assertEquals(
            KinematicProfile.NonAxialCompound,
            inferKinematicProfile(MovementPattern.SquatPattern, "Smith Machine Back Squat"),
        )
        assertEquals(
            KinematicProfile.NonAxialCompound,
            inferKinematicProfile(MovementPattern.HipHinge, "Weighted Roman Chair Hip Hinge"),
        )
    }
}
