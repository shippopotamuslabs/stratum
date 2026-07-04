package com.stratum.core.inference

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FirebaseCallablePayloadEncoderTest {
    @Test
    fun `encodeNamingPayload uses firebase-compatible primitive maps`() {
        val payload =
            FirebaseCallablePayloadEncoder.encodeNamingPayload(
                com.stratum.domain.program.generation.naming.ProgramNamingPayload(
                    promptVersion = 1,
                    intent =
                        com.stratum.domain.program.generation.naming.ProgramNamingIntentPayload(
                            trainingAge = "Intermediate",
                            sessionLengthMinutes = 75,
                            trainingDaysPerWeek = 5,
                            cycleLength = 5,
                            specialization = mapOf("Bicep" to "Specialize"),
                            specializedMuscles = listOf("Bicep"),
                            trainingWeekdays = listOf("Monday", "Tuesday", "Wednesday", "Friday", "Saturday"),
                        ),
                    program =
                        com.stratum.domain.program.generation.naming.ProgramNamingProgramPayload(
                            workouts =
                                listOf(
                                    com.stratum.domain.program.generation.naming.ProgramNamingWorkoutPayload(
                                        position = 0,
                                        weekday = "Monday",
                                        workoutLifts =
                                            listOf(
                                                com.stratum.domain.program.generation.naming.ProgramNamingLiftPayload(
                                                    name = "Bench Press",
                                                    movementPattern = "HorizontalPress",
                                                    primaryMuscles = listOf("Chest"),
                                                    setCount = 3,
                                                ),
                                            ),
                                    ),
                                ),
                        ),
                ),
            )

        assertFirebaseEncodable(payload)
    }

    private fun assertFirebaseEncodable(value: Any?) {
        when (value) {
            null, is String, is Boolean, is Int, is Long, is Double, is Float -> Unit
            is Map<*, *> ->
                value.forEach { (key, entry) ->
                    assertTrue(key is String)
                    assertFirebaseEncodable(entry)
                }
            is List<*> -> value.forEach { entry -> assertFirebaseEncodable(entry) }
            else -> error("Not Firebase-encodable: ${value::class.java.name}")
        }
    }
}
