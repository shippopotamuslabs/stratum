package com.stratum.domain.training

import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.Weight
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ObserveWorkoutHistoryCalendarUseCaseTest {

    private val zone = ZoneOffset.UTC

    @Test
    @DisplayName("Groups completed workouts by local date of endedAt")
    fun groupsByLocalDate() = runTest {
        val s1 =
            WorkoutLoggingFixtures.completedLiveSession(id = "ts-1").copy(
                startedAt = Instant.parse("2025-11-02T10:00:00Z"),
                endedAt = Instant.parse("2025-11-02T11:00:00Z"),
                workoutNameSnapshot = "Upper A",
            )
        val s2 =
            WorkoutLoggingFixtures.completedLiveSession(id = "ts-2").copy(
                startedAt = Instant.parse("2025-11-03T10:00:00Z"),
                endedAt = Instant.parse("2025-11-03T11:00:00Z"),
                workoutNameSnapshot = "Lower A",
            )
        val inProgress = WorkoutLoggingFixtures.inProgressSession(id = "ts-ip")
        val rest =
            WorkoutLoggingFixtures.completedLiveSession(
                id = "ts-rest",
                workoutId = null,
                kind = TrainingSessionKind.REST_DAY,
            )
        val repo =
            FakeTrainingLogRepository(
                initialSessions = listOf(s1, s2, inProgress, rest),
                initialSets =
                    listOf(
                        setFor("ts-1"),
                        setFor("ts-2"),
                    ),
            )
        val useCase = ObserveWorkoutHistoryCalendarUseCase(repo)

        val calendar =
            useCase(zoneId = zone, today = { LocalDate.of(2025, 11, 10) }).first()

        assertEquals(2, calendar.days.size)
        assertEquals("Upper A", calendar.days[LocalDate.of(2025, 11, 2)]!!.single().workoutName)
        assertEquals("Lower A", calendar.days[LocalDate.of(2025, 11, 3)]!!.single().workoutName)
        assertTrue(calendar.days.values.none { sessions ->
            sessions.any { it.sessionId == TrainingSessionId("ts-ip") }
        })
    }

    @Test
    @DisplayName("Excludes zero-set skip sessions from calendar and stats")
    fun excludesZeroSetSkips() = runTest {
        val trained =
            WorkoutLoggingFixtures.completedLiveSession(id = "ts-real").copy(
                startedAt = Instant.parse("2025-11-02T10:00:00Z"),
                endedAt = Instant.parse("2025-11-02T11:00:00Z"),
                workoutNameSnapshot = "Upper A",
            )
        val skip =
            WorkoutLoggingFixtures.completedLiveSession(id = "ts-skip").copy(
                startedAt = Instant.parse("2025-11-03T10:00:00Z"),
                endedAt = Instant.parse("2025-11-03T10:00:00Z"),
                workoutNameSnapshot = "Lower A",
            )
        val calendar =
            ObserveWorkoutHistoryCalendarUseCase.buildCalendar(
                sessions = listOf(trained, skip),
                sessionIdsWithSets = setOf(TrainingSessionId("ts-real")),
                zoneId = zone,
                today = LocalDate.of(2025, 11, 5),
            )
        assertEquals(1, calendar.days.size)
        assertTrue(LocalDate.of(2025, 11, 3) !in calendar.days)
        // Rest days: Nov 2 trained, 3-5 empty → 3 rest days
        assertEquals(3, calendar.restDaysCount)
    }

    @Test
    @DisplayName("Multiple sessions same day are newest first")
    fun multipleSessionsSameDayNewestFirst() {
        val early =
            WorkoutLoggingFixtures.completedLiveSession(id = "early").copy(
                startedAt = Instant.parse("2025-11-05T08:00:00Z"),
                endedAt = Instant.parse("2025-11-05T09:00:00Z"),
                workoutNameSnapshot = "AM",
            )
        val late =
            WorkoutLoggingFixtures.completedLiveSession(id = "late").copy(
                startedAt = Instant.parse("2025-11-05T18:00:00Z"),
                endedAt = Instant.parse("2025-11-05T19:00:00Z"),
                workoutNameSnapshot = "PM",
            )
        val calendar =
            ObserveWorkoutHistoryCalendarUseCase.buildCalendar(
                sessions = listOf(early, late),
                sessionIdsWithSets =
                    setOf(TrainingSessionId("early"), TrainingSessionId("late")),
                zoneId = zone,
                today = LocalDate.of(2025, 11, 5),
            )
        val day = calendar.days[LocalDate.of(2025, 11, 5)]!!
        assertEquals(listOf("PM", "AM"), day.map { it.workoutName })
    }

    @Test
    @DisplayName("Week streak counts consecutive ISO weeks with workouts from current week")
    fun weekStreakConsecutiveFromCurrentWeek() {
        val today = LocalDate.of(2025, 11, 12)
        val thisWeek = LocalDate.of(2025, 11, 11)
        val lastWeek = LocalDate.of(2025, 11, 5)
        val twoWeeksAgo = LocalDate.of(2025, 10, 28)
        val gapThen = LocalDate.of(2025, 10, 14)

        val dates = setOf(thisWeek, lastWeek, twoWeeksAgo, gapThen)
        assertEquals(3, ObserveWorkoutHistoryCalendarUseCase.computeWeekStreak(dates, today))
    }

    @Test
    @DisplayName("Week streak is zero when current week has no workout")
    fun weekStreakZeroWithoutCurrentWeek() {
        val today = LocalDate.of(2025, 11, 12)
        val lastWeekOnly = setOf(LocalDate.of(2025, 11, 5))
        assertEquals(0, ObserveWorkoutHistoryCalendarUseCase.computeWeekStreak(lastWeekOnly, today))
    }

    @Test
    @DisplayName("Rest days count gaps from first workout through today")
    fun restDaysCountGaps() {
        val today = LocalDate.of(2025, 11, 5)
        val dates = setOf(LocalDate.of(2025, 11, 1), LocalDate.of(2025, 11, 3))
        assertEquals(3, ObserveWorkoutHistoryCalendarUseCase.computeRestDays(dates, today))
    }

    @Test
    @DisplayName("Empty history yields zero streak and rest days")
    fun emptyHistory() {
        val calendar =
            ObserveWorkoutHistoryCalendarUseCase.buildCalendar(
                sessions = emptyList(),
                sessionIdsWithSets = emptySet(),
                zoneId = zone,
                today = LocalDate.of(2025, 11, 5),
            )
        assertTrue(calendar.days.isEmpty())
        assertEquals(0, calendar.weekStreak)
        assertEquals(0, calendar.restDaysCount)
    }

    @Test
    @DisplayName("Uses workoutNameSnapshot when present")
    fun usesNameSnapshot() {
        val session =
            WorkoutLoggingFixtures.completedLiveSession(
                id = "ts-1",
                workoutId = WorkoutId("gone"),
            ).copy(workoutNameSnapshot = "Snap Name")
        val calendar =
            ObserveWorkoutHistoryCalendarUseCase.buildCalendar(
                sessions = listOf(session),
                sessionIdsWithSets = setOf(TrainingSessionId("ts-1")),
                zoneId = zone,
                today = LocalDate.of(2025, 1, 1),
            )
        assertEquals(
            "Snap Name",
            calendar.days.values.single().single().workoutName,
        )
    }

    private fun setFor(sessionId: String) =
        LoggedSet(
            id = LoggedSetId("set-$sessionId"),
            sessionId = TrainingSessionId(sessionId),
            exerciseNameRaw = "Bench",
            liftId = WorkoutLoggingFixtures.liftBenchId,
            setIndex = 0,
            weight = Weight(135.0, Weight.WeightUnit.Lb),
            reps = 8,
            rir = 2,
            completedAt = Instant.parse("2025-11-02T10:30:00Z"),
            liftContext = WorkoutLoggingFixtures.importLiftContext(),
        )
}
