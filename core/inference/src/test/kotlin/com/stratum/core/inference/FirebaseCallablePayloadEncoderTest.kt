package com.stratum.core.inference

import com.stratum.domain.program.generation.swap.LiftSwapCandidatePayload
import com.stratum.domain.program.generation.swap.LiftSwapExercisePayload
import com.stratum.domain.program.generation.swap.LiftSwapProgramIntentPayload
import com.stratum.domain.program.generation.swap.LiftSwapProgramPayload
import com.stratum.domain.program.generation.swap.LiftSwapReviewPayload
import com.stratum.domain.program.generation.swap.LiftSwapWorkoutPayload
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FirebaseCallablePayloadEncoderTest {
    @Test
    fun `encodeSwapReviewPayload uses firebase-compatible primitive maps`() {
        val payload =
            FirebaseCallablePayloadEncoder.encodeSwapReviewPayload(
                LiftSwapReviewPayload(
                    promptVersion = 1,
                    intent =
                        LiftSwapProgramIntentPayload(
                            trainingAge = "Intermediate",
                            sessionLengthMinutes = 75,
                            trainingDaysPerWeek = 5,
                            cycleLength = 5,
                            specialization = mapOf("Bicep" to "Specialize"),
                            specializedMuscles = listOf("Bicep"),
                        ),
                    program =
                        LiftSwapProgramPayload(
                            cycleLength = 5,
                            workouts =
                                listOf(
                                    LiftSwapWorkoutPayload(
                                        position = 0,
                                        name = "Upper A",
                                        workoutLifts =
                                            listOf(
                                                LiftSwapExercisePayload(
                                                    position = 0,
                                                    liftId = "bench-press",
                                                    name = "Bench Press",
                                                    movementPattern = "HorizontalPress",
                                                    primaryMuscles = listOf("Chest"),
                                                    secondaryMuscles = listOf("Tricep"),
                                                    setCount = 3,
                                                    repMin = 6,
                                                    repMax = 10,
                                                    candidates =
                                                        listOf(
                                                            LiftSwapCandidatePayload(
                                                                liftId = "db-press",
                                                                name = "DB Press",
                                                                movementPattern = "HorizontalPress",
                                                                primaryMuscles = listOf("Chest"),
                                                            ),
                                                        ),
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
