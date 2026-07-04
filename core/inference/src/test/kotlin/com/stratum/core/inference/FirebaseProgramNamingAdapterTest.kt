package com.stratum.core.inference

import com.stratum.domain.program.generation.naming.ProgramNamingWorkoutName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FirebaseProgramNamingAdapterTest {

    @Test
    fun `parseNameProgramResponse maps program and workout names`() {
        val data =
            mapOf(
                "programName" to "5-Day Arm Focus",
                "workouts" to
                    listOf(
                        mapOf("position" to 0, "name" to "Upper — Arms"),
                        mapOf("position" to 2, "name" to "Lower — Legs"),
                    ),
            )

        val result = parseNameProgramResponse(data)

        assertEquals("5-Day Arm Focus", result.programName)
        assertEquals(
            listOf(
                ProgramNamingWorkoutName(0, "Upper — Arms"),
                ProgramNamingWorkoutName(2, "Lower — Legs"),
            ),
            result.workoutNames,
        )
    }

    @Test
    fun `parseNameProgramResponse returns empty on invalid payload`() {
        val result = parseNameProgramResponse(emptyMap<String, Any>())

        assertNull(result.programName)
        assertEquals(0, result.workoutNames.size)
    }
}
