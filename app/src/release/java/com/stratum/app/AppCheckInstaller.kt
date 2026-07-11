package com.stratum.app

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.stratum.domain.logging.LoggingPort

/**
 * Release App Check install (Play Integrity). Debug uses the same FQCN under `src/debug`.
 */
object AppCheckInstaller {
    @Suppress("UNUSED_PARAMETER")
    fun install(appCheck: FirebaseAppCheck, loggingPort: LoggingPort) {
        appCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance(),
        )
    }
}
