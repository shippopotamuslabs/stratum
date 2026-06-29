@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.schema

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.Program
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import java.time.Instant
import java.util.UUID

/** Maps a normalized generation output to a domain [Program], assigning UUID ids client-side (D1). */
fun NormalizedProgram.toDomainProgram(createdAt: Instant = Instant.now()): Program {
    val programId = ProgramId(UUID.randomUUID().toString())
    val workouts = this.workouts.mapIndexed { index, workout ->
        val workoutId = WorkoutId(UUID.randomUUID().toString())
        Workout(
            id = workoutId,
            programId = programId,
            name = workout.name,
            weekday = workout.weekday?.let { runCatching { Weekday.valueOf(it) }.getOrNull() },
            position = workout.position,
            workoutLifts = workout.workoutLifts.mapIndexed { liftIndex, lift ->
                val workoutLiftId = WorkoutLiftId(UUID.randomUUID().toString())
                WorkoutLift(
                    id = workoutLiftId,
                    workoutId = workoutId,
                    liftId = LiftId(lift.liftId),
                    progressionScheme = lift.progressionScheme,
                    position = lift.position,
                    sets = lift.workoutLiftSets.map { set ->
                        LiftSet(
                            id = LiftSetId(UUID.randomUUID().toString()),
                            workoutLiftId = workoutLiftId,
                            position = set.position,
                            setType = set.setType,
                            repMin = set.repMin,
                            repMax = set.repMax,
                            rirGoal = set.rirGoal,
                            isTopSet = set.isTopSet,
                        )
                    },
                )
            },
        )
    }
    return Program(
        id = programId,
        name = name,
        createdAt = createdAt,
        cycleLength = cycleLength,
        trainingWeekdays = emptySet(),
        workouts = workouts,
    )
}
