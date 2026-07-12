@file:Suppress("TooManyFunctions")

package com.stratum.domain.program.generation.synthesis.wire

import com.stratum.domain.json.JsonArray
import com.stratum.domain.json.JsonNull
import com.stratum.domain.json.JsonObject
import com.stratum.domain.json.JsonReader
import com.stratum.domain.json.JsonValue
import com.stratum.domain.json.jsonArray
import com.stratum.domain.json.jsonBoolean
import com.stratum.domain.json.jsonBooleanOrNull
import com.stratum.domain.json.jsonNumber
import com.stratum.domain.json.jsonNumberOrNull
import com.stratum.domain.json.jsonObject
import com.stratum.domain.json.jsonString
import com.stratum.domain.json.jsonStringOrNull
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.KinematicProfile
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.LiftEffectivenessEntry
import com.stratum.domain.program.generation.context.MuscleLengthBias
import com.stratum.domain.program.generation.context.MuscleVolumeSnapshotContext
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.context.VolumeCorrelationContext
import com.stratum.domain.program.generation.context.VolumeSnapshotSource
import com.stratum.domain.program.generation.prescription.FilledSlot
import com.stratum.domain.program.generation.prescription.SessionSlotPlan
import com.stratum.domain.program.generation.prescription.SynthesisObjectiveBreakdown
import com.stratum.domain.program.generation.reference.RpRepProfile
import com.stratum.domain.program.generation.split.MuscleLengthBiasPreference
import com.stratum.domain.program.generation.split.ResolvedSessionPlan
import com.stratum.domain.program.generation.split.SessionComposition
import com.stratum.domain.program.generation.split.SessionTypeId
import com.stratum.domain.program.generation.split.SiblingVariant
import com.stratum.domain.program.generation.split.SlotRole
import com.stratum.domain.program.generation.split.WorkoutSlotTemplate
import com.stratum.domain.program.generation.synthesis.ProgramSynthesisResult
import com.stratum.domain.program.generation.volume.WeeklyVolumeTarget
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
 *
 * Enums serialize as **enum name** strings (`MuscleGroup.Chest` → `"Chest"`).
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
        )

    fun decodeResult(json: String): ProgramSynthesisResult =
        wrapParse("result") {
            val root = JsonReader(json).readRootObject()
            ProgramSynthesisResult(
                sessions = root.requireArray("sessions").values.map {
                    decodeResolvedSession(it.requireAsObject("sessions[]"))
                },
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
            )
        }

    // region Weekly targets

    private fun encodeWeeklyTargets(targets: WeeklyVolumeTargets): String =
        jsonObject(
            targets.map { (muscle, target) ->
                muscle to
                    jsonObject(
                        "min" to jsonNumber(target.min),
                        "aim" to jsonNumber(target.aim),
                        "max" to jsonNumber(target.max),
                    )
            },
        )

    private fun decodeWeeklyTargets(obj: JsonObject): WeeklyVolumeTargets =
        obj.entries.mapValues { (_, value) ->
            val t = value.requireAsObject("weeklyTargets entry")
            WeeklyVolumeTarget(
                min = t.requireDouble("min"),
                aim = t.requireDouble("aim"),
                max = t.requireDouble("max"),
            )
        }

    // endregion

    // region Context

    private fun encodeContext(context: ProgramGenerationContext): String =
        jsonObject(
            "trainingAge" to jsonString(context.trainingAge),
            "lastTrained" to jsonStringOrNull(context.lastTrained),
            "sessionLengthMinutes" to jsonNumberOrNull(context.sessionLengthMinutes),
            "trainingDaysPerWeek" to jsonNumber(context.trainingDaysPerWeek),
            "weekdays" to jsonArray(context.weekdays.map { jsonString(it) }),
            "specialization" to
                jsonObject(
                    context.specialization.map { (muscle, level) ->
                        muscle.name to jsonString(level.name)
                    },
                ),
            "liftsById" to
                jsonObject(
                    context.liftsById.map { (id, lift) -> id to encodeLiftContext(lift) },
                ),
            "muscleVolumeSnapshotsByMuscle" to
                jsonObject(
                    context.muscleVolumeSnapshotsByMuscle.map { (muscle, snap) ->
                        muscle to encodeMuscleVolumeSnapshot(snap)
                    },
                ),
            "volumeCorrelationsByMuscle" to
                jsonObject(
                    context.volumeCorrelationsByMuscle.map { (muscle, corr) ->
                        muscle to encodeVolumeCorrelation(corr)
                    },
                ),
            "enabledLiftIds" to jsonArray(context.enabledLiftIds.map { jsonString(it) }),
            "movementPatternByLiftId" to
                jsonObject(
                    context.movementPatternByLiftId.map { (id, pattern) ->
                        id to jsonStringOrNull(pattern?.name)
                    },
                ),
            "liftEffectivenessByLiftId" to
                jsonObject(
                    context.liftEffectivenessByLiftId.map { (id, entry) ->
                        id to encodeLiftEffectiveness(entry)
                    },
                ),
        )

    private fun decodeContext(obj: JsonObject): ProgramGenerationContext =
        ProgramGenerationContext(
            trainingAge = obj.requireString("trainingAge"),
            lastTrained = obj.optionalString("lastTrained"),
            sessionLengthMinutes = obj.optionalInt("sessionLengthMinutes"),
            trainingDaysPerWeek = obj.requireInt("trainingDaysPerWeek"),
            weekdays =
                obj.requireArray("weekdays").values.map {
                    it.asStringOrNull() ?: error("Expected string weekday")
                },
            specialization =
                obj.requireObject("specialization").entries.mapKeys { (k, _) ->
                    enumValueOf<MuscleGroup>(k)
                }.mapValues { (_, v) ->
                    enumValueOf<SpecializationLevel>(
                        v.asStringOrNull() ?: error("Expected specialization level string"),
                    )
                },
            liftsById =
                obj.requireObject("liftsById").entries.mapValues { (_, v) ->
                    decodeLiftContext(v.requireAsObject("liftsById entry"))
                },
            muscleVolumeSnapshotsByMuscle =
                obj.requireObject("muscleVolumeSnapshotsByMuscle").entries.mapValues { (_, v) ->
                    decodeMuscleVolumeSnapshot(v.requireAsObject("snapshot"))
                },
            volumeCorrelationsByMuscle =
                obj.requireObject("volumeCorrelationsByMuscle").entries.mapValues { (_, v) ->
                    decodeVolumeCorrelation(v.requireAsObject("correlation"))
                },
            enabledLiftIds =
                obj.requireArray("enabledLiftIds").values.map {
                    it.asStringOrNull() ?: error("Expected string lift id")
                }.toSet(),
            movementPatternByLiftId =
                obj.requireObject("movementPatternByLiftId").entries.mapValues { (_, v) ->
                    if (v.isNull()) {
                        null
                    } else {
                        enumValueOf<MovementPattern>(
                            v.asStringOrNull() ?: error("Expected pattern string or null"),
                        )
                    }
                },
            liftEffectivenessByLiftId =
                obj.requireObject("liftEffectivenessByLiftId").entries.mapValues { (_, v) ->
                    decodeLiftEffectiveness(v.requireAsObject("effectiveness"))
                },
        )

    private fun encodeLiftContext(lift: LiftContext): String =
        jsonObject(
            "id" to jsonString(lift.id),
            "name" to jsonString(lift.name),
            "movementPattern" to jsonStringOrNull(lift.movementPattern?.name),
            "movementPatterns" to jsonArray(lift.movementPatterns.map { jsonString(it.name) }),
            "primaryMuscles" to jsonArray(lift.primaryMuscles.map { jsonString(it.name) }),
            "secondaryMuscles" to jsonArray(lift.secondaryMuscles.map { jsonString(it.name) }),
            "mainstream" to jsonBooleanOrNull(lift.mainstream),
            "progressTrend" to jsonStringOrNull(lift.progressTrend),
            "proven" to jsonBooleanOrNull(lift.proven),
            "sessionCount" to jsonNumberOrNull(lift.sessionCount),
            "effectivenessRank" to jsonNumberOrNull(lift.effectivenessRank),
            "effectivenessEvidence" to jsonStringOrNull(lift.effectivenessEvidence),
            "fatigueDecaySlope" to jsonNumberOrNull(lift.fatigueDecaySlope),
            "fatigueDecayIntercept" to jsonNumberOrNull(lift.fatigueDecayIntercept),
            "muscleLengthBias" to jsonStringOrNull(lift.muscleLengthBias?.name),
            "equipment" to jsonStringOrNull(lift.equipment),
            "kinematicProfile" to jsonString(lift.kinematicProfile.name),
            "incrementTiers" to jsonArray(lift.incrementTiers.map { encodeIncrementTier(it) }),
            "estimatedOneRm" to encodeWeightOrNull(lift.estimatedOneRm),
        )

    private fun decodeLiftContext(obj: JsonObject): LiftContext =
        LiftContext(
            id = obj.requireString("id"),
            name = obj.requireString("name"),
            movementPattern = obj.optionalString("movementPattern")?.let { enumValueOf<MovementPattern>(it) },
            movementPatterns =
                obj.requireArray("movementPatterns").values.map {
                    enumValueOf<MovementPattern>(it.asStringOrNull() ?: error("Expected pattern"))
                }.toSet(),
            primaryMuscles =
                obj.requireArray("primaryMuscles").values.map {
                    enumValueOf<MuscleGroup>(it.asStringOrNull() ?: error("Expected muscle"))
                }.toSet(),
            secondaryMuscles =
                obj.requireArray("secondaryMuscles").values.map {
                    enumValueOf<MuscleGroup>(it.asStringOrNull() ?: error("Expected muscle"))
                }.toSet(),
            mainstream = obj.optionalBoolean("mainstream"),
            progressTrend = obj.optionalString("progressTrend"),
            proven = obj.optionalBoolean("proven"),
            sessionCount = obj.optionalInt("sessionCount"),
            effectivenessRank = obj.optionalInt("effectivenessRank"),
            effectivenessEvidence = obj.optionalString("effectivenessEvidence"),
            fatigueDecaySlope = obj.optionalDouble("fatigueDecaySlope"),
            fatigueDecayIntercept = obj.optionalDouble("fatigueDecayIntercept"),
            muscleLengthBias = obj.optionalString("muscleLengthBias")?.let { enumValueOf<MuscleLengthBias>(it) },
            equipment = obj.optionalString("equipment"),
            kinematicProfile =
                obj.optionalString("kinematicProfile")?.let { enumValueOf<KinematicProfile>(it) }
                    ?: KinematicProfile.Isolation,
            incrementTiers =
                (obj["incrementTiers"] as? JsonArray)?.values?.map {
                    decodeIncrementTier(it.requireAsObject("incrementTier"))
                } ?: emptyList(),
            estimatedOneRm = decodeWeightOrNull(obj["estimatedOneRm"]),
        )

    private fun encodeIncrementTier(tier: IncrementTier): String =
        jsonObject(
            "lowerBound" to encodeWeight(tier.lowerBound),
            "upperBound" to encodeWeightOrNull(tier.upperBound),
            "increment" to encodeWeight(tier.increment),
        )

    private fun decodeIncrementTier(obj: JsonObject): IncrementTier =
        IncrementTier(
            lowerBound = decodeWeight(obj.requireObject("lowerBound")),
            upperBound = decodeWeightOrNull(obj["upperBound"]),
            increment = decodeWeight(obj.requireObject("increment")),
        )

    private fun encodeWeight(weight: Weight): String =
        jsonObject(
            "amount" to jsonNumber(weight.amount),
            "unit" to jsonString(weight.unit.name),
        )

    private fun encodeWeightOrNull(weight: Weight?): String =
        if (weight == null) "null" else encodeWeight(weight)

    private fun decodeWeight(obj: JsonObject): Weight =
        Weight(
            amount = obj.requireDouble("amount"),
            unit = enumValueOf<Weight.WeightUnit>(obj.requireString("unit")),
        )

    private fun decodeWeightOrNull(value: JsonValue?): Weight? {
        if (value == null || value.isNull()) return null
        return decodeWeight(value.requireAsObject("weight"))
    }

    private fun encodeMuscleVolumeSnapshot(snap: MuscleVolumeSnapshotContext): String =
        jsonObject(
            "muscle" to jsonString(snap.muscle),
            "retentionFloorMv" to jsonNumber(snap.retentionFloorMv),
            "paretoVolumePv" to jsonNumber(snap.paretoVolumePv),
            "recoverabilityCeilingRc" to jsonNumber(snap.recoverabilityCeilingRc),
            "growthEntryFloor" to jsonNumber(snap.growthEntryFloor),
            "maintenanceCeiling" to jsonNumber(snap.maintenanceCeiling),
            "posture" to jsonStringOrNull(snap.posture),
            "observedDoseMax" to jsonNumberOrNull(snap.observedDoseMax),
            "observedDoseMedian" to jsonNumberOrNull(snap.observedDoseMedian),
            "source" to jsonString(snap.source.name),
        )

    private fun decodeMuscleVolumeSnapshot(obj: JsonObject): MuscleVolumeSnapshotContext =
        MuscleVolumeSnapshotContext(
            muscle = obj.requireString("muscle"),
            retentionFloorMv = obj.requireDouble("retentionFloorMv"),
            paretoVolumePv = obj.requireDouble("paretoVolumePv"),
            recoverabilityCeilingRc = obj.requireDouble("recoverabilityCeilingRc"),
            growthEntryFloor = obj.requireDouble("growthEntryFloor"),
            maintenanceCeiling = obj.requireDouble("maintenanceCeiling"),
            posture = obj.optionalString("posture"),
            observedDoseMax = obj.optionalDouble("observedDoseMax"),
            observedDoseMedian = obj.optionalDouble("observedDoseMedian"),
            source = enumValueOf<VolumeSnapshotSource>(obj.requireString("source")),
        )

    private fun encodeVolumeCorrelation(corr: VolumeCorrelationContext): String =
        jsonObject(
            "muscle" to jsonString(corr.muscle),
            "correlationSlope" to jsonNumber(corr.correlationSlope),
            "evidence" to jsonString(corr.evidence),
        )

    private fun decodeVolumeCorrelation(obj: JsonObject): VolumeCorrelationContext =
        VolumeCorrelationContext(
            muscle = obj.requireString("muscle"),
            correlationSlope = obj.requireDouble("correlationSlope"),
            evidence = obj.requireString("evidence"),
        )

    private fun encodeLiftEffectiveness(entry: LiftEffectivenessEntry): String =
        jsonObject(
            "liftId" to jsonString(entry.liftId),
            "pattern" to jsonString(entry.pattern.name),
            "rank" to jsonNumber(entry.rank),
            "effectivenessScore" to jsonNumberOrNull(entry.effectivenessScore),
            "evidence" to jsonString(entry.evidence),
        )

    private fun decodeLiftEffectiveness(obj: JsonObject): LiftEffectivenessEntry =
        LiftEffectivenessEntry(
            liftId = obj.requireString("liftId"),
            pattern = enumValueOf<MovementPattern>(obj.requireString("pattern")),
            rank = obj.requireInt("rank"),
            effectivenessScore = obj.optionalDouble("effectivenessScore"),
            evidence = obj.requireString("evidence"),
        )

    // endregion

    // region Result nested types

    private fun encodeResolvedSession(session: ResolvedSessionPlan): String =
        jsonObject(
            "sessionType" to jsonString(session.sessionType.name),
            "name" to jsonString(session.name),
            "slots" to jsonArray(session.slots.map { encodeWorkoutSlotTemplate(it) }),
            "siblingIndex" to jsonNumber(session.siblingIndex),
            "position" to jsonNumber(session.position),
            "composition" to jsonStringOrNull(session.composition?.name),
        )

    private fun decodeResolvedSession(obj: JsonObject): ResolvedSessionPlan =
        ResolvedSessionPlan(
            sessionType = enumValueOf<SessionTypeId>(obj.requireString("sessionType")),
            name = obj.requireString("name"),
            slots =
                obj.requireArray("slots").values.map {
                    decodeWorkoutSlotTemplate(it.requireAsObject("slot"))
                },
            siblingIndex = obj.requireInt("siblingIndex"),
            position = obj.requireInt("position"),
            composition = obj.optionalString("composition")?.let { enumValueOf<SessionComposition>(it) },
        )

    private fun encodeSessionSlotPlan(plan: SessionSlotPlan): String =
        jsonObject(
            "sessionType" to jsonString(plan.sessionType.name),
            "name" to jsonString(plan.name),
            "slots" to jsonArray(plan.slots.map { encodeWorkoutSlotTemplate(it) }),
            "siblingIndex" to jsonNumber(plan.siblingIndex),
            "position" to jsonNumber(plan.position),
            "filledSlots" to jsonArray(plan.filledSlots.map { encodeFilledSlot(it) }),
        )

    private fun decodeSessionSlotPlan(obj: JsonObject): SessionSlotPlan =
        SessionSlotPlan(
            sessionType = enumValueOf<SessionTypeId>(obj.requireString("sessionType")),
            name = obj.requireString("name"),
            slots =
                obj.requireArray("slots").values.map {
                    decodeWorkoutSlotTemplate(it.requireAsObject("slot"))
                },
            siblingIndex = obj.requireInt("siblingIndex"),
            position = obj.requireInt("position"),
            filledSlots =
                obj.requireArray("filledSlots").values.map {
                    decodeFilledSlot(it.requireAsObject("filledSlot"))
                }.toMutableList(),
        )

    private fun encodeFilledSlot(slot: FilledSlot): String =
        jsonObject(
            "slotId" to jsonString(slot.slotId),
            "role" to jsonString(slot.role.name),
            "pattern" to jsonString(slot.pattern.name),
            "primaryMuscle" to jsonString(slot.primaryMuscle.name),
            "repProfile" to jsonString(slot.repProfile.name),
            "lengthBiasPreference" to jsonStringOrNull(slot.lengthBiasPreference?.name),
            "siblingVariant" to jsonStringOrNull(slot.siblingVariant?.name),
            "isCrossDomainTouch" to jsonBoolean(slot.isCrossDomainTouch),
            "setCount" to jsonNumber(slot.setCount),
        )

    private fun decodeFilledSlot(obj: JsonObject): FilledSlot =
        FilledSlot(
            slotId = obj.requireString("slotId"),
            role = enumValueOf<SlotRole>(obj.requireString("role")),
            pattern = enumValueOf<MovementPattern>(obj.requireString("pattern")),
            primaryMuscle = enumValueOf<MuscleGroup>(obj.requireString("primaryMuscle")),
            repProfile = enumValueOf<RpRepProfile>(obj.requireString("repProfile")),
            lengthBiasPreference =
                obj.optionalString("lengthBiasPreference")?.let {
                    enumValueOf<MuscleLengthBiasPreference>(it)
                },
            siblingVariant =
                obj.optionalString("siblingVariant")?.let { enumValueOf<SiblingVariant>(it) },
            isCrossDomainTouch = obj.optionalBoolean("isCrossDomainTouch") ?: false,
            setCount = obj.requireInt("setCount"),
        )

    private fun encodeWorkoutSlotTemplate(slot: WorkoutSlotTemplate): String =
        jsonObject(
            "slotId" to jsonString(slot.slotId),
            "role" to jsonString(slot.role.name),
            "pattern" to jsonString(slot.pattern.name),
            "primaryMuscle" to jsonString(slot.primaryMuscle.name),
            "repProfile" to jsonString(slot.repProfile.name),
            "lengthBiasPreference" to jsonStringOrNull(slot.lengthBiasPreference?.name),
            "siblingVariant" to jsonStringOrNull(slot.siblingVariant?.name),
            "isCrossDomainTouch" to jsonBoolean(slot.isCrossDomainTouch),
            "allowedKinematicProfiles" to
                if (slot.allowedKinematicProfiles == null) {
                    "null"
                } else {
                    jsonArray(slot.allowedKinematicProfiles.map { jsonString(it.name) })
                },
        )

    private fun decodeWorkoutSlotTemplate(obj: JsonObject): WorkoutSlotTemplate =
        WorkoutSlotTemplate(
            slotId = obj.requireString("slotId"),
            role = enumValueOf<SlotRole>(obj.requireString("role")),
            pattern = enumValueOf<MovementPattern>(obj.requireString("pattern")),
            primaryMuscle = enumValueOf<MuscleGroup>(obj.requireString("primaryMuscle")),
            repProfile = enumValueOf<RpRepProfile>(obj.requireString("repProfile")),
            lengthBiasPreference =
                obj.optionalString("lengthBiasPreference")?.let {
                    enumValueOf<MuscleLengthBiasPreference>(it)
                },
            siblingVariant =
                obj.optionalString("siblingVariant")?.let { enumValueOf<SiblingVariant>(it) },
            isCrossDomainTouch = obj.optionalBoolean("isCrossDomainTouch") ?: false,
            allowedKinematicProfiles = decodeKinematicProfileSet(obj["allowedKinematicProfiles"]),
        )

    private fun decodeKinematicProfileSet(value: JsonValue?): Set<KinematicProfile>? {
        if (value == null || value.isNull()) return null
        val arr = value.asArrayOrNull() ?: error("Expected array or null for allowedKinematicProfiles")
        return arr.values.map {
            enumValueOf<KinematicProfile>(it.asStringOrNull() ?: error("Expected kinematic profile"))
        }.toSet()
    }

    private fun encodeObjective(objective: SynthesisObjectiveBreakdown): String =
        jsonObject(
            "total" to jsonNumber(objective.total),
            "recoveryPenalty" to jsonNumber(objective.recoveryPenalty),
            "specializedMevShortfall" to jsonNumber(objective.specializedMevShortfall),
            "coSpecializationImbalance" to jsonNumber(objective.coSpecializationImbalance),
            "specializeAimDeviation" to jsonNumber(objective.specializeAimDeviation),
            "maintenanceAimDeviation" to jsonNumber(objective.maintenanceAimDeviation),
            "minimumVolumeAimDeviation" to jsonNumber(objective.minimumVolumeAimDeviation),
            "roleMinimumShortfall" to jsonNumber(objective.roleMinimumShortfall),
            "pushPullImbalance" to jsonNumber(objective.pushPullImbalance),
            "sessionFillShortfall" to jsonNumber(objective.sessionFillShortfall),
        )

    private fun decodeObjective(obj: JsonObject): SynthesisObjectiveBreakdown =
        SynthesisObjectiveBreakdown(
            total = obj.requireLong("total"),
            recoveryPenalty = obj.requireLong("recoveryPenalty"),
            specializedMevShortfall = obj.requireLong("specializedMevShortfall"),
            coSpecializationImbalance = obj.requireLong("coSpecializationImbalance"),
            specializeAimDeviation = obj.requireLong("specializeAimDeviation"),
            maintenanceAimDeviation = obj.requireLong("maintenanceAimDeviation"),
            minimumVolumeAimDeviation = obj.requireLong("minimumVolumeAimDeviation"),
            roleMinimumShortfall = obj.requireLong("roleMinimumShortfall"),
            pushPullImbalance = obj.requireLong("pushPullImbalance"),
            sessionFillShortfall = obj.requireLong("sessionFillShortfall"),
        )

    // endregion

    private fun JsonValue.requireAsObject(label: String): JsonObject =
        asObjectOrNull() ?: error("Expected object for $label")

    private inline fun <T> wrapParse(kind: String, block: () -> T): T =
        try {
            block()
        } catch (e: ProgramSynthesisWireException) {
            throw e
        } catch (e: IllegalArgumentException) {
            throw ProgramSynthesisWireException("Invalid program synthesis $kind JSON: ${e.message}", e)
        } catch (e: IllegalStateException) {
            throw ProgramSynthesisWireException("Invalid program synthesis $kind JSON: ${e.message}", e)
        } catch (e: IndexOutOfBoundsException) {
            throw ProgramSynthesisWireException("Invalid program synthesis $kind JSON: ${e.message}", e)
        }
}

/** Thrown when synthesis wire JSON cannot be parsed or fails schema checks. */
class ProgramSynthesisWireException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)
