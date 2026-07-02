package com.stratum.domain.program.generation.split

import com.stratum.domain.program.generation.GenerationContextTestFixtures
import com.stratum.domain.program.generation.context.LiftContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CrossDomainTouchSlotsTest {

    private val touchTestLifts =
        GenerationContextTestFixtures.volumePlannerLifts +
            LiftContext(
                id = "calf-raise",
                name = "Calf Raise",
                movementPattern = "PlantarFlexion",
                movementPatterns = listOf("PlantarFlexion"),
                primaryMuscles = listOf("Calf"),
                secondaryMuscles = emptyList(),
            )

    @Test
    fun `six day calf specialization injects touch on first upper session only`() {
        val context =
            GenerationContextTestFixtures.contextWith(
                profileOverrides = mapOf(
                    "trainingDaysPerWeek" to 6,
                    "weekdays" to listOf(
                        "Monday",
                        "Tuesday",
                        "Wednesday",
                        "Thursday",
                        "Friday",
                        "Saturday",
                    ),
                    "sessionLengthMinutes" to 75,
                ),
                specialization = mapOf("Calf" to "Specialize"),
                provenLifts = touchTestLifts,
                literatureVolumeSnapshots = GenerationContextTestFixtures.volumePlannerSnapshots,
            )
        val firstUpper =
            slotsForSessionType(
                sessionType = SessionTypeId.UpperPush,
                context = context,
                sessionPosition = 1,
            )
        val secondUpper =
            slotsForSessionType(
                sessionType = SessionTypeId.UpperPush,
                context = context,
                siblingIndex = 1,
                sessionPosition = 4,
            )
        assertTrue(firstUpper.any { it.isCrossDomainTouch && it.primaryMuscle == "Calf" })
        assertFalse(secondUpper.any { it.isCrossDomainTouch && it.primaryMuscle == "Calf" })
    }

    @Test
    fun `four day calf specialization still allows touch on each eligible upper session`() {
        val context =
            GenerationContextTestFixtures.contextWith(
                specialization = mapOf("Calf" to "Specialize"),
                provenLifts = touchTestLifts,
                literatureVolumeSnapshots = GenerationContextTestFixtures.volumePlannerSnapshots,
            )
        val upperPush =
            slotsForSessionType(
                sessionType = SessionTypeId.UpperPush,
                context = context,
                sessionPosition = 1,
            )
        assertTrue(upperPush.any { it.isCrossDomainTouch && it.primaryMuscle == "Calf" })
    }

    @Test
    fun `limits cross-domain touch budget on lower days when calf is specialized in-domain`() {
        val context =
            GenerationContextTestFixtures.contextWith(
                specialization = mapOf(
                    "Calf" to "Specialize",
                    "Bicep" to "Specialize",
                    "Tricep" to "Specialize",
                ),
                provenLifts = touchTestLifts,
                literatureVolumeSnapshots = GenerationContextTestFixtures.volumePlannerSnapshots,
            )
        val lower = slotsForSessionType(sessionType = SessionTypeId.Lower, context = context)
        val touchSlots = lower.filter { it.isCrossDomainTouch }
        assertTrue(touchSlots.size <= 1)
    }
}
