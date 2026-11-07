@file:Suppress("ForbiddenMockKInTests") // Firebase Firestore SDK boundary — no fake available.

package com.stratum.core.infrastructure.subscription

import app.cash.turbine.test
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Source
import com.stratum.core.infrastructure.logging.LoggingPort
import com.stratum.domain.subscription.ComplimentaryAccess
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class FirestoreEntitlementDataSourceTest {

    private val usersCollection = mockk<CollectionReference>()
    private val userDocument = mockk<DocumentReference>()
    private val entitlementCollection = mockk<CollectionReference>()
    private val overrideDocument = mockk<DocumentReference>()
    private val firestore = mockk<FirebaseFirestore>()
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private val registration = mockk<ListenerRegistration>(relaxed = true)
    private val listeners = mutableListOf<EventListener<DocumentSnapshot>>()

    private lateinit var dataSource: FirestoreEntitlementDataSource

    @BeforeEach
    fun setup() {
        listeners.clear()
        every { firestore.collection("users") } returns usersCollection
        every { usersCollection.document(USER_ID) } returns userDocument
        every { userDocument.collection("entitlement") } returns entitlementCollection
        every { entitlementCollection.document("override") } returns overrideDocument
        every { overrideDocument.addSnapshotListener(any<EventListener<DocumentSnapshot>>()) } answers {
            listeners += firstArg<EventListener<DocumentSnapshot>>()
            registration
        }
        dataSource = FirestoreEntitlementDataSource(firestore, loggingPort)
    }

    @Test
    @DisplayName("an override with freeAccess true is Granted")
    fun grantedSnapshot() = runTest {
        dataSource.observeComplimentaryAccess(USER_ID).test {
            listeners.single().onEvent(snapshot(exists = true, freeAccess = true), null)
            assertEquals(ComplimentaryAccess.Granted, awaitItem())
            cancel()
        }
    }

    @Test
    @DisplayName("a missing override is NotGranted")
    fun missingSnapshot() = runTest {
        dataSource.observeComplimentaryAccess(USER_ID).test {
            listeners.single().onEvent(snapshot(exists = false, freeAccess = null), null)
            assertEquals(ComplimentaryAccess.NotGranted, awaitItem())
            cancel()
        }
    }

    @Test
    @DisplayName("a listener error is Unknown, never NotGranted")
    fun listenerErrorIsUnknown() = runTest {
        dataSource.observeComplimentaryAccess(USER_ID).test {
            listeners.single().onEvent(null, permissionDenied())
            assertEquals(ComplimentaryAccess.Unknown, awaitItem())
            verify(exactly = 1) { loggingPort.error(any(), any(), any()) }
            cancel()
        }
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @DisplayName("after a listener error the listener re-registers and can recover to Granted")
    fun listenerErrorRetriesAndRecovers() = runTest {
        dataSource.observeComplimentaryAccess(USER_ID).test {
            listeners.single().onEvent(null, permissionDenied())
            assertEquals(ComplimentaryAccess.Unknown, awaitItem())

            advanceTimeBy(6.seconds)

            assertEquals(2, listeners.size)
            listeners[1].onEvent(snapshot(exists = true, freeAccess = true), null)
            assertEquals(ComplimentaryAccess.Granted, awaitItem())
            cancel()
        }
    }

    @Test
    @DisplayName("one-shot fetch of an override with freeAccess true is Granted")
    fun getGranted() = runTest {
        every { overrideDocument.get(Source.DEFAULT) } returns
            Tasks.forResult(snapshot(exists = true, freeAccess = true))

        assertEquals(ComplimentaryAccess.Granted, dataSource.getComplimentaryAccess(USER_ID))
    }

    @Test
    @DisplayName("one-shot fetch with no override is NotGranted")
    fun getNotGranted() = runTest {
        every { overrideDocument.get(Source.DEFAULT) } returns
            Tasks.forResult(snapshot(exists = false, freeAccess = null))

        assertEquals(ComplimentaryAccess.NotGranted, dataSource.getComplimentaryAccess(USER_ID))
    }

    @Test
    @DisplayName("a failed one-shot fetch is Unknown, never NotGranted")
    fun getFailureIsUnknown() = runTest {
        every { overrideDocument.get(Source.DEFAULT) } returns
            Tasks.forException(permissionDenied())

        assertEquals(ComplimentaryAccess.Unknown, dataSource.getComplimentaryAccess(USER_ID))
    }

    private fun snapshot(exists: Boolean, freeAccess: Boolean?): DocumentSnapshot = mockk {
        every { exists() } returns exists
        every { getBoolean("freeAccess") } returns freeAccess
    }

    // Mocked rather than constructed: FirebaseFirestoreException's Code enum cannot
    // initialize in a plain JVM unit test. The data source only reads the message.
    private fun permissionDenied(): FirebaseFirestoreException = mockk {
        every { message } returns "Missing or insufficient permissions."
    }

    private companion object {
        const val USER_ID = "uid-1"
    }
}
