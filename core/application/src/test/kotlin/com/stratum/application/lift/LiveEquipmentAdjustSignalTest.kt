package com.stratum.application.lift

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
import com.stratum.domain.lift.EffectiveSteps
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.residual
import com.stratum.domain.lift.shouldShowLiveEquipmentAdjust

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
        enabled = true,
        loadType = loadType,
    )

    private val pinTiers = PinAdderCatalogFixtures.pinTiers()
    private val pinMicro = PinAdderCatalogFixtures.pinMicro()

    private fun weights(vararg amounts: Double) = amounts.map { Weight(it, lb) }

    /** Lift-form convenience: the signal over the lift's own effective steps. */
    private fun signal(weights: List<Weight>, lift: Lift) =
        shouldShowLiveEquipmentAdjust(weights, EffectiveSteps.from(lift), lift.loadType)

    @Test
    @DisplayName("Given an off-lattice logged weight when checked then the chip shows")
    fun offLatticeShowsChip() {
        assertTrue(
            signal(
                weights(60.0, 62.5),
                lift(),
            ),
        )
    }

    @Test
    @DisplayName("Given a single off-lattice set with no peers when checked then the chip shows")
    fun singleOffLatticeSetShowsChip() {
        assertTrue(signal(weights(62.5), lift()))
    }

    @Test
    @DisplayName("Given on-lattice logged weights when checked then the chip stays hidden")
    fun onLatticeHidesChip() {
        assertFalse(
            signal(
                weights(60.0, 65.0),
                lift(),
            ),
        )
    }

    @Test
    @DisplayName("Given no logged sets when checked then the chip stays hidden")
    fun noLoggedSetsHidesChip() {
        assertFalse(signal(emptyList(), lift()))
    }

    @Test
    @DisplayName("Given only near-zero weights when checked then the chip stays hidden")
    fun zeroWeightsHideChip() {
        assertFalse(signal(weights(0.0, 0.0), lift()))
    }

    @Test
    @DisplayName("Given a bodyweight lift when checked then the chip stays hidden")
    fun bodyweightHidesChip() {
        assertFalse(
            signal(
                weights(62.5),
                lift(loadType = LoadType.Bodyweight),
            ),
        )
    }

    @Test
    @DisplayName("Given empty tiers when checked then the chip stays hidden")
    fun emptyTiersHideChip() {
        assertFalse(
            signal(
                weights(62.5),
                lift(tiers = emptyList()),
            ),
        )
    }

    @Test
    @DisplayName("Given a typo-magnitude outlier among on-lattice peers when checked then it is ignored")
    fun typoOutlierIgnored() {
        assertFalse(
            signal(
                weights(60.0, 65.0, 625.0),
                lift(),
            ),
        )
    }

    @Test
    @DisplayName("Given an off-lattice assisted load when checked then magnitude decides")
    fun assistedOffLatticeShowsChip() {
        assertTrue(
            signal(
                weights(-62.5),
                lift(loadType = LoadType.Assisted),
            ),
        )
    }

    @Test
    @DisplayName("Given an on-lattice assisted load when checked then the chip stays hidden")
    fun assistedOnLatticeHidesChip() {
        assertFalse(
            signal(
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
            val live = signal(weights(amounts), catalogLift)
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
    @DisplayName("Given explicit steps when checked then the steps lattice decides, not the lift's")
    fun explicitStepsDecide() {
        // STR-128: the swapped-to equipment's steps replace the lift's own lattice.
        val swappedSteps =
            EffectiveSteps(
                tiers = listOf(IncrementTier(Weight(0.0, lb), null, Weight(2.5, lb))),
                microIncrements = emptyList(),
            )
        // 62.5 is off the lift's open 5-lb lattice but on the swapped 2.5-lb one.
        assertTrue(signal(weights(62.5), lift()))
        assertFalse(
            shouldShowLiveEquipmentAdjust(
                loggedWeights = weights(62.5),
                steps = swappedSteps,
                loadType = LoadType.Weighted,
            ),
        )
        // And the reverse: on the lift's lattice, off the swapped one.
        val tenSteps =
            EffectiveSteps(
                tiers = listOf(IncrementTier(Weight(0.0, lb), null, Weight(10.0, lb))),
                microIncrements = emptyList(),
            )
        assertFalse(signal(weights(65.0), lift()))
        assertTrue(
            shouldShowLiveEquipmentAdjust(
                loggedWeights = weights(65.0),
                steps = tenSteps,
                loadType = LoadType.Weighted,
            ),
        )
    }

    @Test
    @DisplayName("Given adder-exact logged weights when checked then the chip stays hidden")
    fun adderExactHidesChip() {
        assertFalse(
            signal(
                weights(65.0, 70.0),
                lift(tiers = pinTiers, micro = pinMicro),
            ),
        )
    }

    @Test
    @DisplayName("Given true residual alongside adder use when checked then the chip shows")
    fun trueResidualWithAdderShowsChip() {
        assertTrue(
            signal(
                weights(65.0, 62.0),
                lift(tiers = pinTiers, micro = pinMicro),
            ),
        )
    }

    private fun weights(amounts: List<Double>) = amounts.map { Weight(it, lb) }
}
