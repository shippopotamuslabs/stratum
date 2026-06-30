package com.stratum.domain.program.generation.split

import com.stratum.domain.program.generation.GenerationContextTestFixtures
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.MuscleLengthBias
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpecializedVariantSlotsTest {

    private val dualCurlLifts =
        GenerationContextTestFixtures.volumePlannerLifts +
            listOf(
                LiftContext(
                    id = "incline-curl",
                    name = "Incline Curl",
                    movementPattern = "ElbowFlexion",
                    movementPatterns = listOf("ElbowFlexion"),
                    primaryMuscles = listOf("Bicep"),
                    secondaryMuscles = emptyList(),
                    muscleLengthBias = MuscleLengthBias.Lengthened,
                ),
                LiftContext(
                    id = "tricep-pushdown",
                    name = "Tricep Pushdown",
                    movementPattern = "ElbowExtension",
                    movementPatterns = listOf("ElbowExtension"),
                    primaryMuscles = listOf("Tricep"),
                    secondaryMuscles = emptyList(),
                    muscleLengthBias = MuscleLengthBias.Shortened,
                ),
            )

    @Test
    fun `specialized bicep on upper injects second in-domain slot when two curls are available`() {
        val context =
            GenerationContextTestFixtures.contextWith(
                specialization = mapOf("Bicep" to "Specialize"),
                provenLifts = dualCurlLifts,
            )
        val slots = slotsForSessionType(SessionTypeId.Upper, context = context)
        val bicepSlots = slots.filter { it.primaryMuscle == "Bicep" && !it.isCrossDomainTouch }
        assertEquals(2, bicepSlots.size)
        assertTrue(bicepSlots.any { it.slotId == "bicep" })
        assertTrue(bicepSlots.any { it.slotId == "bicep-alt" })
    }

    @Test
    fun `does not inject variant slot when only one lift is available for the pattern`() {
        val context =
            GenerationContextTestFixtures.contextWith(
                specialization = mapOf("Bicep" to "Specialize"),
                provenLifts = GenerationContextTestFixtures.volumePlannerLifts,
            )
        val slots = slotsForSessionType(SessionTypeId.Upper, context = context)
        val bicepSlots = slots.filter { it.primaryMuscle == "Bicep" && !it.isCrossDomainTouch }
        assertEquals(1, bicepSlots.size)
    }

    @Test
    fun `variant slot uses alternate length bias to steer lift selection`() {
        val context =
            GenerationContextTestFixtures.contextWith(
                specialization = mapOf("Bicep" to "Specialize"),
                provenLifts = dualCurlLifts,
            )
        val slots = slotsForSessionType(SessionTypeId.Upper, context = context)
        val primary = slots.first { it.slotId == "bicep" }
        val variant = slots.first { it.slotId == "bicep-alt" }
        assertEquals(MuscleLengthBiasPreference.Lengthened, variant.lengthBiasPreference)
        assertFalse(variant.lengthBiasPreference == primary.lengthBiasPreference)
    }
}
