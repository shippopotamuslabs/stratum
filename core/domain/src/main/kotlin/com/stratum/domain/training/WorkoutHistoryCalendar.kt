package com.stratum.domain.training

import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import java.time.LocalDate

/**
 * Read model for the workout history calendar: completed workouts bucketed by local calendar day,
 * plus streak and rest-day counters for the stats strip.
 */
data class WorkoutHistoryCalendar(
    /** Completed workout days, keyed by local date. Values newest-session-first within a day. */
    val days: Map<LocalDate, List<HistoryDaySession>>,
    /**
     * Consecutive ISO weeks (Monday–Sunday) ending at the week of [today] that each contain at
     * least one completed workout. Zero when the current week has no completed workout.
     */
    val weekStreak: Int,
    /**
     * Calendar days from the first completed-workout day through [today] (inclusive) that have
     * no completed workout. Zero when there is no history.
     */
    val restDaysCount: Int,
)

data class HistoryDaySession(
    val sessionId: TrainingSessionId,
    val workoutName: String,
    val completedAt: Instant,
)
