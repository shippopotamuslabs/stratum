@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.selection

import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.prescription.FilledSlot
import com.stratum.domain.program.generation.split.MuscleLengthBiasPreference
import com.stratum.domain.program.generation.split.SiblingVariant
import com.stratum.domain.program.generation.split.SlotRole
import com.stratum.domain.program.generation.split.WorkoutSlotTemplate

data class SelectedSlotLift(
    val slot: FilledSlot,
    val lift: LiftContext,
    val candidates: List<LiftContext>,
)

private fun matchesPattern(lift: LiftContext, pattern: String): Boolean =
    lift.movementPattern == pattern || pattern in lift.movementPatterns

private fun slotRoleBonus(slot: WorkoutSlotTemplate, lift: LiftContext): Double {
    var bonus = 0.0
    val mainstream = if (lift.mainstream == true) 1.0 else mainstreamNameScore(lift.name)
    when (slot.role) {
        SlotRole.Anchor -> {
            bonus += mainstream * 0.3
            if (Regex("""\bbarbell\b""", RegexOption.IGNORE_CASE).containsMatchIn(lift.name)) bonus += 0.15
            if (Regex("""\b(life fitness|hammer strength|nautilus|precor)\b""", RegexOption.IGNORE_CASE)
                    .containsMatchIn(lift.name)
            ) {
                bonus -= 0.2
            }
            if (lift.equipment != null &&
                Regex("""\b(machine|brand)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lift.equipment)
            ) {
                bonus -= 0.1
            }
        }
        SlotRole.Isolation -> {
            if (Regex("""\b(cable|machine)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lift.name)) {
                bonus += 0.05
            }
        }
        else -> Unit
    }
    return bonus
}

private fun lengthBiasBonus(
    slot: WorkoutSlotTemplate,
    lift: LiftContext,
    siblingIndex: Int,
): Double {
    val pref = slot.lengthBiasPreference ?: return 0.0
    if (pref == MuscleLengthBiasPreference.Any) return 0.0
    val bias = lift.muscleLengthBias?.name ?: "MidRange"
    if (slot.siblingVariant == SiblingVariant.B && siblingIndex % 2 == 1) {
        return when (bias) {
            "Lengthened" -> 0.1
            "Shortened" -> -0.05
            else -> 0.0
        }
    }
    if (pref == MuscleLengthBiasPreference.Lengthened && bias == "Lengthened") return 0.1
    if (pref == MuscleLengthBiasPreference.Shortened && bias == "Shortened") return 0.1
    return 0.0
}

fun scoreLiftForSlot(
    lift: LiftContext,
    slot: WorkoutSlotTemplate,
    context: ProgramGenerationContext,
    siblingIndex: Int,
): Double =
    scoreLift(lift, context) +
        slotRoleBonus(slot, lift) +
        lengthBiasBonus(slot, lift, siblingIndex) +
        liftCatalogQualityAdjustment(lift, slot)

fun candidatesForSlot(
    slot: WorkoutSlotTemplate,
    context: ProgramGenerationContext,
): List<LiftContext> {
    val out = mutableListOf<LiftContext>()
    for (lift in context.liftsById.values) {
        if (lift.id !in context.enabledLiftIds) continue
        if (!matchesPattern(lift, slot.pattern)) continue
        if (slot.primaryMuscle.isNotEmpty()) {
            val primaryMatch = slot.primaryMuscle in lift.primaryMuscles
            if (slot.role == SlotRole.Isolation || slot.role == SlotRole.Anchor) {
                if (!primaryMatch) continue
            } else if (!primaryMatch && slot.primaryMuscle !in lift.secondaryMuscles) {
                continue
            }
        }
        out.add(lift)
    }
    return out
}

fun selectLiftForSlot(
    slot: FilledSlot,
    context: ProgramGenerationContext,
    siblingIndex: Int,
    usedLiftIds: MutableSet<String>,
    minCycleGap: Int,
    sessionIndex: Int,
    lastUsedSession: MutableMap<String, Int>,
): SelectedSlotLift? {
    if (slot.setCount <= 0) return null

    val ranked = candidatesForSlot(slot.toWorkoutSlotTemplate(), context)
        .filter { lift ->
            val last = lastUsedSession[lift.id]
            last == null || sessionIndex - last >= minCycleGap || lift.id !in usedLiftIds
        }
        .sortedWith { a, b ->
            val sa = scoreLiftForSlot(a, slot.toWorkoutSlotTemplate(), context, siblingIndex)
            val sb = scoreLiftForSlot(b, slot.toWorkoutSlotTemplate(), context, siblingIndex)
            if (sb != sa) {
                return@sortedWith when {
                    sb > sa -> 1
                    sb < sa -> -1
                    else -> 0
                }
            }
            compareLiftCandidates(
                LiftCandidate(
                    liftId = a.id,
                    name = a.name,
                    pattern = a.movementPattern,
                    priority = sa,
                    priorityTier = if (a.proven == true) "proven" else "available",
                    isCompound = a.movementPatterns.size > 1,
                    primaryMuscles = a.primaryMuscles,
                    secondaryMuscles = a.secondaryMuscles,
                ),
                LiftCandidate(
                    liftId = b.id,
                    name = b.name,
                    pattern = b.movementPattern,
                    priority = sb,
                    priorityTier = if (b.proven == true) "proven" else "available",
                    isCompound = b.movementPatterns.size > 1,
                    primaryMuscles = b.primaryMuscles,
                    secondaryMuscles = b.secondaryMuscles,
                ),
            )
        }

    val topCandidates = ranked.take(5)
    val pick = topCandidates.firstOrNull { it.id !in usedLiftIds } ?: topCandidates.firstOrNull()
        ?: return null

    usedLiftIds.add(pick.id)
    lastUsedSession[pick.id] = sessionIndex
    return SelectedSlotLift(slot = slot, lift = pick, candidates = topCandidates)
}
