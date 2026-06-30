package com.stratum.domain.program.generation.synthesis

import com.stratum.core.testing.program.scenarioContext
import com.stratum.domain.program.generation.split.SlotRole
import com.stratum.domain.program.generation.volume.planWeeklyVolume
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class SlotUniverseBuilderTest {

    @Test
    @DisplayName("universe includes specialized muscles and prunes to cap")
    fun specializedMusclesIncluded() {
        val context = scenarioContext(days = 4, specialization = mapOf("Chest" to "Specialize"))
        val targets = planWeeklyVolume(context)
        val universe = buildSlotUniverse(context, targets)

        assertTrue(universe.isNotEmpty())
        assertTrue(universe.size <= 80)
        assertTrue(universe.any { slot -> slot.primaryMuscle == "Chest" })
        assertTrue(universe.any { slot -> slot.role == SlotRole.Anchor })
    }

    @Test
    @DisplayName("cross-domain touch slots allow upper and lower domains")
    fun crossDomainTouchSlotsAllowBothDomains() {
        val context =
            scenarioContext(
                days = 5,
                specialization = mapOf("Bicep" to "Specialize", "Tricep" to "Specialize"),
            )
        val targets = planWeeklyVolume(context)
        val universe = buildSlotUniverse(context, targets)
        val touchSlots = universe.filter { slot -> slot.isCrossDomainTouch }

        assertTrue(touchSlots.isNotEmpty())
        assertTrue(touchSlots.all { slot -> slot.allowedDomains.size >= 2 })
    }
}
