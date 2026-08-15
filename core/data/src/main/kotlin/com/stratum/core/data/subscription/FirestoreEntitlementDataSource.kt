package com.stratum.core.data.subscription

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.stratum.domain.logging.LoggingPort
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Reads and listens for complimentary / developer free-access overrides stored in Firestore under
 * `users/{userId}/entitlement/override`.
 *
 * Security rules enforce that this document is read-only for the client and can only be modified
 * via Firebase Console or Firebase Admin SDK.
 */
@Singleton
class FirestoreEntitlementDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val loggingPort: LoggingPort,
) : ComplimentaryAccessStore {
    override fun observeComplimentaryAccess(userId: String): Flow<Boolean> = callbackFlow {
        val docRef = firestore.collection("users")
            .document(userId)
            .collection("entitlement")
            .document("override")

        val registration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                loggingPort.error(TAG, "Failed listening for entitlement override for $userId: ${error.message}", error)
                trySend(false)
                return@addSnapshotListener
            }
            val hasFreeAccess = snapshot != null && snapshot.exists() && snapshot.getBoolean("freeAccess") == true
            trySend(hasFreeAccess)
        }

        awaitClose {
            registration.remove()
        }
    }

    override suspend fun getComplimentaryAccess(userId: String): Boolean = runCatching {
        val snapshot = firestore.collection("users")
            .document(userId)
            .collection("entitlement")
            .document("override")
            .get(Source.DEFAULT)
            .await()
        snapshot != null && snapshot.exists() && snapshot.getBoolean("freeAccess") == true
    }.getOrElse { error ->
        loggingPort.warning(TAG, "Failed fetching entitlement override for $userId: ${error.message}")
        false
    }

    companion object {
        private const val TAG = "FirestoreEntitlement"
    }
}
