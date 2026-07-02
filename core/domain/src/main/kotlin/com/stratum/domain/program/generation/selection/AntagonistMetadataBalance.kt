package com.stratum.domain.program.generation.selection

import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.PRIMARY_SET_CREDIT
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.context.SECONDARY_SET_CREDIT
import kotlin.math.abs

/**
 * Maintenance antagonist pairs balanced on lift-metadata weekly volume (what the Programs UI shows),
 * not solver pattern credits. Joint-safer direction: back ≥ chest, ham ≥ quad. Symmetric pairs
 * (biceps/triceps) are kept roughly even with no directional preference.
 */
object AntagonistMetadataBalance {
    private data class Pair(
        val weaker: String,
        val stronger: String,
    )

    private data class SymmetricPair(
        val muscleA: String,
        val muscleB: String,
    )

    private val maintenancePairs =
        listOf(
            Pair("Chest", "Back"),
            Pair("Quad", "Hamstring"),
        )

    /** Pairs kept roughly even with no directional preference. */
    private val symmetricPairs =
        listOf(
            SymmetricPair("Bicep", "Tricep"),
        )

    /** Penalty scale when a candidate lift widens the wrong-way gap (quad > ham, chest > back). */
    private const val WRONG_WAY_METADATA_PENALTY_PER_SET = 0.18

    /** Small reward when a candidate closes the wrong-way gap without overshooting. */
    private const val GAP_CLOSE_METADATA_BONUS_PER_SET = 0.06

    /** Penalty scale when a candidate widens a symmetric imbalance (e.g. biceps vs triceps). */
    private const val SYMMETRIC_IMBALANCE_PENALTY_PER_SET = 0.18

    /** Small reward when a candidate closes a symmetric imbalance. */
    private const val SYMMETRIC_GAP_CLOSE_BONUS_PER_SET = 0.06

    fun projectLiftMetadataVolume(
        lift: LiftContext,
        setCount: Int,
        totals: MutableMap<String, Double>,
    ) {
        if (setCount <= 0) return
        for (muscle in lift.primaryMuscles) {
            totals[muscle] = (totals[muscle] ?: 0.0) + setCount * PRIMARY_SET_CREDIT
        }
        for (muscle in lift.secondaryMuscles) {
            if (muscle in lift.primaryMuscles) continue
            totals[muscle] = (totals[muscle] ?: 0.0) + setCount * SECONDARY_SET_CREDIT
        }
    }

    /**
     * Score adjustment for [lift] given [runningWeeklyVolume] after selection.
     * Negative when the lift would push quad above ham (or chest above back), or widen any
     * symmetric imbalance (e.g. biceps vs triceps).
     */
    fun balanceBonus(
        lift: LiftContext,
        setCount: Int,
        context: ProgramGenerationContext,
        runningWeeklyVolume: Map<String, Double>,
    ): Double {
        if (setCount <= 0) return 0.0
        val projected = runningWeeklyVolume.toMutableMap()
        projectLiftMetadataVolume(lift, setCount, projected)

        var bonus = 0.0
        for (pair in maintenancePairs) {
            if (!shouldBalancePair(pair.weaker, pair.stronger, context)) continue
            val weakBefore = runningWeeklyVolume[pair.weaker] ?: 0.0
            val strongBefore = runningWeeklyVolume[pair.stronger] ?: 0.0
            val weakAfter = projected[pair.weaker] ?: 0.0
            val strongAfter = projected[pair.stronger] ?: 0.0
            val excessBefore = wrongWayExcess(weakBefore, strongBefore)
            val excessAfter = wrongWayExcess(weakAfter, strongAfter)
            val delta = excessAfter - excessBefore
            if (delta > 0.0) {
                bonus -= delta * WRONG_WAY_METADATA_PENALTY_PER_SET
            } else if (delta < 0.0) {
                bonus += -delta * GAP_CLOSE_METADATA_BONUS_PER_SET
            }
        }
        for (pair in symmetricPairs) {
            if (!shouldBalanceSymmetricPair(pair.muscleA, pair.muscleB, context)) continue
            val aBefore = runningWeeklyVolume[pair.muscleA] ?: 0.0
            val bBefore = runningWeeklyVolume[pair.muscleB] ?: 0.0
            val aAfter = projected[pair.muscleA] ?: 0.0
            val bAfter = projected[pair.muscleB] ?: 0.0
            val imbalanceBefore = abs(aBefore - bBefore)
            val imbalanceAfter = abs(aAfter - bAfter)
            val delta = imbalanceAfter - imbalanceBefore
            if (delta > 0.0) {
                bonus -= delta * SYMMETRIC_IMBALANCE_PENALTY_PER_SET
            } else if (delta < 0.0) {
                bonus += -delta * SYMMETRIC_GAP_CLOSE_BONUS_PER_SET
            }
        }
        return bonus
    }

    private fun wrongWayExcess(weaker: Double, stronger: Double): Double =
        maxOf(0.0, weaker - stronger)

    private fun specializationTierValue(level: String): Int =
        when (level) {
            "Specialize" -> 2
            "Maintenance" -> 1
            "MinimumVolume" -> 0
            else -> 1
        }

    private fun shouldBalancePair(
        weaker: String,
        stronger: String,
        context: ProgramGenerationContext,
    ): Boolean {
        val weakerLevel = context.specialization[weaker] ?: "Maintenance"
        val strongerLevel = context.specialization[stronger] ?: "Maintenance"
        if (weakerLevel == "Omit" || strongerLevel == "Omit") return false
        val valWeaker = specializationTierValue(weakerLevel)
        val valStronger = specializationTierValue(strongerLevel)
        return valStronger >= valWeaker
    }

    /**
     * Symmetric pair balance fires only when both muscles share the same specialization tier. When
     * tiers differ (e.g. Tricep Specialize + Bicep Maintenance) there is no preferred direction to
     * absorb the gap, so balancing would fight the solver's already-correct specialization decision.
     * Same-tier (notably both Maintenance, the common case) is where parity is achievable.
     */
    private fun shouldBalanceSymmetricPair(
        muscleA: String,
        muscleB: String,
        context: ProgramGenerationContext,
    ): Boolean {
        val levelA = context.specialization[muscleA] ?: "Maintenance"
        val levelB = context.specialization[muscleB] ?: "Maintenance"
        if (levelA == "Omit" || levelB == "Omit") return false
        return specializationTierValue(levelA) == specializationTierValue(levelB)
    }
}
