package com.stratum.domain.lift

import com.stratum.core.testing.lift.PinAdderCatalogFixtures
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("shouldShowLiveEquipmentAdjust")
class LiveEquipmentAdjustSignalTest {

    private val lb = Weight.WeightUnit.Lb
    private val openFive =
        listOf(IncrementTier(Weight(0.0, lb), null, Weight(5.0, lb)))

    private fun lift(
        tiers: List<IncrementTier> = openFive,
        loadType: LoadType = LoadType.Weighted,
        micro: List<Weight> = emptyList(),
    ) = Lift(
        id = LiftId("lat-pulldown"),
        name = "Lat pulldown",
        movementPatterns = setOf(MovementPattern.VerticalPull),
        primaryMuscles = setOf(MuscleGroup.Back),
        secondaryMuscles = emptySet(),
        incrementTiers = tiers,
        presetTags = emptySet(),
        microIncrements = micro,
        usesEquipmentDefaultMicro = false,
        enabled = true,
        loadType = loadType,
    )

    private val pinTiers = PinAdderCatalogFixtures.pinTiers()
    private val pinMicro = PinAdderCatalogFixtures.pinMicro()

    private fun weights(vararg amounts: Double) = amounts.map { Weight(it, lb) }

    @Test
    @DisplayName("Given an off-lattice logged weight when checked then the chip shows")
    fun offLatticeShowsChip() {
        assertTrue(
            shouldShowLiveEquipmentAdjust(
                weights(60.0, 62.5),
                lift(),
            ),
        )
    }

    @Test
    @DisplayName("Given a single off-lattice set with no peers when checked then the chip shows")
    fun singleOffLatticeSetShowsChip() {
        assertTrue(shouldShowLiveEquipmentAdjust(weights(62.5), lift()))
    }

    @Test
    @DisplayName("Given on-lattice logged weights when checked then the chip stays hidden")
    fun onLatticeHidesChip() {
        assertFalse(
            shouldShowLiveEquipmentAdjust(
                weights(60.0, 65.0),
                lift(),
            ),
        )
    }

    @Test
    @DisplayName("Given no logged sets when checked then the chip stays hidden")
    fun noLoggedSetsHidesChip() {
        assertFalse(shouldShowLiveEquipmentAdjust(emptyList(), lift()))
    }

    @Test
    @DisplayName("Given only near-zero weights when checked then the chip stays hidden")
    fun zeroWeightsHideChip() {
        assertFalse(shouldShowLiveEquipmentAdjust(weights(0.0, 0.0), lift()))
    }

    @Test
    @DisplayName("Given a bodyweight lift when checked then the chip stays hidden")
    fun bodyweightHidesChip() {
        assertFalse(
            shouldShowLiveEquipmentAdjust(
                weights(62.5),
                lift(loadType = LoadType.Bodyweight),
            ),
        )
    }

    @Test
    @DisplayName("Given empty tiers when checked then the chip stays hidden")
    fun emptyTiersHideChip() {
        assertFalse(
            shouldShowLiveEquipmentAdjust(
                weights(62.5),
                lift(tiers = emptyList()),
            ),
        )
    }

    @Test
    @DisplayName("Given a typo-magnitude outlier among on-lattice peers when checked then it is ignored")
    fun typoOutlierIgnored() {
        assertFalse(
            shouldShowLiveEquipmentAdjust(
                weights(60.0, 65.0, 625.0),
                lift(),
            ),
        )
    }

    @Test
    @DisplayName("Given an off-lattice assisted load when checked then magnitude decides")
    fun assistedOffLatticeShowsChip() {
        assertTrue(
            shouldShowLiveEquipmentAdjust(
                weights(-62.5),
                lift(loadType = LoadType.Assisted),
            ),
        )
    }

    @Test
    @DisplayName("Given an on-lattice assisted load when checked then the chip stays hidden")
    fun assistedOnLatticeHidesChip() {
        assertFalse(
            shouldShowLiveEquipmentAdjust(
                weights(-65.0),
                lift(loadType = LoadType.Assisted),
            ),
        )
    }

    @Test
    @DisplayName("Given session weights when checked then live and post-workout signals agree")
    fun liveAndPostWorkoutSignalsAgree() {
        val cases =
            listOf(
                listOf(60.0, 65.0) to false,
                listOf(62.5) to true,
                listOf(60.0, 65.0, 625.0) to false,
                listOf(0.0, 0.0) to false,
            )
        for ((amounts, expected) in cases) {
            val catalogLift = lift()
            val loggedSets =
                amounts.mapIndexed { index, amount ->
                    LoggedSet(
                        id = LoggedSetId("s-$index"),
                        sessionId = TrainingSessionId("ts-1"),
                        exerciseNameRaw = "Lat pulldown",
                        liftId = catalogLift.id,
                        setIndex = index,
                        weight = Weight(amount, lb),
                        reps = 10,
                        rir = 2,
                        completedAt = Instant.parse("2025-01-01T10:30:00Z"),
                        liftContext = WorkoutLoggingFixtures.importLiftContext("Lat pulldown"),
                    )
                }
            val live = shouldShowLiveEquipmentAdjust(weights(amounts), catalogLift)
            val postWorkout =
                anomalyForLift(
                    liftId = catalogLift.id,
                    liftSets = loggedSets,
                    liftsById = mapOf(catalogLift.id to catalogLift),
                ) != null
            assertEquals(expected, live, "live signal for $amounts")
            assertEquals(expected, postWorkout, "post-workout signal for $amounts")
        }
    }

    @Test
    @DisplayName("Given adder-exact logged weights when checked then the chip stays hidden")
    fun adderExactHidesChip() {
        assertFalse(
            shouldShowLiveEquipmentAdjust(
                weights(65.0, 70.0),
                lift(tiers = pinTiers, micro = pinMicro),
            ),
        )
    }

    @Test
    @DisplayName("Given true residual alongside adder use when checked then the chip shows")
    fun trueResidualWithAdderShowsChip() {
        assertTrue(
            shouldShowLiveEquipmentAdjust(
                weights(65.0, 62.0),
                lift(tiers = pinTiers, micro = pinMicro),
            ),
        )
    }

    private fun weights(amounts: List<Double>) = amounts.map { Weight(it, lb) }
}
