@file:Suppress("ForbiddenMockKInTests") // Firebase Auth/Functions SDK boundary — no fake available.

package com.stratum.core.inference

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.HttpsCallableReference
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.domain.lift.ScanPhoto
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Callable contract and [FirebaseIncrementScanAdapter.scanTiers] behavior. Wire parsing
 * itself is covered by [IncrementScanResponseParserTest]; the network happy-path stays
 * untested here (no live callable in unit tests).
 *
 * Runs under Robolectric: `android.util.Base64` (photo encoding) and
 * `android.util.SparseArray` (inside `FirebaseFunctionsException.Code`) need
 * framework shadows on the JVM.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class FirebaseIncrementScanAdapterTest {
    private val auth: FirebaseAuth = mockk()
    private val functions: FirebaseFunctions = mockk()
    private val fakeLogging = FakeLoggingPort()
    private lateinit var adapter: FirebaseIncrementScanAdapter

    private val photo = ScanPhoto(bytes = byteArrayOf(1, 2, 3), mimeType = "image/jpeg")

    @BeforeEach
    fun setup() {
        adapter =
            FirebaseIncrementScanAdapter(
                loggingPort = fakeLogging,
                functions = functions,
                auth = auth,
            )
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    @DisplayName("callable name and timeout match the server endpoint")
    fun callableContract() {
        assertEquals("inferEquipmentIncrements", FirebaseIncrementScanAdapter.CALLABLE_NAME)
        assertEquals(120L, FirebaseIncrementScanAdapter.CALLABLE_TIMEOUT_SECONDS)
    }

    @Test
    @DisplayName("signed-out scan fails without touching the network")
    fun signedOutFailsWithoutNetwork() = runTest {
        every { auth.currentUser } returns null

        val result = adapter.scanTiers(photo)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        verify(exactly = 0) { functions.getHttpsCallable(any()) }
        assertEquals(1, fakeLogging.errors.size)
    }

    @Test
    @DisplayName("callable error maps to failure and is logged with the callable name")
    fun callableErrorMapsToFailure() = runTest {
        val user: FirebaseUser = mockk()
        every { auth.currentUser } returns user
        val callable: HttpsCallableReference = mockk()
        every {
            functions.getHttpsCallable(FirebaseIncrementScanAdapter.CALLABLE_NAME)
        } returns callable
        every { callable.withTimeout(any(), any()) } returns callable
        val callableError: FirebaseFunctionsException = mockk()
        every { callableError.code } returns FirebaseFunctionsException.Code.UNAVAILABLE
        every { callableError.message } returns "unavailable"
        every { callable.call(any()) } returns Tasks.forException(callableError)

        val result = adapter.scanTiers(photo)

        assertTrue(result.isFailure)
        assertEquals(callableError, result.exceptionOrNull())
        assertEquals(1, fakeLogging.errors.size)
        val logged = fakeLogging.errors.single()
        assertEquals(FirebaseIncrementScanAdapter.TAG, logged.tag)
        assertTrue(logged.message.contains(FirebaseIncrementScanAdapter.CALLABLE_NAME))
        assertTrue(logged.message.contains("UNAVAILABLE"))
    }

    @Test
    @DisplayName("cancellation is rethrown and never logged as a normal failure")
    fun cancellationIsRethrown() = runTest {
        val user: FirebaseUser = mockk()
        every { auth.currentUser } returns user
        val callable: HttpsCallableReference = mockk()
        every {
            functions.getHttpsCallable(FirebaseIncrementScanAdapter.CALLABLE_NAME)
        } returns callable
        every { callable.withTimeout(any(), any()) } returns callable
        every { callable.call(any()) } returns
            Tasks.forException(CancellationException("call cancelled"))

        try {
            adapter.scanTiers(photo)
            fail("Expected CancellationException to propagate")
        } catch (error: CancellationException) {
            assertEquals("call cancelled", error.message)
        }
        assertTrue(fakeLogging.errors.isEmpty())
    }
}
