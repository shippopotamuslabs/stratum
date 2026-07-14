package com.stratum.domain.program.generation.naming

import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.draft.ProgramDraft

private fun specializedMuscles(context: ProgramGenerationContext): List<String> =
    context.specialization
        .filter { (_, level) -> level == SpecializationLevel.Specialize }
        .keys
        .map { it.name }
        .toList()

fun buildProgramNamingRequest(
    program: ProgramDraft,
    context: ProgramGenerationContext,
): ProgramNamingRequest {
    val cycleLength = program.cycleLength ?: program.workouts.size
    return ProgramNamingRequest(
        namingPayload =
            ProgramNamingPayload(
                promptVersion = PROGRAM_NAMING_PROMPT_VERSION,
                intent =
                    ProgramNamingIntentPayload(
                        trainingAge = context.trainingAge,
                        sessionLengthMinutes = context.sessionLengthMinutes,
                        trainingDaysPerWeek = context.trainingDaysPerWeek,
                        cycleLength = cycleLength,
                        specialization = context.specialization
                            .mapKeys { it.key.name }
                            .mapValues { it.value.name },
                        specializedMuscles = specializedMuscles(context),
                    ),
                program =
                    ProgramNamingProgramPayload(
                        workouts =
                            program.workouts.mapIndexed { workoutPosition, workout ->
                                ProgramNamingWorkoutPayload(
                                    position = workout.position ?: workoutPosition,
                                    weekday = workout.weekday,
                                    workoutLifts =
                                        workout.workoutLifts.orEmpty().map { lift ->
                                            val liftId = lift.liftId.orEmpty()
                                            val meta = context.liftsById[liftId]
                                            ProgramNamingLiftPayload(
                                                name = meta?.name ?: liftId,
                                                movementPattern = meta?.movementPattern?.name ?: "Unknown",
                                                primaryMuscles = meta?.primaryMuscles.orEmpty().map { it.name },
                                                setCount = lift.workoutLiftSets.orEmpty().size,
                                            )
                                        },
                                )
                            },
                    ),
            ),
    )
}

fun applyProgramNames(
    program: ProgramDraft,
    result: ProgramNamingResult,
): ProgramDraft {
    val programName = result.programName?.takeIf { it.isNotBlank() }
    if (programName == null && result.workoutNames.isEmpty()) return program
    val namesByPosition = result.workoutNamesByPosition
    val workouts =
        program.workouts.mapIndexed { index, workout ->
            val position = workout.position ?: index
            val newName = namesByPosition[position]
            if (newName.isNullOrBlank()) workout else workout.copy(name = newName)
        }
    return program.copy(
        name = programName ?: program.name,
        workouts = workouts,
    )
}
