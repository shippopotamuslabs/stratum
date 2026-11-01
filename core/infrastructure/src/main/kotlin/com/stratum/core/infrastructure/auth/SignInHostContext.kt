package com.stratum.core.infrastructure.auth

import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the foreground Activity [Context] required by Credential Manager UI flows.
 * [com.stratum.MainActivity] attaches on create and detaches on destroy.
 */
@Singleton
class SignInHostContext @Inject constructor() {
    @Volatile
    private var hostContext: Context? = null

    fun attach(context: Context) {
        hostContext = context
    }

    fun detach(context: Context) {
        if (hostContext === context) {
            hostContext = null
        }
    }

    fun requireContext(): Context =
        hostContext ?: error("Sign-in requires a foreground Activity context.")
}
