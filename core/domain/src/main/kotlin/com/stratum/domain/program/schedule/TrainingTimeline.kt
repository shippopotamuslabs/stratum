package com.stratum.domain.program.schedule

/**
 * Recovery / structure geometry for program generation: training slots on a ring of
 * [cycleLength] (train + rest). Rest slots are not listed; they only affect [gap] by
 * increasing modular distance between training slots.
 *
 * Classic layouts often use [cycleLength] = 7; rotating gen (e.g. 6-in-8) uses the resolved
 * rest pattern from [materializeGeneratedCycleSpace].
 */
data class TrainingTimeline(
    val cycleLength: Int,
    val trainingSlotIndices: List<Int>,
) {
    init {
        require(cycleLength > 0) { "cycleLength must be positive, was $cycleLength" }
        require(trainingSlotIndices.all { index -> index in 0 until cycleLength }) {
            "trainingSlotIndices $trainingSlotIndices must all lie in [0, $cycleLength)"
        }
        require(trainingSlotIndices.distinct().size == trainingSlotIndices.size) {
            "trainingSlotIndices must be unique: $trainingSlotIndices"
        }
    }

    val trainingSlotSet: Set<Int> get() = trainingSlotIndices.toSet()

    val restSlotIndices: Set<Int>
        get() = (0 until cycleLength).filterNot { index -> index in trainingSlotSet }.toSet()

    /**
     * Forward modular gap from [earlierSlot] to [laterSlot] on the cycle ring.
     * Gap 1 = consecutive slots (back-to-back days).
     */
    fun gap(earlierSlot: Int, laterSlot: Int): Int {
        if (cycleLength <= 0) return 0
        return (laterSlot - earlierSlot + cycleLength) % cycleLength
    }

    /** Ordered (earlier, later) pairs of training slots with [gap] == 1, including wrap. */
    fun adjacentTrainingPairs(): List<Pair<Int, Int>> =
        pairsWithMaxGap(maxGap = 1).filter { (earlier, later) -> gap(earlier, later) == 1 }

    /**
     * Ordered (earlier, later) training-slot pairs with gap in `1..maxGap` (inclusive),
     * including cycle wrap.
     */
    fun pairsWithMaxGap(maxGap: Int): List<Pair<Int, Int>> {
        if (maxGap < 1 || trainingSlotIndices.size < 2) return emptyList()
        val pairs = mutableListOf<Pair<Int, Int>>()
        for (earlier in trainingSlotIndices) {
            for (later in trainingSlotIndices) {
                if (earlier == later) continue
                val g = gap(earlier, later)
                if (g in 1..maxGap) pairs += earlier to later
            }
        }
        return pairs
    }

    /**
     * All ordered training pairs for soft recovery checks: every unordered pair as
     * forward (earlier, later) by slot index order, plus wrap last → first when
     * wrap gap is in `1 until cycleLength`.
     */
    fun pairsForRecoveryCheck(): List<Pair<Int, Int>> {
        val sorted = trainingSlotIndices.sorted()
        if (sorted.size < 2) return emptyList()
        val pairs = mutableListOf<Pair<Int, Int>>()
        for (earlierIndex in sorted.indices) {
            for (laterIndex in earlierIndex + 1 until sorted.size) {
                pairs += sorted[earlierIndex] to sorted[laterIndex]
            }
        }
        val wrapEarlier = sorted.last()
        val wrapLater = sorted.first()
        val wrapGap = gap(wrapEarlier, wrapLater)
        if (wrapGap in 1 until cycleLength) {
            pairs += wrapEarlier to wrapLater
        }
        return pairs
    }

    /**
     * True when [slot] is a training day with rest on both previous and next cycle slots
     * (ring wrap).
     */
    fun isRestSandwiched(slot: Int): Boolean {
        if (slot !in trainingSlotSet) return false
        val previous = (slot - 1 + cycleLength) % cycleLength
        val next = (slot + 1) % cycleLength
        return previous !in trainingSlotSet && next !in trainingSlotSet
    }

    companion object {
        /**
         * Builds a timeline from an already-resolved [MaterializedCycleSpace].
         * Training slots = complement of rests in `[0, cycleLength)`.
         */
        fun fromCycleSpace(space: MaterializedCycleSpace): TrainingTimeline {
            val train =
                (0 until space.cycleLength)
                    .filterNot { index -> index in space.restSlotIndices }
                    .sorted()
            return TrainingTimeline(
                cycleLength = space.cycleLength,
                trainingSlotIndices = train,
            )
        }

        /**
         * Resolves cycle-space via [materializeGeneratedCycleSpace], then builds the timeline.
         * Prefer [ProgramGenerationContext.resolveTrainingTimeline] at pipeline/solver entry.
         */
        fun resolve(
            trainingSlotCount: Int,
            restSlotIndices: Set<Int>? = null,
            cycleLength: Int? = null,
        ): TrainingTimeline {
            val space =
                materializeGeneratedCycleSpace(
                    trainingSlotCount = trainingSlotCount,
                    restSlotIndicesHint = restSlotIndices,
                    cycleLengthHint = cycleLength,
                )
            return fromCycleSpace(space)
        }
    }
}

/** Forward modular gap on a ring of [cycleLength]. Prefer [TrainingTimeline.gap] when available. */
fun cycleSlotGap(earlierSlot: Int, laterSlot: Int, cycleLength: Int): Int {
    require(cycleLength > 0) { "cycleLength must be positive, was $cycleLength" }
    return (laterSlot - earlierSlot + cycleLength) % cycleLength
}
