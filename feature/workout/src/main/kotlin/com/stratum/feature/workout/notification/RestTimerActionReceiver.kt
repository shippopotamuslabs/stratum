package com.stratum.feature.workout.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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

    override fun onReceive(context: Context, intent: Intent?) {
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
                // User swiped the rest notification away — hand off to active-workout surface.
                coordinator.onRestNotificationDismissed()
            }
            else -> return
        }
    }
}
