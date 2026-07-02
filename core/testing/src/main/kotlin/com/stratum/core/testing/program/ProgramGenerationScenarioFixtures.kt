@file:Suppress("MagicNumber")

package com.stratum.core.testing.program

import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.MuscleLengthBias
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.context.MuscleVolumeSnapshotContext
import com.stratum.domain.program.generation.context.VolumeSnapshotSource
import com.stratum.domain.program.LiteratureVolumeDefaultsProvider
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

private val SCENARIO_MUSCLE_NAMES =
    setOf(
        "Chest",
        "Back",
        "Quad",
        "Hamstring",
        "Glute",
        "Calf",
        "Tricep",
        "Bicep",
        "LateralDeltoid",
        "PosteriorDeltoid",
        "AnteriorDeltoid",
    )

/** Literature defaults from [LiteratureVolumeDefaultsProvider] — kept in sync with production priors. */
val SCENARIO_SNAPSHOTS: List<MuscleVolumeSnapshotContext> =
    LiteratureVolumeDefaultsProvider.all()
        .filter { literature -> literature.muscle.name in SCENARIO_MUSCLE_NAMES }
        .map { literature -> literature.toScenarioSnapshot() }

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
                "literatureVolumeSnapshots" to snapshotsToJson(SCENARIO_SNAPSHOTS),
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

    val muscleVolumeSnapshotsByMuscle = SCENARIO_SNAPSHOTS.associateBy { it.muscle }.toMutableMap()
    for (row in root["literatureVolumeSnapshots"]?.jsonArray.orEmpty()) {
        readSnapshot(row, VolumeSnapshotSource.Literature)?.let { snapshot ->
            muscleVolumeSnapshotsByMuscle.putIfAbsent(snapshot.muscle, snapshot)
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
        muscleVolumeSnapshotsByMuscle = muscleVolumeSnapshotsByMuscle,
        volumeCorrelationsByMuscle = emptyMap(),
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

private fun com.stratum.domain.program.LiteratureVolumeDefaults.toScenarioSnapshot(): MuscleVolumeSnapshotContext =
    MuscleVolumeSnapshotContext(
        muscle = muscle.name,
        retentionFloorMv = retentionFloorMv,
        paretoVolumePv = paretoVolumePv,
        recoverabilityCeilingRc = recoverabilityCeilingRc,
        growthEntryFloor = growthEntryFloor,
        maintenanceCeiling = maintenanceCeiling,
        posture = null,
        source = VolumeSnapshotSource.Literature,
    )

private fun snapshotsToJson(snapshots: List<MuscleVolumeSnapshotContext>): JsonArray =
    JsonArray(
        snapshots.map { row ->
            jsonObj(
                "muscle" to JsonPrimitive(row.muscle),
                "retentionFloorMv" to JsonPrimitive(row.retentionFloorMv),
                "paretoVolumePv" to JsonPrimitive(row.paretoVolumePv),
                "recoverabilityCeilingRc" to JsonPrimitive(row.recoverabilityCeilingRc),
                "growthEntryFloor" to JsonPrimitive(row.growthEntryFloor),
                "maintenanceCeiling" to JsonPrimitive(row.maintenanceCeiling),
            )
        },
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

private fun readSnapshot(element: JsonElement, source: VolumeSnapshotSource): MuscleVolumeSnapshotContext? {
    val row = element.jsonObject
    val muscle = row.string("muscle") ?: return null
    return MuscleVolumeSnapshotContext(
        muscle = muscle,
        retentionFloorMv = row.number("retentionFloorMv", 4.0),
        paretoVolumePv = row.number("paretoVolumePv", 13.0),
        recoverabilityCeilingRc = row.number("recoverabilityCeilingRc", 20.0),
        growthEntryFloor = row.number("growthEntryFloor", 6.0),
        maintenanceCeiling = row.number("maintenanceCeiling", 16.5),
        posture = null,
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
