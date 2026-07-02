@file:Suppress("MagicNumber")

package com.stratum.domain.solver

import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.generation.context.PRIMARY_SET_CREDIT
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.draft.ProgramWorkoutDraft
import com.stratum.domain.program.generation.prescription.FilledSlot
import com.stratum.domain.program.generation.prescription.SessionSlotPlan
import com.stratum.domain.program.generation.prescription.dominantStressClass
import com.stratum.domain.program.generation.prescription.recoveryStressClassForContributor
import com.stratum.domain.program.generation.reference.LiftStressClass
import com.stratum.domain.program.generation.reference.MuscleSessionStress
import com.stratum.domain.program.generation.reference.calendarDayIndexGap
import com.stratum.domain.program.generation.reference.computePatternSecondaryCredits
import com.stratum.domain.program.generation.reference.muscleRecoveryGapViolated
import com.stratum.domain.program.generation.validation.computeWorkoutMuscleSessionStress

internal fun recoveryGapViolationsFromPlans(
    plans: List<SessionSlotPlan>,
    weekdayIndexByPosition: List<Int>,
    context: ProgramGenerationContext,
    muscleFilter: (String) -> Boolean = { true },
    calendarGapFilter: Int? = null,
): List<String> {
    val ordered = plans.sortedBy { plan -> weekdayIndexByPosition[plan.position] }
    val stressByPlan = ordered.associateWith { plan -> planMuscleSessionStress(plan, context) }
    val issues = mutableListOf<String>()
    for (index in 1 until ordered.size) {
        issues += recoveryGapIssuesBetween(
            earlierPlan = ordered[index - 1],
            laterPlan = ordered[index],
            earlierDayIndex = weekdayIndexByPosition[ordered[index - 1].position],
            laterDayIndex = weekdayIndexByPosition[ordered[index].position],
            earlierStress = stressByPlan.getValue(ordered[index - 1]),
            laterStress = stressByPlan.getValue(ordered[index]),
            muscleFilter = muscleFilter,
            calendarGapFilter = calendarGapFilter,
        )
    }
    if (ordered.size >= 2) {
        val firstDayIndex = weekdayIndexByPosition[ordered.first().position]
        val lastDayIndex = weekdayIndexByPosition[ordered.last().position]
        if (firstDayIndex == Weekday.Monday.ordinal && lastDayIndex == Weekday.Sunday.ordinal) {
            issues += recoveryGapIssuesBetween(
                earlierPlan = ordered.last(),
                laterPlan = ordered.first(),
                earlierDayIndex = lastDayIndex,
                laterDayIndex = firstDayIndex,
                earlierStress = stressByPlan.getValue(ordered.last()),
                laterStress = stressByPlan.getValue(ordered.first()),
                muscleFilter = muscleFilter,
                calendarGapFilter = calendarGapFilter,
            )
        }
    }
    return issues
}

internal fun recoveryGapViolationsFromDraftWorkouts(
    workouts: List<ProgramWorkoutDraft>,
    context: ProgramGenerationContext,
    muscleFilter: (String) -> Boolean = { true },
    calendarGapFilter: Int? = null,
): List<String> {
    val ordered =
        workouts.sortedBy { workout ->
            workout.weekday?.let { day -> Weekday.entries.indexOfFirst { it.name == day } } ?: Int.MAX_VALUE
        }
    val stressByWorkout = ordered.associate { workout -> workout to computeWorkoutMuscleSessionStress(workout, context) }
    val issues = mutableListOf<String>()
    for (index in 1 until ordered.size) {
        val earlierDay = ordered[index - 1].weekday ?: continue
        val laterDay = ordered[index].weekday ?: continue
        val earlierDayIndex = Weekday.entries.indexOfFirst { it.name == earlierDay }
        val laterDayIndex = Weekday.entries.indexOfFirst { it.name == laterDay }
        issues += recoveryGapIssuesBetween(
            earlierLabel = ordered[index - 1].name.orEmpty(),
            laterLabel = ordered[index].name.orEmpty(),
            earlierDayIndex = earlierDayIndex,
            laterDayIndex = laterDayIndex,
            earlierStress = stressByWorkout.getValue(ordered[index - 1]),
            laterStress = stressByWorkout.getValue(ordered[index]),
            muscleFilter = muscleFilter,
            calendarGapFilter = calendarGapFilter,
        )
    }
    return issues
}

private fun recoveryGapIssuesBetween(
    earlierPlan: SessionSlotPlan,
    laterPlan: SessionSlotPlan,
    earlierDayIndex: Int,
    laterDayIndex: Int,
    earlierStress: Map<String, MuscleSessionStress>,
    laterStress: Map<String, MuscleSessionStress>,
    muscleFilter: (String) -> Boolean,
    calendarGapFilter: Int? = null,
): List<String> =
    recoveryGapIssuesBetween(
        earlierLabel = "Session ${earlierPlan.position}",
        laterLabel = "Session ${laterPlan.position}",
        earlierDayIndex = earlierDayIndex,
        laterDayIndex = laterDayIndex,
        earlierStress = earlierStress,
        laterStress = laterStress,
        muscleFilter = muscleFilter,
        calendarGapFilter = calendarGapFilter,
    )

private fun recoveryGapIssuesBetween(
    earlierLabel: String,
    laterLabel: String,
    earlierDayIndex: Int,
    laterDayIndex: Int,
    earlierStress: Map<String, MuscleSessionStress>,
    laterStress: Map<String, MuscleSessionStress>,
    muscleFilter: (String) -> Boolean,
    calendarGapFilter: Int? = null,
): List<String> {
    val calendarGap = calendarDayIndexGap(earlierDayIndex, laterDayIndex)
    if (calendarGap <= 0) return emptyList()
    if (calendarGapFilter != null && calendarGap != calendarGapFilter) return emptyList()
    val issues = mutableListOf<String>()
    for (muscle in earlierStress.keys + laterStress.keys) {
        if (!muscleFilter(muscle)) continue
        val earlier = earlierStress[muscle] ?: continue
        val laterDose = laterStress[muscle]?.fractionalSets ?: 0.0
        if (muscleRecoveryGapViolated(earlier, laterDose, calendarGap)) {
            issues += "$earlierLabel→$laterLabel: $muscle (${earlier.fractionalSets}, $laterDose)"
        }
    }
    return issues
}

internal fun planMuscleSessionStress(
    plan: SessionSlotPlan,
    context: ProgramGenerationContext,
): Map<String, MuscleSessionStress> {
    val coefficients = VolumeAllocationCoefficients(computePatternSecondaryCredits(context))
    val doses = planMuscleCredits(plan, coefficients)
    val classContributors = mutableMapOf<String, MutableList<Pair<LiftStressClass, Double>>>()
    for (slot in plan.filledSlots) {
        if (slot.setCount <= 0) continue
        accumulateSlotStressContributors(slot, coefficients, classContributors)
    }
    return doses.mapValues { (muscle, dose) ->
        MuscleSessionStress(dose, dominantStressClass(classContributors[muscle].orEmpty()))
    }
}

private fun accumulateSlotStressContributors(
    slot: FilledSlot,
    coefficients: VolumeAllocationCoefficients,
    classContributors: MutableMap<String, MutableList<Pair<LiftStressClass, Double>>>,
) {
    val stressClass = coefficients.slotLiftStressClass(slot)
    classContributors.getOrPut(slot.primaryMuscle) { mutableListOf() }
        .add(
            recoveryStressClassForContributor(stressClass, isPrimaryContributor = true) to
                slot.setCount * PRIMARY_SET_CREDIT,
        )
    for (secondary in coefficients.musclesForPattern(slot.pattern)) {
        if (secondary.muscle == slot.primaryMuscle) continue
        classContributors.getOrPut(secondary.muscle) { mutableListOf() }
            .add(
                recoveryStressClassForContributor(stressClass, isPrimaryContributor = false) to
                    slot.setCount * secondary.creditPerSet,
            )
    }
}

private fun planMuscleCredits(
    plan: SessionSlotPlan,
    coefficients: VolumeAllocationCoefficients,
): Map<String, Double> {
    val totals = mutableMapOf<String, Double>()
    for (slot in plan.filledSlots) {
        if (slot.setCount <= 0) continue
        val muscles = linkedSetOf(slot.primaryMuscle)
        coefficients.musclesForPattern(slot.pattern).forEach { muscles += it.muscle }
        for (muscle in muscles) {
            val coeff = coefficients.coeff(slot, muscle)
            if (coeff <= 0.0) continue
            totals[muscle] = (totals[muscle] ?: 0.0) + slot.setCount * coeff
        }
    }
    return totals
}
