package com.stratum.feature.workout.notification

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.stratum.feature.workout.R
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single gate for rest-timer expiry side effects. [completeIfDue] is safe to call from both
 * the in-app ticker and the rest FGS: only the first successful transition to Idle plays the
 * boxing bell and posts the (silent) completion heads-up — no double ding.
 */
@Singleton
class RestTimerCompletion @Inject constructor(
    private val restTimer: RestTimerController,
    private val announcer: RestCompleteAnnouncer,
) {
    /**
     * @return true if the timer was running and just completed (effects already fired).
     */
    fun completeIfDue(): Boolean {
        val running = restTimer.state.value as? RestTimerState.Running ?: return false
        if (!restTimer.completeIfDue()) return false
        announcer.announceComplete(
            workoutId = running.workoutId,
            workoutName = running.workoutName,
        )
        return true
    }
}

/** Plays the rest-complete chime and optional heads-up. */
fun interface RestCompleteAnnouncer {
    fun announceComplete(workoutId: String, workoutName: String)
}

/**
 * Plays [R.raw.boxing_bell] once and shows a silent completion notification for lock-screen
 * return. Sound lives here (not on the notification channel) so foreground + background share
 * one chime path.
 */
@Singleton
class RestTimerCompletionEffects @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationHelper: WorkoutNotificationHelper,
) : RestCompleteAnnouncer {
    override fun announceComplete(workoutId: String, workoutName: String) {
        playBell()
        notificationHelper.postRestCompleteAlert(
            workoutId = workoutId,
            workoutName = workoutName,
        )
    }

    private fun playBell() {
        try {
            val player = MediaPlayer.create(context, R.raw.boxing_bell) ?: return
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            player.setOnCompletionListener { mediaPlayer -> mediaPlayer.release() }
            player.setOnErrorListener { mediaPlayer, _, _ ->
                mediaPlayer.release()
                true
            }
            player.start()
        } catch (_: Exception) {
            // Best-effort chime; rest UI still completes.
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RestCompleteAnnouncerModule {
    @Binds
    @Singleton
    abstract fun bindRestCompleteAnnouncer(
        impl: RestTimerCompletionEffects,
    ): RestCompleteAnnouncer
}
