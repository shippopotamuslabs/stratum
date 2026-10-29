package com.stratum.domain.checkin

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.ProgramChangeEvent
import com.stratum.domain.training.ProgramChangeEventPayload
import com.stratum.domain.training.ProgramChangeEventType
import com.stratum.domain.training.identity.ProgramChangeEventId
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.workout.LiftSwapped

class LiftRotationTenureTest {

    private val workoutLiftId = WorkoutLiftId("wl-1")
    private val currentLift = LiftId("bench-press")
    private val programCreated = Instant.parse("2026-01-01T00:00:00Z")
    private val now = Instant.parse("2026-02-12T00:00:00Z") // 6 weeks later

    @Test
    @DisplayName("falls back to program createdAt when no swap events")
    fun fallsBackToProgramCreated() {
        val weeks =
            liftRotationTenureWeeks(
                workoutLiftId = workoutLiftId,
                currentLiftId = currentLift,
                programCreatedAt = programCreated,
                events = emptyList(),
                now = now,
            )
        assertEquals(6, weeks)
    }

    @Test
    @DisplayName("uses most recent swap onto current lift")
    fun usesLastSwapOntoCurrent() {
        val swapAt = now.minus(14, ChronoUnit.DAYS)
        val events =
            listOf(
                swapEvent(
                    occurredAt = programCreated.plus(7, ChronoUnit.DAYS),
                    newLiftId = currentLift,
                ),
                swapEvent(
                    occurredAt = swapAt,
                    newLiftId = currentLift,
                ),
            )
        val weeks =
            liftRotationTenureWeeks(
                workoutLiftId = workoutLiftId,
                currentLiftId = currentLift,
                programCreatedAt = programCreated,
                events = events,
                now = now,
            )
        assertEquals(2, weeks)
    }

    @Test
    @DisplayName("ignores swaps for other workout lifts or other new lifts")
    fun ignoresUnrelatedSwaps() {
        val events =
            listOf(
                swapEvent(
                    occurredAt = now.minus(7, ChronoUnit.DAYS),
                    newLiftId = LiftId("squat"),
                ),
                ProgramChangeEvent(
                    id = ProgramChangeEventId("e2"),
                    programId = ProgramId("p1"),
                    eventType = ProgramChangeEventType.LIFT_SWAPPED,
                    occurredAt = now.minus(7, ChronoUnit.DAYS),
                    payload =
                        ProgramChangeEventPayload.LiftSwapped(
                            workoutLiftId = WorkoutLiftId("other"),
                            previousLiftId = LiftId("a"),
                            newLiftId = currentLift,
                        ),
                ),
            )
        val weeks =
            liftRotationTenureWeeks(
                workoutLiftId = workoutLiftId,
                currentLiftId = currentLift,
                programCreatedAt = programCreated,
                events = events,
                now = now,
            )
        assertEquals(6, weeks)
    }

    private fun swapEvent(occurredAt: Instant, newLiftId: LiftId): ProgramChangeEvent =
        ProgramChangeEvent(
            id = ProgramChangeEventId("e-${occurredAt.epochSecond}"),
            programId = ProgramId("p1"),
            eventType = ProgramChangeEventType.LIFT_SWAPPED,
            occurredAt = occurredAt,
            payload =
                ProgramChangeEventPayload.LiftSwapped(
                    workoutLiftId = workoutLiftId,
                    previousLiftId = LiftId("old"),
                    newLiftId = newLiftId,
                ),
        )
}
