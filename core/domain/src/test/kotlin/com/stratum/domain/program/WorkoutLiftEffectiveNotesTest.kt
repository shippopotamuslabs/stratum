package com.stratum.domain.program

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("WorkoutLift.effectiveNotes")
class WorkoutLiftEffectiveNotesTest {

    @Test
    @DisplayName("List form wins over the scalar")
    fun listWinsOverScalar() {
        val lift = sampleLift(pinnedNote = "scalar", pinnedNotes = listOf("one", "two"))

        assertEquals(listOf("one", "two"), lift.effectiveNotes())
    }

    @Test
    @DisplayName("Scalar is the fallback when the list is empty")
    fun scalarFallback() {
        val lift = sampleLift(pinnedNote = "scalar", pinnedNotes = emptyList())

        assertEquals(listOf("scalar"), lift.effectiveNotes())
    }

    @Test
    @DisplayName("Empty when both forms are empty")
    fun emptyWhenBothEmpty() {
        val lift = sampleLift(pinnedNote = null, pinnedNotes = emptyList())

        assertEquals(emptyList<String>(), lift.effectiveNotes())
    }

    private fun sampleLift(pinnedNote: String?, pinnedNotes: List<String>): WorkoutLift =
        WorkoutLift(
            id = WorkoutLiftId("wl-1"),
            workoutId = WorkoutId("w-1"),
            liftId = LiftId("lift-bench"),
            progressionScheme = ProgressionScheme.Double,
            position = 0,
            sets = emptyList(),
            pinnedNote = pinnedNote,
            pinnedNotes = pinnedNotes,
        )
}
