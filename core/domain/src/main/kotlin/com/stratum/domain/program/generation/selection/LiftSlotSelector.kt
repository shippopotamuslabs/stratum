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

private fun matchesPattern(lift: LiftContext, pattern: String, strictPrimaryPattern: Boolean = false): Boolean =
    if (strictPrimaryPattern) {
        lift.movementPattern == pattern
    } else {
        lift.movementPattern == pattern || pattern in lift.movementPatterns
    }

/** Cross-domain touch slots for arms/calves must pick true isolation lifts only. */
private val STRICT_ISOLATION_TOUCH_PATTERNS = setOf(
    "ElbowFlexion",
    "ElbowExtension",
    "PlantarFlexion",
)

private fun isStrictIsolationTouch(slot: WorkoutSlotTemplate): Boolean =
    slot.isCrossDomainTouch && slot.pattern in STRICT_ISOLATION_TOUCH_PATTERNS

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
    setCount: Int,
    runningWeeklyVolume: Map<String, Double> = emptyMap(),
): Double =
    scoreLift(lift, context) +
        slotRoleBonus(slot, lift) +
        lengthBiasBonus(slot, lift, siblingIndex) +
        liftCatalogQualityAdjustment(lift, slot) +
        AntagonistMetadataBalance.balanceBonus(
            lift = lift,
            setCount = setCount,
            context = context,
            runningWeeklyVolume = runningWeeklyVolume,
        )

fun candidatesForSlot(
    slot: WorkoutSlotTemplate,
    context: ProgramGenerationContext,
): List<LiftContext> {
    val out = mutableListOf<LiftContext>()
    for (lift in context.liftsById.values) {
        if (lift.id !in context.enabledLiftIds) continue
        val patternMatch = matchesPattern(lift, slot.pattern, strictPrimaryPattern = isStrictIsolationTouch(slot))
        // Kinematic-profile fallback: when the slot allows NonAxialCompound lifts (e.g. quad-iso
        // admitting leg press/hack squat), a NonAxialCompound lift with the right primary muscle
        // is a candidate even if its movement pattern doesn't match the slot's pattern. This is
        // the explicit replacement for the old KneeExtension-pattern-overloading hack.
        val kinematicMatch = slot.allowedKinematicProfiles != null &&
            lift.kinematicProfile in slot.allowedKinematicProfiles &&
            slot.primaryMuscle in lift.primaryMuscles
        if (!patternMatch && !kinematicMatch) continue
        if (slot.primaryMuscle.isNotEmpty()) {
            val primaryMatch = slot.primaryMuscle in lift.primaryMuscles
            when {
                slot.isCrossDomainTouch -> {
                    if (!primaryMatch) continue
                }
                slot.role == SlotRole.Isolation || slot.role == SlotRole.Anchor -> {
                    if (!primaryMatch) continue
                }
                !primaryMatch && slot.primaryMuscle !in lift.secondaryMuscles -> continue
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
    selectionState: LiftSelectionState,
    runningWeeklyVolume: MutableMap<String, Double> = mutableMapOf(),
): SelectedSlotLift? {
    if (slot.setCount <= 0) return null

    val slotTemplate = slot.toWorkoutSlotTemplate()
    val allCandidates = candidatesForSlot(slotTemplate, context)
    if (allCandidates.isEmpty()) return null

    val avoidLiftIds = selectionState.avoidLiftIdsForSlot(
        pattern = slot.pattern,
        primaryMuscle = slot.primaryMuscle,
        slotId = slot.slotId,
    )

    fun rankCandidates(candidates: List<LiftContext>): List<LiftContext> =
        candidates.sortedWith { a, b ->
            val sa = scoreLiftForSlot(a, slotTemplate, context, siblingIndex, slot.setCount, runningWeeklyVolume) +
                cycleVarietyBonus(a.id, slot.pattern, selectionState)
            val sb = scoreLiftForSlot(b, slotTemplate, context, siblingIndex, slot.setCount, runningWeeklyVolume) +
                cycleVarietyBonus(b.id, slot.pattern, selectionState)
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

    val gapEligible =
        allCandidates.filter { lift ->
            lift.id !in avoidLiftIds &&
                run {
                    val last = lastUsedSession[lift.id]
                    last == null || sessionIndex - last >= minCycleGap
                }
        }
    val ranked = rankCandidates(gapEligible.ifEmpty { allCandidates.filter { it.id !in avoidLiftIds } })
    val pick =
        pickWithCycleVariety(
            ranked = ranked,
            pattern = slot.pattern,
            usedLiftIds = usedLiftIds,
            selectionState = selectionState,
            allowAnchorReuse = slot.role == SlotRole.Anchor && slot.setCount > 0,
        ) ?: return null

    usedLiftIds.add(pick.id)
    lastUsedSession[pick.id] = sessionIndex
    selectionState.recordSelection(
        pattern = slot.pattern,
        primaryMuscle = slot.primaryMuscle,
        liftId = pick.id,
        isVariantAlt = slot.slotId.endsWith("-alt"),
    )
    AntagonistMetadataBalance.projectLiftMetadataVolume(pick, slot.setCount, runningWeeklyVolume)
    return SelectedSlotLift(slot = slot, lift = pick, candidates = ranked.take(5))
}

private fun cycleVarietyBonus(
    liftId: String,
    pattern: String,
    selectionState: LiftSelectionState,
): Double {
    val used = selectionState.cycleUsedLiftIdsByPattern[pattern].orEmpty()
    return if (liftId in used) -0.35 else 0.2
}

private fun pickWithCycleVariety(
    ranked: List<LiftContext>,
    pattern: String,
    usedLiftIds: Set<String>,
    selectionState: LiftSelectionState,
    allowAnchorReuse: Boolean,
): LiftContext? {
    val sessionEligible = ranked.filter { it.id !in usedLiftIds }
    val cycleFresh = sessionEligible.filter {
        it.id !in selectionState.cycleUsedLiftIdsByPattern[pattern].orEmpty()
    }
    return cycleFresh.firstOrNull()
        ?: sessionEligible.firstOrNull()
        ?: ranked.firstOrNull { it.id !in usedLiftIds }
        ?: if (allowAnchorReuse) ranked.firstOrNull() else null
}
