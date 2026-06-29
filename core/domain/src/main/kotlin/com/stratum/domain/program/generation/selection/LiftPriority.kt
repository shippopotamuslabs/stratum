@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.selection

import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.reference.MAJOR_COMPOUND_PATTERNS
import kotlin.math.log10
import kotlin.math.min

/** A lift with its denormalized priority score and trimmed insights, ready for a builder call. */
data class LiftCandidate(
    val liftId: String,
    val name: String,
    val pattern: String,
    /** 0.0–1.0 priority score (higher = more relevant for this day). */
    val priority: Double,
    /** Coarse tier label for the prompt: "proven" or "available". */
    val priorityTier: String,
    /** Whether this lift is a major compound (multi-joint) — derived from [pattern] via
     *  [MAJOR_COMPOUND_PATTERNS]. Surfaced to the builder so it can order compounds before
     *  isolation/accessory work. */
    val isCompound: Boolean,
    val mainstream: Boolean? = null,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    /** Trimmed insights: only fatigue decay + progress trend. Undefined when the lift has no insights. */
    val trainingInsights: TrainingInsights? = null,
) {
    data class TrainingInsights(
        val fatigueDecaySlope: Double? = null,
        val fatigueDecayIntercept: Double? = null,
        val progressTrend: String? = null,
    )
}

/** Maximum candidates per pattern slot sent to a builder. */
const val CANDIDATES_PER_SLOT = 5

/** Weight for the mainstream-name bonus blended into [scoreLift]. */
private const val MAINSTREAM_NAME_WEIGHT = 0.12

/**
 * 0.0–1.0 score favoring bread-and-butter lifts over obscure yuhonas catalog tail entries.
 * Used as a tie-breaker when proven-tier and insight signals are absent (typical for availableLifts).
 */
fun mainstreamNameScore(name: String): Double {
    val n = name.lowercase()
    if (Regex("""^(alternating|alternate)\b""").containsMatchIn(n)) return 0.05
    if (
        Regex(
            """\bbalance board\b|around the world|renegade row|see-saw|flutter kick|wind sprint|spell caster|judo flip|landmine 180|bottoms-up|bottoms up""",
        ).containsMatchIn(n)
    ) {
        return 0.1
    }
    if (
        Regex(
            """barbell (bench press|squat|deadlift|row|hip thrust|curl|overhead press|incline|full squat|front squat|romanian)""",
        ).containsMatchIn(n)
    ) {
        return 1.0
    }
    if (
        Regex(
            """^(bench press|squat|deadlift|chin-up|chin up|pull-up|pull up|lat pulldown|leg press|romanian deadlift|seated row|cable row|overhead press|military press|hip thrust|leg curl|leg extension|calf raise|barbell row|incline bench|\bdips?\b)""",
        ).containsMatchIn(n)
    ) {
        return 1.0
    }
    if (Regex("""\bdecline bench\b""").containsMatchIn(n)) return 0.45
    if (Regex("""\bbehind.?neck\b""").containsMatchIn(n) || Regex("""\bbehind the neck\b""").containsMatchIn(n)) {
        return 0.2
    }
    if (Regex("""\bone.?leg\b""").containsMatchIn(n) && Regex("""\bsquat\b""").containsMatchIn(n)) return 0.25
    if (Regex("""\bbarbell\b""").containsMatchIn(n)) return 0.9
    if (Regex("""\b(machine|smith machine|smith)\b""").containsMatchIn(n)) return 0.85
    if (Regex("""\b(dumbbell|db)\b""").containsMatchIn(n)) return 0.8
    if (Regex("""\b(cable|pulley)\b""").containsMatchIn(n)) return 0.8
    if (Regex("""\bez-bar\b|\bez bar\b""").containsMatchIn(n)) return 0.8
    return 0.3
}

/** Sort comparator for [LiftCandidate]s: proven first, mainstream second, priority desc third. */
fun compareLiftCandidates(a: LiftCandidate, b: LiftCandidate): Int {
    val aProven = if (a.priorityTier == "proven") 1 else 0
    val bProven = if (b.priorityTier == "proven") 1 else 0
    if (bProven != aProven) return bProven - aProven

    val aMain = if (a.mainstream == true) 1 else 0
    val bMain = if (b.mainstream == true) 1 else 0
    if (bMain != aMain) return bMain - aMain

    if (b.priority != a.priority) {
        return when {
            b.priority > a.priority -> 1
            b.priority < a.priority -> -1
            else -> 0
        }
    }
    return a.name.compareTo(b.name)
}

/**
 * Computes the 0.0–1.0 priority score for [lift] under [context].
 */
fun scoreLift(lift: LiftContext, context: ProgramGenerationContext): Double {
    val provenTier = if (lift.proven == true) 1.0 else 0.3

    val sessionCount = lift.sessionCount ?: 0
    val recencyScore =
        if (sessionCount > 0) {
            min(1.0, log10(sessionCount + 1.0) / log10(21.0))
        } else {
            0.0
        }

    val effectiveness = context.liftEffectivenessByLiftId[lift.id]
    val effectivenessScore =
        if (effectiveness != null && effectiveness.rank > 0) 1.0 / effectiveness.rank else 0.4

    val trend = lift.progressTrend
    val progressTrendScore =
        when (trend) {
            "Improving" -> 1.0
            "Stagnant" -> 0.5
            "Regressing" -> 0.2
            else -> 0.4
        }

    val evidence = effectiveness?.evidence
    val evidenceTier =
        when (evidence) {
            "Proven" -> 1.0
            "Hypothesis" -> 0.4
            "InsufficientData" -> 0.2
            else -> 0.3
        }

    val hasInsights = if (lift.sessionCount != null || lift.progressTrend != null) 1.0 else 0.3

    val mainstream = if (lift.mainstream == true) 1.0 else mainstreamNameScore(lift.name)

    return (
        0.30 * provenTier +
            0.20 * recencyScore +
            0.20 * effectivenessScore +
            0.15 * progressTrendScore +
            0.10 * evidenceTier +
            0.05 * hasInsights +
            MAINSTREAM_NAME_WEIGHT * mainstream
        )
}
