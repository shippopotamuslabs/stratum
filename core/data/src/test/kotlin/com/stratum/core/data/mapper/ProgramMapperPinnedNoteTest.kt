package com.stratum.core.data.mapper

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ProgramMapperPinnedNoteTest {

    @Test
    @DisplayName("workout-lift pinnedNote round-trips through entity mapping")
    fun workoutLiftPinnedNoteRoundTrip() {
        val domain =
            WorkoutLift(
                id = WorkoutLiftId("wl-1"),
                workoutId = WorkoutId("w-1"),
                liftId = LiftId("lift-1"),
                progressionScheme = ProgressionScheme.Double,
                position = 0,
                sets = emptyList(),
                pinnedNote = "Elbows in",
            )
        val entity = domain.toEntity(nowMillis = 1L)
        assertEquals("Elbows in", entity.pinnedNote)
        val back = entity.toDomain(sets = emptyList())
        assertEquals("Elbows in", back.pinnedNote)
    }
}
