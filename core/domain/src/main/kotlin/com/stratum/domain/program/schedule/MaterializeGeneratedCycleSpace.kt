@file:Suppress("MatchingDeclarationName")

package com.stratum.domain.program.schedule

import com.stratum.domain.program.Program

/**
 * Full-cycle identity for a generated program: [cycleLength] training **plus** rest slots, and
 * the rest indices within that cycle.
 *
 * Invariant: `trainingSlotCount + restSlotIndices.size == cycleLength`.
 *
 * Canonical generation / runtime schedule identity.
 * [GenerationCycleSpace] is an alias for call sites that want the domain name.
 */
data class MaterializedCycleSpace(
    val cycleLength: Int,
    val restSlotIndices: Set<Int>,
) {
    init {
        require(cycleLength > 0) { "cycleLength must be positive, was $cycleLength" }
        require(restSlotIndices.all { index -> index in 0 until cycleLength }) {
            "restSlotIndices $restSlotIndices must all lie in [0, $cycleLength)"
        }
    }

    val trainingSlotCount: Int get() = cycleLength - restSlotIndices.size
}

/** Domain name for [MaterializedCycleSpace] — generation and runtime schedule identity. */
typealias GenerationCycleSpace = MaterializedCycleSpace

/**
 * Derives [MaterializedCycleSpace] for generation materialize.
 *
 * **Sole choke point** for applying generation cycle-space defaults. Upstream layers pass
 * raw UX hints only. New gen always supplies explicit rest + cycle length (precedence #1).
 *
 * Precedence:
 * 1. **Explicit rest pattern** ([restSlotIndicesHint], optional [cycleLengthHint]) — primary
 *    path for gen. When only rests are provided, `cycleLength = trainingSlotCount + rests.size`.
 * 2. **Train-only** when [cycleLengthHint] equals train count (empty rests).
 * 3. **Defaults** via [GenerationCycleDefaults] for N ∈ [1, 6] (7-slot classic or 8-slot rotating).
 * 4. **Train-only** fallback: `cycleLength = trainingSlotCount`, empty rests.
 *
 * No weekday list — slot count + rest indices are the only identity.
 */
fun materializeGeneratedCycleSpace(
    trainingSlotCount: Int,
    restSlotIndicesHint: Set<Int>? = null,
    cycleLengthHint: Int? = null,
): MaterializedCycleSpace {
    require(trainingSlotCount > 0) {
        "trainingSlotCount must be positive, was $trainingSlotCount"
    }

    if (restSlotIndicesHint != null) {
        val cycleLength = cycleLengthHint ?: (trainingSlotCount + restSlotIndicesHint.size)
        val restsInRange =
            cycleLength > 0 && restSlotIndicesHint.all { index -> index in 0 until cycleLength }
        val trainingFromPattern = cycleLength - restSlotIndicesHint.size
        if (restsInRange && trainingFromPattern == trainingSlotCount) {
            return MaterializedCycleSpace(
                cycleLength = cycleLength,
                restSlotIndices = restSlotIndicesHint,
            )
        }
        // Hint no longer matches (e.g. sanitization dropped workouts) — fall through.
    }

    if (cycleLengthHint != null && cycleLengthHint == trainingSlotCount) {
        return MaterializedCycleSpace(
            cycleLength = cycleLengthHint,
            restSlotIndices = emptySet(),
        )
    }

    if (trainingSlotCount in 1..GenerationCycleDefaults.MAX_GEN_TRAINING_DAYS) {
        val length =
            cycleLengthHint
                ?: GenerationCycleDefaults.defaultCycleLength(trainingSlotCount)
        val rests =
            GenerationCycleDefaults.defaultRestSlotIndices(
                cycleLength = length,
                trainingDayCount = trainingSlotCount,
            )
        if (length - rests.size == trainingSlotCount) {
            return MaterializedCycleSpace(cycleLength = length, restSlotIndices = rests)
        }
    }

    return MaterializedCycleSpace(
        cycleLength = trainingSlotCount,
        restSlotIndices = emptySet(),
    )
}

/**
 * Re-derives [Program.cycleLength] + [Program.restSlotIndices] from the current workouts and
 * optional generation rest pattern. Call after sanitization drops workouts so the denormalized
 * cycle stays consistent with survivors.
 */
fun Program.withMaterializedCycleSpace(
    restSlotIndicesHint: Set<Int>? = null,
    cycleLengthHint: Int? = null,
): Program {
    if (workouts.isEmpty()) return this
    val space =
        materializeGeneratedCycleSpace(
            trainingSlotCount = workouts.size,
            restSlotIndicesHint = restSlotIndicesHint ?: restSlotIndices.takeIf { it.isNotEmpty() },
            cycleLengthHint = cycleLengthHint ?: cycleLength,
        )
    return if (cycleLength == space.cycleLength && restSlotIndices == space.restSlotIndices) {
        this
    } else {
        copy(
            cycleLength = space.cycleLength,
            restSlotIndices = space.restSlotIndices,
        )
    }
}
