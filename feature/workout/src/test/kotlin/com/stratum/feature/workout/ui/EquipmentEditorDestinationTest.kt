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
                EquipmentEditRequest(
                    liftId = "lift-1",
                    target = EquipmentEditTarget(equipmentId = "eq-1", hasIncrementOverride = true),
                ),
            ),
        )
    }

    @Test
    @DisplayName("Given inherited equipment defaults when resolved then the shared equipment editor opens")
    fun inheritedDefaultsRouteToSharedEquipment() {
        assertEquals(
            EquipmentEditorDestination.SharedEquipment("eq-1", "lift-1"),
            resolveEquipmentEditorDestination(
                EquipmentEditRequest(
                    liftId = "lift-1",
                    target = EquipmentEditTarget(equipmentId = "eq-1", hasIncrementOverride = false),
                ),
            ),
        )
    }

    @Test
    @DisplayName("Given no equipment and no override when resolved then the lift override editor opens")
    fun unassignedLiftRoutesToLiftOverride() {
        assertEquals(
            EquipmentEditorDestination.LiftOverride("lift-1"),
            resolveEquipmentEditorDestination(
                EquipmentEditRequest(
                    liftId = "lift-1",
                    target = EquipmentEditTarget(equipmentId = null, hasIncrementOverride = false),
                ),
            ),
        )
    }

    @Test
    @DisplayName("Given a rendered lift inheriting equipment when requested then the shared editor opens")
    fun renderedLiftRequestRoutesToSharedEquipment() {
        val request =
            equipmentEditRequestFor(
                RenderedLiftUi(
                    workoutLiftId = "wl-1",
                    liftId = "lift-1",
                    liftName = "Lat pulldown",
                    sets = emptyList(),
                    equipmentId = "eq-1",
                    hasIncrementOverride = false,
                ),
            )
        assertEquals(
            EquipmentEditorDestination.SharedEquipment("eq-1", "lift-1"),
            resolveEquipmentEditorDestination(request),
        )
    }

    @Test
    @DisplayName("Given a rendered lift with an override when requested then the override editor opens")
    fun renderedLiftOverrideRequestRoutesToLiftOverride() {
        val request =
            equipmentEditRequestFor(
                RenderedLiftUi(
                    workoutLiftId = "wl-1",
                    liftId = "lift-1",
                    liftName = "Lat pulldown",
                    sets = emptyList(),
                    equipmentId = "eq-1",
                    hasIncrementOverride = true,
                ),
            )
        assertEquals(
            EquipmentEditorDestination.LiftOverride("lift-1"),
            resolveEquipmentEditorDestination(request),
        )
    }
}
