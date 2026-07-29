package com.stratum.domain.workout.progression

import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.WeightConversion
import com.stratum.domain.lift.roundToIncrement
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.training.EstimatedOneRm
import com.stratum.domain.workout.BACKOFF_REP_FLOOR
import com.stratum.domain.workout.SetSlot
import com.stratum.domain.workout.SlotHistoryEntry
import com.stratum.domain.workout.SlotRecommendation
import com.stratum.domain.workout.fatigue.projectReps

/** Request for a [ProgressionEngine] per-slot recommendation. */
data class SlotProgressionRequest(
    val slot: SetSlot,
    val history: List<SlotHistoryEntry>,
    val fallbackOneRm: Weight?,
    val loadType: LoadType = LoadType.Weighted,
    val currentTopSetWeight: Weight? = null,
    val priorSlotRecommendation: SlotRecommendation? = null,
    val priorSlot: SetSlot? = null,
    val perLiftSlope: Double? = null,
    val perLiftIntercept: Double? = null,
    /**
     * Progression-math baseline for this slot, built by the orchestrator from a
     * capacity-ordered recent-session block. `null` = cold start (no history for this
     * lift at all). See [ProgressionBaseline] for the two production modes.
     *
     * [history] is retained for `previousWeight`/`previousReps`/`previousRir` (UI display
     * of what was actually done at this slot position last time) — it is NOT used for math.
     */
    val progressionBaseline: ProgressionBaseline? = null,
    /**
     * Per-lift increment bands from the catalog. Empty → engine uses its scheme default
     * (Double: 5 lb; Linear: [LinearProgressionConfig] values).
     */
    val incrementTiers: List<IncrementTier> = emptyList(),
)

/**
 * Dispatch interface consumed by `GenerateProgressionSnapshotUseCase`. Each scheme has
 * a concrete implementation ([DoubleProgressionEngine], [LinearProgressionEngine]).
 */
interface ProgressionEngine {
    val scheme: ProgressionScheme
    fun recommend(request: SlotProgressionRequest): SlotRecommendation
}

/**
 * Scheme-specific top-set load selection for [recommendShared]. Engines supply only the
 * advance gate and increment amount; shared orchestration owns floor drops, fatigue
 * projection, inherit guards, and cold-start rep floors.
 */
data class ProgressionLoadPolicy(
    /**
     * Whether the top-set load lever advances from [baseline] (Double: top-at-RIR;
     * Linear: bottom-at-RIR).
     */
    val shouldAdvanceLoad: (baseline: SlotHistoryEntry?, slot: SetSlot) -> Boolean,
    /** Increment magnitude when [shouldAdvanceLoad] is true. */
    val incrementFor: (request: SlotProgressionRequest, baseWeight: Weight) -> Weight,
    /**
     * Held-load, missing-RIR top-set rep target. Double keeps [prevReps]; Linear resets
     * to [minRecommendedReps].
     */
    val heldTopRepsWithoutRir: (prevReps: Int, slot: SetSlot) -> Int,
)

/**
 * Shared Double/Linear recommend orchestration. Bodyweight short-circuits; weighted
 * path selects target load via [policy], then projects reps (and floor-driven weight
 * drops) from the capacity baseline / prior-slot fatigue model.
 */
fun recommendShared(
    request: SlotProgressionRequest,
    policy: ProgressionLoadPolicy,
): SlotRecommendation {
    val slot = request.slot
    val latest = request.history.firstOrNull()
    val prevWeight = latest?.loggedSet?.weight
    val prevReps = latest?.loggedSet?.reps
    val prevRir = latest?.loggedSet?.rir
    val baselineEntry = baselineEntryFor(request.progressionBaseline)

    if (request.loadType == LoadType.Bodyweight) {
        val zeroWeight = Weight(0.0, prevWeight?.unit ?: Weight.WeightUnit.Lb)
        val baselineReps = baselineEntry?.loggedSet?.reps ?: slot.repMin
        return SlotRecommendation(
            slotId = slot.slotId,
            recommendedWeight = zeroWeight,
            baselineExpectedReps = baselineReps,
            previousWeight = prevWeight,
            previousReps = prevReps,
            previousRir = prevRir,
        )
    }

    val baseWeight = baselineEntry?.loggedSet?.weight
        ?: request.fallbackOneRm
        ?: Weight(0.0, Weight.WeightUnit.Lb)
    val baselineExpectedReps = baselineEntry?.loggedSet?.reps ?: minRecommendedReps(slot)

    val targetWeight = request.currentTopSetWeight ?: selectTopOrBaseWeight(
        request = request,
        slot = slot,
        policy = policy,
        baselineEntry = baselineEntry,
        baseWeight = baseWeight,
    )

    val (finalWeight, rawReps) = resolveWeightedRepsAndWeight(
        request = request,
        policy = policy,
        slot = slot,
        baselineEntry = baselineEntry,
        baselineExpectedReps = baselineExpectedReps,
        targetWeight = targetWeight,
    )
    // Final safety net: never emit sub-floor recommended reps for weighted progression.
    val recommendedReps = rawReps.coerceAtLeast(minRecommendedReps(slot))

    val isProjected = baselineEntry != null || (request.priorSlotRecommendation?.isProjected == true)

    return SlotRecommendation(
        slotId = slot.slotId,
        recommendedWeight = finalWeight,
        baselineExpectedReps = recommendedReps,
        previousWeight = prevWeight,
        previousReps = prevReps,
        previousRir = prevRir,
        isProjected = isProjected,
    )
}

private fun selectTopOrBaseWeight(
    request: SlotProgressionRequest,
    slot: SetSlot,
    policy: ProgressionLoadPolicy,
    baselineEntry: SlotHistoryEntry?,
    baseWeight: Weight,
): Weight {
    if (!slot.isTopSet) return baseWeight
    val raw =
        if (policy.shouldAdvanceLoad(baselineEntry, slot)) {
            val increment = policy.incrementFor(request, baseWeight)
            Weight(baseWeight.amount + increment.amount, baseWeight.unit)
        } else {
            baseWeight
        }
    return request.incrementTiers
        .roundToIncrement(raw, request.loadType)
        .capAssistedAtZero(request.loadType)
}

/**
 * Chooses reps (and possibly a lower weight when not locked to a shared straight-set load)
 * from baseline / sequential prior / cold-start paths.
 */
private fun resolveWeightedRepsAndWeight(
    request: SlotProgressionRequest,
    policy: ProgressionLoadPolicy,
    slot: SetSlot,
    baselineEntry: SlotHistoryEntry?,
    baselineExpectedReps: Int,
    targetWeight: Weight,
): Pair<Weight, Int> {
    // No recent baseline — project one fatigue step from the prior slot rec (sequential).
    if (baselineEntry == null &&
        request.progressionBaseline is ProgressionBaseline.ProjectFromPrior
    ) {
        val projected = projectFromPrior(request, slot, targetWeight)
        return if (projected != null) {
            applyFloorWeight(request, targetWeight, projected.first) to projected.second
        } else {
            targetWeight to coldStartReps(slot, baselineExpectedReps)
        }
    }

    if (baselineEntry != null && baselineEntry.loggedSet.weight == targetWeight) {
        return resolveSameWeightBaselineReps(
            request = request,
            policy = policy,
            slot = slot,
            baselineEntry = baselineEntry,
            baselineExpectedReps = baselineExpectedReps,
            targetWeight = targetWeight,
        )
    }

    if (baselineEntry != null) {
        return resolveDifferentWeightBaselineReps(
            request = request,
            policy = policy,
            slot = slot,
            baselineEntry = baselineEntry,
            targetWeight = targetWeight,
        )
    }

    return resolvePriorOrColdStart(request, slot, baselineExpectedReps, targetWeight)
}

private fun resolveSameWeightBaselineReps(
    request: SlotProgressionRequest,
    policy: ProgressionLoadPolicy,
    slot: SetSlot,
    baselineEntry: SlotHistoryEntry,
    baselineExpectedReps: Int,
    targetWeight: Weight,
): Pair<Weight, Int> {
    if (slot.isTopSet && policy.shouldAdvanceLoad(baselineEntry, slot) &&
        request.currentTopSetWeight == null
    ) {
        return targetWeight to minRecommendedReps(slot)
    }
    val prevSet = baselineEntry.loggedSet
    if (prevSet.rir != null) {
        val capacity = prevSet.reps + prevSet.rir
        val e1rm = EstimatedOneRm.calculate(
            WeightConversion.loadMagnitudeLb(prevSet.weight),
            capacity,
        )
        val (w, r) = ensureRepFloorPreferWeightDrop(
            targetWeight = targetWeight,
            capacityRepsAt0Rir = capacity,
            slot = slot,
            e1rm = e1rm,
            incrementTiers = request.incrementTiers,
            loadType = request.loadType,
        )
        return applyFloorWeight(request, targetWeight, w) to r
    }
    val reps = clampRecommendedReps(
        if (slot.isTopSet) {
            policy.heldTopRepsWithoutRir(prevSet.reps, slot)
        } else {
            baselineExpectedReps
        },
        slot,
    )
    return targetWeight to reps
}

/**
 * Real [FromRecent] baseline at a different target load: always reproject **this** set's
 * capacity via e1RM ([projectBaselineAtTargetWeight]). Do **not** chain FatigueModel from
 * the prior slot — that discards known mid-block history after shared reduce / advance.
 * Sequential prior fatigue is reserved for [ProgressionBaseline.ProjectFromPrior] only.
 */
private fun resolveDifferentWeightBaselineReps(
    request: SlotProgressionRequest,
    policy: ProgressionLoadPolicy,
    slot: SetSlot,
    baselineEntry: SlotHistoryEntry,
    targetWeight: Weight,
): Pair<Weight, Int> {
    val advancedAboveHistory = slot.isTopSet &&
        policy.shouldAdvanceLoad(baselineEntry, slot) &&
        WeightConversion.amountInLb(targetWeight) >
            WeightConversion.amountInLb(baselineEntry.loggedSet.weight) + AMOUNT_EPS
    if (advancedAboveHistory) {
        // Load lever advanced: rep lever resets. Shared look-ahead already ensured floor.
        return targetWeight to minRecommendedReps(slot)
    }
    val (w, r) = projectBaselineAtTargetWeight(
        baselineEntry = baselineEntry,
        targetWeight = targetWeight,
        slot = slot,
        incrementTiers = request.incrementTiers,
        loadType = request.loadType,
    )
    return applyFloorWeight(request, targetWeight, w) to r
}

private fun resolvePriorOrColdStart(
    request: SlotProgressionRequest,
    slot: SetSlot,
    baselineExpectedReps: Int,
    targetWeight: Weight,
): Pair<Weight, Int> {
    val priorRec = request.priorSlotRecommendation
    val priorSlot = request.priorSlot
    if (priorRec != null && priorSlot != null) {
        val (w, r) = floorFromPriorFatigue(
            request = request,
            slot = slot,
            targetWeight = targetWeight,
            priorRec = priorRec,
            priorSlot = priorSlot,
        )
        return applyFloorWeight(request, targetWeight, w) to r
    }
    return targetWeight to coldStartReps(slot, baselineExpectedReps)
}

/**
 * Projects reps (and possibly a lower weight) from the prior slot recommendation via
 * the fatigue model. Returns null when no prior rec is available.
 */
fun projectFromPrior(
    request: SlotProgressionRequest,
    slot: SetSlot,
    targetWeight: Weight,
): Pair<Weight, Int>? {
    val priorRec = request.priorSlotRecommendation ?: return null
    val priorSlot = request.priorSlot ?: return null
    return floorFromPriorFatigue(request, slot, targetWeight, priorRec, priorSlot)
}

/**
 * Straight-set inheritance: when [SlotProgressionRequest.currentTopSetWeight] is set, the
 * orchestrator has already chosen one shared load (including look-ahead reduce). Keep that
 * weight and adjust reps only — never splinter straight sets with a per-slot drop.
 *
 * When unset (load-selection call for a top set / non-straight path), accept a floor-driven
 * [floorWeight] so single-slot capacity can still cut load.
 */
fun applyFloorWeight(
    request: SlotProgressionRequest,
    targetWeight: Weight,
    floorWeight: Weight,
): Weight =
    if (request.currentTopSetWeight != null) {
        targetWeight
    } else {
        floorWeight
    }

/** Cold-start / no-prior rep target: floor for top sets, clamped baseline for backoffs. */
fun coldStartReps(slot: SetSlot, baselineExpectedReps: Int): Int =
    if (slot.isTopSet) {
        minRecommendedReps(slot)
    } else {
        clampRecommendedReps(baselineExpectedReps, slot)
    }

fun baselineEntryFor(baseline: ProgressionBaseline?): SlotHistoryEntry? =
    when (baseline) {
        is ProgressionBaseline.FromRecent -> baseline.entry
        ProgressionBaseline.ProjectFromPrior, null -> null
    }

/**
 * Reprojects a real baseline performance at a different target load (same set capacity,
 * no multi-set fatigue). Used when shared-weight look-ahead lowered load below history.
 */
fun projectBaselineAtTargetWeight(
    baselineEntry: SlotHistoryEntry,
    targetWeight: Weight,
    slot: SetSlot,
    incrementTiers: List<IncrementTier>,
    loadType: LoadType,
): Pair<Weight, Int> {
    val prevSet = baselineEntry.loggedSet
    val failureReps = (prevSet.reps + (prevSet.rir ?: 0)).coerceAtLeast(1)
    val e1rm = EstimatedOneRm.calculate(
        WeightConversion.loadMagnitudeLb(prevSet.weight),
        failureReps,
    )
    val capacityAtTarget =
        EstimatedOneRm.repsForWeight(e1rm, WeightConversion.loadMagnitudeLb(targetWeight))
    return ensureRepFloorPreferWeightDrop(
        targetWeight = targetWeight,
        capacityRepsAt0Rir = capacityAtTarget,
        slot = slot,
        e1rm = e1rm,
        incrementTiers = incrementTiers,
        loadType = loadType,
    )
}

/**
 * One fatigue step forward from the prior slot recommendation (prior treated as set index 1).
 * Orchestrator must pass the **immediately previous** slot — not always the top set — so
 * mid-block history decay is not discarded by re-anchoring to the top set.
 */
private fun floorFromPriorFatigue(
    request: SlotProgressionRequest,
    slot: SetSlot,
    targetWeight: Weight,
    priorRec: SlotRecommendation,
    priorSlot: SetSlot,
): Pair<Weight, Int> {
    val priorFailureReps = (priorRec.baselineExpectedReps + priorSlot.rirGoal).coerceAtLeast(1)
    val priorE1rm = EstimatedOneRm.calculate(
        WeightConversion.loadMagnitudeLb(priorRec.recommendedWeight),
        priorFailureReps,
    )
    val rawCapacityReps = projectReps(
        baselineOneRm = priorE1rm,
        targetWeight = targetWeight,
        nextSetIndex = NEXT_SET_FROM_PRIOR_INDEX,
        perLiftSlope = request.perLiftSlope,
        perLiftIntercept = request.perLiftIntercept,
    )
    return ensureRepFloorPreferWeightDrop(
        targetWeight = targetWeight,
        capacityRepsAt0Rir = rawCapacityReps,
        slot = slot,
        e1rm = priorE1rm,
        incrementTiers = request.incrementTiers,
        loadType = request.loadType,
    )
}

/** [projectReps] index for "one set after the prior baseline" (prior = index 1). */
private const val NEXT_SET_FROM_PRIOR_INDEX: Int = 2

private const val AMOUNT_EPS: Double = 1e-9

/**
 * Clamps an assisted progression result at 0 so the weight never crosses into positive
 * (a positive assisted weight would mean the lifter is now *adding* load, which is
 * weighted territory — graduation from assistance is signaled by reaching 0, after which
 * the lifter should swap to the bodyweight or weighted variant). No-op for non-assisted
 * load types. Shared by both progression engines so the cap logic is canonical.
 */
fun Weight.capAssistedAtZero(loadType: LoadType): Weight =
    if (loadType == LoadType.Assisted && amount > 0.0) Weight(0.0, unit) else this

/**
 * RIR-aware rep projection for a held weight (D8). When the load lever is unchanged
 * across sessions, the reps lever advances instead: a set logged at [prevReps] with
 * [prevRir] reps-in-reserve implies a projected-to-failure capacity of
 * `prevReps + prevRir`; the next session targets `failureCapacity − slot.rirGoal`.
 *
 * - **Top sets** (rep range present, [SetSlot.isTopSet] = true): the target is coerced
 *   into `[max(repMin, floor), max(repMax, floor)]` so it stays in band and never
 *   drops below [BACKOFF_REP_FLOOR].
 * - **Backoff sets** (RIR-only slots): the target is floored at [BACKOFF_REP_FLOOR]
 *   so a wide gap doesn't prescribe an unreasonable last-set rep count.
 *
 * Returns `null` when RIR data is absent — callers fall back to the previous rep count
 * (no rep-projection signal available). Pure and shared by both progression engines so
 * the rep-scaling math is canonical.
 *
 * Note: this helper only adjusts **reps**. Callers that detect capacity below the floor
 * should prefer [ensureRepFloorPreferWeightDrop] so load is reduced first.
 */
fun rirAdjustedReps(
    prevReps: Int,
    prevRir: Int?,
    slot: SetSlot,
): Int? {
    if (prevRir == null) return null
    val failureCapacity = prevReps + prevRir
    val targetAtRir = failureCapacity - slot.rirGoal
    return clampRecommendedReps(targetAtRir, slot)
}

/** Lower bound for recommended reps on [slot] — never below [BACKOFF_REP_FLOOR]. */
fun minRecommendedReps(slot: SetSlot): Int =
    if (slot.isTopSet) maxOf(slot.repMin, BACKOFF_REP_FLOOR) else BACKOFF_REP_FLOOR

/** Upper bound for top-set recommended reps; backoff slots are unbounded above the floor. */
fun maxRecommendedReps(slot: SetSlot): Int =
    if (slot.isTopSet && slot.repMax > 0) maxOf(slot.repMax, BACKOFF_REP_FLOOR) else Int.MAX_VALUE

/** Clamps a raw at-RIR rep projection into the slot band with a global floor of 5. */
fun clampRecommendedReps(atTargetRir: Int, slot: SetSlot): Int {
    val minReps = minRecommendedReps(slot)
    val maxReps = maxRecommendedReps(slot)
    return if (slot.isTopSet && slot.repMax > 0) {
        atTargetRir.coerceIn(minReps, maxReps)
    } else {
        atTargetRir.coerceAtLeast(minReps)
    }
}

/**
 * Enforces [BACKOFF_REP_FLOOR] on a (weight, reps) recommendation. When capacity at
 * [targetWeight] would yield fewer than the floor at the slot's RIR goal, **drop weight**
 * (tier-rounded) so the lifter can hit the floor rather than prescribing sub-floor reps.
 * If load cannot be reduced (bodyweight / zero e1RM / already at floor load), still never
 * returns a rep recommendation below the floor.
 *
 * @param capacityRepsAt0Rir failure-capacity reps at [targetWeight] (0-RIR scale)
 * @param e1rm e1RM used to invert a lower load when dropping weight; ≤0 skips the drop
 */
fun ensureRepFloorPreferWeightDrop(
    targetWeight: Weight,
    capacityRepsAt0Rir: Int,
    slot: SetSlot,
    e1rm: Double,
    incrementTiers: List<IncrementTier>,
    loadType: LoadType,
): Pair<Weight, Int> {
    val minReps = minRecommendedReps(slot)
    val atRir = capacityRepsAt0Rir - slot.rirGoal
    if (atRir >= minReps) {
        return targetWeight to clampRecommendedReps(atRir, slot)
    }
    // Capacity below floor — prefer a weight drop so ≥minReps is achievable at rirGoal.
    if (loadType == LoadType.Bodyweight || e1rm <= 0.0 || targetWeight.amount <= 0.0) {
        return targetWeight to minReps
    }
    val reducedAmount = EstimatedOneRm.weightForReps(e1rm, minReps + slot.rirGoal)
    val reduced = incrementTiers
        .roundToIncrement(Weight(reducedAmount, targetWeight.unit), loadType)
        .capAssistedAtZero(loadType)
    // Never raise load when trying to recover the rep floor.
    val finalWeight =
        if (reduced.amount < targetWeight.amount) reduced else targetWeight
    return finalWeight to minReps
}
