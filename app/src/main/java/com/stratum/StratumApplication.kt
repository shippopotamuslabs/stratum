package com.stratum

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.stratum.domain.logging.ProcessErrorSafetyNet
import com.stratum.feature.workout.notification.WorkoutNotificationCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point. Implements [Configuration.Provider] so WorkManager uses
 * [HiltWorkerFactory] for `@HiltWorker` injection (dose-response modeling, etc.).
 * The default [androidx.work.WorkManagerInitializer] is removed in the manifest so this
 * on-demand configuration takes over.
 */
@HiltAndroidApp
class StratumApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var workoutNotificationCoordinator: WorkoutNotificationCoordinator

    @Inject
    lateinit var processErrorSafetyNet: ProcessErrorSafetyNet

    override fun onCreate() {
        super.onCreate()
        // Hilt injects members during super.onCreate(); install after so LoggingPort is ready.
        processErrorSafetyNet.installUncaughtExceptionHandler()
        workoutNotificationCoordinator.start()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
