package com.stratum.core.data.mapper

import com.stratum.core.data.db.IncrementTierEntity
import com.stratum.core.data.db.LiftEntity
import com.stratum.core.data.db.LiftWithTiers
import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class LiftMapperTest {

    @Test
    @DisplayName("Given a lift with muscles + presets when mapped to entity and back then it round-trips")
    fun roundTripsMusclesAndPresets() {
        val lift = Lift(
            id = LiftId("bench-press"),
            name = "Bench Press",
            movementPattern = MovementPattern.HorizontalPress,
            primaryMuscles = setOf(MuscleGroup.Chest),
            secondaryMuscles = setOf(MuscleGroup.Tricep, MuscleGroup.AnteriorDeltoid),
            allowWeightChangeBetweenSets = false,
            incrementTiers = emptyList(),
            presetTags = setOf(GymPreset.CommercialGym, GymPreset.GarageGym),
            enabled = true,
            isCustom = false,
        )

        val entity = lift.toEntity()
        val back = LiftWithTiers(entity, emptyList()).toDomain()

        assertEquals(lift.copy(incrementTiers = back.incrementTiers), back)
        assertEquals(setOf(MuscleGroup.Chest), back.primaryMuscles)
        assertEquals(setOf(MuscleGroup.Tricep, MuscleGroup.AnteriorDeltoid), back.secondaryMuscles)
        assertEquals(setOf(GymPreset.CommercialGym, GymPreset.GarageGym), back.presetTags)
    }

    @Test
    @DisplayName("Given tiers when mapped to entities and back then they round-trip sorted")
    fun roundTripsTiers() {
        val lb = Weight.WeightUnit.Lb
        val lift = Lift(
            id = LiftId("squat"),
            name = "Back Squat",
            movementPattern = MovementPattern.SquatPattern,
            primaryMuscles = setOf(MuscleGroup.Quad),
            secondaryMuscles = emptySet(),
            allowWeightChangeBetweenSets = false,
            incrementTiers = listOf(
                com.stratum.domain.lift.IncrementTier(Weight(0.0, lb), Weight(40.0, lb), Weight(2.5, lb)),
                com.stratum.domain.lift.IncrementTier(Weight(40.0, lb), Weight(100.0, lb), Weight(5.0, lb)),
                com.stratum.domain.lift.IncrementTier(Weight(100.0, lb), null, Weight(10.0, lb)),
            ),
            presetTags = emptySet(),
            enabled = false,
            isCustom = false,
        )

        val entities = lift.toTierEntities()
        // Persist out-of-order to prove the decoder sorts by lowerBound.
        val shuffled = listOf(entities[2], entities[0], entities[1])
        val back = LiftWithTiers(
            lift = lift.toEntity(),
            tiers = shuffled,
        ).toDomain()

        assertEquals(listOf(0.0, 40.0, 100.0), back.incrementTiers.map { it.lowerBound.amount })
        assertEquals(null, back.incrementTiers.last().upperBound?.amount)
    }

    @Test
    @DisplayName("Given a typed id when mapped then it passes through unchanged (no .value unwrap)")
    fun typedIdPassesThrough() {
        val lift = Lift(
            id = LiftId("leg-press"),
            name = "Leg Press",
            movementPattern = MovementPattern.SquatPattern,
            primaryMuscles = setOf(MuscleGroup.Quad),
            secondaryMuscles = emptySet(),
            allowWeightChangeBetweenSets = true,
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = false,
            isCustom = false,
        )
        val entity = lift.toEntity()
        assertEquals(LiftId("leg-press"), entity.id)
        assertEquals(LiftId("leg-press"), LiftWithTiers(entity, emptyList()).toDomain().id)
    }

    @Test
    @DisplayName("Given an empty preset set when encoded then the csv is the bare delimiters")
    fun emptyPresetsEncodeToBareDelimiters() {
        val lift = Lift(
            id = LiftId("x"),
            name = "X",
            movementPattern = MovementPattern.SquatPattern,
            primaryMuscles = emptySet(),
            secondaryMuscles = emptySet(),
            allowWeightChangeBetweenSets = false,
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = false,
        )
        assertEquals(",", lift.toEntity().presetTags)
    }

    @Test
    @DisplayName("Given a lift with equipment when mapped to entity and back then equipment round-trips")
    fun roundTripsEquipment() {
        val lift = Lift(
            id = LiftId("chest-press"),
            name = "Machine Chest Press",
            movementPattern = MovementPattern.HorizontalPress,
            primaryMuscles = setOf(MuscleGroup.Chest),
            secondaryMuscles = emptySet(),
            allowWeightChangeBetweenSets = true,
            incrementTiers = emptyList(),
            presetTags = setOf(GymPreset.CommercialGym),
            equipment = "Hammer Strength",
            enabled = true,
            isCustom = false,
        )

        val entity = lift.toEntity()
        assertEquals("Hammer Strength", entity.equipment)
        val back = LiftWithTiers(entity, emptyList()).toDomain()
        assertEquals("Hammer Strength", back.equipment)
    }

    @Test
    @DisplayName("Given a lift with null equipment when mapped to entity then equipment is null")
    fun nullEquipmentRoundTrips() {
        val lift = Lift(
            id = LiftId("squat"),
            name = "Back Squat",
            movementPattern = MovementPattern.SquatPattern,
            primaryMuscles = setOf(MuscleGroup.Quad),
            secondaryMuscles = emptySet(),
            allowWeightChangeBetweenSets = false,
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            equipment = null,
            enabled = false,
        )

        assertNull(lift.toEntity().equipment)
        val back = LiftWithTiers(lift.toEntity(), emptyList()).toDomain()
        assertNull(back.equipment)
    }

    @Test
    @DisplayName("Given a muscle bitmask when decoded then it returns the matching MuscleGroup set")
    fun bitmaskDecodesToMuscleSet() {
        val mask = (1 shl MuscleGroup.Chest.ordinal) or (1 shl MuscleGroup.Tricep.ordinal)
        assertEquals(setOf(MuscleGroup.Chest, MuscleGroup.Tricep), mask.toMuscleSet())
    }

    @Test
    @DisplayName("Given a preset csv with an unknown name when decoded then unknown entries are dropped")
    fun unknownPresetsDropped() {
        assertEquals(setOf(GymPreset.CommercialGym), ",CommercialGym,Bogus,".toPresetSet())
    }
}
