package com.stratum.domain.program.generation.prescription

import com.stratum.domain.lift.KinematicProfile
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
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
                movementPattern = MovementPattern.HipHinge,
                movementPatterns = setOf(MovementPattern.HipHinge),
                primaryMuscles = setOf(MuscleGroup.LowerBack),
                secondaryMuscles = setOf(MuscleGroup.Back, MuscleGroup.Quad, MuscleGroup.Hamstring, MuscleGroup.Glute),
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
                movementPattern = MovementPattern.HorizontalPress,
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
                movementPattern = MovementPattern.ElbowFlexion,
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
                movementPattern = MovementPattern.KneeExtension,
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
            movementPattern = MovementPattern.HorizontalPull,
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
                movementPattern = MovementPattern.SquatPattern,
                liftName = "Smith Machine Squat",
            ),
        )
    }

    // --- KinematicProfile → LiftStressClass mapping (Bug #2 refactor) ---

    @Test
    @DisplayName("kinematicProfileToStressClass maps AxialCompound to HeavyAxial")
    fun axialCompoundMapsToHeavyAxial() {
        assertEquals(LiftStressClass.HeavyAxial, kinematicProfileToStressClass(KinematicProfile.AxialCompound))
    }

    @Test
    @DisplayName("kinematicProfileToStressClass maps NonAxialCompound to UpperCompound")
    fun nonAxialCompoundMapsToUpperCompound() {
        assertEquals(LiftStressClass.UpperCompound, kinematicProfileToStressClass(KinematicProfile.NonAxialCompound))
    }

    @Test
    @DisplayName("kinematicProfileToStressClass maps Isolation to Isolation")
    fun isolationMapsToIsolation() {
        assertEquals(LiftStressClass.Isolation, kinematicProfileToStressClass(KinematicProfile.Isolation))
    }

    @Test
    @DisplayName("kinematicProfileToStressClass defaults unknown string to Isolation")
    fun unknownProfileDefaultsToIsolation() {
        // The mapping is now a closed enum (KinematicProfile); the only non-Axial/NonAxial value
        // (Isolation) is the default fallback, preserving the original "unknown → Isolation" intent.
        assertEquals(LiftStressClass.Isolation, kinematicProfileToStressClass(KinematicProfile.Isolation))
    }

    @Test
    @DisplayName("classifyLiftStressClass(LiftContext) uses kinematicProfile, not pattern/name heuristic")
    fun classifyLiftStressClassByLiftContextUsesKinematicProfile() {
        // Leg press is SquatPattern but NonAxialCompound — must classify as UpperCompound, not HeavyAxial.
        val legPress = LiftContext(
            id = "sled-leg-press",
            name = "Sled Leg Press",
            movementPattern = MovementPattern.SquatPattern,
            movementPatterns = setOf(MovementPattern.SquatPattern),
            primaryMuscles = setOf(MuscleGroup.Quad),
            secondaryMuscles = setOf(MuscleGroup.Calf, MuscleGroup.Glute),
            proven = true,
            kinematicProfile = KinematicProfile.NonAxialCompound,
        )
        assertEquals(LiftStressClass.UpperCompound, classifyLiftStressClass(legPress))
    }

    @Test
    @DisplayName("classifyLiftStressClass(LiftContext) maps AxialCompound barbell squat to HeavyAxial")
    fun classifyLiftStressClassByLiftContextAxialSquat() {
        val backSquat = LiftContext(
            id = "barbell-back-squat",
            name = "Barbell Back Squat",
            movementPattern = MovementPattern.SquatPattern,
            movementPatterns = setOf(MovementPattern.SquatPattern),
            primaryMuscles = setOf(MuscleGroup.Quad),
            secondaryMuscles = setOf(MuscleGroup.Calf, MuscleGroup.Glute),
            proven = true,
            kinematicProfile = KinematicProfile.AxialCompound,
        )
        assertEquals(LiftStressClass.HeavyAxial, classifyLiftStressClass(backSquat))
    }

    @Test
    @DisplayName("classifyLiftStressClass(LiftContext) maps Isolation leg extension to Isolation")
    fun classifyLiftStressClassByLiftContextIsolation() {
        val legExtension = LiftContext(
            id = "machine-leg-extension",
            name = "Machine Leg Extension",
            movementPattern = MovementPattern.KneeExtension,
            movementPatterns = setOf(MovementPattern.KneeExtension),
            primaryMuscles = setOf(MuscleGroup.Quad),
            secondaryMuscles = emptySet(),
            proven = true,
            kinematicProfile = KinematicProfile.Isolation,
        )
        assertEquals(LiftStressClass.Isolation, classifyLiftStressClass(legExtension))
    }

    @Test
    @DisplayName("classifyLiftStressClass(LiftContext) defaults to Isolation when kinematicProfile is unset")
    fun classifyLiftStressClassByLiftContextDefaultsToIsolation() {
        // Legacy LiftContext without kinematicProfile (defaults to "Isolation")
        val legacyLift = LiftContext(
            id = "some-lift",
            name = "Some Lift",
            movementPattern = MovementPattern.SquatPattern,
            movementPatterns = setOf(MovementPattern.SquatPattern),
            primaryMuscles = setOf(MuscleGroup.Quad),
            secondaryMuscles = emptySet(),
            proven = true,
        )
        assertEquals(LiftStressClass.Isolation, classifyLiftStressClass(legacyLift))
    }
}
