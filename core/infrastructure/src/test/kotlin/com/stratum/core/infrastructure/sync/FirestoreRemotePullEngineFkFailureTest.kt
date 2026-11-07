@file:Suppress("ForbiddenMockKInTests") // Firebase Firestore SDK boundary — no fake available.

package com.stratum.core.infrastructure.sync

import android.database.sqlite.SQLiteConstraintException
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * F4 leg of the pull-engine de-log: an FK/constraint failure while applying a
 * remote document composes into the same hadFailures result as a decode
 * failure, with no engine record. Robolectric so the Android
 * [SQLiteConstraintException] can be constructed for real.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class FirestoreRemotePullEngineFkFailureTest {

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    @DisplayName("Given upsert throws a constraint failure when pull then hadFailures result, frozen cursor, and no engine record")
    fun constraintFailureFailsWithoutRecord() = runTest {
        val logging = FakeLoggingPort()
        val stateStore = mockk<FirestoreSyncStateStore> {
            coEvery { deviceId() } returns "device-1"
            coEvery { lastPullCursor(any(), any()) } returns PullCursorRecord.EMPTY
            coEvery { setLastPullCursor(any(), any(), any()) } returns Unit
            coEvery { isHydrated(any()) } returns false
            coEvery { setHydrated(any(), any()) } returns Unit
        }
        val doc = mockk<DocumentSnapshot> {
            every { id } returns "doc-1"
            every { data } returns mapOf(
                FirestoreSyncMetadata.UPDATED_AT to 100L,
                FirestoreSyncMetadata.DEVICE_ID to "other-device",
                FirestoreSyncMetadata.DELETED to false,
            )
        }
        val subject = FirestoreRemotePullEngine(
            firestore = firestore(doc),
            stateStore = stateStore,
            handlers = registry(),
            writeCoordinator = UserOwnedWriteCoordinator(),
            loggingPort = logging,
        )

        val result = subject.pull("uid-1")

        assertTrue(result.isSuccess)
        assertEquals(PullResult(hadFailures = true, hydrated = false), result.getOrNull())
        assertTrue(logging.errors.isEmpty())
        coVerify(exactly = 0) { stateStore.setLastPullCursor(any(), any(), any()) }
    }

    private fun registry(): SyncHandlerRegistry {
        val handlers: List<SyncCollectionHandler> = SyncCollection.entries.map { collection ->
            GenericSyncHandler(
                collection = collection,
                loadPending = { emptyList() },
                loadLocalMeta = { null },
                deleteById = {},
                markSynced = { _, _ -> },
                upsertRemote = if (collection == SyncCollection.UserProfile) {
                    { _, _ -> throw SQLiteConstraintException("fk failed") }
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

    private fun firestore(doc: DocumentSnapshot): FirebaseFirestore {
        val badRef = collectionReference(listOf(doc))
        val emptyRef = collectionReference(emptyList())
        return mockk {
            every { collection(any()) } answers {
                if (firstArg<String>().endsWith(SyncCollection.UserProfile.firestorePath)) {
                    badRef
                } else {
                    emptyRef
                }
            }
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
}
