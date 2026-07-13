package com.stratum.feature.workout.notification

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Bridges process lifecycle + rest timer + active session into notification surfaces.
 *
 * - Rest running (and not user-dismissed) → start [RestTimerForegroundService].
 * - App backgrounded + active session + rest idle (or rest notif dismissed) → active-workout notif.
 * - Session cleared → cancel all workout notifications and stop rest FGS.
 * - Domain active session becomes null (Programs discard, complete, etc.) → [DomainActiveSessionSurfaceSync].
 */
@Singleton
class WorkoutNotificationCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val restTimer: RestTimerController,
    private val activeWorkout: ActiveWorkoutTracker,
    private val notificationHelper: WorkoutNotificationHelper,
    private val domainActiveSessionSurfaceSync: DomainActiveSessionSurfaceSync,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false
    @Volatile
    private var appInForeground = true

    /**
     * When the user swipes away the rest FGS notification we suppress re-starting it for the
     * current countdown so the active-workout notification can take over while backgrounded.
     * Cleared when rest becomes [RestTimerState.Idle] or a new rest starts after idle.
     */
    @Volatile
    private var restNotificationSuppressed = false

    private var previousRestWasRunning = false

    fun start() {
        if (started) return
        started = true
        notificationHelper.ensureChannels()

        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    appInForeground = true
                    notificationHelper.cancelActiveWorkout()
                }

                override fun onStop(owner: LifecycleOwner) {
                    appInForeground = false
                    reconcile()
                }
            },
        )

        scope.launch {
            combine(restTimer.state, activeWorkout.active) { rest, session ->
                rest to session
            }.distinctUntilChanged().collect { (rest, session) ->
                val running = rest is RestTimerState.Running
                if (running && !previousRestWasRunning) {
                    // Fresh rest start after idle — allow the rest notification again and
                    // clear any leftover "rest complete" heads-up so it does not stack with
                    // the active countdown notification.
                    restNotificationSuppressed = false
                    notificationHelper.cancelRestComplete()
                }
                if (!running) {
                    restNotificationSuppressed = false
                }
                previousRestWasRunning = running
                applyNotificationPolicy(rest, session)
            }
        }

        domainActiveSessionSurfaceSync.start(scope)
    }

    /**
     * User dismissed the rest progress notification (swipe) or the rest FGS was torn down
     * while a countdown is still active. Fall back to the active-workout surface when
     * the app is in the background.
     *
     * Idempotent: [RestTimerForegroundService.onDestroy] may call this after the delete
     * intent already did.
     */
    fun onRestNotificationDismissed() {
        val alreadySuppressed = restNotificationSuppressed
        restNotificationSuppressed = true
        if (!alreadySuppressed) {
            // Stop the FGS if it is still alive so we do not keep a headless foreground service.
            context.stopRestTimerService()
        }
        if (!appInForeground) {
            val session = activeWorkout.active.value
            if (session != null) {
                notificationHelper.showActiveWorkout(session)
            }
        }
    }

    private fun reconcile() {
        applyNotificationPolicy(restTimer.state.value, activeWorkout.active.value)
    }

    private fun applyNotificationPolicy(
        rest: RestTimerState,
        session: ActiveWorkoutSnapshot?,
    ) {
        when {
            rest is RestTimerState.Running && !restNotificationSuppressed -> {
                notificationHelper.cancelActiveWorkout()
                context.startRestTimerService()
            }
            rest is RestTimerState.Running && restNotificationSuppressed -> {
                // Rest still ticking in-process, but user removed the rest notif — show
                // active workout while backgrounded so there is still a return path.
                if (session != null && !appInForeground) {
                    notificationHelper.showActiveWorkout(session)
                } else {
                    notificationHelper.cancelActiveWorkout()
                }
            }
            session == null -> {
                context.stopRestTimerService()
                restTimer.cancel()
                notificationHelper.cancelAll()
            }
            else -> {
                // Rest idle with an active session: only show workout notif when backgrounded.
                if (!appInForeground) {
                    notificationHelper.showActiveWorkout(session)
                } else {
                    notificationHelper.cancelActiveWorkout()
                }
            }
        }
    }
}
