package com.stratum.feature.workout.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class EquipmentEditorDestinationTest {

    @Test
    @DisplayName("Given a per-lift override when resolved then the lift override editor wins over equipment")
    fun overrideBeatsSharedEquipment() {
        assertEquals(
            EquipmentEditorDestination.LiftOverride("lift-1"),
            resolveEquipmentEditorDestination(
                liftId = "lift-1",
                equipmentId = "eq-1",
                hasIncrementOverride = true,
            ),
        )
    }

    @Test
    @DisplayName("Given inherited equipment defaults when resolved then the shared equipment editor opens")
    fun inheritedDefaultsRouteToSharedEquipment() {
        assertEquals(
            EquipmentEditorDestination.SharedEquipment("eq-1"),
            resolveEquipmentEditorDestination(
                liftId = "lift-1",
                equipmentId = "eq-1",
                hasIncrementOverride = false,
            ),
        )
    }

    @Test
    @DisplayName("Given no equipment and no override when resolved then the lift override editor opens")
    fun unassignedLiftRoutesToLiftOverride() {
        assertEquals(
            EquipmentEditorDestination.LiftOverride("lift-1"),
            resolveEquipmentEditorDestination(
                liftId = "lift-1",
                equipmentId = null,
                hasIncrementOverride = false,
            ),
        )
    }
}
