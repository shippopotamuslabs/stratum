@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.schema

import com.stratum.domain.program.SetType
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.draft.LiftSetDraft
import com.stratum.domain.program.generation.draft.ProgramDraft
import com.stratum.domain.program.generation.draft.ProgramLiftDraft
import com.stratum.domain.program.generation.draft.ProgramWorkoutDraft

fun ProgramGenerationContext.toProgramNormalizationRequest(): ProgramNormalizationRequest =
    ProgramNormalizationRequest(
        trainingAge = trainingAge,
        lastTrained = lastTrained,
        enabledLiftIds = enabledLiftIds,
        movementPatternByLiftId = movementPatternByLiftId,
    )

fun NormalizedProgram.toDraft(): ProgramDraft =
    ProgramDraft(
        name = name,
        cycleLength = cycleLength,
        restSlotIndices = restSlotIndices,
        workouts = workouts.map { workout ->
            ProgramWorkoutDraft(
                name = workout.name,
                weekday = workout.weekday,
                position = workout.position,
                workoutLifts = workout.workoutLifts.map { lift ->
                    ProgramLiftDraft(
                        liftId = lift.liftId,
                        position = lift.position,
                        workoutLiftSets = lift.workoutLiftSets.map { set ->
                            LiftSetDraft(
                                position = set.position,
                                setType = set.setType,
                                repMin = set.repMin,
                                repMax = set.repMax,
                                rirGoal = set.rirGoal,
                                isTopSet = set.isTopSet,
                            )
                        }.toMutableList(),
                    )
                }.toMutableList(),
            )
        },
    )
