package com.stratum.feature.workout.notification

import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.training.ObserveCycleProgressUseCase
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class DomainActiveSessionSurfaceSyncTest {

    @Test
    @DisplayName("When domain session is present then active workout surface is published")
    fun publishesActiveWhenDomainSessionPresent() =
        runTest(UnconfinedTestDispatcher()) {
            val session = sampleSession()
            val domainActive = MutableStateFlow<TrainingSession?>(session)
            val surfaces = surfaces()

            DomainActiveSessionSurfaceSync(
                observeCycleProgress = unusedCycleProgress(),
                liveWorkoutSurfaces = surfaces,
            ).start(backgroundScope, domainActive)

            val active = surfaces.activeWorkout.active.value
            assertNotNull(active)
            assertEquals("w-1", active!!.workoutId)
            assertEquals("Push", active.workoutName)
            assertEquals(session.startedAt.toEpochMilli(), active.startedAtEpochMs)
        }

    @Test
    @DisplayName("When domain active session becomes null then live surfaces are cleared")
    fun clearsSurfacesWhenDomainSessionNull() =
        runTest(UnconfinedTestDispatcher()) {
            val session = sampleSession()
            val domainActive = MutableStateFlow<TrainingSession?>(session)
            val restTimer = RestTimerController(clock = EpochMilliClock { 0L })
            val activeWorkout = ActiveWorkoutTracker()
            val surfaces =
                LiveWorkoutSurfaces(
                    activeWorkout = activeWorkout,
                    restTimer = restTimer,
                    restCompletion =
                        RestTimerCompletion(
                            restTimer = restTimer,
                            announcer = RestCompleteAnnouncer { _, _ -> },
                        ),
                )
            restTimer.start(seconds = 90, workoutId = "w-1", workoutName = "Push")

            DomainActiveSessionSurfaceSync(
                observeCycleProgress = unusedCycleProgress(),
                liveWorkoutSurfaces = surfaces,
            ).start(backgroundScope, domainActive)

            assertNotNull(activeWorkout.active.value)
            assertTrue(restTimer.state.value is RestTimerState.Running)

            domainActive.value = null

            assertNull(activeWorkout.active.value)
            assertTrue(restTimer.state.value is RestTimerState.Idle)
        }

    private fun surfaces(): LiveWorkoutSurfaces {
        val restTimer = RestTimerController(clock = EpochMilliClock { 0L })
        return LiveWorkoutSurfaces(
            activeWorkout = ActiveWorkoutTracker(),
            restTimer = restTimer,
            restCompletion =
                RestTimerCompletion(
                    restTimer = restTimer,
                    announcer = RestCompleteAnnouncer { _, _ -> },
                ),
        )
    }

    private fun unusedCycleProgress() =
        ObserveCycleProgressUseCase(
            programRepository = FakeProgramRepository(null),
            trainingLogRepository = FakeTrainingLogRepository(),
        )

    private fun sampleSession(): TrainingSession =
        TrainingSession(
            id = TrainingSessionId("s-1"),
            programId = null,
            workoutId = WorkoutId("w-1"),
            status = TrainingSessionStatus.IN_PROGRESS,
            kind = TrainingSessionKind.PROGRAM_WORKOUT,
            origin = TrainingSessionOrigin.LIVE,
            cycleNumberSnapshot = 1,
            weekdaySnapshot = null,
            startedAt = Instant.parse("2026-07-05T10:00:00Z"),
            endedAt = null,
            sourceApp = null,
            workoutNameSnapshot = "Push",
        )
}
