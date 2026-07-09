package com.stratum.feature.workout.notification

object WorkoutNotificationIds {
    const val REST_PROGRESS = 4101
    const val REST_COMPLETE = 4102
    const val ACTIVE_WORKOUT = 4103
}

object WorkoutNotificationChannels {
    const val REST_PROGRESS = "rest_timer_progress"
    /** Bumped when sound moved off-channel to MediaPlayer (avoid double-ding + stale channel config). */
    const val REST_COMPLETE = "rest_timer_complete_v2"
    const val ACTIVE_WORKOUT = "active_workout"
}

object WorkoutNavExtras {
    const val EXTRA_WORKOUT_ID = "com.stratum.extra.WORKOUT_ID"
}

object RestTimerActions {
    const val ADD_10 = "com.stratum.feature.workout.action.REST_ADD_10"
    const val SUBTRACT_10 = "com.stratum.feature.workout.action.REST_SUBTRACT_10"
    const val SKIP = "com.stratum.feature.workout.action.REST_SKIP"
    /** Fired when the user swipes away the rest progress notification. */
    const val DISMISS = "com.stratum.feature.workout.action.REST_DISMISS"
}
