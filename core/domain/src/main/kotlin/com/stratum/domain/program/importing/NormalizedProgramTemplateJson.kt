package com.stratum.domain.program.importing

import com.stratum.domain.json.JsonArray
import com.stratum.domain.json.JsonObject
import com.stratum.domain.json.JsonReader

/**
 * Parses the normalized program-template JSON produced by the server (deterministic parser or LLM
 * conversion) into a [NormalizedProgramTemplate].
 *
 * Hand-rolled rather than kotlinx.serialization so the pure-JVM `:core:domain` module stays free of
 * serialization framework dependencies (ARCHITECTURE.md §2 — stdlib, coroutines, javax.inject only).
 * The shape is narrow and controlled by this codebase's server side, so a focused parser is robust.
 *
 * Expected shape:
 * ```
 * { "programName": "..." | null, "cycleLength": N | null,
 *   "workouts": [{ "name": "...", "weekday": "..." | null, "position": N,
 *     "lifts": [{ "name": "...", "position": N, "supersetGroupKey": "..." | null,
 *       "sets": [{ "position": N, "setTypeLabel": "..." | null,
 *                  "repMin": N | null, "repMax": N | null, "rirGoal": N | null,
 *                  "restSeconds": N | null, "isTopSet": bool }] }] }] }
 * ```
 */
internal object NormalizedProgramTemplateJson {
    fun parse(json: String): NormalizedProgramTemplate {
        val root = JsonReader(json).readRootObject()
        return parseTemplate(root)
    }

    private fun parseTemplate(obj: JsonObject): NormalizedProgramTemplate {
        val workouts = (obj["workouts"] as? JsonArray)
            ?.values
            ?.mapNotNull { it as? JsonObject }
            ?.mapIndexed { index, workout -> parseWorkout(workout, index) }
            ?: emptyList()
        return NormalizedProgramTemplate(
            programName = obj.optionalString("programName"),
            cycleLength = obj.optionalInt("cycleLength"),
            workouts = workouts,
        )
    }

    private fun parseWorkout(obj: JsonObject, fallbackIndex: Int): NormalizedProgramTemplateWorkout =
        NormalizedProgramTemplateWorkout(
            name = obj.optionalString("name").orEmpty(),
            weekday = obj.optionalString("weekday"),
            position = obj.optionalInt("position") ?: fallbackIndex,
            lifts = (obj["lifts"] as? JsonArray)
                ?.values
                ?.mapNotNull { it as? JsonObject }
                ?.mapIndexed { index, lift -> parseLift(lift, index) }
                ?: emptyList(),
            restDay = obj.optionalBoolean("restDay") ?: false,
        )

    private fun parseLift(obj: JsonObject, fallbackIndex: Int): NormalizedProgramTemplateLift =
        NormalizedProgramTemplateLift(
            name = obj.optionalString("name").orEmpty(),
            position = obj.optionalInt("position") ?: fallbackIndex,
            sets = (obj["sets"] as? JsonArray)
                ?.values
                ?.mapNotNull { it as? JsonObject }
                ?.mapIndexed { index, set -> parseSet(set, index) }
                ?: emptyList(),
            supersetGroupKey = obj.optionalString("supersetGroupKey"),
        )

    private fun parseSet(obj: JsonObject, fallbackIndex: Int): NormalizedProgramTemplateSet =
        NormalizedProgramTemplateSet(
            position = obj.optionalInt("position") ?: fallbackIndex,
            setTypeLabel = obj.optionalString("setTypeLabel"),
            repMin = obj.optionalInt("repMin"),
            repMax = obj.optionalInt("repMax"),
            rirGoal = obj.optionalInt("rirGoal"),
            restSeconds = obj.optionalInt("restSeconds"),
            isTopSet = obj.optionalBoolean("isTopSet") ?: false,
        )
}
