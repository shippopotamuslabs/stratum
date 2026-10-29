package com.stratum

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.stratum.app.AppCheckInstaller
import com.stratum.domain.di.ApplicationScope
import com.stratum.application.featureflag.FeatureFlagRepository
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.ProcessErrorSafetyNet
import com.stratum.domain.telemetry.ClientLogBuffer
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
    lateinit var loggingPort: LoggingPort

    @Inject
    lateinit var clientLogBuffer: ClientLogBuffer

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
        // App Check installs first so every Firebase caller in this process — including
        // background workers that never touch an activity — is attested from the start.
        initFirebaseAppCheck()
        processErrorSafetyNet.installUncaughtExceptionHandler()
        // Tier-2 telemetry (STR-27): restore the offline queue and start periodic
        // upload of warning()/errorSkippingNonFatal() entries.
        clientLogBuffer.start()
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

    /**
     * Installs the variant-specific App Check provider (`src/debug` vs `src/release`).
     * Enforced server-side: the AI callables enforce App Check in code and Cloud
     * Firestore enforces it in the Firebase console (STR-202), so attested tokens
     * attach automatically from process start. Debug uses
     * [com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory] (debug
     * dependency only); release uses Play Integrity. Register debug secrets in
     * Firebase Console → App Check → Manage debug tokens when using debug builds
     * against enforced endpoints.
     */
    private fun initFirebaseAppCheck() {
        FirebaseApp.initializeApp(this)
        AppCheckInstaller.install(FirebaseAppCheck.getInstance(), loggingPort)
    }
}
