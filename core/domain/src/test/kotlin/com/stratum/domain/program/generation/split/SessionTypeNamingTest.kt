package com.stratum.domain.program.generation.split

import com.stratum.domain.program.generation.split.SessionTypeId.Lower
import com.stratum.domain.program.generation.split.SessionTypeId.Upper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SessionTypeNamingTest {

    @Test
    fun sixDayUpperLowerCycleUsesDistinctLetters() {
        val names =
            listOf(Lower, Upper, Lower, Upper, Lower, Upper).mapIndexed { index, type ->
                displayNameForSessionType(type, siblingIndexForSessionType(listOf(Lower, Upper, Lower, Upper, Lower, Upper), index))
            }
        assertEquals(
            listOf("Lower A", "Upper A", "Lower B", "Upper B", "Lower C", "Upper C"),
            names,
        )
    }

    @Test
    fun inferComboSessionTypes() {
        assertEquals(SessionTypeId.ChestTris, inferSessionTypeFromWorkoutName("Chest & Tris"))
        assertEquals(SessionTypeId.BackBis, inferSessionTypeFromWorkoutName("Back & Bis"))
        assertEquals(SessionTypeId.Chest, inferSessionTypeFromWorkoutName("Chest"))
    }
}
