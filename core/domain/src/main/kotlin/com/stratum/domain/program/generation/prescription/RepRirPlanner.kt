@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.prescription

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.reference.RP_REP_RIR_BANDS
import com.stratum.domain.program.generation.reference.RpRepProfile
import com.stratum.domain.program.generation.split.WorkoutSlotTemplate

private val LOWER_FREE_WEIGHT_PATTERNS = setOf(MovementPattern.SquatPattern, MovementPattern.HipHinge)

fun classifyLiftRepProfile(
    slot: WorkoutSlotTemplate,
    lift: LiftContext,
): RpRepProfile {
    if (slot.repProfile != RpRepProfile.Isolation && slot.repProfile != RpRepProfile.UpperMachineOrPull) {
        if (
            lift.movementPattern in LOWER_FREE_WEIGHT_PATTERNS &&
            isLowerFreeWeightName(lift.name)
        ) {
            return RpRepProfile.LowerFreeWeightCompound
        }
        return slot.repProfile
    }
    if (slot.repProfile == RpRepProfile.Isolation) return RpRepProfile.Isolation
    return RpRepProfile.UpperMachineOrPull
}

private fun isLowerFreeWeightName(name: String): Boolean {
    val n = name.lowercase()
    if (Regex("""\b(machine|smith)\b""").containsMatchIn(n)) return false
    return Regex("""\b(barbell|squat|deadlift|rdl|romanian)\b""").containsMatchIn(n) ||
        Regex("""\bsquat\b""").containsMatchIn(n)
}

data class RepRirPlan(
    val repMin: Int,
    val repMax: Int,
    val topSetRir: Int,
    val backOffRir: Int,
)

fun repRirForSlot(
    slot: WorkoutSlotTemplate,
    lift: LiftContext,
): RepRirPlan {
    val profile = classifyLiftRepProfile(slot, lift)
    val band = RP_REP_RIR_BANDS.getValue(profile)
    return RepRirPlan(
        repMin = band.repMin,
        repMax = band.repMax,
        topSetRir = band.topSetRir,
        backOffRir = kotlin.math.round((band.backOffRirMin + band.backOffRirMax) / 2.0).toInt(),
    )
}
