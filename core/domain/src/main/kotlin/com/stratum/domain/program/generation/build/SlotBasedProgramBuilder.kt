@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.build

import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.draft.ProgramWorkoutDraft
import com.stratum.domain.program.generation.prescription.SessionSlotPlan
import com.stratum.domain.program.generation.prescription.SynthesisObjectiveBreakdown
import com.stratum.domain.program.generation.selection.LiftSelectionState
import com.stratum.domain.program.generation.selection.SelectedSlotLift
import com.stratum.domain.program.generation.selection.selectLiftForSlot
import com.stratum.domain.program.generation.synthesis.PLACEHOLDER_PROGRAM_NAME
import com.stratum.domain.program.generation.synthesis.ProgramSynthesisPort
import com.stratum.domain.program.generation.validation.validateActiveSlotSelections
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
    val weekdayIndexByPosition: List<Int>,
    val synthesisObjective: SynthesisObjectiveBreakdown,
)

suspend fun buildProgramWithSelections(
    context: ProgramGenerationContext,
    weeklyTargets: WeeklyVolumeTargets? = null,
    programSynthesisPort: ProgramSynthesisPort,
    onSynthesizingProgram: suspend () -> Unit = {},
    onSelectingExercises: suspend () -> Unit = {},
): BuildProgramResult {
    val targets = weeklyTargets ?: planWeeklyVolume(context)
    onSynthesizingProgram()
    val synthesis = programSynthesisPort.synthesize(targets, context)
    onSelectingExercises()

    val sessionPlans = synthesis.plans
    val lastUsedSession = mutableMapOf<String, Int>()
    val minCycleGap = 2
    val selectionState = LiftSelectionState()
    val runningWeeklyVolume = mutableMapOf<MuscleGroup, Double>()

    val selectionsBySession =
        sessionPlans.mapIndexed { sessionIndex, plan ->
            val sessionUsedLiftIds = mutableSetOf<String>()
            selectionState.sessionPrimaryLiftByMusclePattern.clear()
            val selections = mutableListOf<SelectedSlotLift>()
            for (slot in plan.filledSlots) {
                val sel =
                    selectLiftForSlot(
                        slot = slot,
                        context = context,
                        siblingIndex = plan.siblingIndex,
                        usedLiftIds = sessionUsedLiftIds,
                        minCycleGap = minCycleGap,
                        sessionIndex = sessionIndex,
                        lastUsedSession = lastUsedSession,
                        selectionState = selectionState,
                        runningWeeklyVolume = runningWeeklyVolume,
                    )
                if (sel != null) selections.add(sel)
            }
            selections
        }

    val selectionIssues = validateActiveSlotSelections(sessionPlans, selectionsBySession, context)
    check(selectionIssues.isEmpty()) {
        selectionIssues.joinToString("\n") { issue -> issue.message }
    }

    val workouts =
        assembleWorkouts(
            sessionPlans,
            selectionsBySession,
            context,
            synthesis.weekdayIndexByPosition,
        )
    val cycleLength = synthesis.sessions.size

    check(workouts.size == cycleLength) {
        "cycleLength $cycleLength != workouts ${workouts.size}"
    }

    return BuildProgramResult(
        program =
            BuiltProgram(
                name = PLACEHOLDER_PROGRAM_NAME,
                cycleLength = cycleLength,
                workouts = workouts,
            ),
        selectionsBySession = selectionsBySession,
        weekdayIndexByPosition = synthesis.weekdayIndexByPosition,
        synthesisObjective = synthesis.objective,
    )
}
