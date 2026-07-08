@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.build

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.KinematicProfile
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.LastTrainedBucket
import com.stratum.domain.onboarding.TrainingAge
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.program.ProgressionScheme as DomainProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.draft.LiftSetDraft
import com.stratum.domain.program.generation.draft.ProgramLiftDraft
import com.stratum.domain.program.generation.draft.ProgramWorkoutDraft
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.generation.prescription.SessionSlotPlan
import com.stratum.domain.program.generation.prescription.repRirForSlot
import com.stratum.domain.program.generation.reference.schemeForTrainingAge
import com.stratum.domain.program.generation.selection.SelectedSlotLift
import com.stratum.domain.program.generation.synthesis.relabelPlaceholderSessionNamesByWeekday

private val ANTAGONIST_PAIRS: List<Pair<MuscleGroup, MuscleGroup>> = listOf(
    MuscleGroup.Chest to MuscleGroup.Back,
    MuscleGroup.Bicep to MuscleGroup.Tricep,
    MuscleGroup.Quad to MuscleGroup.Hamstring,
)

private val PULL_PATTERNS: Set<MovementPattern> = setOf(
    MovementPattern.VerticalPull,
    MovementPattern.HorizontalPull,
    MovementPattern.ElbowFlexion,
    MovementPattern.KneeFlexion,
    MovementPattern.HipHinge,
)

private val PUSH_PATTERNS: Set<MovementPattern> = setOf(
    MovementPattern.HorizontalPress,
    MovementPattern.InclinePress,
    MovementPattern.VerticalPress,
    MovementPattern.ElbowExtension,
    MovementPattern.SquatPattern,
    MovementPattern.KneeExtension,
)

private enum class PushPullSide {
    Push,
    Pull,
}

private fun isAntagonist(a: MuscleGroup, b: MuscleGroup): Boolean =
    ANTAGONIST_PAIRS.any { (x, y) -> (a == x && b == y) || (a == y && b == x) }

private fun specializedMuscles(context: ProgramGenerationContext): Set<MuscleGroup> {
    val out = mutableSetOf<MuscleGroup>()
    for ((muscle, level) in context.specialization) {
        if (level == SpecializationLevel.Specialize) out.add(muscle)
    }
    return out
}

private fun pushPullSide(selection: SelectedSlotLift): PushPullSide? {
    val pattern = selection.slot.pattern
    if (pattern in PULL_PATTERNS) return PushPullSide.Pull
    if (pattern in PUSH_PATTERNS) return PushPullSide.Push
    return null
}

private fun oppositePushPullSide(side: PushPullSide): PushPullSide =
    if (side == PushPullSide.Push) PushPullSide.Pull else PushPullSide.Push

private data class InterleaveContext(
    val lastMuscle: MuscleGroup?,
    val lastSide: PushPullSide?,
)

private fun pickIndex(
    selections: List<SelectedSlotLift>,
    used: Set<Int>,
    predicate: (SelectedSlotLift, Int) -> Boolean,
): Int {
    selections.forEachIndexed { index, selection ->
        if (index !in used && predicate(selection, index)) return index
    }
    return -1
}

/** Greedy push/pull + antagonist interleave; avoids stacking the same side when alternatives exist. */
private fun interleaveSelections(
    selections: List<SelectedSlotLift>,
    start: InterleaveContext = InterleaveContext(lastMuscle = null, lastSide = null),
): List<SelectedSlotLift> {
    val ordered = mutableListOf<SelectedSlotLift>()
    val used = mutableSetOf<Int>()
    var lastMuscle = start.lastMuscle
    var lastSide = start.lastSide

    while (ordered.size < selections.size) {
        var picked = -1

        if (ordered.isEmpty() && lastSide != null) {
            val bridge = oppositePushPullSide(lastSide)
            picked = pickIndex(selections, used) { selection, _ -> pushPullSide(selection) == bridge }
        }

        if (picked < 0 && lastSide != null) {
            val want = oppositePushPullSide(lastSide)
            picked = pickIndex(selections, used) { selection, _ -> pushPullSide(selection) == want }
        }

        if (picked < 0 && lastMuscle != null) {
            picked = pickIndex(selections, used) { selection, _ ->
                isAntagonist(lastMuscle!!, selection.slot.primaryMuscle)
            }
        }

        if (picked < 0 && lastMuscle != null) {
            picked = pickIndex(selections, used) { selection, _ ->
                selection.slot.primaryMuscle != lastMuscle
            }
        }

        if (picked < 0) {
            picked = pickIndex(selections, used) { _, _ -> true }
        }

        if (picked < 0) break

        used.add(picked)
        val selection = selections[picked]
        ordered.add(selection)
        lastMuscle = selection.slot.primaryMuscle
        val side = pushPullSide(selection)
        if (side != null) lastSide = side
    }

    return ordered
}

private fun orderSelections(
    selections: List<SelectedSlotLift>,
    context: ProgramGenerationContext,
): List<SelectedSlotLift> {
    val specialized = specializedMuscles(context)
    val spec = selections.filter { specialized.contains(it.slot.primaryMuscle) }
    val rest = selections.filter { !specialized.contains(it.slot.primaryMuscle) }

    val orderedSpec = interleaveSelections(spec)
    val lastSpec = orderedSpec.lastOrNull()
    val compoundStart = InterleaveContext(
        lastMuscle = lastSpec?.slot?.primaryMuscle,
        lastSide = lastSpec?.let { pushPullSide(it) },
    )
    return orderedSpec + interleaveSelections(rest, compoundStart)
}

private fun buildLiftDrafts(
    selections: List<SelectedSlotLift>,
    context: ProgramGenerationContext,
): List<ProgramLiftDraft> {
    val trainingAge = runCatching { TrainingAge.valueOf(context.trainingAge) }.getOrNull()
    val lastTrained = context.lastTrained?.let { runCatching { LastTrainedBucket.valueOf(it) }.getOrNull() }
    return selections.mapIndexed { liftIndex, sel ->
        val isCompound =
            sel.lift.kinematicProfile == KinematicProfile.AxialCompound ||
                sel.lift.kinematicProfile == KinematicProfile.NonAxialCompound
        val scheme = schemeForTrainingAge(trainingAge, lastTrained, isCompound)
        val repRir = repRirForSlot(
            slot = sel.slot.toWorkoutSlotTemplate(),
            lift = sel.lift,
            setCount = sel.slot.setCount,
        )
        val setCount = sel.slot.setCount
        val sets = (0 until setCount).map { i ->
            val isTopSet = scheme == DomainProgressionScheme.Double && i == 0
            LiftSetDraft(
                position = i,
                setType = SetType.Standard,
                repMin = repRir.repMin,
                repMax = repRir.repMax,
                rirGoal = if (isTopSet) repRir.topSetRir else repRir.backOffRir,
                isTopSet = isTopSet,
            )
        }
        ProgramLiftDraft(
            liftId = sel.lift.id,
            position = liftIndex,
            workoutLiftSets = sets.toMutableList(),
        )
    }
}

private val WEEKDAY_BY_INDEX = Weekday.entries

fun assembleWorkouts(
    plans: List<SessionSlotPlan>,
    selectionsBySession: List<List<SelectedSlotLift>>,
    context: ProgramGenerationContext,
    weekdayIndexByPosition: List<Int>? = null,
): List<ProgramWorkoutDraft> {
    val workouts =
        plans.mapIndexed { idx, plan ->
            val selections = orderSelections(selectionsBySession.getOrElse(idx) { emptyList() }, context)
            ProgramWorkoutDraft(
                name = plan.name,
                weekday = weekdayIndexByPosition?.getOrNull(plan.position)?.let { index -> WEEKDAY_BY_INDEX[index].name },
                position = plan.position,
                workoutLifts = buildLiftDrafts(selections, context).toMutableList(),
            )
        }
    return if (weekdayIndexByPosition != null) {
        relabelPlaceholderSessionNamesByWeekday(workouts)
    } else {
        workouts
    }
}

/** @internal Exported for unit tests. */
internal fun orderSelectionsForTest(
    selections: List<SelectedSlotLift>,
    context: ProgramGenerationContext,
): List<SelectedSlotLift> = orderSelections(selections, context)
