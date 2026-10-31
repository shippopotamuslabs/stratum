package com.stratum.core.inference

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.stratum.application.subscription.PlayEntitlementSyncPort
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Thin Firebase Callable client for `syncPlayEntitlement`.
 * Never logs the raw Play purchase token.
 *
 * Failures are returned as [Result] and surfaced by the caller as
 * entitlement diagnostic values drained exactly once at the app boundary,
 * so this adapter deliberately does not log (avoids logging the same
 * failure at two layers).
 */
@Singleton
class FirebasePlayEntitlementSyncAdapter @Inject constructor() : PlayEntitlementSyncPort {
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    override suspend fun syncPurchaseToken(purchaseToken: String): Result<Boolean> =
        runCatching {
            checkNotNull(auth.currentUser) { "Sign-in required to sync Play entitlement." }
            check(purchaseToken.isNotBlank()) { "purchaseToken is required." }
            val callable =
                functions
                    .getHttpsCallable(CALLABLE_NAME)
                    .withTimeout(CALLABLE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            val result = callable.call(mapOf("purchaseToken" to purchaseToken)).await()
            parsePlayEntitlementSyncResponse(result.data)
        }

    private companion object {
        const val CALLABLE_NAME = "syncPlayEntitlement"
        const val CALLABLE_TIMEOUT_SECONDS = 120L
    }
}

internal fun parsePlayEntitlementSyncResponse(data: Any?): Boolean {
    val map = data as? Map<*, *> ?: error("Invalid syncPlayEntitlement response")
    return when (val entitled = map["entitled"]) {
        is Boolean -> entitled
        else -> error("syncPlayEntitlement response missing entitled")
    }
}
