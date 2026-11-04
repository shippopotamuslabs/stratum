@file:Suppress("ForbiddenMockKInTests") // CloudSyncScheduler is a concrete WorkManager boundary — no fake available.

package com.stratum.core.infrastructure.sync

import com.stratum.core.infrastructure.work.CloudSyncScheduler
import com.stratum.core.testing.logging.FakeLoggingPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class SyncTouchTest {

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    @DisplayName("Given gate enabled and enqueueSoon throws when markDirty then exactly one error carrying the thrown instance")
    fun markDirtyEnqueueFailureIsRecordedExactlyOnce() {
        val scheduleFailure = IllegalStateException("work manager unavailable")
        val scheduler = mockk<CloudSyncScheduler> {
            every { enqueueSoon() } throws scheduleFailure
        }
        val logging = FakeLoggingPort()
        val subject = SyncTouch(
            cloudDeleteQueue = mockk(relaxed = true),
            cloudSyncScheduler = scheduler,
            featureGate = DefaultCloudSyncFeatureGate().apply { setEnabledForTests(true) },
            writeCoordinator = UserOwnedWriteCoordinator(),
            loggingPort = logging,
        )

        subject.markDirty()

        assertEquals(1, logging.errors.size)
        assertSame(scheduleFailure, logging.errors.single().throwable)
    }

    @Test
    @DisplayName("Given gate disabled when markDirty then scheduler is not touched and nothing is recorded")
    fun markDirtyGateDisabledRecordsNothing() {
        val scheduler = mockk<CloudSyncScheduler> {
            every { enqueueSoon() } throws IllegalStateException("must not be called")
        }
        val logging = FakeLoggingPort()
        val subject = SyncTouch(
            cloudDeleteQueue = mockk(relaxed = true),
            cloudSyncScheduler = scheduler,
            featureGate = DefaultCloudSyncFeatureGate().apply { setEnabledForTests(false) },
            writeCoordinator = UserOwnedWriteCoordinator(),
            loggingPort = logging,
        )

        subject.markDirty()

        assertEquals(0, logging.errors.size)
    }
}
