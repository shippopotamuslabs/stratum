package com.stratum.domain.program.generation.selection

import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.PRIMARY_SET_CREDIT
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.context.SECONDARY_SET_CREDIT

/**
 * Maintenance antagonist pairs balanced on lift-metadata weekly volume (what the Programs UI shows),
 * not solver pattern credits. Joint-safer direction: back ≥ chest, ham ≥ quad.
 */
object AntagonistMetadataBalance {
    private data class Pair(
        val weaker: String,
        val stronger: String,
    )

    private val maintenancePairs =
        listOf(
            Pair("Chest", "Back"),
            Pair("Quad", "Hamstring"),
        )

    /** Penalty scale when a candidate lift widens the wrong-way gap (quad > ham, chest > back). */
    private const val WRONG_WAY_METADATA_PENALTY_PER_SET = 0.18

    /** Small reward when a candidate closes the wrong-way gap without overshooting. */
    private const val GAP_CLOSE_METADATA_BONUS_PER_SET = 0.06

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
     * Negative when the lift would push quad above ham (or chest above back).
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
}
