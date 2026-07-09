package com.stratum.feature.workout.notification

import com.stratum.feature.workout.notification.RestTimerDefaults.ADJUST_STEP_SECONDS
import com.stratum.feature.workout.notification.RestTimerDefaults.MAX_REST_SECONDS
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the live rest countdown for the process. Compose UI, the rest-timer FGS, and
 * notification action receivers all bind to [state] so background actions stay in sync
 * with the in-workout chrome.
 */
@Singleton
class RestTimerController @Inject constructor(
    private val clock: EpochMilliClock,
) {
    private val _state = MutableStateFlow<RestTimerState>(RestTimerState.Idle)
    val state: StateFlow<RestTimerState> = _state.asStateFlow()

    fun start(seconds: Int, workoutId: String, workoutName: String) {
        if (seconds <= 0) return
        val clamped = seconds.coerceAtMost(MAX_REST_SECONDS)
        val now = clock.nowMs()
        _state.value = RestTimerState.Running(
            endsAtEpochMs = now + clamped * 1000L,
            totalSeconds = clamped,
            workoutId = workoutId,
            workoutName = workoutName,
        )
    }

    fun adjustBy(deltaSeconds: Int) {
        val current = _state.value as? RestTimerState.Running ?: return
        val remaining = remainingSeconds(current)
        val newRemaining = (remaining + deltaSeconds).coerceIn(0, MAX_REST_SECONDS)
        if (newRemaining == 0) {
            _state.value = RestTimerState.Idle
            return
        }
        val now = clock.nowMs()
        val newTotal = max(current.totalSeconds, newRemaining)
        _state.value = current.copy(
            endsAtEpochMs = now + newRemaining * 1000L,
            totalSeconds = newTotal,
        )
    }

    fun add10Seconds() = adjustBy(ADJUST_STEP_SECONDS)

    fun subtract10Seconds() = adjustBy(-ADJUST_STEP_SECONDS)

    fun skip() {
        _state.value = RestTimerState.Idle
    }

    fun cancel() {
        _state.value = RestTimerState.Idle
    }

    /**
     * Marks the timer idle if the wall clock has reached [RestTimerState.Running.endsAtEpochMs].
     * Returns true when a transition to idle occurred (caller should post completion alert).
     */
    fun completeIfDue(): Boolean {
        val current = _state.value as? RestTimerState.Running ?: return false
        if (clock.nowMs() < current.endsAtEpochMs) return false
        _state.value = RestTimerState.Idle
        return true
    }

    fun remainingSeconds(nowMs: Long = clock.nowMs()): Int {
        val current = _state.value as? RestTimerState.Running ?: return 0
        return remainingSeconds(current, nowMs)
    }

    fun remainingSeconds(running: RestTimerState.Running, nowMs: Long = clock.nowMs()): Int {
        val remainingMs = running.endsAtEpochMs - nowMs
        // Ceil-divide ms → whole seconds so a partial second still displays as 1.
        return ((remainingMs + MS_PER_SECOND - 1) / MS_PER_SECOND).toInt().coerceAtLeast(0)
    }

    private companion object {
        const val MS_PER_SECOND = 1000L
    }

    fun isRunning(): Boolean = _state.value is RestTimerState.Running
}
