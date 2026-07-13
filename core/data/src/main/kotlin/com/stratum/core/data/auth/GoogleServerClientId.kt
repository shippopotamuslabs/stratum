package com.stratum.core.data.auth

import javax.inject.Qualifier

/**
 * OAuth 2.0 **Web** client ID used as Credential Manager / Google Sign-In
 * `serverClientId` (maps to Firebase `default_web_client_id` from google-services).
 *
 * Provided from the app module with a static `R.string.default_web_client_id` reference so
 * resource shrinking cannot strip the string (unlike [android.content.res.Resources.getIdentifier]).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GoogleServerClientId
