package com.stratum.feature.workout.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * STR-86/83: per-lift override destinations are gone — every "Edit equipment" request
 * resolves to the shared equipment editor.
 */
class EquipmentEditorDestinationTest {

    @Test
    @DisplayName("Given inherited equipment defaults when resolved then the shared equipment editor opens")
    fun inheritedDefaultsRouteToSharedEquipment() {
        assertEquals(
            EquipmentEditorDestination.SharedEquipment("eq-1", "lift-1"),
            resolveEquipmentEditorDestination(
                EquipmentEditRequest(
                    liftId = "lift-1",
                    target = EquipmentEditTarget(equipmentId = "eq-1"),
                ),
            ),
        )
    }

    @Test
    @DisplayName("Given no equipment when resolved then the shared destination carries a null equipment id")
    fun unassignedLiftRoutesToSharedEquipmentWithNullId() {
        val destination =
            resolveEquipmentEditorDestination(
                EquipmentEditRequest(
                    liftId = "lift-1",
                    target = EquipmentEditTarget(equipmentId = null),
                ),
            ) as EquipmentEditorDestination.SharedEquipment
        assertNull(destination.equipmentId)
        assertEquals("lift-1", destination.sourceLiftId)
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
                ),
            )
        assertEquals(
            EquipmentEditorDestination.SharedEquipment("eq-1", "lift-1"),
            resolveEquipmentEditorDestination(request),
        )
    }
}
