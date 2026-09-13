package com.stratum

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.stratum.domain.di.ApplicationScope
import com.stratum.domain.featureflag.FeatureFlagRepository
import com.stratum.domain.logging.ProcessErrorSafetyNet
import com.stratum.feature.workout.notification.WorkoutNotificationCoordinator
import com.stratum.sync.CloudSyncAuthListener
import com.stratum.telemetry.TelemetryAuthListener
import com.stratum.telemetry.TelemetryContextListener
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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

    @Inject
    lateinit var cloudSyncAuthListener: CloudSyncAuthListener

    @Inject
    lateinit var telemetryAuthListener: TelemetryAuthListener

    @Inject
    lateinit var telemetryContextListener: TelemetryContextListener

    @Inject
    lateinit var featureFlagRepository: FeatureFlagRepository

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Hilt injects members during super.onCreate(); install after so LoggingPort is ready.
        processErrorSafetyNet.installUncaughtExceptionHandler()
        workoutNotificationCoordinator.start()
        cloudSyncAuthListener.start()
        telemetryAuthListener.start()
        telemetryContextListener.start()
        applicationScope.launch {
            featureFlagRepository.syncFlags()
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
