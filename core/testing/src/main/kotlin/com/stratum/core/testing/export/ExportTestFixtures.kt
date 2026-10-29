@file:Suppress("MagicNumber")

package com.stratum.core.testing.export

import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.Weight
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
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.training.LoggedLiftContext
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import java.time.ZoneId

object ExportTestFixtures {
    val ZONE_NY: ZoneId = ZoneId.of("America/New_York")
    val CLOCK_INSTANT: Instant = Instant.parse("2026-07-16T15:00:00Z")

    fun session(
        id: String,
        startedAt: Instant,
        origin: TrainingSessionOrigin = TrainingSessionOrigin.IMPORT,
        sourceApp: String? = "hevy",
    ): TrainingSession =
        TrainingSession(
            id = TrainingSessionId(id),
            programId = null,
            workoutId = null,
            status = TrainingSessionStatus.COMPLETED,
            kind = TrainingSessionKind.AD_HOC,
            origin = origin,
            cycleNumberSnapshot = 1,
            weekdaySnapshot = null,
            startedAt = startedAt,
            endedAt = startedAt.plusSeconds(3600),
            sourceApp = sourceApp,
        )

    fun set(
        id: String,
        sessionId: String,
        exercise: String = "Squat",
        weightAmount: Double = 100.0,
        weightUnit: Weight.WeightUnit = Weight.WeightUnit.Lb,
        reps: Int = 5,
        rir: Int? = 2,
        completedAt: Instant = Instant.parse("2026-07-01T14:05:00Z"),
    ): LoggedSet =
        LoggedSet(
            id = LoggedSetId(id),
            sessionId = TrainingSessionId(sessionId),
            exerciseNameRaw = exercise,
            liftId = null,
            setIndex = 0,
            weight = Weight(weightAmount, weightUnit),
            reps = reps,
            rir = rir,
            completedAt = completedAt,
            liftContext = LoggedLiftContext(liftName = exercise),
        )

    val sessionA = session("sess-a", Instant.parse("2026-07-01T14:00:00Z"))
    val sessionB = session("sess-b", Instant.parse("2026-06-01T14:00:00Z"))
    val sessionLive =
        session(
            "sess-live",
            Instant.parse("2026-07-10T18:00:00Z"),
            origin = TrainingSessionOrigin.LIVE,
            sourceApp = null,
        )
    val sessionOld = session("sess-old", Instant.parse("2025-01-15T14:00:00Z"))

    val setA1 =
        set(
            "set-a1",
            "sess-a",
            exercise = "Squat",
            weightAmount = 100.0,
            weightUnit = Weight.WeightUnit.Lb,
            reps = 5,
            rir = 2,
            completedAt = Instant.parse("2026-07-01T14:05:00Z"),
        )
    val setB1 =
        set(
            "set-b1",
            "sess-b",
            exercise = "Bench",
            weightAmount = 60.0,
            weightUnit = Weight.WeightUnit.Kg,
            reps = 8,
            rir = null,
            completedAt = Instant.parse("2026-06-01T14:05:00Z"),
        )
    val setLive1 =
        set(
            "set-live1",
            "sess-live",
            exercise = "Deadlift",
            weightAmount = 140.0,
            weightUnit = Weight.WeightUnit.Kg,
            reps = 3,
            rir = null,
            completedAt = Instant.parse("2026-07-10T18:05:00Z"),
        )
    val setOld1 =
        set(
            "set-old1",
            "sess-old",
            exercise = "Row",
            completedAt = Instant.parse("2025-01-15T14:05:00Z"),
        )

    fun minimalProgram(
        id: String,
        name: String,
        isActive: Boolean,
        workoutName: String = "Day A",
        liftName: String = "Squat",
    ): Program {
        val programId = ProgramId(id)
        val workoutId = WorkoutId("$id-wkt")
        val workoutLiftId = WorkoutLiftId("$id-wl")
        return Program(
            id = programId,
            name = name,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            cycleLength = 4,
            workouts =
                listOf(
                    Workout(
                        id = workoutId,
                        programId = programId,
                        name = workoutName,
                        weekday = null,
                        position = 0,
                        workoutLifts =
                            listOf(
                                WorkoutLift(
                                    id = workoutLiftId,
                                    workoutId = workoutId,
                                    liftId = LiftId("lift-$liftName".lowercase().replace(' ', '-')),
                                    progressionScheme = ProgressionScheme.Double,
                                    position = 0,
                                    sets =
                                        listOf(
                                            LiftSet(
                                                id = LiftSetId("$id-set0"),
                                                workoutLiftId = workoutLiftId,
                                                position = 0,
                                                setType = SetType.Standard,
                                                repMin = 5,
                                                repMax = 8,
                                                rirGoal = 2,
                                                restSeconds = 120,
                                                isTopSet = true,
                                            ),
                                        ),
                                ),
                            ),
                    ),
                ),
            isActive = isActive,
        )
    }

    val programActive = minimalProgram("prog-active", "Push Pull", isActive = true, workoutName = "Day A", liftName = "Squat")
    val programLib = minimalProgram("prog-lib", "Upper Lower", isActive = false, workoutName = "Upper", liftName = "Bench")
    val programImportHistory =
        Program(
            id = ProgramId.ImportHistory,
            name = "Import History",
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            cycleLength = 1,
            workouts = emptyList(),
            isActive = false,
        )
}
