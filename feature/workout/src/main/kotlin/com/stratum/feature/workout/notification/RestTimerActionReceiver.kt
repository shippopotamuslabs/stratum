package com.stratum.feature.workout.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.stratum.domain.logging.LoggingPort
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Handles rest-timer notification actions (−10 / skip / +10) and user dismissal of the
 * rest progress notification without opening the app UI.
 */
@AndroidEntryPoint
class RestTimerActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var restTimer: RestTimerController

    @Inject
    lateinit var coordinator: WorkoutNotificationCoordinator

    @Inject
    lateinit var loggingPort: LoggingPort

    override fun onReceive(context: Context, intent: Intent?) {
        runCatching {
            when (intent?.action) {
                RestTimerActions.ADD_10 -> {
                    restTimer.add10Seconds()
                    if (restTimer.isRunning()) {
                        context.startRestTimerService()
                    } else {
                        context.stopRestTimerService()
                    }
                }
                RestTimerActions.SUBTRACT_10 -> {
                    restTimer.subtract10Seconds()
                    if (restTimer.isRunning()) {
                        context.startRestTimerService()
                    } else {
                        context.stopRestTimerService()
                    }
                }
                RestTimerActions.SKIP -> {
                    restTimer.skip()
                    context.stopRestTimerService()
                }
                RestTimerActions.DISMISS -> {
                    coordinator.onRestNotificationDismissed()
                }
                else -> return
            }
        }.onFailure { error ->
            loggingPort.error(TAG, "Rest timer action failed: ${error.message}", error)
        }
    }

    private companion object {
        const val TAG = "RestTimerAction"
    }
}
