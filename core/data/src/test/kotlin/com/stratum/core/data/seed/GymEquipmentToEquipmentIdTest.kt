package com.stratum.core.data.seed

import com.stratum.domain.importing.GymEquipment
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class GymEquipmentToEquipmentIdTest {

    @Test
    @DisplayName("T-bar station maps to single weight plate")
    fun tBarMapsToSinglePlate() {
        assertEquals(
            EquipmentId("single-weight-plate"),
            GymEquipmentToEquipmentId.map(GymEquipment.TBarRowStation),
        )
    }

    @Test
    @DisplayName("Fixed barbell maps to fixed-barbell seed id")
    fun fixedBarbellMaps() {
        assertEquals(
            EquipmentId("fixed-barbell"),
            GymEquipmentToEquipmentId.map(GymEquipment.FixedBarbell),
        )
    }

    @Test
    @DisplayName("Generic barbell still maps to double weight plates")
    fun barbellMapsToDoublePlates() {
        assertEquals(
            EquipmentId("double-weight-plates"),
            GymEquipmentToEquipmentId.map(GymEquipment.Barbell),
        )
    }

    @Test
    @DisplayName("T-bar tag wins over generic barbell when both present")
    fun tBarBeatsBarbell() {
        assertEquals(
            EquipmentId("single-weight-plate"),
            GymEquipmentToEquipmentId.equipmentIdFor(
                setOf(GymEquipment.Barbell, GymEquipment.TBarRowStation),
            ),
        )
    }

    @Test
    @DisplayName("one-arm-long-bar-row overrides to single weight plate")
    fun longBarRowOverride() {
        assertEquals(
            EquipmentId("single-weight-plate"),
            GymEquipmentToEquipmentId.equipmentIdFor(
                LiftId("one-arm-long-bar-row"),
                setOf(GymEquipment.Barbell),
            ),
        )
    }

    @Test
    @DisplayName("free-text cable maps to seed cable id")
    fun freeTextCable() {
        assertEquals(EquipmentId("cable"), GymEquipmentToEquipmentId.seedIdFromFreeText("cable"))
    }

    @Test
    @DisplayName("GymEquipment enum name CableStation maps to seed cable id")
    fun freeTextGymEquipmentEnumName() {
        assertEquals(
            EquipmentId("cable"),
            GymEquipmentToEquipmentId.seedIdFromFreeText("CableStation"),
        )
    }
}
