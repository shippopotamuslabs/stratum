package com.stratum.core.inference

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import java.time.Instant
import java.util.UUID

/**
 * Parses the `generateProgram` Callable response (the server's normalized `Program` document) into
 * the domain hierarchy, assigning UUID value-class ids client-side (D1). The server returns
 * structure only — no ids — so every id is minted here.
 *
 * Defensive parsing: malformed rows are dropped (`mapIndexedNotNull`), an unrecognized `weekday` or
 * `setType` maps to `null`/`Standard`, and a response with no workouts/members is rejected. The
 * `progressionScheme` is taken from the document if present (the server assigns it), defaulting to
 * `Linear` when absent.
 */
internal object ProgramResponseParser {
    fun parse(data: Any?): Program {
        val root = data as? Map<*, *> ?: error("Malformed program response: expected object")
        val programMap = root["program"] as? Map<*, *>
            ?: error("Malformed program response: missing program")
        val programId = ProgramId(UUID.randomUUID().toString())
        val workouts = (programMap["workouts"] as? List<*>).orEmpty()
            .mapIndexedNotNull { index, w -> parseWorkout(w, programId, index) }
        if (workouts.isEmpty()) error("Malformed program response: no workouts")
        val cycleLength = (programMap["cycleLength"] as? Number)?.toInt() ?: workouts.size
        return Program(
            id = programId,
            name = programMap["name"] as? String ?: "Program",
            createdAt = Instant.now(),
            cycleLength = cycleLength,
            workouts = workouts,
        )
    }

    private fun parseWorkout(raw: Any?, programId: ProgramId, fallbackIndex: Int): Workout? {
        val map = raw as? Map<*, *> ?: return null
        val workoutId = WorkoutId(UUID.randomUUID().toString())
        val lifts = (map["workoutLifts"] as? List<*>).orEmpty()
            .mapIndexedNotNull { index, l -> parseLift(l, workoutId, index) }
        if (lifts.isEmpty()) return null
        return Workout(
            id = workoutId,
            programId = programId,
            name = map["name"] as? String ?: "Workout",
            weekday = (map["weekday"] as? String)?.let { runCatching { Weekday.valueOf(it) }.getOrNull() },
            position = (map["position"] as? Number)?.toInt() ?: fallbackIndex,
            workoutLifts = lifts,
        )
    }

    private fun parseLift(raw: Any?, workoutId: WorkoutId, fallbackIndex: Int): WorkoutLift? {
        val map = raw as? Map<*, *> ?: return null
        val liftIdRaw = map["liftId"] as? String
        if (liftIdRaw.isNullOrBlank()) return null
        val liftLiftId = WorkoutLiftId(UUID.randomUUID().toString())
        val sets = (map["workoutLiftSets"] as? List<*>).orEmpty()
            .mapIndexedNotNull { index, s -> parseSet(s, liftLiftId, index) }
        if (sets.isEmpty()) return null
        return WorkoutLift(
            id = liftLiftId,
            workoutId = workoutId,
            liftId = LiftId(liftIdRaw),
            progressionScheme = (map["progressionScheme"] as? String)
                ?.let { runCatching { ProgressionScheme.valueOf(it) }.getOrNull() }
                ?: ProgressionScheme.Linear,
            position = (map["position"] as? Number)?.toInt() ?: fallbackIndex,
            sets = sets,
        )
    }

    private fun parseSet(raw: Any?, workoutLiftId: WorkoutLiftId, fallbackIndex: Int): LiftSet? {
        val map = raw as? Map<*, *> ?: return null
        return LiftSet(
            id = LiftSetId(UUID.randomUUID().toString()),
            workoutLiftId = workoutLiftId,
            position = (map["position"] as? Number)?.toInt() ?: fallbackIndex,
            setType = (map["setType"] as? String)
                ?.let { runCatching { SetType.valueOf(it) }.getOrNull() }
                ?: SetType.Standard,
            repMin = (map["repMin"] as? Number)?.toInt() ?: 0,
            repMax = (map["repMax"] as? Number)?.toInt() ?: 0,
            rirGoal = (map["rirGoal"] as? Number)?.toInt() ?: 0,
            isTopSet = (map["isTopSet"] as? Boolean) ?: false,
        )
    }
}
