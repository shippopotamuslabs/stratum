@file:Suppress("ForbiddenMockKInTests") // Firebase Firestore SDK boundary — no fake available.

package com.stratum.core.infrastructure.sync

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.stratum.core.infrastructure.sync.handlers.GenericSyncHandler
import com.stratum.core.infrastructure.sync.handlers.SyncCollectionHandler
import com.stratum.core.infrastructure.sync.handlers.SyncHandlerRegistry
import com.stratum.core.infrastructure.sync.handlers.SyncHandlersProvider
import com.stratum.core.testing.logging.FakeLoggingPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class FirestoreRemotePullEngineTest {

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    @DisplayName("Given the pull pass throws when pull then Result failure carries it and engine records nothing")
    fun pullFailureRecordsNothing() = runTest {
        val pullFailure = IllegalStateException("pull blew up")
        val logging = FakeLoggingPort()
        val stateStore = mockk<FirestoreSyncStateStore> {
            coEvery { deviceId() } throws pullFailure
        }
        val subject = FirestoreRemotePullEngine(
            firestore = mockk(relaxed = true),
            stateStore = stateStore,
            handlers = registry(),
            writeCoordinator = UserOwnedWriteCoordinator(),
            loggingPort = logging,
        )

        val result = subject.pull("uid-1")

        assertTrue(result.isFailure)
        assertSame(pullFailure, result.exceptionOrNull())
        // STR-238: the failure rides the Result to the adapter's drained
        // outcome; the engine itself records nothing on the propagate path.
        assertTrue(logging.errors.isEmpty())
    }

    @Test
    @DisplayName("Given a document with missing data when pull then hadFailures result, frozen cursor, and no engine record")
    fun missingDocumentDataFailsWithoutRecord() = runTest {
        val logging = FakeLoggingPort()
        val stateStore = stateStore()
        val badDoc = document(id = "doc-1", data = null)
        val subject = engine(
            stateStore = stateStore,
            logging = logging,
            docsByCollection = mapOf(SyncCollection.UserProfile to listOf(badDoc)),
        )

        val result = subject.pull("uid-1")

        assertTrue(result.isSuccess)
        assertEquals(PullResult(hadFailures = true, hydrated = false), result.getOrNull())
        assertTrue(logging.errors.isEmpty())
        // Cursor-freeze composition: a failed doc never advances any cursor.
        coVerify(exactly = 0) { stateStore.setLastPullCursor(any(), any(), any()) }
    }

    @Test
    @DisplayName("Given a document that fails to decode when pull then hadFailures result, frozen cursor, and no engine record")
    fun decodeFailureFailsWithoutRecord() = runTest {
        val logging = FakeLoggingPort()
        val stateStore = stateStore()
        val doc = document(id = "doc-1", data = remoteData())
        val subject = engine(
            stateStore = stateStore,
            logging = logging,
            docsByCollection = mapOf(SyncCollection.UserProfile to listOf(doc)),
            userProfileUpsert = { _, _ -> false },
        )

        val result = subject.pull("uid-1")

        assertTrue(result.isSuccess)
        assertEquals(PullResult(hadFailures = true, hydrated = false), result.getOrNull())
        assertTrue(logging.errors.isEmpty())
        coVerify(exactly = 0) { stateStore.setLastPullCursor(any(), any(), any()) }
    }

    private fun remoteData(): Map<String, Any?> = mapOf(
        FirestoreSyncMetadata.UPDATED_AT to 100L,
        FirestoreSyncMetadata.DEVICE_ID to "other-device",
        FirestoreSyncMetadata.DELETED to false,
    )

    private fun stateStore(): FirestoreSyncStateStore = mockk {
        coEvery { deviceId() } returns "device-1"
        coEvery { lastPullCursor(any(), any()) } returns PullCursorRecord.EMPTY
        coEvery { setLastPullCursor(any(), any(), any()) } returns Unit
        coEvery { isHydrated(any()) } returns false
        coEvery { setHydrated(any(), any()) } returns Unit
    }

    private fun registry(
        userProfileUpsert: suspend (String, Map<String, Any?>) -> Boolean = { _, _ -> true },
    ): SyncHandlerRegistry {
        val handlers: List<SyncCollectionHandler> = SyncCollection.entries.map { collection ->
            GenericSyncHandler(
                collection = collection,
                loadPending = { emptyList() },
                loadLocalMeta = { null },
                deleteById = {},
                markSynced = { _, _ -> },
                upsertRemote = if (collection == SyncCollection.UserProfile) {
                    userProfileUpsert
                } else {
                    { _, _ -> true }
                },
            )
        }
        val provider = mockk<SyncHandlersProvider> {
            every { all() } returns handlers
        }
        return SyncHandlerRegistry(provider)
    }

    private fun engine(
        stateStore: FirestoreSyncStateStore,
        logging: FakeLoggingPort,
        docsByCollection: Map<SyncCollection, List<DocumentSnapshot>>,
        userProfileUpsert: suspend (String, Map<String, Any?>) -> Boolean = { _, _ -> true },
    ): FirestoreRemotePullEngine =
        FirestoreRemotePullEngine(
            firestore = firestore(docsByCollection),
            stateStore = stateStore,
            handlers = registry(userProfileUpsert),
            writeCoordinator = UserOwnedWriteCoordinator(),
            loggingPort = logging,
        )

    private fun firestore(
        docsByCollection: Map<SyncCollection, List<DocumentSnapshot>>,
    ): FirebaseFirestore {
        val refs = docsByCollection.mapKeys { (collection, _) ->
            "users/uid-1/${collection.firestorePath}"
        }.mapValues { (_, docs) -> collectionReference(docs) }
        val emptyRef = collectionReference(emptyList())
        return mockk {
            every { collection(any()) } answers { refs[firstArg()] ?: emptyRef }
        }
    }

    private fun collectionReference(docs: List<DocumentSnapshot>): CollectionReference {
        val snapshot = mockk<QuerySnapshot> {
            every { documents } returns docs
        }
        val task = mockk<Task<QuerySnapshot>>()
        every { task.isComplete } returns true
        every { task.isSuccessful } returns true
        every { task.isCanceled } returns false
        every { task.exception } returns null
        every { task.result } returns snapshot
        every { task.addOnCompleteListener(any()) } returns task
        val query = mockk<Query>()
        every { query.orderBy(any<FieldPath>()) } returns query
        every { query.limit(any()) } returns query
        every { query.get() } returns task
        return mockk {
            every { orderBy(any<String>()) } returns query
        }
    }

    private fun document(id: String, data: Map<String, Any?>?): DocumentSnapshot = mockk {
        every { this@mockk.id } returns id
        every { this@mockk.data } returns data
    }
}
