package com.stratum.feature.program

import com.stratum.domain.program.ProgressionScheme
import com.stratum.feature.program.ui.EditSetState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class EditWorkoutSchemeSetsTest {

    @Test
    @DisplayName("showsRepRange is true for every Linear set and only Double top set")
    fun showsRepRangePolicy() {
        val top = set(id = "a", number = 1, min = 8, max = 12, isTop = true)
        val backoff = set(id = "b", number = 2, min = 0, max = 0, isTop = false)

        assertTrue(showsRepRange(ProgressionScheme.Linear, top))
        assertTrue(showsRepRange(ProgressionScheme.Linear, backoff))
        assertTrue(showsRepRange(ProgressionScheme.Double, top))
        assertFalse(showsRepRange(ProgressionScheme.Double, backoff))
    }

    @Test
    @DisplayName("applyProgressionSchemeToSets Double zeros non-top ranges")
    fun applyDoubleZerosBackoffs() {
        val sets =
            listOf(
                set(id = "a", number = 1, min = 8, max = 12, isTop = true),
                set(id = "b", number = 2, min = 8, max = 12, isTop = false),
            )
        val result = applyProgressionSchemeToSets(sets, ProgressionScheme.Double)

        assertTrue(result[0].isTopSet)
        assertEquals(8, result[0].repMin)
        assertEquals(12, result[0].repMax)
        assertFalse(result[1].isTopSet)
        assertEquals(0, result[1].repMin)
        assertEquals(0, result[1].repMax)
    }

    @Test
    @DisplayName("applyProgressionSchemeToSets Linear copies shared band to every set")
    fun applyLinearSharesBand() {
        val sets =
            listOf(
                set(id = "a", number = 1, min = 5, max = 8, isTop = true),
                set(id = "b", number = 2, min = 0, max = 0, isTop = false),
            )
        val result = applyProgressionSchemeToSets(sets, ProgressionScheme.Linear)

        assertTrue(result[0].isTopSet)
        assertFalse(result[1].isTopSet)
        assertEquals(5, result[0].repMin)
        assertEquals(8, result[0].repMax)
        assertEquals(5, result[1].repMin)
        assertEquals(8, result[1].repMax)
    }

    @Test
    @DisplayName("applyUserRepBand Linear uses edited band even when prior top differed")
    fun applyUserBandIgnoresPriorTop() {
        val sets =
            listOf(
                set(id = "a", number = 1, min = 8, max = 12, isTop = true),
                set(id = "b", number = 2, min = 8, max = 12, isTop = false),
            )
        val result = applyUserRepBand(sets, ProgressionScheme.Linear, repMin = 6, repMax = 6)
        assertTrue(result.all { it.repMin == 6 && it.repMax == 6 })
    }

    @Test
    @DisplayName("normalizeSetsForScheme can promote removed top band via fallback")
    fun fallbackPreservesRemovedTopBand() {
        val remaining = listOf(set(id = "b", number = 2, min = 0, max = 0, isTop = false))
        val result =
            normalizeSetsForScheme(
                sets = remaining,
                scheme = ProgressionScheme.Double,
                fallbackRepMin = 5,
                fallbackRepMax = 5,
            )
        assertEquals(1, result.size)
        assertTrue(result[0].isTopSet)
        assertEquals(5, result[0].repMin)
        assertEquals(5, result[0].repMax)
        assertEquals(1, result[0].setNumber)
    }

    private fun set(
        id: String,
        number: Int,
        min: Int,
        max: Int,
        isTop: Boolean,
    ): EditSetState =
        EditSetState(
            setId = id,
            setNumber = number,
            repMin = min,
            repMax = max,
            restSeconds = 120,
            rirGoal = 2,
            isTopSet = isTop,
        )
}
