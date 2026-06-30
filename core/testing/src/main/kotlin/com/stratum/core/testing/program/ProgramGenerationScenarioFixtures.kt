@file:Suppress("MagicNumber")

package com.stratum.core.testing.program

import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.MuscleLengthBias
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.context.VolumeLandmarkContext
import com.stratum.domain.program.generation.context.VolumeLandmarkSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

val SCENARIO_LIFT_CATALOG: List<LiftContext> = listOf(
    lift("bench", "Bench Press", "HorizontalPress", listOf("Chest"), listOf("Tricep", "AnteriorDeltoid")),
    lift("db-press", "Dumbbell Press", "HorizontalPress", listOf("Chest"), listOf("Tricep")),
    lift("inc-press", "Incline Press", "InclinePress", listOf("Chest"), listOf("AnteriorDeltoid")),
    lift("row", "Barbell Row", "HorizontalPull", listOf("Back"), listOf("Bicep", "PosteriorDeltoid")),
    lift("seated-row", "Seated Row", "HorizontalPull", listOf("Back"), listOf("Bicep")),
    lift("pullup", "Pullup", "VerticalPull", listOf("Back"), listOf("Bicep")),
    lift("squat", "Squat", "SquatPattern", listOf("Quad"), listOf("Glute")),
    lift("front-squat", "Front Squat", "SquatPattern", listOf("Quad"), listOf("Glute")),
    lift("leg-ext", "Leg Extension", "KneeExtension", listOf("Quad")),
    lift("rdl", "RDL", "HipHinge", listOf("Hamstring"), listOf("Glute", "LowerBack")),
    lift("gm", "Good Morning", "HipHinge", listOf("Hamstring"), listOf("LowerBack")),
    lift("leg-curl", "Leg Curl", "KneeFlexion", listOf("Hamstring")),
    lift("calf-raise", "Calf Raise", "PlantarFlexion", listOf("Calf")),
    lift("seated-calf", "Seated Calf Raise", "PlantarFlexion", listOf("Calf")),
    lift("lat-raise", "Lateral Raise", "ShoulderAbduction", listOf("LateralDeltoid")),
    lift("rear-delt-fly", "Rear Delt Fly", "ShoulderHorizontalAbduction", listOf("PosteriorDeltoid")),
    lift("ohp", "Overhead Press", "VerticalPress", listOf("AnteriorDeltoid"), listOf("Tricep")),
    lift("curl", "Curl", "ElbowFlexion", listOf("Bicep")),
    lift("tricep-ext", "Tricep Extension", "ElbowExtension", listOf("Tricep")),
    lift("hip-thrust", "Hip Thrust", "HipExtension", listOf("Glute"), listOf("Hamstring")),
)

val SCENARIO_LANDMARKS: List<VolumeLandmarkContext> = listOf(
    landmark("Chest", 8.0, 14.0, 20.0, 11.0),
    landmark("Back", 8.0, 14.0, 20.0, 11.0),
    landmark("Quad", 8.0, 14.0, 20.0, 11.0),
    landmark("Hamstring", 6.0, 10.0, 16.0, 8.0),
    landmark("Glute", 4.0, 8.0, 14.0, 6.0),
    landmark("Calf", 6.0, 10.0, 16.0, 8.0),
    landmark("Tricep", 4.0, 8.0, 14.0, 6.0),
    landmark("Bicep", 4.0, 8.0, 14.0, 6.0),
    landmark("LateralDeltoid", 4.0, 8.0, 14.0, 6.0),
    landmark("PosteriorDeltoid", 4.0, 8.0, 14.0, 6.0),
    landmark("AnteriorDeltoid", 4.0, 8.0, 14.0, 6.0),
)

val SPECIALIZATION_PRESETS: Map<String, Map<String, String>> = mapOf(
    "maintenance" to emptyMap(),
    "arms" to mapOf("Bicep" to "Specialize", "Tricep" to "Specialize"),
    "chest" to mapOf("Chest" to "Specialize"),
    "back" to mapOf("Back" to "Specialize"),
    "glutes" to mapOf("Glute" to "Specialize"),
    "shoulders" to mapOf("LateralDeltoid" to "Specialize", "AnteriorDeltoid" to "Specialize"),
    "legs" to mapOf("Quad" to "Specialize", "Glute" to "Specialize"),
)

data class ScenarioConfig(
    val days: Int,
    val preset: String,
    val label: String,
)

val WEEKDAY_LAYOUTS: Map<String, List<String>> = mapOf(
    "sequential" to listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"),
    "mwf" to listOf("Monday", "Wednesday", "Friday"),
    "spread5" to listOf("Monday", "Tuesday", "Thursday", "Friday", "Saturday"),
    "mtwfs" to listOf("Monday", "Tuesday", "Wednesday", "Friday", "Saturday"),
    "sean5" to listOf("Sunday", "Monday", "Wednesday", "Thursday", "Friday"),
    "weird4" to listOf("Monday", "Tuesday", "Wednesday", "Friday"),
)

val GENERATION_SCENARIOS: List<ScenarioConfig> = buildList {
    for (days in listOf(3, 4, 5, 6)) {
        for (preset in SPECIALIZATION_PRESETS.keys) {
            add(ScenarioConfig(days = days, preset = preset, label = "$days-day $preset"))
        }
    }
}

/** Key presets across non-sequential weekday layouts for synthesis regression. */
val WEEKDAY_LAYOUT_SCENARIOS: List<ScenarioConfig> = buildList {
    add(ScenarioConfig(days = 3, preset = "arms", label = "3-day mwf arms"))
    add(ScenarioConfig(days = 5, preset = "arms", label = "5-day spread5 arms"))
    add(ScenarioConfig(days = 4, preset = "chest", label = "4-day spread5 chest"))
}

fun scenarioContext(days: Int, specialization: Map<String, String>): ProgramGenerationContext =
    parseScenarioContext(scenarioPayload(days, specialization))

fun scenarioPayload(
    days: Int,
    specialization: Map<String, String>,
    sessionLengthMinutes: Int = 75,
    weekdayLayout: String = "sequential",
): String {
    val layoutDays = WEEKDAY_LAYOUTS[weekdayLayout] ?: WEEKDAY_LAYOUTS.getValue("sequential")
    val weekdays =
        if (layoutDays.size >= days) {
            layoutDays.take(days)
        } else {
            val padded = layoutDays.toMutableList()
            val sequential = WEEKDAY_LAYOUTS.getValue("sequential")
            while (padded.size < days) {
                padded += sequential[padded.size]
            }
            padded.take(days)
        }.joinToString(",")
    return Json.encodeToString(
        JsonObject.serializer(),
        JsonObject(
            mapOf(
                "profile" to jsonObj(
                    "trainingAge" to JsonPrimitive("Advanced"),
                    "sessionLengthMinutes" to JsonPrimitive(sessionLengthMinutes),
                    "weekdays" to JsonPrimitive(weekdays),
                    "trainingDaysPerWeek" to JsonPrimitive(days),
                ),
                "specialization" to JsonObject(
                    specialization.mapValues { JsonPrimitive(it.value) },
                ),
                "provenLifts" to liftsToJson(SCENARIO_LIFT_CATALOG),
                "availableLifts" to JsonArray(emptyList()),
                "literatureVolumeLandmarks" to landmarksToJson(SCENARIO_LANDMARKS),
            ),
        ),
    )
}

fun parseScenarioContext(payload: String): ProgramGenerationContext {
    val root = Json.parseToJsonElement(payload).jsonObject
    val profile = root["profile"]?.jsonObject ?: JsonObject(emptyMap())
    val trainingAge = profile.string("trainingAge").orEmpty()
    val sessionLengthMinutes = profile["sessionLengthMinutes"]?.jsonPrimitive?.intOrNull
    val trainingDaysPerWeek = profile["trainingDaysPerWeek"]?.jsonPrimitive?.intOrNull
        ?: profile.string("weekdays")?.split(",")?.count { it.isNotBlank() }
        ?: 0
    val weekdays = profile.string("weekdays")
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()
    val specialization = root["specialization"]?.jsonObject
        ?.mapNotNull { (muscle, value) ->
            value.jsonPrimitive.contentOrNull?.let { muscle to it }
        }
        ?.toMap()
        .orEmpty()

    val liftsById = linkedMapOf<String, LiftContext>()
    val enabledLiftIds = mutableSetOf<String>()
    val movementPatternByLiftId = mutableMapOf<String, String>()
    for (lift in readLiftArray(root["provenLifts"], provenTier = true) +
        readLiftArray(root["availableLifts"], provenTier = false)) {
        liftsById[lift.id] = lift
        enabledLiftIds += lift.id
        movementPatternByLiftId[lift.id] = lift.movementPattern
    }

    val volumeLandmarksByMuscle = SCENARIO_LANDMARKS.associateBy { it.muscle }.toMutableMap()
    for (row in root["literatureVolumeLandmarks"]?.jsonArray.orEmpty()) {
        readLandmark(row, VolumeLandmarkSource.Literature)?.let { landmark ->
            volumeLandmarksByMuscle.putIfAbsent(landmark.muscle, landmark)
        }
    }

    return ProgramGenerationContext(
        trainingAge = trainingAge,
        lastTrained = null,
        sessionLengthMinutes = sessionLengthMinutes,
        trainingDaysPerWeek = trainingDaysPerWeek,
        weekdays = weekdays,
        specialization = specialization,
        liftsById = liftsById,
        volumeLandmarksByMuscle = volumeLandmarksByMuscle,
        volumeResponsesByMuscle = emptyMap(),
        enabledLiftIds = enabledLiftIds,
        movementPatternByLiftId = movementPatternByLiftId,
        liftEffectivenessByLiftId = emptyMap(),
    )
}

private fun lift(
    id: String,
    name: String,
    pattern: String,
    primary: List<String>,
    secondary: List<String> = emptyList(),
): LiftContext = LiftContext(
    id = id,
    name = name,
    movementPattern = pattern,
    movementPatterns = listOf(pattern),
    primaryMuscles = primary,
    secondaryMuscles = secondary,
    proven = true,
)

private fun landmark(
    muscle: String,
    mev: Double,
    mav: Double,
    mrv: Double,
    pv: Double,
): VolumeLandmarkContext = VolumeLandmarkContext(
    muscle = muscle,
    estimatedMev = mev,
    estimatedMav = mav,
    estimatedMrv = mrv,
    estimatedPv = pv,
    source = VolumeLandmarkSource.Literature,
)

private fun liftsToJson(lifts: List<LiftContext>): JsonArray =
    JsonArray(
        lifts.map { lift ->
            jsonObj(
                "id" to JsonPrimitive(lift.id),
                "name" to JsonPrimitive(lift.name),
                "movementPattern" to JsonPrimitive(lift.movementPattern),
                "primaryMuscles" to JsonArray(lift.primaryMuscles.map(::JsonPrimitive)),
                "secondaryMuscles" to JsonArray(lift.secondaryMuscles.map(::JsonPrimitive)),
            )
        },
    )

private fun landmarksToJson(landmarks: List<VolumeLandmarkContext>): JsonArray =
    JsonArray(
        landmarks.map { row ->
            jsonObj(
                "muscle" to JsonPrimitive(row.muscle),
                "estimatedMev" to JsonPrimitive(row.estimatedMev),
                "estimatedMav" to JsonPrimitive(row.estimatedMav),
                "estimatedMrv" to JsonPrimitive(row.estimatedMrv),
                "estimatedPv" to JsonPrimitive(row.estimatedPv),
            )
        },
    )

private fun readLiftArray(element: JsonElement?, provenTier: Boolean): List<LiftContext> =
    element?.jsonArray.orEmpty().mapNotNull { readLift(it, provenTier) }

private fun readLift(element: JsonElement, provenTier: Boolean): LiftContext? {
    val lift = element.jsonObject
    val id = lift.string("id") ?: return null
    val name = lift.string("name") ?: id
    val movementPatterns = lift.stringList("movementPatterns")
    val movementPattern = movementPatterns.firstOrNull() ?: lift.string("movementPattern").orEmpty()
    return LiftContext(
        id = id,
        name = name,
        movementPattern = movementPattern,
        movementPatterns = if (movementPatterns.isEmpty()) listOf(movementPattern) else movementPatterns,
        primaryMuscles = lift.stringList("primaryMuscles"),
        secondaryMuscles = lift.stringList("secondaryMuscles"),
        mainstream = lift["mainstream"]?.jsonPrimitive?.booleanOrNull,
        proven = provenTier || lift["proven"]?.jsonPrimitive?.booleanOrNull == true,
    )
}

private fun readLandmark(element: JsonElement, source: VolumeLandmarkSource): VolumeLandmarkContext? {
    val row = element.jsonObject
    val muscle = row.string("muscle") ?: return null
    return VolumeLandmarkContext(
        muscle = muscle,
        estimatedMev = row.number("estimatedMev", 8.0),
        estimatedMav = row.number("estimatedMav", 14.0),
        estimatedMrv = row.number("estimatedMrv", 20.0),
        estimatedPv = row.number("estimatedPv", 11.0),
        source = source,
    )
}

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

private fun JsonObject.stringList(key: String): List<String> =
    this[key]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }

private fun JsonObject.number(key: String, fallback: Double): Double =
    this[key]?.jsonPrimitive?.doubleOrNull ?: fallback

private fun jsonObj(vararg pairs: Pair<String, JsonElement>): JsonObject =
    JsonObject(mapOf(*pairs))
