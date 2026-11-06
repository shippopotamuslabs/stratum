package com.stratum.application.checkin

import app.cash.turbine.test
import com.stratum.core.testing.checkin.FakeCheckInPreferencesRepository
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.checkin.CheckInPreferences

class CheckInUseCasesTest {

    private val fake = FakeCheckInPreferencesRepository()
    private lateinit var observePending: ObservePendingCheckInUseCase
    private lateinit var setEnabled: SetCheckInEnabledUseCase
    private lateinit var markPending: MarkCheckInPendingUseCase
    private lateinit var skip: SkipCheckInUseCase
    private lateinit var complete: CompleteCheckInUseCase

    @BeforeEach
    fun setup() {
        fake.seed(CheckInPreferences())
        observePending = ObservePendingCheckInUseCase(fake)
        setEnabled = SetCheckInEnabledUseCase(fake)
        markPending = MarkCheckInPendingUseCase(fake)
        skip = SkipCheckInUseCase(fake)
        complete = CompleteCheckInUseCase(fake)
    }

    @Test
    @DisplayName("markPending sets pending when enabled")
    fun markPendingWhenEnabled() = runTest {
        val extracted = Instant.parse("2026-03-01T00:00:00Z")
        markPending(extracted)
        assertEquals(extracted, fake.current.pendingSinceExtractedAt)
        observePending().test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("markPending is no-op when disabled")
    fun markPendingWhenDisabled() = runTest {
        setEnabled(false)
        markPending(Instant.parse("2026-03-01T00:00:00Z"))
        assertNull(fake.current.pendingSinceExtractedAt)
        assertFalse(fake.current.isPending)
    }

    @Test
    @DisplayName("skip clears pending and records lastSkippedAt")
    fun skipClearsPending() = runTest {
        markPending(Instant.parse("2026-03-01T00:00:00Z"))
        val now = Instant.parse("2026-03-02T00:00:00Z")
        skip(now)
        assertNull(fake.current.pendingSinceExtractedAt)
        assertEquals(now, fake.current.lastSkippedAt)
    }

    @Test
    @DisplayName("complete clears pending and records lastCompletedAt")
    fun completeClearsPending() = runTest {
        markPending(Instant.parse("2026-03-01T00:00:00Z"))
        val now = Instant.parse("2026-03-03T00:00:00Z")
        complete(now)
        assertNull(fake.current.pendingSinceExtractedAt)
        assertEquals(now, fake.current.lastCompletedAt)
    }

    @Test
    @DisplayName("disable clears pending")
    fun disableClearsPending() = runTest {
        markPending(Instant.parse("2026-03-01T00:00:00Z"))
        setEnabled(false)
        assertFalse(fake.current.enabled)
        assertNull(fake.current.pendingSinceExtractedAt)
    }

    @Test
    @DisplayName("setEnabled returns PersistenceFailed carrying the failure diagnostic (STR-243/STR-237)")
    fun setEnabledPersistenceFailure() = runTest {
        val cause = RuntimeException("store unavailable")
        val throwing =
            object : CheckInPreferencesRepository by fake {
                override suspend fun setEnabled(enabled: Boolean) {
                    throw cause
                }
            }
        val result = SetCheckInEnabledUseCase(throwing)(true)
        val failed = result as CheckInPreferenceResult.PersistenceFailed
        assertEquals(1, failed.diagnostics.size)
        val diagnostic = failed.diagnostics.single()
        assertEquals("SetCheckInEnabled", diagnostic.tag)
        assertEquals("Failed to set check-in enabled: store unavailable", diagnostic.message)
        assertSame(cause, diagnostic.cause)
    }

    @Test
    @DisplayName("skip propagates CancellationException instead of capturing it (STR-243)")
    fun skipCancellationPropagates() {
        val cancelling =
            object : CheckInPreferencesRepository by fake {
                override suspend fun skip(now: Instant) {
                    throw kotlin.coroutines.cancellation.CancellationException("cancelled")
                }
            }
        val subject = SkipCheckInUseCase(cancelling)
        org.junit.jupiter.api.Assertions.assertThrows(
            kotlin.coroutines.cancellation.CancellationException::class.java,
        ) {
            kotlinx.coroutines.runBlocking { subject(Instant.parse("2026-03-02T00:00:00Z")) }
        }
    }
}
