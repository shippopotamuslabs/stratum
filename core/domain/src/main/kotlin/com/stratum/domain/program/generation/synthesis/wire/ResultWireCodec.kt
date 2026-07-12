package com.stratum.domain.program.generation.synthesis.wire

import com.stratum.domain.json.JsonObject
import com.stratum.domain.json.JsonValue
import com.stratum.domain.json.jsonArray
import com.stratum.domain.json.jsonBoolean
import com.stratum.domain.json.jsonNumber
import com.stratum.domain.json.jsonObject
import com.stratum.domain.json.jsonString
import com.stratum.domain.json.jsonStringOrNull
import com.stratum.domain.lift.KinematicProfile
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
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
