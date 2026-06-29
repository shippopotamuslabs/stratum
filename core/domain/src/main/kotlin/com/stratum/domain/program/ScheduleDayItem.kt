package com.stratum.domain.program

import com.stratum.domain.onboarding.Weekday

/** One row in the Mon–Sun program schedule: either a rest day or a bound workout. */
sealed interface ScheduleDayItem {
    data class RestDay(val weekday: Weekday) : ScheduleDayItem

    data class WorkoutDay(val weekday: Weekday, val workout: Workout) : ScheduleDayItem
}
