@file:Suppress("MagicNumber")

package com.stratum.core.testing.program

import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.onboarding.Weekday
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

val WEEKDAY_LAYOUTS: Map<String, List<String>> = mapOf(
    "sequential" to listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"),
    "mwf" to listOf("Monday", "Wednesday", "Friday"),
    "spread5" to listOf("Monday", "Tuesday", "Thursday", "Friday", "Saturday"),
    "mtwfs" to listOf("Monday", "Tuesday", "Wednesday", "Friday", "Saturday"),
    "sean5" to listOf("Sunday", "Monday", "Wednesday", "Thursday", "Friday"),
    "weird4" to listOf("Monday", "Tuesday", "Wednesday", "Friday"),
)

fun scenarioContext(days: Int, specialization: Map<String, String>): ProgramGenerationContext =
    parseScenarioContext(scenarioPayload(days, specialization))

fun scenarioPayload(
    days: Int,
    specialization: Map<String, String>,
    sessionLengthMinutes: Int = 75,
    weekdayLayout: String = "sequential",
): String {
    val (cycleLength, restSlotIndices) = cycleSpaceForWeekdayLayout(days, weekdayLayout)
    return Json.encodeToString(
        JsonObject.serializer(),
        JsonObject(
            mapOf(
                "profile" to jsonObj(
                    "trainingAge" to JsonPrimitive("Advanced"),
                    "sessionLengthMinutes" to JsonPrimitive(sessionLengthMinutes),
                    "trainingDaysPerWeek" to JsonPrimitive(days),
                    // Gen schedule identity (not weekday labels): N + rests + length.
                    "cycleLength" to JsonPrimitive(cycleLength),
                    "restSlotIndices" to JsonArray(
                        restSlotIndices.sorted().map { index -> JsonPrimitive(index) },
                    ),
                    "weekdayLayout" to JsonPrimitive(weekdayLayout),
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

/**
 * Maps a named [WEEKDAY_LAYOUTS] key + train-day count N onto a classic 7-slot cycle
 * (Mon=0…Sun=6). Takes the first N labels from the layout (or all if shorter) as training
 * ordinals; rests are the complement.
 */
fun cycleSpaceForWeekdayLayout(days: Int, weekdayLayout: String): Pair<Int, Set<Int>> {
    val layout =
        WEEKDAY_LAYOUTS[weekdayLayout]
            ?: WEEKDAY_LAYOUTS.getValue("sequential")
    val trainOrdinals =
        layout
            .take(days.coerceAtLeast(1))
            .mapNotNull { name -> runCatching { Weekday.valueOf(name).ordinal }.getOrNull() }
            .toSet()
            .ifEmpty { (0 until days.coerceIn(1, 7)).toSet() }
    val restSlotIndices = (0 until 7).filterNot { index -> index in trainOrdinals }.toSet()
    return 7 to restSlotIndices
}

fun parseScenarioContext(payload: String): ProgramGenerationContext {
    val root = Json.parseToJsonElement(payload).jsonObject
    val profile = root["profile"]?.jsonObject ?: JsonObject(emptyMap())
    val trainingAge = profile.string("trainingAge").orEmpty()
    val sessionLengthMinutes = profile["sessionLengthMinutes"]?.jsonPrimitive?.intOrNull
    val trainingDaysPerWeek = profile["trainingDaysPerWeek"]?.jsonPrimitive?.intOrNull ?: 0
    val cycleLength = profile["cycleLength"]?.jsonPrimitive?.intOrNull
    val restSlotIndices =
        profile["restSlotIndices"]
            ?.jsonArray
            ?.mapNotNull { element -> element.jsonPrimitive.intOrNull }
            ?.toSet()
    val specialization = root["specialization"]?.jsonObject
        ?.mapNotNull { (muscle, value) ->
            val mg = runCatching { MuscleGroup.valueOf(muscle) }.getOrNull() ?: return@mapNotNull null
            val level = value.jsonPrimitive.contentOrNull
                ?.let { runCatching { SpecializationLevel.valueOf(it) }.getOrNull() }
                ?: return@mapNotNull null
            mg to level
        }
        ?.toMap()
        .orEmpty()

    val liftsById = linkedMapOf<String, LiftContext>()
    val enabledLiftIds = mutableSetOf<String>()
    val movementPatternByLiftId = mutableMapOf<String, MovementPattern?>()
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
        specialization = specialization,
        liftsById = liftsById,
        muscleVolumeSnapshotsByMuscle = muscleVolumeSnapshotsByMuscle,
        volumeCorrelationsByMuscle = emptyMap(),
        enabledLiftIds = enabledLiftIds,
        movementPatternByLiftId = movementPatternByLiftId,
        liftEffectivenessByLiftId = emptyMap(),
        restSlotIndices = restSlotIndices,
        cycleLength = cycleLength,
    )
}

private fun lift(
    id: String,
    name: String,
    pattern: String,
    primary: List<String>,
    secondary: List<String> = emptyList(),
    fatigueProfile: String = when (pattern) {
        "SquatPattern", "HipHinge" -> if ("barbell" in name.lowercase()) "High" else "Medium"
        "HipExtension", "HorizontalPress", "VerticalPress", "InclinePress",
        "HorizontalPull", "VerticalPull" -> "Medium"
        else -> "Low"
    },
): LiftContext {
    val movementPattern = runCatching { MovementPattern.valueOf(pattern) }.getOrNull()
    return LiftContext(
        id = id,
        name = name,
        movementPattern = movementPattern,
        movementPatterns = listOfNotNull(movementPattern).toSet(),
        primaryMuscles = primary.mapNotNull { runCatching { MuscleGroup.valueOf(it) }.getOrNull() }.toSet(),
        secondaryMuscles = secondary.mapNotNull { runCatching { MuscleGroup.valueOf(it) }.getOrNull() }.toSet(),
        proven = true,
        fatigueProfile = runCatching { FatigueProfile.valueOf(fatigueProfile) }.getOrDefault(FatigueProfile.Low),
    )
}

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
                "movementPattern" to JsonPrimitive(lift.movementPattern?.name),
                "primaryMuscles" to JsonArray(lift.primaryMuscles.map { JsonPrimitive(it.name) }),
                "secondaryMuscles" to JsonArray(lift.secondaryMuscles.map { JsonPrimitive(it.name) }),
            )
        },
    )

private fun readLiftArray(element: JsonElement?, provenTier: Boolean): List<LiftContext> =
    element?.jsonArray.orEmpty().mapNotNull { readLift(it, provenTier) }

private fun readLift(element: JsonElement, provenTier: Boolean): LiftContext? {
    val lift = element.jsonObject
    val id = lift.string("id") ?: return null
    val name = lift.string("name") ?: id
    val movementPatternStrings = lift.stringList("movementPatterns")
    val movementPattern = (movementPatternStrings.firstOrNull() ?: lift.string("movementPattern"))
        ?.let { runCatching { MovementPattern.valueOf(it) }.getOrNull() }
    val patterns = movementPatternStrings.mapNotNull { runCatching { MovementPattern.valueOf(it) }.getOrNull() }.toSet()
    return LiftContext(
        id = id,
        name = name,
        movementPattern = movementPattern,
        movementPatterns = if (patterns.isEmpty()) setOfNotNull(movementPattern) else patterns,
        primaryMuscles = lift.stringList("primaryMuscles").mapNotNull { runCatching { MuscleGroup.valueOf(it) }.getOrNull() }.toSet(),
        secondaryMuscles = lift.stringList("secondaryMuscles").mapNotNull { runCatching { MuscleGroup.valueOf(it) }.getOrNull() }.toSet(),
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
