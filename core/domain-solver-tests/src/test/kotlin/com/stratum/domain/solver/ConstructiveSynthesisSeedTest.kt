@file:Suppress("FunctionNaming")

package com.stratum.domain.solver

import com.stratum.domain.program.generation.structure.SessionRegionFilter
import com.stratum.domain.program.generation.structure.isRestSandwichedTrainingDay
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ConstructiveSynthesisSeedTest {

    @Test
    @DisplayName("rest-sandwiched calendar seeds Unrestricted region filters")
    fun restSandwichedSeedsUnrestricted() {
        val weekdays = listOf("Monday", "Wednesday", "Friday")
        assertEquals(
            listOf(
                SessionRegionFilter.Unrestricted,
                SessionRegionFilter.Unrestricted,
                SessionRegionFilter.Unrestricted,
            ),
            ConstructiveSynthesisSeed.proposeRegionFilters(3, weekdays),
        )
    }

    @Test
    @DisplayName("dense calendar prefers Unrestricted region when allowed")
    fun denseCalendarSeedsUnrestricted() {
        val weekdays =
            listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday")
        assertEquals(
            List(5) { SessionRegionFilter.Unrestricted },
            ConstructiveSynthesisSeed.proposeRegionFilters(5, weekdays),
        )
    }

    @Test
    @DisplayName("dense Unrestricted sessions alternate PressHinge / PullSquat families")
    fun denseUnrestrictedAlternatesFamilies() {
        val trainingDays = setOf(0, 1, 2, 3, 4) // Mon–Fri
        var familyIndex = 0
        val families =
            (0..4).map { day ->
                val family =
                    ConstructiveSynthesisSeed.proposeSeedFamily(
                        regionFilter = SessionRegionFilter.Unrestricted,
                        dayIndex = day,
                        trainingDays = trainingDays,
                        unrestrictedFamilyIndex = familyIndex,
                    )
                if (!isRestSandwichedTrainingDay(day, trainingDays)) familyIndex++
                family
            }
        assertEquals(
            listOf(
                SeedFamily.PressHinge,
                SeedFamily.PullSquat,
                SeedFamily.PressHinge,
                SeedFamily.PullSquat,
                SeedFamily.PressHinge,
            ),
            families,
        )
    }

    @Test
    @DisplayName("rest-sandwiched Unrestricted seeds Full family")
    fun restSandwichSeedsFullFamily() {
        val trainingDays = setOf(0, 2, 4) // MWF
        assertTrue(isRestSandwichedTrainingDay(0, trainingDays))
        assertEquals(
            SeedFamily.Full,
            ConstructiveSynthesisSeed.proposeSeedFamily(
                regionFilter = SessionRegionFilter.Unrestricted,
                dayIndex = 0,
                trainingDays = trainingDays,
                unrestrictedFamilyIndex = 0,
            ),
        )
    }

    @Test
    @DisplayName("UpperOnly/LowerOnly region filters seed RegionGated family")
    fun regionGatedFamily() {
        assertEquals(
            SeedFamily.RegionGated,
            ConstructiveSynthesisSeed.proposeSeedFamily(
                regionFilter = SessionRegionFilter.UpperOnly,
                dayIndex = 0,
                trainingDays = setOf(0, 1),
                unrestrictedFamilyIndex = 0,
            ),
        )
    }
}
