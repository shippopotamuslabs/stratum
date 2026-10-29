package com.stratum.domain.program.schedule


/**
 * Generation cycle-space defaults for program gen.
 *
 * **Single source of default values** for train count N + rest count R.
 * Application of these defaults for generated programs happens only in
 * [materializeGeneratedCycleSpace] when UX does not supply an explicit rest pattern —
 * context mappers and [com.stratum.application.program.GenerateProgramUseCase] pass raw UX
 * hints through and do not re-resolve defaults.
 *
 * No schedule-mode enum — only [cycleLength] + [restSlotIndices].
 *
 * Product rules:
 * - N ∈ [1, 5] → fixed **7-slot** week, R = 7 − N (user places rests; enables Day 1/3/5 splits)
 * - N = 6 → R ∈ {1, 2}, default R = 2 (cycleLength 8; optional 7)
 *
 * UI drafts may read the constants / [defaultRestSlotIndices] for display state only.
 */
object GenerationCycleDefaults {
    /** Program generation accepts at most this many training sessions. */
    const val MAX_GEN_TRAINING_DAYS: Int = 6

    /** Classic gen (N&lt;6) and 6-in-7 fixed week share a 7-slot cycle. */
    const val CLASSIC_WEEK_CYCLE_LENGTH: Int = 7

    /** Default for 6 train days: rotating 8-slot cycle (6 train + 2 rest). */
    const val SIX_DAY_ROTATING_CYCLE_LENGTH: Int = 8

    /** Optional fixed week for 6 train + 1 rest. */
    const val SIX_DAY_FIXED_WEEK_CYCLE_LENGTH: Int = CLASSIC_WEEK_CYCLE_LENGTH

    /**
     * Default rest slots for 6 train / 8-slot: mid-cycle and end (PPL-friendly spacing).
     * Indices are 0-based in `[0, 8)`.
     */
    val SIX_DAY_ROTATING_DEFAULT_RESTS: Set<Int> = setOf(3, 7)

    /** Default single rest for 6-in-7: end of cycle (index 6). */
    val SIX_DAY_FIXED_WEEK_DEFAULT_RESTS: Set<Int> = setOf(6)

    /**
     * Default rest count for [trainingDayCount]:
     * - N = 6 → 2
     * - N ∈ [1, 5] → 7 − N (full week)
     * - else → 0
     */
    fun defaultRestCount(trainingDayCount: Int): Int =
        when {
            trainingDayCount == MAX_GEN_TRAINING_DAYS -> 2
            trainingDayCount in 1 until MAX_GEN_TRAINING_DAYS ->
                CLASSIC_WEEK_CYCLE_LENGTH - trainingDayCount
            else -> 0
        }

    /** Default cycle length: 7 for classic, 8 for six-day. */
    fun defaultCycleLength(trainingDayCount: Int): Int =
        when {
            trainingDayCount == MAX_GEN_TRAINING_DAYS -> SIX_DAY_ROTATING_CYCLE_LENGTH
            trainingDayCount in 1 until MAX_GEN_TRAINING_DAYS -> CLASSIC_WEEK_CYCLE_LENGTH
            else -> trainingDayCount
        }

    /**
     * Allowed rest counts for [trainingDayCount].
     * N&lt;6 → {7−N} only; N=6 → {1, 2}; else empty.
     */
    fun allowedRestCounts(trainingDayCount: Int): Set<Int> =
        when {
            trainingDayCount == MAX_GEN_TRAINING_DAYS -> setOf(1, 2)
            trainingDayCount in 1 until MAX_GEN_TRAINING_DAYS ->
                setOf(CLASSIC_WEEK_CYCLE_LENGTH - trainingDayCount)
            else -> emptySet()
        }

    /**
     * Resolved generation cycle-space when no explicit rest pattern is fully supplied.
     * Returns null when [trainingDayCount] is outside gen range.
     *
     * Prefer UX always passing explicit rests; this fills defaults when only train count
     * (and optional cycle length) is known.
     */
    fun resolveGenerationHints(
        trainingDayCount: Int,
        cycleLength: Int? = null,
        restSlotIndices: Set<Int>? = null,
    ): GenerationCycleHints? {
        if (trainingDayCount !in 1..MAX_GEN_TRAINING_DAYS) return null
        val length = cycleLength ?: defaultCycleLength(trainingDayCount)
        val rests =
            restSlotIndices
                ?: defaultRestSlotIndices(
                    cycleLength = length,
                    trainingDayCount = trainingDayCount,
                )
        return GenerationCycleHints(cycleLength = length, restSlotIndices = rests)
    }

    /**
     * Resolved 6-day cycle-space for [materializeGeneratedCycleSpace] when no explicit rest
     * pattern is supplied. Returns null for classic (&lt;6).
     */
    fun resolveSixDayHints(
        trainingDayCount: Int,
        cycleLength: Int? = null,
        restSlotIndices: Set<Int>? = null,
    ): GenerationCycleHints? {
        if (trainingDayCount != MAX_GEN_TRAINING_DAYS) return null
        return resolveGenerationHints(
            trainingDayCount = trainingDayCount,
            cycleLength = cycleLength,
            restSlotIndices = restSlotIndices,
        )
    }

    /**
     * Default rest indices for a cycle of [cycleLength] with [trainingDayCount] train slots.
     * - 6 train / 8 slots → [SIX_DAY_ROTATING_DEFAULT_RESTS]
     * - 6 train / 7 slots → end rest [SIX_DAY_FIXED_WEEK_DEFAULT_RESTS]
     * - classic 7-slot → common split defaults (e.g. N=3 → rests leave slots 0/2/4 as train)
     * - else rests packed at the end of the cycle
     */
    fun defaultRestSlotIndices(
        cycleLength: Int,
        trainingDayCount: Int,
    ): Set<Int> {
        val restCount = (cycleLength - trainingDayCount).coerceAtLeast(0)
        if (restCount == 0 || cycleLength <= 0) return emptySet()

        val sixDayRests = sixDayDefaultRestSlotIndices(cycleLength, trainingDayCount)
        if (sixDayRests != null) return sixDayRests

        val classicRests =
            if (cycleLength == CLASSIC_WEEK_CYCLE_LENGTH &&
                trainingDayCount in 1 until MAX_GEN_TRAINING_DAYS
            ) {
                classicSevenDayDefaultRests(trainingDayCount)
            } else {
                null
            }
        if (classicRests != null) return classicRests

        // Single rest → end of cycle; multiple → pack toward the end.
        return if (restCount == 1) {
            setOf(cycleLength - 1)
        } else {
            ((cycleLength - restCount) until cycleLength).toSet()
        }
    }

    private fun sixDayDefaultRestSlotIndices(
        cycleLength: Int,
        trainingDayCount: Int,
    ): Set<Int>? {
        if (trainingDayCount != MAX_GEN_TRAINING_DAYS) return null
        return when (cycleLength) {
            SIX_DAY_ROTATING_CYCLE_LENGTH -> SIX_DAY_ROTATING_DEFAULT_RESTS
            SIX_DAY_FIXED_WEEK_CYCLE_LENGTH -> SIX_DAY_FIXED_WEEK_DEFAULT_RESTS
            else -> null
        }
    }

    /**
     * Common 7-slot rest defaults so train slots land on familiar split positions
     * (1-based Day 1/3/5 for 3-day = indices 0/2/4).
     */
    private fun classicSevenDayDefaultRests(trainingDayCount: Int): Set<Int>? =
        when (trainingDayCount) {
            1 -> setOf(1, 2, 3, 4, 5, 6)
            2 -> setOf(1, 2, 4, 5, 6) // train 0, 3
            3 -> setOf(1, 3, 5, 6) // train 0, 2, 4 (Day 1/3/5)
            4 -> setOf(2, 5, 6) // train 0, 1, 3, 4
            5 -> setOf(2, 6) // train 0, 1, 3, 4, 5 (Day 1/2/4/5/6)
            else -> null
        }

    /**
     * Infer train count from an **unambiguous** cycle length only.
     * Classic 7-slot length is ambiguous (3–5 train common) — callers must pass
     * [trainingDayCount] explicitly; this throws for [CLASSIC_WEEK_CYCLE_LENGTH].
     */
    fun inferTrainingDayCount(cycleLength: Int): Int =
        when (cycleLength) {
            SIX_DAY_ROTATING_CYCLE_LENGTH -> MAX_GEN_TRAINING_DAYS
            CLASSIC_WEEK_CYCLE_LENGTH ->
                error(
                    "cycleLength=$CLASSIC_WEEK_CYCLE_LENGTH is ambiguous; pass trainingDayCount " +
                        "explicitly (do not infer rests for classic weeks from length alone)",
                )
            else -> (cycleLength - 1).coerceAtLeast(1)
        }

    /**
     * Default rests when both [cycleLength] and [trainingDayCount] are known.
     * Prefer this over length-only inference.
     */
    fun defaultRestSlotIndicesForCycleLength(
        cycleLength: Int,
        trainingDayCount: Int,
    ): Set<Int> =
        defaultRestSlotIndices(
            cycleLength = cycleLength,
            trainingDayCount = trainingDayCount,
        )
}

/** Optional generation context cycle-space pair (not a schedule-mode enum). */
data class GenerationCycleHints(
    val cycleLength: Int,
    val restSlotIndices: Set<Int>,
) {
    init {
        // Partial hints are legitimate (generation input), but a supplied pair must
        // still satisfy the shared cycle geometry (STR-167).
        requireCycleGeometry(cycleLength, restSlotIndices)
    }
}
