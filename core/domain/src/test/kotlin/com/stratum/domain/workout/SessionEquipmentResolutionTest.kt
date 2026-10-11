package com.stratum.domain.workout

import com.stratum.domain.lift.identity.EquipmentId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("resolveSessionEquipmentId")
class SessionEquipmentResolutionTest {

    private val swap = EquipmentId("swap")
    private val template = EquipmentId("template")
    private val liftDefault = EquipmentId("lift-default")

    @Test
    @DisplayName("live-swap override wins over template and lift default")
    fun swapOverrideWins() {
        assertEquals(
            swap,
            resolveSessionEquipmentId(
                swapOverrideId = swap,
                templateEquipmentId = template,
                liftEquipmentId = liftDefault,
            ),
        )
    }

    @Test
    @DisplayName("template assignment wins over the lift default")
    fun templateWinsOverLiftDefault() {
        assertEquals(
            template,
            resolveSessionEquipmentId(
                swapOverrideId = null,
                templateEquipmentId = template,
                liftEquipmentId = liftDefault,
            ),
        )
    }

    @Test
    @DisplayName("lift default applies when neither override nor template is set")
    fun liftDefaultApplies() {
        assertEquals(
            liftDefault,
            resolveSessionEquipmentId(
                swapOverrideId = null,
                templateEquipmentId = null,
                liftEquipmentId = liftDefault,
            ),
        )
    }

    @Test
    @DisplayName("all-null resolves to null (bodyweight / unassigned)")
    fun allNullResolvesNull() {
        assertNull(
            resolveSessionEquipmentId(
                swapOverrideId = null,
                templateEquipmentId = null,
                liftEquipmentId = null,
            ),
        )
    }
}
