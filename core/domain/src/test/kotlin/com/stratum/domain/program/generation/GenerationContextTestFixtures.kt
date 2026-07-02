package com.stratum.domain.program.generation

import com.stratum.domain.program.LiteratureVolumeLandmarks
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.context.VolumeLandmarkContext
import com.stratum.domain.program.generation.context.VolumeLandmarkSource
import com.stratum.domain.program.generation.context.VolumeResponseContext

internal object GenerationContextTestFixtures {
    fun contextWith(
        profileOverrides: Map<String, Any?> = emptyMap(),
        specialization: Map<String, String> = mapOf("Chest" to "Specialize"),
        provenLifts: List<LiftContext> = defaultProvenLifts(),
        availableLifts: List<LiftContext> = emptyList(),
        literatureVolumeLandmarks: List<VolumeLandmarkContext> = defaultLiteratureLandmarks(),
        volumeResponses: List<VolumeResponseContext> = emptyList(),
    ): ProgramGenerationContext {
        val profile = mutableMapOf(
            "trainingAge" to "Intermediate",
            "sessionLengthMinutes" to 60,
            "weekdays" to listOf("Monday", "Wednesday", "Friday", "Saturday"),
            "trainingDaysPerWeek" to 4,
            "lastTrained" to null,
        )
        profile.putAll(profileOverrides)

        val liftsById = mutableMapOf<String, LiftContext>()
        val enabledLiftIds = mutableSetOf<String>()
        val movementPatternByLiftId = mutableMapOf<String, String>()
        for (lift in provenLifts + availableLifts) {
            liftsById[lift.id] = lift
            enabledLiftIds.add(lift.id)
            movementPatternByLiftId[lift.id] = lift.movementPattern
        }

        val volumeLandmarksByMuscle = literatureVolumeLandmarks.associateBy { it.muscle }
        val volumeResponsesByMuscle = volumeResponses.associateBy { it.muscle }

        return ProgramGenerationContext(
            trainingAge = profile["trainingAge"] as String,
            lastTrained = profile["lastTrained"] as String?,
            sessionLengthMinutes = profile["sessionLengthMinutes"] as Int?,
            trainingDaysPerWeek = profile["trainingDaysPerWeek"] as Int,
            weekdays = (profile["weekdays"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            specialization = specialization,
            liftsById = liftsById,
            volumeLandmarksByMuscle = volumeLandmarksByMuscle,
            volumeResponsesByMuscle = volumeResponsesByMuscle,
            enabledLiftIds = enabledLiftIds,
            movementPatternByLiftId = movementPatternByLiftId,
            liftEffectivenessByLiftId = emptyMap(),
        )
    }

    private fun defaultProvenLifts(): List<LiftContext> = listOf(
        LiftContext(
            id = "bench",
            name = "bench",
            movementPattern = "HorizontalPress",
            movementPatterns = listOf("HorizontalPress"),
            primaryMuscles = listOf("Chest"),
            secondaryMuscles = listOf("Tricep"),
        ),
        LiftContext(
            id = "row",
            name = "row",
            movementPattern = "HorizontalPull",
            movementPatterns = listOf("HorizontalPull"),
            primaryMuscles = listOf("Back"),
            secondaryMuscles = listOf("Bicep"),
        ),
    )

    private fun defaultLiteratureLandmarks(): List<VolumeLandmarkContext> =
        listOf("Chest", "Back").map { muscle -> literatureLandmarkContext(muscle) }

    internal fun literatureLandmarkContext(muscle: String): VolumeLandmarkContext {
        val literature =
            LiteratureVolumeLandmarks.all().single { entry -> entry.muscle.name == muscle }
        return VolumeLandmarkContext(
            muscle = literature.muscle.name,
            estimatedMev = literature.estimatedMev,
            estimatedMav = literature.estimatedMav,
            estimatedMrv = literature.estimatedMrv,
            estimatedPv = literature.estimatedPv,
            estimatedMv = literature.estimatedMv,
            source = VolumeLandmarkSource.Literature,
        )
    }

    internal fun literatureMrv(muscle: String): Double =
        LiteratureVolumeLandmarks.all().single { it.muscle.name == muscle }.estimatedMrv

    internal fun literaturePv(muscle: String): Double =
        LiteratureVolumeLandmarks.all().single { it.muscle.name == muscle }.estimatedPv

    internal fun literatureSpecializeMin(muscle: String): Double {
        val landmark = literatureLandmarkContext(muscle)
        return kotlin.math.max(landmark.estimatedMev, landmark.estimatedMav - 2.0)
    }

    val volumePlannerLifts: List<LiftContext> = listOf(
        LiftContext(
            id = "bench",
            name = "Bench Press",
            movementPattern = "HorizontalPress",
            movementPatterns = listOf("HorizontalPress"),
            primaryMuscles = listOf("Chest"),
            secondaryMuscles = listOf("Tricep"),
        ),
        LiftContext(
            id = "row",
            name = "Row",
            movementPattern = "HorizontalPull",
            movementPatterns = listOf("HorizontalPull"),
            primaryMuscles = listOf("Back"),
            secondaryMuscles = listOf("Bicep"),
        ),
        LiftContext(
            id = "squat",
            name = "Squat",
            movementPattern = "SquatPattern",
            movementPatterns = listOf("SquatPattern"),
            primaryMuscles = listOf("Quad"),
            secondaryMuscles = listOf("Glute"),
        ),
        LiftContext(
            id = "rdl",
            name = "RDL",
            movementPattern = "HipHinge",
            movementPatterns = listOf("HipHinge"),
            primaryMuscles = listOf("Hamstring"),
            secondaryMuscles = listOf("Glute"),
        ),
        LiftContext(
            id = "curl",
            name = "Curl",
            movementPattern = "ElbowFlexion",
            movementPatterns = listOf("ElbowFlexion"),
            primaryMuscles = listOf("Bicep"),
            secondaryMuscles = emptyList(),
        ),
        LiftContext(
            id = "tricep-ext",
            name = "Tricep Extension",
            movementPattern = "ElbowExtension",
            movementPatterns = listOf("ElbowExtension"),
            primaryMuscles = listOf("Tricep"),
            secondaryMuscles = emptyList(),
        ),
    )

    val volumePlannerLandmarks: List<VolumeLandmarkContext> =
        LiteratureVolumeLandmarks.all()
            .filter { literature ->
                literature.muscle.name in
                    setOf("Chest", "Back", "Quad", "Hamstring", "Bicep", "Tricep")
            }
            .map { literature ->
                VolumeLandmarkContext(
                    muscle = literature.muscle.name,
                    estimatedMev = literature.estimatedMev,
                    estimatedMav = literature.estimatedMav,
                    estimatedMrv = literature.estimatedMrv,
                    estimatedPv = literature.estimatedPv,
                    estimatedMv = literature.estimatedMv,
                    source = VolumeLandmarkSource.Literature,
                )
            }

    fun volumePlannerContext(
        profileOverrides: Map<String, Any?> = emptyMap(),
        specialization: Map<String, String> = mapOf(
            "Bicep" to "Specialize",
            "Chest" to "Maintenance",
        ),
    ): ProgramGenerationContext =
        contextWith(
            profileOverrides = mapOf(
                "sessionLengthMinutes" to 75,
                "weekdays" to listOf("Monday", "Tuesday", "Thursday", "Friday"),
                "trainingDaysPerWeek" to 4,
            ) + profileOverrides,
            specialization = specialization,
            provenLifts = volumePlannerLifts,
            literatureVolumeLandmarks = volumePlannerLandmarks,
        )
}
