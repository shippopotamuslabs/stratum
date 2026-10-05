package com.stratum.core.data.mapper

import com.stratum.core.data.db.LiftEntity
import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.WeightConversion
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
        val back = entity.toDomain()

        assertEquals(lift.copy(incrementTiers = back.incrementTiers), back)
        assertEquals(setOf(MuscleGroup.Chest), back.primaryMuscles)
        assertEquals(setOf(MuscleGroup.Tricep, MuscleGroup.AnteriorDeltoid), back.secondaryMuscles)
        assertEquals(setOf(GymPreset.CommercialGym, GymPreset.GarageGym), back.presetTags)
    }

    @Test
    @DisplayName("Given equipment tiers when mapped to entities and back then they round-trip sorted")
    fun roundTripsTiers() {
        val lb = Weight.WeightUnit.Lb
        val equipment =
            com.stratum.domain.lift.Equipment(
                id = com.stratum.domain.lift.identity.EquipmentId("stack"),
                name = "Stack",
                incrementTiers = listOf(
                    com.stratum.domain.lift.IncrementTier(Weight(0.0, lb), Weight(40.0, lb), Weight(2.5, lb)),
                    com.stratum.domain.lift.IncrementTier(Weight(40.0, lb), Weight(100.0, lb), Weight(5.0, lb)),
                    com.stratum.domain.lift.IncrementTier(Weight(100.0, lb), null, Weight(10.0, lb)),
                ),
            )

        val entities = equipment.toTierEntities()
        // Persist out-of-order to prove the decoder sorts by lowerBound.
        val shuffled = listOf(entities[2], entities[0], entities[1])
        val back = shuffled.toDomainTiers()

        assertEquals(listOf(0.0, 40.0, 100.0), back.map { it.lowerBound.amount })
        assertEquals(null, back.last().upperBound?.amount)
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
        assertEquals(LiftId("leg-press"), entity.toDomain().id)
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
            entity.toDomain(
                equipmentById = mapOf(equipmentId to equipment),
            )
        assertEquals(equipmentId, back.equipmentId)
        assertEquals("Pin-Loaded Machine", back.equipmentName)
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
        val back = lift.toEntity().toDomain()
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
        val back = lift.toEntity().toDomain()
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
        val back = entity.toDomain()
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
        val back = lift.toEntity().toDomain()
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

    @Test
    @DisplayName("Given a pinned note when mapped to entity and back then pinnedNote round-trips")
    fun roundTripsPinnedNote() {
        val lift = Lift(
            id = LiftId("squat"),
            name = "Squat",
            movementPatterns = setOf(MovementPattern.SquatPattern),
            primaryMuscles = setOf(MuscleGroup.Quad),
            secondaryMuscles = emptySet(),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = true,
            pinnedNote = "Belt on working sets",
        )
        val entity = lift.toEntity()
        assertEquals("Belt on working sets", entity.pinnedNote)
        val back = entity.toDomain()
        assertEquals("Belt on working sets", back.pinnedNote)
    }

    @Test
    @DisplayName("Given equipment micro json when mapped then stops decode and re-encode")
    fun equipmentMicroRoundTrips() {
        val lb = Weight.WeightUnit.Lb
        val equipment = Equipment(
            id = EquipmentId("pin-loaded-machine"),
            name = "Pin-Loaded Machine",
            incrementTiers = emptyList(),
            microIncrements = listOf(Weight(5.0, lb), Weight(10.0, lb)),
        )

        val entity = equipment.toEntity()
        assertEquals("[5.0,10.0]", entity.microOptionsJson)

        val back = entity.toDomain(emptyList())
        assertEquals(listOf(5.0, 10.0), back.microIncrements.map { it.amount })
    }

    @Test
    @DisplayName("Given kg-denominated stops when mapped then column stores storage-lb doubles")
    fun kgMicroEncodesStorageLb() {
        val kg = Weight.WeightUnit.Kg
        val equipment = Equipment(
            id = EquipmentId("pin-loaded-machine"),
            name = "Pin-Loaded Machine",
            incrementTiers = emptyList(),
            microIncrements = listOf(
                Weight(5.0 * WeightConversion.LB_TO_KG, kg),
                Weight(10.0 * WeightConversion.LB_TO_KG, kg),
            ),
        )

        val entity = equipment.toEntity()
        assertEquals("[5.0,10.0]", entity.microOptionsJson)

        val back = entity.toDomain(emptyList())
        assertEquals(listOf(5.0, 10.0), back.microIncrements.map { it.amount })
        assertEquals(Weight.WeightUnit.Lb, back.microIncrements.first().unit)
    }

    @Test
    @DisplayName("Given empty equipment micro when mapped then column stays null")
    fun emptyEquipmentMicroEncodesNull() {
        val entity = Equipment(
            id = EquipmentId("cable"),
            name = "Cables",
            incrementTiers = emptyList(),
        ).toEntity()

        assertNull(entity.microOptionsJson)
        assertEquals(emptyList<Weight>(), entity.toDomain(emptyList()).microIncrements)
    }

    @Test
    @DisplayName("Given legacy null micro when mapped then lift inherits equipment stops")
    fun nullMicroInherits() {
        val entity = liftEntity(microOptionsJson = null)
        val equipment = pinEquipment()

        val back = entity.toDomain(
            equipmentById = mapOf(equipment.id to equipment),
        )

        assertEquals(listOf(5.0, 10.0), back.microIncrements.map { it.amount })
    }

    @Test
    @DisplayName("Given stale lift micro column when mapped then it is ignored for equipment stops")
    fun staleLiftMicroColumnIgnored() {
        val equipment = pinEquipment()
        val back =
            liftEntity(microOptionsJson = "[2.5]").toDomain(
                equipmentById = mapOf(equipment.id to equipment),
            )

        assertEquals(listOf(5.0, 10.0), back.microIncrements.map { it.amount })
    }

    @Test
    @DisplayName("Given corrupt lift micro when mapped then it reads empty without failing")
    fun corruptMicroReadsEmpty() {
        val back = liftEntity(microOptionsJson = "nope").toDomain()

        assertEquals(emptyList<Weight>(), back.microIncrements)
    }

    @Test
    @DisplayName("Given a lift when written then the vestigial micro column stays null")
    fun liftMicroColumnStaysNull() {
        val lb = Weight.WeightUnit.Lb
        val lift = Lift(
            id = LiftId("lat-pulldown"),
            name = "Lat pulldown",
            movementPatterns = setOf(MovementPattern.VerticalPull),
            primaryMuscles = setOf(MuscleGroup.Back),
            secondaryMuscles = emptySet(),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            microIncrements = listOf(Weight(2.5, lb)),
            enabled = true,
        )

        val entity = lift.toEntity()
        assertNull(entity.microOptionsJson)
    }

    private fun liftEntity(microOptionsJson: String?): LiftEntity =
        LiftEntity(
            id = LiftId("lat-pulldown"),
            name = "Lat pulldown",
            movementPatternsBitmask = setOf(MovementPattern.VerticalPull).toBitmask(),
            primaryMusclesBitmask = setOf(MuscleGroup.Back).toBitmask(),
            secondaryMusclesBitmask = 0,
            presetTags = emptySet<GymPreset>().toPresetCsv(),
            equipmentId = EquipmentId("pin-loaded-machine"),
            isCustom = false,
            enabled = true,
            microOptionsJson = microOptionsJson,
        )

    private fun pinEquipment(): Equipment {
        val lb = Weight.WeightUnit.Lb
        return Equipment(
            id = EquipmentId("pin-loaded-machine"),
            name = "Pin-Loaded Machine",
            incrementTiers = emptyList(),
            microIncrements = listOf(Weight(5.0, lb), Weight(10.0, lb)),
        )
    }
}
