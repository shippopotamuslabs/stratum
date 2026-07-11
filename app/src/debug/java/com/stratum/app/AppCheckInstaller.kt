package com.stratum.app

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.stratum.domain.logging.LoggingPort

/**
 * Debug-only App Check install. Release uses the same FQCN under `src/release`.
 */
object AppCheckInstaller {
    fun install(appCheck: FirebaseAppCheck, loggingPort: LoggingPort) {
        appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        appCheck.getAppCheckToken(false)
            .addOnSuccessListener {
                loggingPort.info(TAG, "App Check debug token obtained successfully.")
            }
            .addOnFailureListener { error ->
                loggingPort.error(
                    TAG,
                    "App Check debug token failed (register the debug secret from logcat in " +
                        "Firebase Console → App Check → Manage debug tokens): ${error.message}",
                    error,
                )
            }
    }

    private const val TAG = "StratumAppCheck"
}
