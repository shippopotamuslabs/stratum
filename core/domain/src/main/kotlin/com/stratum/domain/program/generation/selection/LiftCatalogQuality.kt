@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.selection

import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.split.WorkoutSlotTemplate

private val LIFT_ID_PENALTIES: Map<String, Double> = mapOf(
    "decline-barbell-bench-press" to 0.4,
    "dip-machine" to 0.35,
    "one-leg-barbell-squat" to 0.45,
    "standing-barbell-press-behind-neck" to 0.5,
)

private val LIFT_ID_BONUSES: Map<String, Double> = mapOf(
    "ez-bar-skull-crusher" to 0.25,
    "cable-rope-triceps-pushdown" to 0.25,
    "cable-one-arm-triceps-extension" to 0.2,
    "machine-triceps-extension" to 0.15,
    "machine-shoulder-military-press" to 0.2,
    "smith-machine-overhead-shoulder-press" to 0.15,
    "bulgarian-split-squat" to 0.15,
    "barbell-full-squat" to 0.1,
    "barbell-front-squat" to 0.1,
)

private fun namePenalty(name: String, pattern: String, slot: WorkoutSlotTemplate?): Double {
    val n = name.lowercase()
    var penalty = 0.0

    if (Regex("""\bdecline\b""").containsMatchIn(n) && !Regex("""\bincline\b""").containsMatchIn(n)) {
        penalty += 0.35
    }
    if (Regex("""\bbehind.?neck\b""").containsMatchIn(n) || Regex("""\bbehind the neck\b""").containsMatchIn(n)) {
        penalty += 0.45
    }
    if (Regex("""\bdip machine\b""").containsMatchIn(n) ||
        (Regex("""\bdips?\b""").containsMatchIn(n) && Regex("""\bmachine\b""").containsMatchIn(n))
    ) {
        penalty += if (slot?.pattern == "ElbowExtension") 0.4 else 0.15
    }
    if (Regex("""\bone.?leg\b""").containsMatchIn(n) && pattern == "SquatPattern") {
        penalty += 0.4
    }
    if (Regex("""\bguillotine\b""").containsMatchIn(n)) penalty += 0.42
    if (Regex("""\bwide stance\b""").containsMatchIn(n)) penalty += 0.2
    if (Regex("""^(alternating|alternate)\b""").containsMatchIn(n)) {
        penalty += 0.15
    }

    return penalty
}

private fun nameBonus(name: String, pattern: String, slot: WorkoutSlotTemplate?): Double {
    val n = name.lowercase()
    var bonus = 0.0

    if (pattern == "HorizontalPress" &&
        Regex("""\b(bench press|barbell bench)\b""").containsMatchIn(n) &&
        !Regex("""\bdecline\b""").containsMatchIn(n)
    ) {
        bonus += 0.12
    }
    if (pattern == "InclinePress" && Regex("""\bincline\b""").containsMatchIn(n)) {
        bonus += 0.1
    }
    if (pattern == "VerticalPress") {
        if (Regex("""\b(dumbbell|db)\b.*\bpress\b""").containsMatchIn(n) ||
            Regex("""\bmachine\b.*\b(military|shoulder|press)\b""").containsMatchIn(n)
        ) {
            bonus += 0.18
        }
        if (Regex("""\b(overhead|military)\b.*\bpress\b""").containsMatchIn(n) &&
            !Regex("""\bbehind\b""").containsMatchIn(n)
        ) {
            bonus += 0.12
        }
    }
    if (slot?.pattern == "ElbowExtension" || pattern == "ElbowExtension") {
        if (Regex("""\b(skull|skullcrusher|skull crusher)\b""").containsMatchIn(n)) bonus += 0.22
        if (Regex("""\b(tricep extension|triceps extension)\b""").containsMatchIn(n) &&
            Regex("""\b(cable|rope|dumbbell|db)\b""").containsMatchIn(n)
        ) {
            bonus += 0.2
        }
        if (Regex("""\bpush.?down\b""").containsMatchIn(n)) bonus += 0.15
    }
    if (pattern == "SquatPattern") {
        if (Regex("""\b(front squat|back squat|barbell squat|full squat)\b""").containsMatchIn(n)) bonus += 0.15
        if (Regex("""\b(bulgarian|split squat)\b""").containsMatchIn(n)) bonus += 0.08
    }

    return bonus
}

private fun isHighSystemicFatigueCompound(lift: LiftContext): Boolean {
    val isCompound = lift.kinematicProfile == "AxialCompound" || lift.kinematicProfile == "NonAxialCompound"
    if (!isCompound) return false
    val hasLowerBackPrimary = lift.primaryMuscles.contains("LowerBack")
    val hasManySecondaries = lift.secondaryMuscles.size >= 6
    return hasLowerBackPrimary || hasManySecondaries
}

/** Net quality adjustment blended into lift priority (negative = demote). */
fun liftCatalogQualityAdjustment(
    lift: LiftContext,
    slot: WorkoutSlotTemplate? = null,
): Double {
    val pattern = lift.movementPattern
    var adjustment = 0.0

    adjustment -= LIFT_ID_PENALTIES[lift.id] ?: 0.0
    adjustment += LIFT_ID_BONUSES[lift.id] ?: 0.0
    adjustment -= namePenalty(lift.name, pattern, slot)
    adjustment += nameBonus(lift.name, pattern, slot)

    if (lift.mainstream == false) {
        adjustment -= 0.08
    }

    if (isHighSystemicFatigueCompound(lift)) {
        adjustment += LiftSelectionWeights.SYSTEMIC_FATIGUE_PENALTY
    }

    return adjustment
}
