package com.stratum.domain.program.generation.synthesis.wire

import com.stratum.domain.json.JsonArray
import com.stratum.domain.json.JsonObject
import com.stratum.domain.json.jsonArray
import com.stratum.domain.json.jsonBooleanOrNull
import com.stratum.domain.json.jsonNumber
import com.stratum.domain.json.jsonNumberOrNull
import com.stratum.domain.json.jsonObject
import com.stratum.domain.json.jsonString
import com.stratum.domain.json.jsonStringOrNull
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.LiftEffectivenessEntry
import com.stratum.domain.program.generation.context.MuscleLengthBias
import com.stratum.domain.program.generation.context.MuscleVolumeSnapshotContext
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.context.VolumeCorrelationContext
import com.stratum.domain.program.generation.context.VolumeSnapshotSource
import com.stratum.domain.program.generation.volume.WeeklyVolumeTarget
import com.stratum.domain.program.generation.volume.WeeklyVolumeTargets

// region Weekly targets

internal fun encodeWeeklyTargets(targets: WeeklyVolumeTargets): String =
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

internal fun decodeWeeklyTargets(obj: JsonObject): WeeklyVolumeTargets =
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

internal fun encodeContext(context: ProgramGenerationContext): String =
    jsonObject(
        buildList {
            add("trainingAge" to jsonString(context.trainingAge))
            add("lastTrained" to jsonStringOrNull(context.lastTrained))
            add("sessionLengthMinutes" to jsonNumberOrNull(context.sessionLengthMinutes))
            add("trainingDaysPerWeek" to jsonNumber(context.trainingDaysPerWeek))
            add(
                "specialization" to
                    jsonObject(
                        context.specialization.map { (muscle, level) ->
                            muscle.name to jsonString(level.name)
                        },
                    ),
            )
            add(
                "liftsById" to
                    jsonObject(
                        context.liftsById.map { (id, lift) -> id to encodeLiftContext(lift) },
                    ),
            )
            add(
                "muscleVolumeSnapshotsByMuscle" to
                    jsonObject(
                        context.muscleVolumeSnapshotsByMuscle.map { (muscle, snap) ->
                            muscle to encodeMuscleVolumeSnapshot(snap)
                        },
                    ),
            )
            add(
                "volumeCorrelationsByMuscle" to
                    jsonObject(
                        context.volumeCorrelationsByMuscle.map { (muscle, corr) ->
                            muscle to encodeVolumeCorrelation(corr)
                        },
                    ),
            )
            add("enabledLiftIds" to jsonArray(context.enabledLiftIds.map { jsonString(it) }))
            add(
                "movementPatternByLiftId" to
                    jsonObject(
                        context.movementPatternByLiftId.map { (id, pattern) ->
                            id to jsonStringOrNull(pattern?.name)
                        },
                    ),
            )
            add(
                "liftEffectivenessByLiftId" to
                    jsonObject(
                        context.liftEffectivenessByLiftId.map { (id, entry) ->
                            id to encodeLiftEffectiveness(entry)
                        },
                    ),
            )
            // Optional cycle-space rest pattern (6-day 7/8 slot, PR6+). Omitted when unset so
            // older clients stay compatible.
            context.restSlotIndices?.let { rests ->
                add("restSlotIndices" to jsonArray(rests.sorted().map { jsonNumber(it) }))
            }
            context.cycleLength?.let { length ->
                add("cycleLength" to jsonNumber(length))
            }
        },
    )

internal fun decodeContext(obj: JsonObject): ProgramGenerationContext =
    ProgramGenerationContext(
        trainingAge = obj.requireString("trainingAge"),
        lastTrained = obj.optionalString("lastTrained"),
        sessionLengthMinutes = obj.optionalInt("sessionLengthMinutes"),
        trainingDaysPerWeek = obj.requireInt("trainingDaysPerWeek"),
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
        restSlotIndices =
            obj["restSlotIndices"]?.asArrayOrNull()?.values?.map {
                it.asIntOrNull() ?: error("Expected int in restSlotIndices")
            }?.toSet(),
        cycleLength = obj.optionalInt("cycleLength"),
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
        "fatigueProfile" to jsonString(lift.fatigueProfile.name),
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
        fatigueProfile =
            obj.optionalString("fatigueProfile")
                ?.let { runCatching { enumValueOf<FatigueProfile>(it) }.getOrNull() }
                ?: FatigueProfile.Low,
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
