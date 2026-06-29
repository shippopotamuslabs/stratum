@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.build

import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.draft.ProgramWorkoutDraft
import com.stratum.domain.program.generation.prescription.allocateSlotSetCounts
import com.stratum.domain.program.generation.selection.SelectedSlotLift
import com.stratum.domain.program.generation.selection.selectLiftForSlot
import com.stratum.domain.program.generation.split.resolveSplit
import com.stratum.domain.program.generation.volume.WeeklyVolumeTargets
import com.stratum.domain.program.generation.volume.planWeeklyVolume

data class BuiltProgram(
    val name: String,
    val cycleLength: Int,
    val workouts: List<ProgramWorkoutDraft>,
)

data class BuildProgramResult(
    val program: BuiltProgram,
    val selectionsBySession: List<List<SelectedSlotLift>>,
)

fun buildProgramWithSelections(
    context: ProgramGenerationContext,
    weeklyTargets: WeeklyVolumeTargets? = null,
): BuildProgramResult {
    val targets = weeklyTargets ?: planWeeklyVolume(context)
    val resolved = resolveSplit(context, targets)
    val sessionPlans = allocateSlotSetCounts(resolved.sessions, targets, context)

    val usedLiftIds = mutableSetOf<String>()
    val lastUsedSession = mutableMapOf<String, Int>()
    val minCycleGap = 2

    val selectionsBySession = sessionPlans.mapIndexed { sessionIndex, plan ->
        val selections = mutableListOf<SelectedSlotLift>()
        for (slot in plan.filledSlots) {
            val sel = selectLiftForSlot(
                slot = slot,
                context = context,
                siblingIndex = plan.siblingIndex,
                usedLiftIds = usedLiftIds,
                minCycleGap = minCycleGap,
                sessionIndex = sessionIndex,
                lastUsedSession = lastUsedSession,
            )
            if (sel != null) selections.add(sel)
        }
        selections
    }

    val workouts = assembleWorkouts(sessionPlans, selectionsBySession, context)
    val cycleLength = resolved.cycleLength

    check(workouts.size == cycleLength) {
        "cycleLength $cycleLength != workouts ${workouts.size}"
    }

    return BuildProgramResult(
        program = BuiltProgram(
            name = "Hypertrophy — $cycleLength-session cycle",
            cycleLength = cycleLength,
            workouts = workouts,
        ),
        selectionsBySession = selectionsBySession,
    )
}

fun buildProgramFromSlots(
    context: ProgramGenerationContext,
    weeklyTargets: WeeklyVolumeTargets? = null,
): BuiltProgram = buildProgramWithSelections(context, weeklyTargets).program
