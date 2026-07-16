@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.selection

import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.context.hasCompoundFatigue
import com.stratum.domain.program.generation.context.isCompoundLift
import com.stratum.domain.program.generation.prescription.FilledSlot
import com.stratum.domain.program.generation.slots.MuscleLengthBiasPreference
import com.stratum.domain.program.generation.slots.SiblingVariant
import com.stratum.domain.program.generation.slots.SlotRole
import com.stratum.domain.program.generation.slots.WorkoutSlotTemplate

data class SelectedSlotLift(
    val slot: FilledSlot,
    val lift: LiftContext,
    val candidates: List<LiftContext>,
)

private fun matchesPattern(lift: LiftContext, pattern: MovementPattern, strictPrimaryPattern: Boolean = false): Boolean =
    if (strictPrimaryPattern) {
        lift.movementPattern == pattern
    } else {
        lift.movementPattern == pattern || pattern in lift.movementPatterns
    }

/** Cross-domain touch slots for arms/calves must pick true isolation lifts only. */
private val STRICT_ISOLATION_TOUCH_PATTERNS = setOf(
    MovementPattern.ElbowFlexion,
    MovementPattern.ElbowExtension,
    MovementPattern.PlantarFlexion,
)

private fun isStrictIsolationTouch(slot: WorkoutSlotTemplate): Boolean =
    slot.isCrossDomainTouch && slot.pattern in STRICT_ISOLATION_TOUCH_PATTERNS

private fun slotRoleBonus(slot: WorkoutSlotTemplate, lift: LiftContext): Double {
    var bonus = 0.0
    val mainstream = if (lift.mainstream == true) 1.0 else mainstreamNameScore(lift.name)
    when (slot.role) {
        SlotRole.Anchor -> {
            bonus += mainstream * LiftSelectionWeights.ANCHOR_MAINSTREAM_BONUS
            // Prefer High/Medium fatigue for barbell anchors; Low is machine-ish penalty territory.
            if (lift.hasCompoundFatigue) {
                if (Regex("""\bbarbell\b""", RegexOption.IGNORE_CASE).containsMatchIn(lift.name)) {
                    bonus += LiftSelectionWeights.ANCHOR_BARBELL_BONUS
                }
            } else {
                if (Regex("""\b(life fitness|hammer strength|nautilus|precor)\b""", RegexOption.IGNORE_CASE)
                        .containsMatchIn(lift.name)
                ) {
                    bonus += LiftSelectionWeights.ANCHOR_BRAND_PENALTY
                }
                if (lift.equipment != null &&
                    Regex("""\b(machine|brand)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lift.equipment)
                ) {
                    bonus += LiftSelectionWeights.ANCHOR_MACHINE_PENALTY
                }
            }
        }
        SlotRole.Accessory -> {
            if (Regex("""\b(cable|machine)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lift.name)) {
                bonus += LiftSelectionWeights.ACCESSORY_CABLE_MACHINE_BONUS
            }
            // Accessory slots may admit Medium (e.g. leg press) as variety — prefer true Low first.
            if (slot.allowedFatigueProfiles != null) {
                when (lift.fatigueProfile) {
                    FatigueProfile.Low ->
                        bonus += LiftSelectionWeights.ACCESSORY_TRUE_LOW_FATIGUE_BONUS
                    FatigueProfile.Medium ->
                        bonus += LiftSelectionWeights.ACCESSORY_MEDIUM_FALLBACK_PENALTY
                    else -> Unit
                }
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
            "Lengthened" -> LiftSelectionWeights.LENGTH_BIAS_BONUS
            "Shortened" -> -0.5 * LiftSelectionWeights.LENGTH_BIAS_BONUS
            else -> 0.0
        }
    }
    if (pref == MuscleLengthBiasPreference.Lengthened && bias == "Lengthened") return LiftSelectionWeights.LENGTH_BIAS_BONUS
    if (pref == MuscleLengthBiasPreference.Shortened && bias == "Shortened") return LiftSelectionWeights.LENGTH_BIAS_BONUS
    return 0.0
}

fun scoreLiftForSlot(
    lift: LiftContext,
    slot: WorkoutSlotTemplate,
    context: ProgramGenerationContext,
    siblingIndex: Int,
    setCount: Int,
    runningWeeklyVolume: Map<MuscleGroup, Double> = emptyMap(),
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
        // Fatigue allow-list fallback: when the slot admits Medium (e.g. quad-iso + leg press),
        // a matching primary-muscle lift is a candidate even if its movement pattern differs.
        val fatigueAllowListMatch = slot.allowedFatigueProfiles != null &&
            lift.fatigueProfile in slot.allowedFatigueProfiles &&
            slot.primaryMuscle in lift.primaryMuscles
        if (!patternMatch && !fatigueAllowListMatch) continue
        run {
            val primaryMatch = slot.primaryMuscle in lift.primaryMuscles
            when {
                slot.isCrossDomainTouch -> {
                    if (!primaryMatch) continue
                }
                slot.role == SlotRole.Accessory || slot.role == SlotRole.Anchor -> {
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
    runningWeeklyVolume: MutableMap<MuscleGroup, Double> = mutableMapOf(),
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
                    isCompound = a.isCompoundLift,
                    primaryMuscles = a.primaryMuscles,
                    secondaryMuscles = a.secondaryMuscles,
                ),
                LiftCandidate(
                    liftId = b.id,
                    name = b.name,
                    pattern = b.movementPattern,
                    priority = sb,
                    priorityTier = if (b.proven == true) "proven" else "available",
                    isCompound = b.isCompoundLift,
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
    pattern: MovementPattern,
    selectionState: LiftSelectionState,
): Double {
    val used = selectionState.cycleUsedLiftIdsByPattern[pattern].orEmpty()
    return if (liftId in used) {
        LiftSelectionWeights.CYCLE_VARIETY_USED_PENALTY
    } else {
        LiftSelectionWeights.CYCLE_VARIETY_FRESH_BONUS
    }
}

private fun pickWithCycleVariety(
    ranked: List<LiftContext>,
    pattern: MovementPattern,
    usedLiftIds: Set<String>,
    selectionState: LiftSelectionState,
    allowAnchorReuse: Boolean,
): LiftContext? {
    // Session: never repeat a lift inside one workout.
    val sessionEligible = ranked.filter { it.id !in usedLiftIds }
    // Cycle: hard-exclude any lift already used anywhere in the program, unless the pool
    // would otherwise empty (then allow reuse). This covers NonAxial compounds that share the
    // isolation slot but differ in movement pattern from true isolations.
    val cycleFreshGlobal =
        sessionEligible.filter { it.id !in selectionState.cycleUsedLiftIds }
    val cycleFreshByPattern =
        sessionEligible.filter {
            it.id !in selectionState.cycleUsedLiftIdsByPattern[pattern].orEmpty()
        }
    return cycleFreshGlobal.firstOrNull()
        ?: cycleFreshByPattern.firstOrNull()
        ?: sessionEligible.firstOrNull()
        ?: ranked.firstOrNull { it.id !in usedLiftIds }
        ?: if (allowAnchorReuse) ranked.firstOrNull() else null
}
