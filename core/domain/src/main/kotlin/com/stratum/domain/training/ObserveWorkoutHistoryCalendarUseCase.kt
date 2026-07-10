package com.stratum.domain.training

import com.stratum.domain.training.identity.TrainingSessionId
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Emits a live [WorkoutHistoryCalendar] from the training log.
 *
 * Includes only [TrainingSessionStatus.COMPLETED] sessions with kind
 * [TrainingSessionKind.PROGRAM_WORKOUT] or [TrainingSessionKind.AD_HOC] that have **at least one
 * logged set**. Zero-set rows (workout skips recorded as COMPLETED program workouts) are excluded
 * so they do not appear as training days or pollute streak / rest-day stats.
 *
 * Days are bucketed by local date of `endedAt ?: startedAt`.
 *
 * **Week streak:** consecutive ISO weeks (Mon–Sun) counting backward from the week containing
 * [today], each requiring ≥1 completed workout. The current week must have a workout or streak is 0.
 *
 * **Rest days:** count of local calendar days with no completed workout from the first history
 * day through [today] inclusive.
 */
class ObserveWorkoutHistoryCalendarUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
) {
    operator fun invoke(
        zoneId: ZoneId = ZoneId.systemDefault(),
        today: () -> LocalDate = { LocalDate.now(zoneId) },
    ): Flow<WorkoutHistoryCalendar> =
        flow {
            trainingLogRepository.observeAllSessions().collect { sessions ->
                val sessionIdsWithSets =
                    trainingLogRepository.getAllSets().mapTo(mutableSetOf()) { it.sessionId }
                emit(
                    buildCalendar(
                        sessions = sessions,
                        sessionIdsWithSets = sessionIdsWithSets,
                        zoneId = zoneId,
                        today = today(),
                    ),
                )
            }
        }

    companion object {
        fun buildCalendar(
            sessions: List<TrainingSession>,
            sessionIdsWithSets: Set<TrainingSessionId>,
            zoneId: ZoneId,
            today: LocalDate,
        ): WorkoutHistoryCalendar {
            val completed =
                sessions.filter { session ->
                    session.status == TrainingSessionStatus.COMPLETED &&
                        (
                            session.kind == TrainingSessionKind.PROGRAM_WORKOUT ||
                                session.kind == TrainingSessionKind.AD_HOC
                            ) &&
                        session.id in sessionIdsWithSets
                }

            val days =
                completed
                    .map { session ->
                        val completedAt = session.endedAt ?: session.startedAt
                        val date = completedAt.atZone(zoneId).toLocalDate()
                        HistoryDaySession(
                            sessionId = session.id,
                            // Workout title only (snapshot / program name) — never a lift name.
                            workoutName = TrainingSessionDisplayName.resolveForCalendar(session),
                            completedAt = completedAt,
                        ) to date
                    }
                    .groupBy(
                        keySelector = { (_, date) -> date },
                        valueTransform = { (daySession, _) -> daySession },
                    )
                    .mapValues { (_, daySessions) ->
                        daySessions.sortedByDescending { it.completedAt }
                    }

            val workoutDates = days.keys
            return WorkoutHistoryCalendar(
                days = days,
                weekStreak = computeWeekStreak(workoutDates, today),
                restDaysCount = computeRestDays(workoutDates, today),
            )
        }

        /**
         * Consecutive ISO weeks with ≥1 workout, walking backward from the week of [today].
         * Requires the current week itself to have a workout.
         */
        internal fun computeWeekStreak(workoutDates: Set<LocalDate>, today: LocalDate): Int {
            if (workoutDates.isEmpty()) return 0
            val weekStartsWithWorkouts =
                workoutDates
                    .map { date -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
                    .toSet()
            var weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            var streak = 0
            while (weekStart in weekStartsWithWorkouts) {
                streak++
                weekStart = weekStart.minusWeeks(1)
            }
            return streak
        }

        /** Days without a workout from earliest workout day through [today] inclusive. */
        internal fun computeRestDays(workoutDates: Set<LocalDate>, today: LocalDate): Int {
            val first = workoutDates.minOrNull() ?: return 0
            if (first.isAfter(today)) return 0
            var rest = 0
            var cursor = first
            while (!cursor.isAfter(today)) {
                if (cursor !in workoutDates) rest++
                cursor = cursor.plusDays(1)
            }
            return rest
        }

        fun localDateOf(instant: Instant, zoneId: ZoneId): LocalDate =
            instant.atZone(zoneId).toLocalDate()
    }
}
