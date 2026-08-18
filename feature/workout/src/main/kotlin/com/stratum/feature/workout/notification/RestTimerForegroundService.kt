package com.stratum.feature.workout.notification

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.stratum.domain.di.ApplicationScope
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.collectLatestRestarting
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground service that hosts the rest-timer progress notification while a countdown
 * is active. Uses a system chronometer for display and a coroutine sleep until
 * [RestTimerState.Running.endsAtEpochMs] for completion + boxing-bell alert.
 */
@AndroidEntryPoint
class RestTimerForegroundService : Service() {

    @Inject
    lateinit var restTimer: RestTimerController

    @Inject
    lateinit var restCompletion: RestTimerCompletion

    @Inject
    lateinit var notificationHelper: WorkoutNotificationHelper

    @Inject
    lateinit var coordinator: WorkoutNotificationCoordinator

    @Inject
    lateinit var clock: EpochMilliClock

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var loggingPort: LoggingPort

    private var observeJob: Job? = null

    /** True when we are tearing down intentionally (idle / complete / skip), not via swipe. */
    private var stoppingIntentionally = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationHelper.ensureChannels()
        val initial = restTimer.state.value
        val notification = if (initial is RestTimerState.Running) {
            notificationHelper.buildRestProgressNotification(initial)
        } else {
            // startForeground must be called promptly; placeholder until state arrives.
            notificationHelper.buildRestProgressNotification(
                RestTimerState.Running(
                    endsAtEpochMs = clock.nowMs() + 1000L,
                    totalSeconds = 1,
                    workoutId = "",
                    workoutName = getString(com.stratum.feature.workout.R.string.notif_rest_in_progress),
                ),
            )
        }
        ServiceCompat.startForeground(
            this,
            WorkoutNotificationIds.REST_PROGRESS,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        observeJob = applicationScope.launch {
            restTimer.state.collectLatestRestarting(loggingPort, TAG) { state ->
                when (state) {
                    is RestTimerState.Idle -> {
                        stopSelfSafely()
                    }
                    is RestTimerState.Running -> {
                        updateProgress(state)
                        val waitMs = state.endsAtEpochMs - clock.nowMs()
                        if (waitMs <= 0L) {
                            finishRest()
                        } else {
                            delay(waitMs)
                            // Re-check: adjust/skip may have changed endsAt while we slept.
                            val latest = restTimer.state.value
                            if (latest is RestTimerState.Running &&
                                clock.nowMs() >= latest.endsAtEpochMs
                            ) {
                                finishRest()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val state = restTimer.state.value
        if (state is RestTimerState.Running) {
            updateProgress(state)
        } else if (state is RestTimerState.Idle) {
            stopSelfSafely()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        observeJob?.cancel()
        // Do not cancel [applicationScope] — it outlives this service.
        // Swipe-away / system kill can destroy the FGS while rest is still Running. Fall back
        // to the active-workout notification when backgrounded (deleteIntent is best-effort).
        if (!stoppingIntentionally && restTimer.isRunning()) {
            coordinator.onRestNotificationDismissed()
        }
        super.onDestroy()
    }

    private fun updateProgress(running: RestTimerState.Running) {
        val notification = notificationHelper.buildRestProgressNotification(running)
        ServiceCompat.startForeground(
            this,
            WorkoutNotificationIds.REST_PROGRESS,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
    }

    private fun finishRest() {
        // Shared gate with the in-app ticker — only one side fires the bell.
        restCompletion.completeIfDue()
        stopSelfSafely()
    }

    private fun stopSelfSafely() {
        stoppingIntentionally = true
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private companion object {
        const val TAG = "RestTimerFgs"
    }
}
