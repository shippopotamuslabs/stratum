package com.stratum.core.data.mapper

import com.stratum.core.data.db.IncrementTierEntity
import com.stratum.core.data.db.LiftEntity
import com.stratum.core.data.db.LiftWithTiers
import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class LiftMapperTest {

    @Test
    @DisplayName("Given a lift with muscles + presets when mapped to entity and back then it round-trips")
    fun roundTripsMusclesAndPresets() {
        val lift = Lift(
            id = LiftId("bench-press"),
            name = "Bench Press",
            movementPatterns = setOf(MovementPattern.HorizontalPress),
            primaryMuscles = setOf(MuscleGroup.Chest),
            secondaryMuscles = setOf(MuscleGroup.Tricep, MuscleGroup.AnteriorDeltoid),
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
            movementPatterns = setOf(MovementPattern.SquatPattern),
            primaryMuscles = setOf(MuscleGroup.Quad),
            secondaryMuscles = emptySet(),
            incrementTiers = listOf(
                com.stratum.domain.lift.IncrementTier(Weight(0.0, lb), Weight(40.0, lb), Weight(2.5, lb)),
                com.stratum.domain.lift.IncrementTier(Weight(40.0, lb), Weight(100.0, lb), Weight(5.0, lb)),
                com.stratum.domain.lift.IncrementTier(Weight(100.0, lb), null, Weight(10.0, lb)),
            ),
            presetTags = emptySet(),
            enabled = false,
            isCustom = false,
        )

        val overrideLift = lift.copy(usesEquipmentDefaultIncrements = false)
        val entities = overrideLift.toLiftOverrideTiers()
        // Persist out-of-order to prove the decoder sorts by lowerBound.
        val shuffled = listOf(entities[2], entities[0], entities[1])
        val back = LiftWithTiers(
            lift = overrideLift.toEntity(),
            tiers = shuffled,
        ).toDomain()

        assertEquals(listOf(0.0, 40.0, 100.0), back.incrementTiers.map { it.lowerBound.amount })
        assertEquals(null, back.incrementTiers.last().upperBound?.amount)
        assertFalse(back.usesEquipmentDefaultIncrements)
    }

    @Test
    @DisplayName("Given a typed id when mapped then it passes through unchanged (no .value unwrap)")
    fun typedIdPassesThrough() {
        val lift = Lift(
            id = LiftId("leg-press"),
            name = "Leg Press",
            movementPatterns = setOf(MovementPattern.SquatPattern),
            primaryMuscles = setOf(MuscleGroup.Quad),
            secondaryMuscles = emptySet(),
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
            movementPatterns = setOf(MovementPattern.SquatPattern),
            primaryMuscles = emptySet(),
            secondaryMuscles = emptySet(),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = false,
        )
        assertEquals(",", lift.toEntity().presetTags)
    }

    @Test
    @DisplayName("Given a lift with equipmentId when mapped to entity and back then equipmentId round-trips")
    fun roundTripsEquipmentId() {
        val equipmentId = EquipmentId("pin-loaded-machine")
        val lift = Lift(
            id = LiftId("chest-press"),
            name = "Machine Chest Press",
            movementPatterns = setOf(MovementPattern.HorizontalPress),
            primaryMuscles = setOf(MuscleGroup.Chest),
            secondaryMuscles = emptySet(),
            incrementTiers = emptyList(),
            presetTags = setOf(GymPreset.CommercialGym),
            equipmentId = equipmentId,
            enabled = true,
            isCustom = false,
        )

        val entity = lift.toEntity()
        assertEquals(equipmentId, entity.equipmentId)
        val equipment =
            com.stratum.domain.lift.Equipment(
                id = equipmentId,
                name = "Pin-Loaded Machine",
                incrementTiers = emptyList(),
            )
        val back =
            LiftWithTiers(entity, emptyList()).toDomain(
                equipmentById = mapOf(equipmentId to equipment),
            )
        assertEquals(equipmentId, back.equipmentId)
        assertEquals("Pin-Loaded Machine", back.equipmentName)
        assertTrue(back.usesEquipmentDefaultIncrements)
        // Empty equipment lattice falls through to global fallback — not "from equipment".
        assertFalse(back.inheritsFromEquipmentLattice)
    }

    @Test
    @DisplayName("Given a lift with null equipmentId when mapped to entity then equipmentId is null")
    fun nullEquipmentRoundTrips() {
        val lift = Lift(
            id = LiftId("squat"),
            name = "Back Squat",
            movementPatterns = setOf(MovementPattern.SquatPattern),
            primaryMuscles = setOf(MuscleGroup.Quad),
            secondaryMuscles = emptySet(),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            equipmentId = null,
            enabled = false,
        )

        assertNull(lift.toEntity().equipmentId)
        val back = LiftWithTiers(lift.toEntity(), emptyList()).toDomain()
        assertNull(back.equipmentId)
    }

    @Test
    @DisplayName("Given a movement-pattern set when encoded then the bitmask round-trips back to the same set")
    fun movementPatternBitmaskRoundTrips() {
        val patterns = setOf(MovementPattern.SquatPattern, MovementPattern.HipExtension)
        assertEquals(patterns, patterns.toBitmask().toMovementPatternSet())
    }

    @Test
    @DisplayName("Given an empty movement-pattern set when encoded then the bitmask is zero")
    fun emptyMovementPatternSetEncodesToZero() {
        assertEquals(0, emptySet<MovementPattern>().toBitmask())
        assertEquals(emptySet<MovementPattern>(), 0.toMovementPatternSet())
    }

    @Test
    @DisplayName("Given a movement-pattern set when mapped to entity and back then it round-trips")
    fun movementPatternsRoundTripThroughEntity() {
        val lift = Lift(
            id = LiftId("deadlift"),
            name = "Deadlift",
            movementPatterns = setOf(MovementPattern.HipHinge, MovementPattern.HipExtension),
            primaryMuscles = setOf(MuscleGroup.Hamstring),
            secondaryMuscles = emptySet(),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = false,
            isCustom = false,
        )
        val back = LiftWithTiers(lift.toEntity(), emptyList()).toDomain()
        assertEquals(lift.movementPatterns, back.movementPatterns)
    }

    @Test
    @DisplayName("Given a preset csv with an unknown name when decoded then unknown entries are dropped")
    fun unknownPresetsDropped() {
        assertEquals(setOf(GymPreset.CommercialGym), ",CommercialGym,Bogus,".toPresetSet())
    }

    @Test
    @DisplayName("Given an assisted lift when mapped to entity and back then loadType round-trips")
    fun roundTripsLoadTypeAssisted() {
        val lift = Lift(
            id = LiftId("assisted-pull-up"),
            name = "Assisted Pull-Up",
            movementPatterns = setOf(MovementPattern.VerticalPull),
            primaryMuscles = setOf(MuscleGroup.Back),
            secondaryMuscles = setOf(MuscleGroup.Bicep),
            incrementTiers = emptyList(),
            presetTags = setOf(GymPreset.CommercialGym),
            enabled = false,
            isCustom = false,
            loadType = LoadType.Assisted,
        )
        val entity = lift.toEntity()
        assertEquals(LoadType.Assisted, entity.loadType)
        val back = LiftWithTiers(entity, emptyList()).toDomain()
        assertEquals(LoadType.Assisted, back.loadType)
    }

    @Test
    @DisplayName("Given a bodyweight lift when mapped to entity and back then loadType round-trips")
    fun roundTripsLoadTypeBodyweight() {
        val lift = Lift(
            id = LiftId("pull-up"),
            name = "Pull-Up",
            movementPatterns = setOf(MovementPattern.VerticalPull),
            primaryMuscles = setOf(MuscleGroup.Back),
            secondaryMuscles = setOf(MuscleGroup.Bicep),
            incrementTiers = emptyList(),
            presetTags = setOf(GymPreset.CommercialGym),
            enabled = false,
            isCustom = false,
            loadType = LoadType.Bodyweight,
        )
        val back = LiftWithTiers(lift.toEntity(), emptyList()).toDomain()
        assertEquals(LoadType.Bodyweight, back.loadType)
    }

    @Test
    @DisplayName("Given a lift with default loadType when mapped to entity then loadType is Weighted")
    fun defaultLoadTypeIsWeighted() {
        val lift = Lift(
            id = LiftId("bench-press"),
            name = "Bench Press",
            movementPatterns = setOf(MovementPattern.HorizontalPress),
            primaryMuscles = setOf(MuscleGroup.Chest),
            secondaryMuscles = emptySet(),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = true,
            isCustom = false,
        )
        assertEquals(LoadType.Weighted, lift.toEntity().loadType)
    }
}
