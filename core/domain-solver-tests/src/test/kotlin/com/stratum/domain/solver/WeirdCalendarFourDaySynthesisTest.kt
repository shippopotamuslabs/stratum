@file:Suppress("FunctionNaming", "MagicNumber")

package com.stratum.domain.solver

import com.stratum.core.testing.program.parseScenarioContext
import com.stratum.core.testing.program.scenarioPayload
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.generation.volume.planWeeklyVolume
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Acceptance: Mon/Tue/Wed/Fri calendar with chest tier-2 aim should place horizontal
 * press on Mon + Fri (max spacing across the Wed-adjacent rest gap on Thu).
 */
class WeirdCalendarFourDaySynthesisTest {

    private val synthesizer = CpSatProgramSynthesizer()

    @Test
    @DisplayName("weird 4-day calendar spaces chest press on Mon and Fri")
    fun chestPressOnMaxSpacedDays() {
        val context =
            parseScenarioContext(
                scenarioPayload(
                    days = 4,
                    specialization = mapOf("Chest" to "Specialize", "Back" to "Maintenance"),
                    weekdayLayout = "weird4",
                ),
            )
        val targets = planWeeklyVolume(context)
        val result = synthesizer.synthesize(targets, context)

        val chestPressWeekdays = mutableSetOf<Int>()
        val chestPressPatterns = setOf(MovementPattern.HorizontalPress, MovementPattern.InclinePress)
        for (plan in result.plans) {
            val weekday = result.weekdayIndexByPosition[plan.position]
            val hasChestPress =
                plan.filledSlots.any { slot ->
                    slot.pattern in chestPressPatterns &&
                        slot.primaryMuscle == MuscleGroup.Chest &&
                        slot.setCount > 0
                }
            if (hasChestPress) {
                chestPressWeekdays += weekday
            }
        }

        assertTrue(
            chestPressWeekdays.size >= 2,
            "Chest should be trained at least twice weekly; got weekdays $chestPressWeekdays",
        )
        val spread =
            (chestPressWeekdays.maxOrNull() ?: 0) - (chestPressWeekdays.minOrNull() ?: 0)
        assertTrue(
            spread >= 2,
            "Chest press sessions should be calendar-spaced on weird4; got weekdays $chestPressWeekdays",
        )
        assertTrue(result.sessions.none { session -> session.name.contains("organic") })
    }

    private companion object {
        const val MONDAY_INDEX = 0
        const val FRIDAY_INDEX = 4
    }
}

