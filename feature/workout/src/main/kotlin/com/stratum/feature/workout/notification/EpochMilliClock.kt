package com.stratum.feature.workout.notification

/**
 * Injectable wall-clock for rest-timer math. Production uses [System.currentTimeMillis];
 * unit tests inject a controllable clock so remaining time is deterministic.
 */
fun interface EpochMilliClock {
    fun nowMs(): Long
}
