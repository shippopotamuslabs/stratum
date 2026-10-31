package com.stratum.core.infrastructure.healthconnect

import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.training.LoggedLiftContext
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class HealthConnectManagerTest {

    @Test
    @DisplayName("formatSessionNotes formats title, exercises, sets, reps, weight, RIR, and PRs")
    fun formatSessionNotesIncludesAllDetails() {
        val session = TrainingSession(
            id = TrainingSessionId("session-1"),
            programId = null,
            workoutId = null,
            status = TrainingSessionStatus.COMPLETED,
            kind = TrainingSessionKind.PROGRAM_WORKOUT,
            origin = TrainingSessionOrigin.LIVE,
            cycleNumberSnapshot = 1,
            weekdaySnapshot = null,
            startedAt = Instant.parse("2026-08-10T10:00:00Z"),
            endedAt = Instant.parse("2026-08-10T11:00:00Z"),
            sourceApp = null,
            workoutNameSnapshot = "Push Workout A",
        )

        val set1 = LoggedSet(
            id = LoggedSetId("set-1"),
            sessionId = session.id,
            exerciseNameRaw = "Bench Press",
            liftId = LiftId("lift-bench"),
            setIndex = 0,
            weight = Weight(205.0, Weight.WeightUnit.Lb),
            reps = 8,
            rir = 2,
            completedAt = Instant.now(),
            liftContext = com.stratum.domain.training.LoggedLiftContext("Bench Press"),
            isPersonalRecord = true,
        )

        val set2 = LoggedSet(
            id = LoggedSetId("set-2"),
            sessionId = session.id,
            exerciseNameRaw = "Bench Press",
            liftId = LiftId("lift-bench"),
            setIndex = 1,
            weight = Weight(205.0, Weight.WeightUnit.Lb),
            reps = 8,
            rir = 1,
            completedAt = Instant.now(),
            liftContext = com.stratum.domain.training.LoggedLiftContext("Bench Press"),
            isPersonalRecord = false,
        )

        val notes = HealthConnectManager.formatSessionNotes(session, listOf(set1, set2))

        assertTrue(notes.contains("Workout: Push Workout A"))
        assertTrue(notes.contains("Bench Press:"))
        assertTrue(notes.contains("Set 1: 205.0 lb × 8 reps @ 2 RIR (PR)"))
        assertTrue(notes.contains("Set 2: 205.0 lb × 8 reps @ 1 RIR"))
    }

    @Test
    @DisplayName("formatSessionNotes still carries lift names when segments omit them")
    fun formatSessionNotesKeepsUnmappedLiftNames() {
        val session = TrainingSession(
            id = TrainingSessionId("session-2"),
            programId = null,
            workoutId = null,
            status = TrainingSessionStatus.COMPLETED,
            kind = TrainingSessionKind.PROGRAM_WORKOUT,
            origin = TrainingSessionOrigin.LIVE,
            cycleNumberSnapshot = 1,
            weekdaySnapshot = null,
            startedAt = Instant.parse("2026-08-10T10:00:00Z"),
            endedAt = Instant.parse("2026-08-10T11:00:00Z"),
            sourceApp = null,
            workoutNameSnapshot = "Accessories",
        )
        val set = LoggedSet(
            id = LoggedSetId("set-face"),
            sessionId = session.id,
            exerciseNameRaw = "Cable Face Pull",
            liftId = LiftId("lift-face"),
            setIndex = 0,
            weight = Weight(40.0, Weight.WeightUnit.Lb),
            reps = 15,
            rir = 3,
            completedAt = Instant.now(),
            liftContext = LoggedLiftContext("Cable Face Pull"),
            isPersonalRecord = false,
        )

        val notes = HealthConnectManager.formatSessionNotes(session, listOf(set))

        assertTrue(notes.contains("Cable Face Pull:"))
        assertTrue(notes.contains("15 reps @ 3 RIR"))
    }
}
