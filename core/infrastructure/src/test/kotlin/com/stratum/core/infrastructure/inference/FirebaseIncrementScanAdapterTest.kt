@file:Suppress("ForbiddenMockKInTests") // Firebase Auth/Functions SDK boundary — no fake available.

package com.stratum.core.infrastructure.inference

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.HttpsCallableReference
import com.stratum.domain.lift.ScanPhoto
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
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
    private lateinit var adapter: FirebaseIncrementScanAdapter

    private val photo = ScanPhoto(bytes = byteArrayOf(1, 2, 3), mimeType = "image/jpeg")

    @BeforeEach
    fun setup() {
        adapter =
            FirebaseIncrementScanAdapter(
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
        // STR-240 chunk 8 pin inversion (was: exactly 1 adapter error record):
        // the adapter records zero — it has no logging port at all now. The
        // failure rides the Result to the use case's ScanFailed carrier and is
        // recorded exactly once at the :app lift-config boundary.
    }

    @Test
    @DisplayName("callable error maps to failure carrying the real cause, unrecorded by the adapter")
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
        // STR-240 chunk 8 pin inversion (was: exactly 1 adapter error record
        // with the callable name/code): the adapter records zero; the exact
        // callable error rides the Result as the failure value — the carrier
        // the use case turns into the ScanFailed diagnostic.
        assertSame(callableError, result.exceptionOrNull())
    }

    @Test
    @DisplayName("cancellation is rethrown and never treated as a normal failure")
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
    }
}
