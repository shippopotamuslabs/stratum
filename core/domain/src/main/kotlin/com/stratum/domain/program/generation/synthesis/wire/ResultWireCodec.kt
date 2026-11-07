package com.stratum.domain.program.generation.synthesis.wire

import com.stratum.domain.json.JsonArray
import com.stratum.domain.json.JsonBoolean
import com.stratum.domain.json.JsonNull
import com.stratum.domain.json.JsonNumber
import com.stratum.domain.json.JsonObject
import com.stratum.domain.json.JsonString
import com.stratum.domain.json.JsonValue
import com.stratum.domain.json.jsonArray
import com.stratum.domain.json.jsonBoolean
import com.stratum.domain.json.jsonNumber
import com.stratum.domain.json.jsonObject
import com.stratum.domain.json.jsonString
import com.stratum.domain.json.jsonStringOrNull
import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.generation.prescription.FilledSlot
import com.stratum.domain.program.generation.prescription.SessionSlotPlan
import com.stratum.domain.program.generation.prescription.SynthesisObjectiveBreakdown
import com.stratum.domain.program.generation.reference.RpRepProfile
import com.stratum.domain.program.generation.slots.MuscleLengthBiasPreference
import com.stratum.domain.program.generation.split.ResolvedSessionPlan
import com.stratum.domain.program.generation.slots.SessionComposition
import com.stratum.domain.program.generation.slots.SessionTypeId
import com.stratum.domain.program.generation.slots.SlotGenerationDiagnostic
import com.stratum.domain.program.generation.slots.SiblingVariant
import com.stratum.domain.program.generation.slots.SlotRole
import com.stratum.domain.program.generation.slots.WorkoutSlotTemplate

// region Result nested types

internal fun encodeResolvedSession(session: ResolvedSessionPlan): String =
    jsonObject(
        "sessionType" to jsonString(session.sessionType.name),
        "name" to jsonString(session.name),
        "slots" to jsonArray(session.slots.map { encodeWorkoutSlotTemplate(it) }),
        "siblingIndex" to jsonNumber(session.siblingIndex),
        "position" to jsonNumber(session.position),
        "composition" to jsonStringOrNull(session.composition?.name),
    )

internal fun decodeResolvedSession(obj: JsonObject): ResolvedSessionPlan =
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

internal fun encodeSessionSlotPlan(plan: SessionSlotPlan): String =
    jsonObject(
        "sessionType" to jsonString(plan.sessionType.name),
        "name" to jsonString(plan.name),
        "slots" to jsonArray(plan.slots.map { encodeWorkoutSlotTemplate(it) }),
        "siblingIndex" to jsonNumber(plan.siblingIndex),
        "position" to jsonNumber(plan.position),
        "filledSlots" to jsonArray(plan.filledSlots.map { encodeFilledSlot(it) }),
    )

internal fun decodeSessionSlotPlan(obj: JsonObject): SessionSlotPlan =
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
        "allowedFatigueProfiles" to
            if (slot.allowedFatigueProfiles == null) {
                "null"
            } else {
                jsonArray(slot.allowedFatigueProfiles.map { jsonString(it.name) })
            },
        "setCount" to jsonNumber(slot.setCount),
    )

private fun decodeFilledSlot(obj: JsonObject): FilledSlot =
    FilledSlot(
        slotId = obj.requireString("slotId"),
        role = decodeSlotRole(obj.requireString("role")),
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
        allowedFatigueProfiles = decodeFatigueProfileSet(obj["allowedFatigueProfiles"]),
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
        "allowedFatigueProfiles" to
            if (slot.allowedFatigueProfiles == null) {
                "null"
            } else {
                jsonArray(slot.allowedFatigueProfiles.map { jsonString(it.name) })
            },
    )

private fun decodeWorkoutSlotTemplate(obj: JsonObject): WorkoutSlotTemplate =
    WorkoutSlotTemplate(
        slotId = obj.requireString("slotId"),
        role = decodeSlotRole(obj.requireString("role")),
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
        allowedFatigueProfiles = decodeFatigueProfileSet(obj["allowedFatigueProfiles"]),
    )

/**
 * Soft-parse fatigue names; drop unknowns rather than crash on stale wire caches.
 * Does not map legacy kinematic strings (AxialCompound → …) — those are ignored.
 */
private fun decodeFatigueProfileOrNull(name: String?): FatigueProfile? =
    name?.let { FatigueProfile.parseOrNull(it) }

/** SlotRole with one legacy wire alias: Isolation → Accessory (role rename only). */
private fun decodeSlotRole(name: String): SlotRole =
    when (name) {
        "Isolation" -> SlotRole.Accessory
        else -> enumValueOf(name)
    }

private fun decodeFatigueProfileSet(value: JsonValue?): Set<FatigueProfile>? {
    if (value == null || value.isNull()) return null
    val arr = value.asArrayOrNull() ?: error("Expected array or null for allowedFatigueProfiles")
    val profiles =
        arr.values.mapNotNull { decodeFatigueProfileOrNull(it.asStringOrNull()) }.toSet()
    return profiles.ifEmpty { null }
}

/**
 * Slot-build diagnostics (STR-254). The key is optional on decode — pre-diagnostics cloud
 * results carry no entry and decode to empty — and a malformed entry never fails the
 * whole result (same rolling-deploy philosophy as region filters): it is carried as a
 * [SlotGenerationDiagnostic.SlotDiagnosticUndecodable] holding its raw wire content
 * instead of being dropped silently (STR-240).
 */
internal fun encodeSlotDiagnostic(diagnostic: SlotGenerationDiagnostic): String =
    when (diagnostic) {
        is SlotGenerationDiagnostic.CrossDomainTouchSkipped ->
            jsonObject(
                "kind" to jsonString("CrossDomainTouchSkipped"),
                "patternName" to jsonString(diagnostic.patternName),
                "muscle" to jsonString(diagnostic.muscle.name),
            )
        is SlotGenerationDiagnostic.SpecFocusSlotSkipped ->
            jsonObject(
                "kind" to jsonString("SpecFocusSlotSkipped"),
                "patternName" to jsonString(diagnostic.patternName),
                "muscle" to jsonString(diagnostic.muscle.name),
            )
        is SlotGenerationDiagnostic.SpecFocusMuscleUnrecognized ->
            jsonObject(
                "kind" to jsonString("SpecFocusMuscleUnrecognized"),
                "muscleName" to jsonString(diagnostic.muscleName),
                "patternName" to jsonString(diagnostic.patternName),
            )
        is SlotGenerationDiagnostic.SlotDiagnosticUndecodable ->
            jsonObject(
                "kind" to jsonString("SlotDiagnosticUndecodable"),
                "entry" to jsonString(diagnostic.entry),
            )
    }

internal fun decodeSlotDiagnostics(value: JsonValue?): List<SlotGenerationDiagnostic> {
    if (value == null || value.isNull()) return emptyList()
    val arr = value.asArrayOrNull()
        ?: return listOf(SlotGenerationDiagnostic.SlotDiagnosticUndecodable(renderWireValue(value)))
    return arr.values.map { decodeSlotDiagnostic(it) }
}

/**
 * Decodes one diagnostics entry without throwing: every field is read through a
 * null-returning accessor and enum names go through [MuscleGroup.parseOrNull], so a
 * malformed entry — not an object, unknown kind, missing field, unrecognized value —
 * becomes a [SlotGenerationDiagnostic.SlotDiagnosticUndecodable] carrying the entry's
 * raw wire content rather than an exception to swallow or a silent drop.
 */
private fun decodeSlotDiagnostic(entry: JsonValue): SlotGenerationDiagnostic {
    val obj = entry.asObjectOrNull()
        ?: return SlotGenerationDiagnostic.SlotDiagnosticUndecodable(renderWireValue(entry))
    return when (obj.optionalWireString("kind")) {
        "CrossDomainTouchSkipped" ->
            decodePatternAndMuscleDiagnostic(obj, entry) { patternName, muscle ->
                SlotGenerationDiagnostic.CrossDomainTouchSkipped(patternName, muscle)
            }
        "SpecFocusSlotSkipped" ->
            decodePatternAndMuscleDiagnostic(obj, entry) { patternName, muscle ->
                SlotGenerationDiagnostic.SpecFocusSlotSkipped(patternName, muscle)
            }
        "SpecFocusMuscleUnrecognized" -> {
            val muscleName = obj.optionalWireString("muscleName")
            val patternName = obj.optionalWireString("patternName")
            if (muscleName == null || patternName == null) {
                SlotGenerationDiagnostic.SlotDiagnosticUndecodable(renderWireValue(entry))
            } else {
                SlotGenerationDiagnostic.SpecFocusMuscleUnrecognized(muscleName, patternName)
            }
        }
        "SlotDiagnosticUndecodable" ->
            SlotGenerationDiagnostic.SlotDiagnosticUndecodable(
                obj.optionalWireString("entry") ?: renderWireValue(entry),
            )
        else -> SlotGenerationDiagnostic.SlotDiagnosticUndecodable(renderWireValue(entry))
    }
}

private fun decodePatternAndMuscleDiagnostic(
    obj: JsonObject,
    entry: JsonValue,
    build: (patternName: String, muscle: MuscleGroup) -> SlotGenerationDiagnostic,
): SlotGenerationDiagnostic {
    val patternName = obj.optionalWireString("patternName")
    val muscle = obj.optionalWireString("muscle")?.let { MuscleGroup.parseOrNull(it) }
    return if (patternName == null || muscle == null) {
        SlotGenerationDiagnostic.SlotDiagnosticUndecodable(renderWireValue(entry))
    } else {
        build(patternName, muscle)
    }
}

/** Reads a string field without throwing: absent, null, or non-string all read as null. */
private fun JsonObject.optionalWireString(key: String): String? = this[key]?.asStringOrNull()

/** Renders a decoded [JsonValue] back to compact wire JSON, identifying an entry as-is. */
private fun renderWireValue(value: JsonValue): String =
    when (value) {
        is JsonObject ->
            jsonObject(value.entries.map { (key, fieldValue) -> key to renderWireValue(fieldValue) })
        is JsonArray -> jsonArray(value.values.map { renderWireValue(it) })
        is JsonString -> jsonString(value.value)
        is JsonNumber -> value.raw
        is JsonBoolean -> jsonBoolean(value.value)
        JsonNull -> "null"
    }

internal fun encodeObjective(objective: SynthesisObjectiveBreakdown): String =
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

internal fun decodeObjective(obj: JsonObject): SynthesisObjectiveBreakdown =
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
