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
                literatureVolumeLandmarks = GenerationContextTestFixtures.volumePlannerLandmarks,
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
                literatureVolumeLandmarks = GenerationContextTestFixtures.volumePlannerLandmarks,
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
    fun `resolved six day split has at most one calf touch slot in cycle`() {
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
                specialization = mapOf(
                    "Bicep" to "Specialize",
                    "Tricep" to "Specialize",
                    "Calf" to "Specialize",
                ),
                provenLifts = touchTestLifts,
                literatureVolumeLandmarks = GenerationContextTestFixtures.volumePlannerLandmarks,
            )
        val template =
            com.stratum.domain.program.generation.split.SPLIT_CATALOG.first {
                it.trainingDaysPerWeek == 6 && it.isDefault && it.frequencyTier.value == 2
            }
        val sessions = buildSessions(template, context)
        val calfTouchSessions =
            sessions.count { plan ->
                plan.slots.any { slot -> slot.isCrossDomainTouch && slot.primaryMuscle == "Calf" }
            }
        assertEquals(1, calfTouchSessions)
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
                literatureVolumeLandmarks = GenerationContextTestFixtures.volumePlannerLandmarks,
            )
        val lower = slotsForSessionType(sessionType = SessionTypeId.Lower, context = context)
        val touchSlots = lower.filter { it.isCrossDomainTouch }
        assertTrue(touchSlots.size <= 1)
    }
}
