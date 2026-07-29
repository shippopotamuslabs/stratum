package com.stratum.core.inference

import com.stratum.domain.program.generation.swap.LiftSwapReviewPayload

/**
 * Builds Firebase-Callable-compatible `Map<String, Any?>` trees. Firebase's client serializer
 * rejects kotlinx [JsonElement] values — only plain maps, lists, and primitives are valid.
 */
internal object FirebaseCallablePayloadEncoder {
    fun encodeSwapReviewPayload(payload: LiftSwapReviewPayload): Map<String, Any?> =
        mapOf(
            "promptVersion" to payload.promptVersion,
            "intent" to
                buildMap {
                    put("trainingAge", payload.intent.trainingAge)
                    payload.intent.sessionLengthMinutes?.let { put("sessionLengthMinutes", it) }
                    put("trainingDaysPerWeek", payload.intent.trainingDaysPerWeek)
                    put("cycleLength", payload.intent.cycleLength)
                    put("specialization", payload.intent.specialization)
                    put("specializedMuscles", payload.intent.specializedMuscles)
                },
            "program" to
                mapOf(
                    "cycleLength" to payload.program.cycleLength,
                    "workouts" to
                        payload.program.workouts.map { workout ->
                            mapOf(
                                "position" to workout.position,
                                "name" to workout.name,
                                "workoutLifts" to
                                    workout.workoutLifts.map { lift ->
                                        buildMap {
                                            put("position", lift.position)
                                            put("liftId", lift.liftId)
                                            put("name", lift.name)
                                            put("movementPattern", lift.movementPattern)
                                            put("primaryMuscles", lift.primaryMuscles)
                                            put("secondaryMuscles", lift.secondaryMuscles)
                                            put("setCount", lift.setCount)
                                            lift.repMin?.let { put("repMin", it) }
                                            lift.repMax?.let { put("repMax", it) }
                                            put(
                                                "candidates",
                                                lift.candidates.map { candidate ->
                                                    mapOf(
                                                        "liftId" to candidate.liftId,
                                                        "name" to candidate.name,
                                                        "movementPattern" to candidate.movementPattern,
                                                        "primaryMuscles" to candidate.primaryMuscles,
                                                    )
                                                },
                                            )
                                        }
                                    },
                            )
                        },
                ),
        )
}
