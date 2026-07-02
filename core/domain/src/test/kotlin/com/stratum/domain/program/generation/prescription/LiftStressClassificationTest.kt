package com.stratum.domain.program.generation.prescription

import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.reference.LiftStressClass
import com.stratum.domain.program.generation.reference.RpRepProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class LiftStressClassificationTest {

    @Test
    @DisplayName("deadlift classifies as heavy axial for LowerBack primary")
    fun deadliftClassifiesHeavyAxialForLowerBack() {
        val lift =
            LiftContext(
                id = "barbell-deadlift",
                name = "Barbell Deadlift",
                movementPattern = "HipHinge",
                movementPatterns = listOf("HipHinge"),
                primaryMuscles = listOf("LowerBack"),
                secondaryMuscles = listOf("Back", "Quad", "Hamstring", "Glute"),
                proven = true,
            )
        assertEquals(
            LiftStressClass.HeavyAxial,
            classifyLiftStressClass(
                repProfile = RpRepProfile.LowerFreeWeightCompound,
                movementPattern = lift.movementPattern,
                liftName = lift.name,
            ),
        )
    }

    @Test
    @DisplayName("bench press classifies as upper compound")
    fun benchPressClassifiesUpperCompound() {
        assertEquals(
            LiftStressClass.UpperCompound,
            classifyLiftStressClass(
                repProfile = RpRepProfile.LowerMachineOrUpperPress,
                movementPattern = "HorizontalPress",
                liftName = "Barbell Bench Press",
            ),
        )
    }

    @Test
    @DisplayName("curl classifies as isolation")
    fun curlClassifiesIsolation() {
        assertEquals(
            LiftStressClass.Isolation,
            classifyLiftStressClass(
                repProfile = RpRepProfile.Isolation,
                movementPattern = "ElbowFlexion",
                liftName = "Barbell Curl",
            ),
        )
    }

    @Test
    @DisplayName("leg extension classifies as isolation even when primary is Quad")
    fun legExtensionClassifiesIsolation() {
        assertEquals(
            LiftStressClass.Isolation,
            classifyLiftStressClass(
                repProfile = RpRepProfile.Isolation,
                movementPattern = "KneeExtension",
                liftName = "Leg Extension",
            ),
        )
    }

    @Test
    @DisplayName("dominant class escalates only from contributors with ≥1.0 credit")
    fun dominantClassIgnoresSubUnitContributors() {
        val dominant =
            dominantStressClass(
                listOf(
                    LiftStressClass.Isolation to 0.5,
                    LiftStressClass.UpperCompound to 4.0,
                ),
            )
        assertEquals(LiftStressClass.UpperCompound, dominant)
    }

    @Test
    @DisplayName("compound secondaries price standard isolation recovery")
    fun compoundSecondariesUseIsolationRecoveryClass() {
        assertEquals(
            LiftStressClass.Isolation,
            recoveryStressClassForContributor(LiftStressClass.HeavyAxial, isPrimaryContributor = false),
        )
        assertEquals(
            LiftStressClass.HeavyAxial,
            recoveryStressClassForContributor(LiftStressClass.HeavyAxial, isPrimaryContributor = true),
        )
        assertEquals(
            LiftStressClass.Isolation,
            recoveryStressClassForContributor(LiftStressClass.UpperCompound, isPrimaryContributor = false),
        )
        assertEquals(
            LiftStressClass.UpperCompound,
            recoveryStressClassForContributor(LiftStressClass.UpperCompound, isPrimaryContributor = true),
        )
    }

    @Test
    @DisplayName("row bicep secondary stays isolation; back primary stays upper compound")
    fun rowBicepSecondaryDominantClassIsIsolation() {
        val rowClass = classifyLiftStressClass(
            repProfile = RpRepProfile.LowerMachineOrUpperPress,
            movementPattern = "HorizontalPull",
            liftName = "Barbell Row",
        )
        assertEquals(LiftStressClass.UpperCompound, rowClass)
        val dominant =
            dominantStressClass(
                listOf(
                    recoveryStressClassForContributor(rowClass, isPrimaryContributor = true) to 4.0,
                    recoveryStressClassForContributor(rowClass, isPrimaryContributor = false) to 2.0,
                ),
            )
        assertEquals(LiftStressClass.UpperCompound, dominant)
        val bicepDominant =
            dominantStressClass(
                listOf(
                    recoveryStressClassForContributor(rowClass, isPrimaryContributor = false) to 2.0,
                ),
            )
        assertEquals(LiftStressClass.Isolation, bicepDominant)
    }

    @Test
    @DisplayName("machine squat classifies as upper compound not heavy axial")
    fun machineSquatClassifiesUpperCompound() {
        assertEquals(
            LiftStressClass.UpperCompound,
            classifyLiftStressClass(
                repProfile = RpRepProfile.LowerMachineOrUpperPress,
                movementPattern = "SquatPattern",
                liftName = "Smith Machine Squat",
            ),
        )
    }
}
