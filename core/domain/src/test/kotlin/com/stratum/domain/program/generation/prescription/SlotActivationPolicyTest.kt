package com.stratum.domain.program.generation.prescription

import com.stratum.core.testing.program.parseScenarioContext
import com.stratum.core.testing.program.scenarioPayload
import com.stratum.domain.program.generation.split.SessionTypeId
import com.stratum.domain.program.generation.split.SlotRole
import com.stratum.domain.program.generation.split.slotsForSessionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class SlotActivationPolicyTest {
    @Test
    @DisplayName("Given short non-specialized sessions when calf optional then activation is not preferred")
    fun `optional calf not preferred on short sessions`() {
        val context =
            parseScenarioContext(
                scenarioPayload(
                    days = 3,
                    specialization = emptyMap(),
                    sessionLengthMinutes = 45,
                ),
            )
        val slots = slotsForSessionType(SessionTypeId.Lower, context = context)
        val calf =
            slots
                .map { slot ->
                    FilledSlot(
                        slotId = slot.slotId,
                        role = slot.role,
                        pattern = slot.pattern,
                        primaryMuscle = slot.primaryMuscle,
                        repProfile = slot.repProfile,
                        lengthBiasPreference = slot.lengthBiasPreference,
                        siblingVariant = slot.siblingVariant,
                        isCrossDomainTouch = slot.isCrossDomainTouch,
                        setCount = 0,
                    )
                }
                .first { it.slotId == "calf" && it.role == SlotRole.Optional }

        assertTrue(isSolverActivatableSlot(calf))
        assertFalse(prefersSlotActivation(calf, context))
    }

    @Test
    @DisplayName("Given specialized calf when slots built then optional calf is promoted to isolation")
    fun `specialized calf is promoted to isolation slot`() {
        val context =
            parseScenarioContext(
                scenarioPayload(
                    days = 4,
                    specialization = mapOf("Calf" to "Specialize"),
                    sessionLengthMinutes = 45,
                ),
            )
        val calf =
            slotsForSessionType(SessionTypeId.Lower, context = context)
                .first { it.slotId == "calf" }
        val filled =
            FilledSlot(
                slotId = calf.slotId,
                role = calf.role,
                pattern = calf.pattern,
                primaryMuscle = calf.primaryMuscle,
                repProfile = calf.repProfile,
                lengthBiasPreference = calf.lengthBiasPreference,
                siblingVariant = calf.siblingVariant,
                isCrossDomainTouch = calf.isCrossDomainTouch,
                setCount = 0,
            )

        assertEquals(SlotRole.Isolation, filled.role)
        assertFalse(isSolverActivatableSlot(filled))
    }
}
