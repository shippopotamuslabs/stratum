@file:Suppress("MagicNumber")

package com.stratum.domain.program.generation.prescription

import com.stratum.domain.program.generation.reference.SESSION_MUSCLE_SET_CAP
import com.stratum.domain.program.generation.split.SessionTypeId
import com.stratum.domain.program.generation.split.SlotRole

internal fun balanceDualArmSpecIsolationInSession(
    bicep: FilledSlot?,
    tricep: FilledSlot?,
    plan: SessionSlotPlan,
) {
    if (bicep == null || tricep == null) return

    val maxBicep = maxDirectArmIsolationSets(plan, "Bicep")
    val maxTricep = maxDirectArmIsolationSets(plan, "Tricep")
    if (maxBicep <= 0 && maxTricep <= 0) return

    val bicepSecondary = secondaryCreditsForMuscle(plan, "Bicep")
    val tricepSecondary = secondaryCreditsForMuscle(plan, "Tricep")
    val averageCredits =
        (
            minOf(bicep.setCount + bicepSecondary, SESSION_MUSCLE_SET_CAP.toDouble()) +
                minOf(tricep.setCount + tricepSecondary, SESSION_MUSCLE_SET_CAP.toDouble())
            ) / 2.0

    bicep.setCount = kotlin.math.round(averageCredits - bicepSecondary).toInt().coerceIn(0, maxBicep)
    tricep.setCount = kotlin.math.round(averageCredits - tricepSecondary).toInt().coerceIn(0, maxTricep)
}

internal fun balanceDualArmSpecAcrossUpperPlans(plans: List<SessionSlotPlan>) {
    val upperPlans = plans.filter { it.sessionType == SessionTypeId.Upper }
    if (upperPlans.isEmpty()) return

    repeat(20) {
        val bicepWeekly = upperPlans.sumOf { plan -> muscleCreditsInSession(plan, "Bicep") }
        val tricepWeekly = upperPlans.sumOf { plan -> muscleCreditsInSession(plan, "Tricep") }
        val delta = bicepWeekly - tricepWeekly
        if (kotlin.math.abs(delta) < 0.5) return

        val donorMuscle: String
        val recipientMuscle: String
        val donorPattern: String
        val recipientPattern: String
        if (delta > 0) {
            donorMuscle = "Bicep"
            recipientMuscle = "Tricep"
            donorPattern = "ElbowFlexion"
            recipientPattern = "ElbowExtension"
        } else {
            donorMuscle = "Tricep"
            recipientMuscle = "Bicep"
            donorPattern = "ElbowExtension"
            recipientPattern = "ElbowFlexion"
        }

        val donorSlot = upperPlans
            .flatMap { plan ->
                plan.filledSlots.filter { slot ->
                    slot.pattern == donorPattern &&
                        slot.role == SlotRole.Isolation &&
                        slot.setCount > MIN_ARM_ISO_ON_SPLIT_DAY
                }
            }
            .maxByOrNull { it.setCount }
            ?: return

        val recipientSlot = upperPlans
            .flatMap { plan ->
                plan.filledSlots.filter { slot ->
                    slot.pattern == recipientPattern && slot.role == SlotRole.Isolation
                }.mapNotNull { slot ->
                    val headroom = maxDirectArmIsolationSets(plan, recipientMuscle) - slot.setCount
                    if (headroom > 0) slot else null
                }
            }
            .firstOrNull()
            ?: return

        donorSlot.setCount -= 1
        recipientSlot.setCount += 1
    }
}

private fun maxDirectArmIsolationSets(plan: SessionSlotPlan, armMuscle: String): Int {
    val secondary = secondaryCreditsForMuscle(plan, armMuscle)
    return kotlin.math.floor(SESSION_MUSCLE_SET_CAP - secondary).toInt()
}

private const val MIN_ARM_ISO_ON_SPLIT_DAY = 3
