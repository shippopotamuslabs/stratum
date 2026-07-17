package com.stratum.domain.training

import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("TrainingSession.liveWorkoutRouteId")
class TrainingSessionLiveRouteIdTest {

    @Test
    fun `program workout uses template workout id`() {
        val session =
            sample(
                id = "ts-1",
                workoutId = WorkoutId("w-push"),
                kind = TrainingSessionKind.PROGRAM_WORKOUT,
            )
        assertEquals("w-push", session.liveWorkoutRouteId())
    }

    @Test
    fun `ad-hoc uses session id when workout id is null`() {
        val session =
            sample(
                id = "ts-adhoc",
                workoutId = null,
                kind = TrainingSessionKind.AD_HOC,
            )
        assertEquals("ts-adhoc", session.liveWorkoutRouteId())
    }

    private fun sample(
        id: String,
        workoutId: WorkoutId?,
        kind: TrainingSessionKind,
    ): TrainingSession =
        TrainingSession(
            id = TrainingSessionId(id),
            programId = ProgramId("p-1"),
            workoutId = workoutId,
            status = TrainingSessionStatus.IN_PROGRESS,
            kind = kind,
            origin = TrainingSessionOrigin.LIVE,
            cycleNumberSnapshot = 1,
            weekdaySnapshot = null,
            startedAt = Instant.parse("2026-07-05T10:00:00Z"),
            endedAt = null,
            sourceApp = null,
        )
}
