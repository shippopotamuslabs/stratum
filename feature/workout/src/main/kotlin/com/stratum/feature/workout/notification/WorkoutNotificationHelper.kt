package com.stratum.feature.workout.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.stratum.feature.workout.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds workout-related notifications and ensures channels exist.
 * Callers are responsible for posting (and for holding POST_NOTIFICATIONS when required).
 */
@Singleton
class WorkoutNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val notificationManager: NotificationManager
        get() = context.getSystemService(NotificationManager::class.java)

    fun ensureChannels() {
        val progress = NotificationChannel(
            WorkoutNotificationChannels.REST_PROGRESS,
            context.getString(R.string.notif_channel_rest_progress),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notif_channel_rest_progress_desc)
            setSound(null, null)
            enableVibration(false)
        }

        // Sound is played once via [RestTimerCompletionEffects] (MediaPlayer), not the channel,
        // so in-app + FGS completion cannot double-ding through channel + player.
        val complete = NotificationChannel(
            WorkoutNotificationChannels.REST_COMPLETE,
            context.getString(R.string.notif_channel_rest_complete),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notif_channel_rest_complete_desc)
            setSound(null, null)
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val active = NotificationChannel(
            WorkoutNotificationChannels.ACTIVE_WORKOUT,
            context.getString(R.string.notif_channel_active_workout),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notif_channel_active_workout_desc)
            setSound(null, null)
            enableVibration(false)
        }

        notificationManager.createNotificationChannel(progress)
        notificationManager.createNotificationChannel(complete)
        notificationManager.createNotificationChannel(active)
    }

    fun areNotificationsEnabled(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun buildRestProgressNotification(running: RestTimerState.Running): Notification {
        ensureChannels()
        val openWorkout = activityPendingIntent(
            requestCode = REQUEST_OPEN_REST,
            workoutId = running.workoutId,
        )
        return NotificationCompat.Builder(context, WorkoutNotificationChannels.REST_PROGRESS)
            .setSmallIcon(R.drawable.ic_rest_notification)
            .setContentTitle(running.workoutName)
            .setContentText(context.getString(R.string.notif_rest_in_progress))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(running.endsAtEpochMs)
            .setContentIntent(openWorkout)
            .setDeleteIntent(actionPendingIntent(RestTimerActions.DISMISS, REQUEST_DISMISS))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(
                0,
                context.getString(R.string.notif_rest_action_minus_10),
                actionPendingIntent(RestTimerActions.SUBTRACT_10, REQUEST_SUBTRACT),
            )
            .addAction(
                0,
                context.getString(R.string.notif_rest_action_skip),
                actionPendingIntent(RestTimerActions.SKIP, REQUEST_SKIP),
            )
            .addAction(
                0,
                context.getString(R.string.notif_rest_action_plus_10),
                actionPendingIntent(RestTimerActions.ADD_10, REQUEST_ADD),
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    fun postRestCompleteAlert(workoutId: String, workoutName: String) {
        if (!areNotificationsEnabled()) return
        ensureChannels()
        val notification = NotificationCompat.Builder(context, WorkoutNotificationChannels.REST_COMPLETE)
            .setSmallIcon(R.drawable.ic_rest_notification)
            .setContentTitle(context.getString(R.string.notif_rest_complete_title))
            .setContentText(workoutName)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(activityPendingIntent(REQUEST_OPEN_COMPLETE, workoutId))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        try {
            NotificationManagerCompat.from(context)
                .notify(WorkoutNotificationIds.REST_COMPLETE, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS denied at runtime — in-app timer still works.
        }
    }

    fun buildActiveWorkoutNotification(snapshot: ActiveWorkoutSnapshot): Notification {
        ensureChannels()
        return NotificationCompat.Builder(context, WorkoutNotificationChannels.ACTIVE_WORKOUT)
            .setSmallIcon(R.drawable.ic_workout_notification)
            .setContentTitle(snapshot.workoutName)
            .setContentText(context.getString(R.string.notif_active_workout_text))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setWhen(snapshot.startedAtEpochMs)
            .setContentIntent(activityPendingIntent(REQUEST_OPEN_ACTIVE, snapshot.workoutId))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    fun showActiveWorkout(snapshot: ActiveWorkoutSnapshot) {
        if (!areNotificationsEnabled()) return
        try {
            NotificationManagerCompat.from(context)
                .notify(WorkoutNotificationIds.ACTIVE_WORKOUT, buildActiveWorkoutNotification(snapshot))
        } catch (_: SecurityException) {
            // Permission denied — ignore.
        }
    }

    fun cancelActiveWorkout() {
        NotificationManagerCompat.from(context).cancel(WorkoutNotificationIds.ACTIVE_WORKOUT)
    }

    fun cancelRestComplete() {
        NotificationManagerCompat.from(context).cancel(WorkoutNotificationIds.REST_COMPLETE)
    }

    fun cancelAll() {
        val nm = NotificationManagerCompat.from(context)
        nm.cancel(WorkoutNotificationIds.REST_PROGRESS)
        nm.cancel(WorkoutNotificationIds.REST_COMPLETE)
        nm.cancel(WorkoutNotificationIds.ACTIVE_WORKOUT)
    }

    private fun activityPendingIntent(requestCode: Int, workoutId: String): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent()
        launch.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(WorkoutNavExtras.EXTRA_WORKOUT_ID, workoutId)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun actionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, RestTimerActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val REQUEST_OPEN_REST = 4201
        const val REQUEST_OPEN_COMPLETE = 4202
        const val REQUEST_OPEN_ACTIVE = 4203
        const val REQUEST_ADD = 4211
        const val REQUEST_SUBTRACT = 4212
        const val REQUEST_SKIP = 4213
        const val REQUEST_DISMISS = 4214
    }
}

/**
 * Starts the rest-timer foreground service when a countdown is running.
 */
fun Context.startRestTimerService() {
    val intent = Intent(this, RestTimerForegroundService::class.java)
    ContextCompat.startForegroundService(this, intent)
}

fun Context.stopRestTimerService() {
    stopService(Intent(this, RestTimerForegroundService::class.java))
}
