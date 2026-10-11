package com.stratum.domain.lift

import com.stratum.domain.lift.identity.EquipmentId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("EquipmentPickAwaiter")
class EquipmentPickAwaiterTest {

    private val picks = EquipmentPickResult()
    private val awaiter = EquipmentPickAwaiter(picks)
    private val machine = EquipmentId("new-machine")

    @Test
    @DisplayName("delivers a fresh pick for the armed target exactly once")
    fun deliversFreshPickOnce() {
        awaiter.arm("wl-1")
        picks.publish(machine)

        val delivered = awaiter.onPick(picks.result.value, openTargetId = "wl-1")

        assertEquals("wl-1", delivered?.targetId)
        assertEquals(machine, delivered?.pick?.equipmentId)
        assertNull(awaiter.onPick(picks.result.value, openTargetId = "wl-1"))
    }

    @Test
    @DisplayName("ignores picks published before arming")
    fun ignoresStaleGeneration() {
        picks.publish(machine)
        awaiter.arm("wl-1")

        assertNull(awaiter.onPick(picks.result.value, openTargetId = "wl-1"))
    }

    @Test
    @DisplayName("ignores picks when not armed and null picks when armed")
    fun ignoresUnarmedAndNull() {
        picks.publish(machine)

        assertNull(awaiter.onPick(picks.result.value, openTargetId = "wl-1"))
        awaiter.arm("wl-1")
        assertNull(awaiter.onPick(null, openTargetId = "wl-1"))
    }

    @Test
    @DisplayName("disarm abandons the pending target")
    fun disarmAbandons() {
        awaiter.arm("wl-1")
        awaiter.disarm()
        picks.publish(machine)

        assertNull(awaiter.onPick(picks.result.value, openTargetId = "wl-1"))
    }

    @Test
    @DisplayName("re-arming replaces the pending target")
    fun rearmReplacesTarget() {
        awaiter.arm("wl-1")
        awaiter.arm("wl-2")
        picks.publish(machine)

        val delivered = awaiter.onPick(picks.result.value, openTargetId = "wl-2")

        assertEquals("wl-2", delivered?.targetId)
    }

    @Test
    @DisplayName("mismatched open target consumes the pick without delivering")
    fun mismatchConsumesWithoutDelivering() {
        awaiter.arm("wl-1")
        picks.publish(machine)

        assertNull(awaiter.onPick(picks.result.value, openTargetId = "wl-2"))
        // Consumed: re-opening the original target cannot resurrect it.
        assertNull(awaiter.onPick(picks.result.value, openTargetId = "wl-1"))
    }

    @Test
    @DisplayName("delivers picks with a null equipment id for the host to filter")
    fun deliversNullEquipmentId() {
        awaiter.arm("wl-1")
        picks.publish(null)

        val delivered = awaiter.onPick(picks.result.value, openTargetId = "wl-1")

        assertEquals("wl-1", delivered?.targetId)
        assertNull(delivered?.pick?.equipmentId)
    }
}
