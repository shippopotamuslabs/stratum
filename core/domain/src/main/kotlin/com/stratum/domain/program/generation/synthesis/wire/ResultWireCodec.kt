package com.stratum.domain.program.generation.synthesis.wire

import com.stratum.domain.json.JsonObject
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
    name?.let { runCatching { enumValueOf<FatigueProfile>(it) }.getOrNull() }

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
 * results carry no entry and decode to empty — and unknown future kinds are dropped rather
 * than failing the whole result (same rolling-deploy philosophy as region filters).
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
    }

internal fun decodeSlotDiagnostics(value: JsonValue?): List<SlotGenerationDiagnostic> {
    if (value == null || value.isNull()) return emptyList()
    val arr = value.asArrayOrNull() ?: return emptyList()
    return arr.values.mapNotNull {
        runCatching { decodeSlotDiagnosticOrNull(it.requireAsObject("diagnostic")) }.getOrNull()
    }
}

private fun decodeSlotDiagnosticOrNull(obj: JsonObject): SlotGenerationDiagnostic? =
    when (obj.requireString("kind")) {
        "CrossDomainTouchSkipped" ->
            SlotGenerationDiagnostic.CrossDomainTouchSkipped(
                patternName = obj.requireString("patternName"),
                muscle = enumValueOf(obj.requireString("muscle")),
            )
        "SpecFocusSlotSkipped" ->
            SlotGenerationDiagnostic.SpecFocusSlotSkipped(
                patternName = obj.requireString("patternName"),
                muscle = enumValueOf(obj.requireString("muscle")),
            )
        "SpecFocusMuscleUnrecognized" ->
            SlotGenerationDiagnostic.SpecFocusMuscleUnrecognized(
                muscleName = obj.requireString("muscleName"),
                patternName = obj.requireString("patternName"),
            )
        else -> null
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
