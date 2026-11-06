package com.stratum.core.infrastructure.subscription

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.stratum.core.infrastructure.logging.LoggingPort
import com.stratum.domain.subscription.ComplimentaryAccess
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.tasks.await

/**
 * Reads and listens for complimentary / developer free-access overrides stored in Firestore under
 * `users/{userId}/entitlement/override`.
 *
 * Security rules enforce that this document is read-only for the client and can only be modified
 * via Firebase Console or Firebase Admin SDK.
 *
 * Read failures surface as [ComplimentaryAccess.Unknown], never as
 * [ComplimentaryAccess.NotGranted]: a denied or failed read (for example a transient
 * App Check token failure under enforcement) is not evidence that the user has no
 * override, and mapping it to "no access" paywalls entitled users (STR-280).
 */
@Singleton
class FirestoreEntitlementDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val loggingPort: LoggingPort,
) : ComplimentaryAccessStore {

    override fun observeComplimentaryAccess(userId: String): Flow<ComplimentaryAccess> = flow {
        // A Firestore snapshot listener never fires again after an error, so a failed
        // listener is re-registered after a delay for as long as this flow is collected.
        while (currentCoroutineContext().isActive) {
            emitAll(listenForComplimentaryAccess(userId))
            delay(RETRY_DELAY_MILLIS)
        }
    }

    private fun listenForComplimentaryAccess(userId: String): Flow<ComplimentaryAccess> = callbackFlow {
        val registration = overrideDocument(userId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                loggingPort.error(
                    TAG,
                    "Failed listening for entitlement override for $userId: ${error.message}",
                    error,
                )
                trySend(ComplimentaryAccess.Unknown)
                close()
                return@addSnapshotListener
            }
            val hasFreeAccess = snapshot != null && snapshot.exists() && snapshot.getBoolean("freeAccess") == true
            trySend(if (hasFreeAccess) ComplimentaryAccess.Granted else ComplimentaryAccess.NotGranted)
        }

        awaitClose {
            registration.remove()
        }
    }

    override suspend fun getComplimentaryAccess(userId: String): ComplimentaryAccess = try {
        val snapshot = overrideDocument(userId)
            .get(Source.DEFAULT)
            .await()
        val hasFreeAccess = snapshot != null && snapshot.exists() && snapshot.getBoolean("freeAccess") == true
        if (hasFreeAccess) ComplimentaryAccess.Granted else ComplimentaryAccess.NotGranted
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        loggingPort.warning(TAG, "Failed fetching entitlement override for $userId: ${error.message}")
        ComplimentaryAccess.Unknown
    }

    private fun overrideDocument(userId: String): DocumentReference = firestore.collection("users")
        .document(userId)
        .collection("entitlement")
        .document("override")

    companion object {
        private const val TAG = "FirestoreEntitlement"
        private const val RETRY_DELAY_MILLIS = 5_000L
    }
}
