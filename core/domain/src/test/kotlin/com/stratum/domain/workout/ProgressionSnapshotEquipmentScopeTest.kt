package com.stratum.domain.workout

import com.stratum.core.testing.importing.FakeLiftTrainingInsightsRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeProgramChangeEventRepository
import com.stratum.core.testing.workout.FakeProgressionHistoryRepository
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.LoggedLiftContext
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.domain.workout.progression.DoubleProgressionEngine
import com.stratum.domain.workout.progression.LinearProgressionDefaults
import com.stratum.domain.workout.progression.LinearProgressionEngine
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * STR-123: snapshot histories scope to the in-use (lift, equipment) pair so
 * recommendations and previous-sets follow the lift card's equipment.
 */
@DisplayName("Progression snapshot equipment scoping (STR-123)")
class ProgressionSnapshotEquipmentScopeTest {

    private val programId = ProgramId("p-1")
    private val workoutId = WorkoutId("w-1")
    private val workoutLiftId = WorkoutLiftId("wl-1")
    private val liftId = LiftId("L-1")
    private val barbell = EquipmentId("barbell")
    private val ssb = EquipmentId("ssb")

    private fun lb(amount: Double) = Weight(amount, Weight.WeightUnit.Lb)

    private fun singleSlotLift(): WorkoutLift =
        WorkoutLift(
            id = workoutLiftId,
            workoutId = workoutId,
            liftId = liftId,
            progressionScheme = ProgressionScheme.Double,
            position = 0,
            sets =
                listOf(
                    LiftSet(
                        id = LiftSetId("ls-0"),
                        workoutLiftId = workoutLiftId,
                        position = 0,
                        setType = SetType.Standard,
                        repMin = 5,
                        repMax = 8,
                        rirGoal = 2,
                        isTopSet = true,
                    ),
                ),
        )

    private fun session(id: String, startedAt: String) = TrainingSession(
        id = TrainingSessionId(id),
        programId = programId,
        workoutId = workoutId,
        status = TrainingSessionStatus.COMPLETED,
        kind = TrainingSessionKind.PROGRAM_WORKOUT,
        origin = TrainingSessionOrigin.IMPORT,
        cycleNumberSnapshot = 1,
        weekdaySnapshot = Weekday.Monday,
        startedAt = Instant.parse(startedAt),
        endedAt = Instant.parse(startedAt).plusSeconds(3600),
        sourceApp = null,
    )

    private fun logged(
        sessionId: TrainingSessionId,
        weight: Double,
        reps: Int,
        rir: Int?,
        completedAt: Instant,
        equipmentId: EquipmentId?,
    ) = LoggedSet(
        id = LoggedSetId("set-${sessionId.value}-${equipmentId?.value}"),
        sessionId = sessionId,
        exerciseNameRaw = "Squat",
        liftId = liftId,
        equipmentId = equipmentId,
        setIndex = 0,
        weight = Weight(weight, Weight.WeightUnit.Lb),
        reps = reps,
        rir = rir,
        completedAt = completedAt,
        liftContext = LoggedLiftContext(
            liftName = "Squat",
            movementPatternsBitmask = 1,
            primaryMusclesBitmask = 1,
            secondaryMusclesBitmask = 0,
            fatigueProfile = FatigueProfile.Low,
            muscleLengthBias = MuscleLengthBias.MidRange,
        ),
    )

    private fun useCase(history: FakeProgressionHistoryRepository) =
        GenerateProgressionSnapshotUseCase(
            progressionHistoryPort = history,
            doubleProgressionEngine = DoubleProgressionEngine(),
            linearProgressionEngine = LinearProgressionEngine(LinearProgressionDefaults.config),
            programChangeEventRepository = FakeProgramChangeEventRepository(),
            liftTrainingInsightsRepository = FakeLiftTrainingInsightsRepository(),
            programRepository = FakeProgramRepository(),
        )

    private fun seedSlot(
        history: FakeProgressionHistoryRepository,
        entry: SlotHistoryEntry,
    ) {
        history.seed(
            SlotHistoryRequest(programId, workoutId, workoutLiftId, liftId, 0),
            entry,
        )
    }

    private fun mixedHistory(): FakeProgressionHistoryRepository {
        val history = FakeProgressionHistoryRepository()
        val bbSession = session("s-bb", "2026-07-01T10:00:00Z")
        val ssbSession = session("s-ssb", "2026-06-01T10:00:00Z")
        seedSlot(
            history,
            SlotHistoryEntry(
                bbSession,
                logged(bbSession.id, 200.0, 5, 2, Instant.parse("2026-07-01T10:30:00Z"), barbell),
            ),
        )
        seedSlot(
            history,
            SlotHistoryEntry(
                ssbSession,
                logged(ssbSession.id, 100.0, 5, 2, Instant.parse("2026-06-01T10:30:00Z"), ssb),
            ),
        )
        return history
    }

    private suspend fun recFor(
        history: FakeProgressionHistoryRepository,
        equipmentId: EquipmentId?,
        loadType: LoadType = LoadType.Weighted,
        scoped: Boolean = true,
    ): SlotRecommendation {
        val request =
            SnapshotRequest(
                programId = programId,
                workoutId = workoutId,
                lifts = listOf(singleSlotLift()),
                loadTypesById = mapOf(liftId to loadType),
                equipmentIdByWorkoutLiftId =
                    if (scoped) mapOf(workoutLiftId to equipmentId) else emptyMap(),
            )
        return useCase(history)(request).getOrThrow()
            .bySlot.getValue(SetSlotId.Template(LiftSetId("ls-0")))
    }

    @Test
    @DisplayName("scoped pool drives previous and recommendations per equipment")
    fun scopedPoolDrivesPreviousAndRecs() = runTest {
        val history = mixedHistory()

        val barbellRec = recFor(history, barbell)
        assertEquals(200.0, barbellRec.previousWeight!!.amount, 0.01)
        assertEquals(200.0, barbellRec.recommendedWeight.amount, 0.01)

        val ssbRec = recFor(history, ssb)
        assertEquals(100.0, ssbRec.previousWeight!!.amount, 0.01)
        assertEquals(100.0, ssbRec.recommendedWeight.amount, 0.01)
    }

    @Test
    @DisplayName("cold pair (no history) takes the cold-start path")
    fun coldPairStartsCold() = runTest {
        val history = mixedHistory()
        val rec = recFor(history, EquipmentId("cables"))
        assertNull(rec.previousWeight)
        assertEquals(0.0, rec.recommendedWeight.amount, 0.01)
    }

    @Test
    @DisplayName("missing equipment key leaves history unfiltered")
    fun missingKeyUnfiltered() = runTest {
        val history = mixedHistory()
        val rec = recFor(history, equipmentId = null, scoped = false)
        // Most-recent session wins regardless of equipment.
        assertEquals(200.0, rec.previousWeight!!.amount, 0.01)
    }

    @Test
    @DisplayName("null equipment value leaves history unfiltered")
    fun nullValueUnfiltered() = runTest {
        val history = mixedHistory()
        val rec = recFor(history, equipmentId = null, scoped = true)
        assertEquals(200.0, rec.previousWeight!!.amount, 0.01)
    }

    @Test
    @DisplayName("legacy null-equipment rows are excluded from a scoped pool")
    fun legacyNullsExcludedFromScopedPool() = runTest {
        val history = FakeProgressionHistoryRepository()
        val legacy = session("s-legacy", "2026-07-01T10:00:00Z")
        seedSlot(
            history,
            SlotHistoryEntry(
                legacy,
                logged(legacy.id, 200.0, 5, 2, Instant.parse("2026-07-01T10:30:00Z"), null),
            ),
        )
        val rec = recFor(history, barbell)
        assertNull(rec.previousWeight)
        assertEquals(0.0, rec.recommendedWeight.amount, 0.01)
    }

    @Test
    @DisplayName("bodyweight lifts are never equipment-filtered")
    fun bodyweightNeverFiltered() = runTest {
        val history = FakeProgressionHistoryRepository()
        val bw = session("s-bw", "2026-07-01T10:00:00Z")
        seedSlot(
            history,
            SlotHistoryEntry(
                bw,
                logged(bw.id, 0.0, 12, 1, Instant.parse("2026-07-01T10:30:00Z"), null),
            ),
        )
        val rec = recFor(history, barbell, loadType = LoadType.Bodyweight)
        assertNotNull(rec.previousReps)
        assertEquals(12, rec.previousReps)
    }
}
