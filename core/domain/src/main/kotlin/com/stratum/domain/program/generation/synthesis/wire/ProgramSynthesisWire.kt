package com.stratum.domain.program.generation.synthesis.wire

import com.stratum.domain.json.JsonObject
import com.stratum.domain.json.JsonReader
import com.stratum.domain.json.jsonArray
import com.stratum.domain.json.jsonNumber
import com.stratum.domain.json.jsonObject
import com.stratum.domain.json.jsonString
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.slots.SessionTypeId
import com.stratum.domain.program.generation.split.ResolvedSessionPlan
import com.stratum.domain.program.generation.structure.SessionRegionFilter
import com.stratum.domain.program.generation.synthesis.ProgramSynthesisResult
import com.stratum.domain.program.generation.volume.WeeklyVolumeTargets

/**
 * Decoded synthesis request envelope for cloud CP-SAT (request body).
 */
data class ProgramSynthesisWireRequest(
    val weeklyTargets: WeeklyVolumeTargets,
    val context: ProgramGenerationContext,
)

/**
 * Hand-rolled JSON wire codec for [ProgramSynthesisPort] request/response.
 *
 * Domain models stay free of serialization annotations; this object is the only boundary that
 * knows the JSON shape for cloud synthesis.
 *
 * ### Request JSON shape
 * ```json
 * {
 *   "weeklyTargets": { "Chest": { "min": 0, "aim": 10, "max": 20 }, ... },
 *   "context": {
 *     "trainingAge": "...",
 *     "lastTrained": null,
 *     "sessionLengthMinutes": 60,
 *     "trainingDaysPerWeek": 4,
 *     "weekdays": ["Monday", ...],
 *     "specialization": { "Chest": "Specialize", ... },
 *     "liftsById": { "lift-id": { ...LiftContext fields... }, ... },
 *     "muscleVolumeSnapshotsByMuscle": { ... },
 *     "volumeCorrelationsByMuscle": { ... },
 *     "enabledLiftIds": ["..."],
 *     "movementPatternByLiftId": { "id": "HorizontalPress" | null },
 *     "liftEffectivenessByLiftId": { ... }
 *   }
 * }
 * ```
 *
 * ### Result JSON shape
 * Fully reconstructs [ProgramSynthesisResult]:
 * - `sessions`: [ResolvedSessionPlan] list
 * - `plans`: [SessionSlotPlan] list (`filledSlots` as arrays; `setCount` as number)
 * - `weekdayIndexByPosition`: int array
 * - `objective`: [SynthesisObjectiveBreakdown] (all Long fields)
 * - `activeSlotIdsBySession`: array of string arrays
 * - `regionFilterBySession`: array of [SessionRegionFilter] enum name strings
 *   (optional on **decode** for rolling-deploy compatibility with pre-field solvers;
 *   inferred from legacy `sessions[].sessionType` when absent/empty)
 *
 * Enums serialize as **enum name** strings (`MuscleGroup.Chest` → `"Chest"`).
 *
 * Nested encode/decode lives in package-private sibling files
 * (`ContextWireCodec`, `ResultWireCodec`, `WireCodecHelpers`).
 */
object ProgramSynthesisWire {
    fun encodeRequest(weeklyTargets: WeeklyVolumeTargets, context: ProgramGenerationContext): String =
        jsonObject(
            "weeklyTargets" to encodeWeeklyTargets(weeklyTargets),
            "context" to encodeContext(context),
        )

    fun decodeRequest(json: String): ProgramSynthesisWireRequest =
        wrapParse("request") {
            val root = JsonReader(json).readRootObject()
            ProgramSynthesisWireRequest(
                weeklyTargets = decodeWeeklyTargets(root.requireObject("weeklyTargets")),
                context = decodeContext(root.requireObject("context")),
            )
        }

    fun encodeResult(result: ProgramSynthesisResult): String =
        jsonObject(
            "sessions" to jsonArray(result.sessions.map { encodeResolvedSession(it) }),
            "plans" to jsonArray(result.plans.map { encodeSessionSlotPlan(it) }),
            "weekdayIndexByPosition" to jsonArray(result.weekdayIndexByPosition.map { jsonNumber(it) }),
            "objective" to encodeObjective(result.objective),
            "activeSlotIdsBySession" to
                jsonArray(
                    result.activeSlotIdsBySession.map { sessionIds ->
                        jsonArray(sessionIds.map { jsonString(it) })
                    },
                ),
            "regionFilterBySession" to
                jsonArray(result.regionFilterBySession.map { jsonString(it.name) }),
        )

    fun decodeResult(json: String): ProgramSynthesisResult =
        wrapParse("result") {
            val root = JsonReader(json).readRootObject()
            val sessions =
                root.requireArray("sessions").values.map {
                    decodeResolvedSession(it.requireAsObject("sessions[]"))
                }
            ProgramSynthesisResult(
                sessions = sessions,
                plans = root.requireArray("plans").values.map {
                    decodeSessionSlotPlan(it.requireAsObject("plans[]"))
                },
                weekdayIndexByPosition =
                    root.requireArray("weekdayIndexByPosition").values.map {
                        it.asIntOrNull() ?: error("Expected int in weekdayIndexByPosition")
                    },
                objective = decodeObjective(root.requireObject("objective")),
                activeSlotIdsBySession =
                    root.requireArray("activeSlotIdsBySession").values.map { sessionVal ->
                        val arr = sessionVal.asArrayOrNull()
                            ?: error("Expected array in activeSlotIdsBySession")
                        arr.values.map {
                            it.asStringOrNull() ?: error("Expected string slot id")
                        }.toSet()
                    },
                regionFilterBySession = decodeRegionFilterBySession(root, sessions),
            )
        }
}

/**
 * Prefer explicit wire field; when missing/empty (legacy cloud results), derive coarse
 * filters from legacy [ResolvedSessionPlan.sessionType] display labels.
 */
internal fun decodeRegionFilterBySession(
    root: JsonObject,
    sessions: List<ResolvedSessionPlan>,
): List<SessionRegionFilter> {
    val raw = root["regionFilterBySession"]
    if (raw == null || raw.isNull()) {
        return sessions.map { sessionTypeIdToRegionFilter(it.sessionType) }
    }
    val arr = raw.asArrayOrNull()
        ?: error("Expected array in regionFilterBySession")
    if (arr.values.isEmpty() && sessions.isNotEmpty()) {
        return sessions.map { sessionTypeIdToRegionFilter(it.sessionType) }
    }
    return arr.values.map { filterVal ->
        val name = filterVal.asStringOrNull()
            ?: error("Expected string in regionFilterBySession")
        enumValueOf<SessionRegionFilter>(name)
    }
}

/** Coarse legacy map: old wire only had SessionTypeId labels, not structure filters. */
internal fun sessionTypeIdToRegionFilter(sessionType: SessionTypeId): SessionRegionFilter =
    when (sessionType) {
        SessionTypeId.Lower,
        SessionTypeId.QuadsCalves,
        SessionTypeId.GlutesHams,
        -> SessionRegionFilter.LowerOnly
        SessionTypeId.FullBody,
        SessionTypeId.FullPush,
        SessionTypeId.FullPull,
        -> SessionRegionFilter.Unrestricted
        else -> SessionRegionFilter.UpperOnly
    }

/** Thrown when synthesis wire JSON cannot be parsed or fails schema checks. */
class ProgramSynthesisWireException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)
