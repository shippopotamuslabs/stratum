package com.stratum.core.data.work

import com.stratum.core.testing.healthconnect.FakeHealthConnectPreferencesRepository
import com.stratum.core.testing.healthconnect.FakeHealthConnectSyncPort
import com.stratum.core.testing.healthconnect.FakeHealthConnectSyncStatusPort
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.application.healthconnect.HealthSyncFailure
import com.stratum.application.healthconnect.HealthSyncFailureKind
import com.stratum.domain.healthconnect.HealthConnectPreferences
import com.stratum.domain.training.identity.TrainingSessionId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class HealthConnectSyncWorkTest {

    private lateinit var preferences: FakeHealthConnectPreferencesRepository
    private lateinit var syncPort: FakeHealthConnectSyncPort
    private lateinit var syncStatus: FakeHealthConnectSyncStatusPort
    private lateinit var trainingLog: FakeTrainingLogRepository
    private lateinit var work: HealthConnectSyncWork

    @BeforeEach
    fun setup() {
        preferences = FakeHealthConnectPreferencesRepository()
        syncPort = FakeHealthConnectSyncPort()
        syncStatus = FakeHealthConnectSyncStatusPort()
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram())
        trainingLog = FakeTrainingLogRepository(programRepository = programRepo)
        work = HealthConnectSyncWork(
            trainingLogRepository = trainingLog,
            preferencesPort = preferences,
            healthConnectSyncPort = syncPort,
            healthConnectSyncStatusPort = syncStatus,
        )
    }

    private suspend fun seedCompletedSession() {
        trainingLog.seedSession(
            WorkoutLoggingFixtures.completedLiveSession(id = "ts-hc-1"),
        )
    }

    @Test
    @DisplayName("Given sync disabled when run then skips without pushing")
    fun skipsWhenDisabled() = runTest {
        seedCompletedSession()
        preferences.setPreferences(HealthConnectPreferences(enabled = false))

        val outcome = work.run(TrainingSessionId("ts-hc-1"))

        assertEquals(HealthConnectSyncWork.Outcome.SkippedDisabled, outcome)
        assertTrue(syncPort.pushedSessions.isEmpty())
    }

    @Test
    @DisplayName("Given no write permission when enabled then skips without pushing or erroring")
    fun skipsWhenWriteNotGranted() = runTest {
        seedCompletedSession()
        preferences.setPreferences(HealthConnectPreferences(enabled = true))
        syncPort.hasPermission = false

        val outcome = work.run(TrainingSessionId("ts-hc-1"))

        assertEquals(HealthConnectSyncWork.Outcome.SkippedDisabled, outcome)
        assertTrue(syncPort.pushedSessions.isEmpty())
    }

    @Test
    @DisplayName("Given SDK unavailable when run then returns terminal failure")
    fun unavailableIsTerminal() = runTest {
        seedCompletedSession()
        preferences.setPreferences(HealthConnectPreferences(enabled = true))
        syncPort.isAvailable = false

        val outcome = work.run(TrainingSessionId("ts-hc-1"))

        assertTrue(outcome is HealthConnectSyncWork.Outcome.TerminalFailure)
        val terminal = outcome as HealthConnectSyncWork.Outcome.TerminalFailure
        assertTrue(terminal.error is HealthSyncFailure)
        assertEquals(HealthSyncFailureKind.Terminal, (terminal.error as HealthSyncFailure).kind)
    }

    @Test
    @DisplayName("Given user-actionable push failure when run then returns terminal failure")
    fun userActionableFailureIsTerminal() = runTest {
        seedCompletedSession()
        preferences.setPreferences(HealthConnectPreferences(enabled = true))
        syncPort.pushFailure = HealthSyncFailure.permissionDenied()

        val outcome = work.run(TrainingSessionId("ts-hc-1"))

        assertTrue(outcome is HealthConnectSyncWork.Outcome.TerminalFailure)
        val terminal = outcome as HealthConnectSyncWork.Outcome.TerminalFailure
        assertTrue(terminal.error is HealthSyncFailure)
        assertEquals(HealthSyncFailureKind.UserActionable, (terminal.error as HealthSyncFailure).kind)
    }

    @Test
    @DisplayName("Given transient push failure when run then returns retryable failure")
    fun transientFailureIsRetryable() = runTest {
        seedCompletedSession()
        preferences.setPreferences(HealthConnectPreferences(enabled = true))
        syncPort.pushFailure = IllegalStateException("network blip")

        val outcome = work.run(TrainingSessionId("ts-hc-1"))

        assertTrue(outcome is HealthConnectSyncWork.Outcome.RetryableFailure)
        val retryable = outcome as HealthConnectSyncWork.Outcome.RetryableFailure
        assertSame(syncPort.pushFailure, retryable.error)
    }

    @Test
    @DisplayName("cancellation from push propagates instead of becoming a retryable failure")
    fun cancellationFromPushPropagates() = runTest {
        seedCompletedSession()
        preferences.setPreferences(HealthConnectPreferences(enabled = true))
        syncPort.pushThrows = kotlinx.coroutines.CancellationException("cancelled")

        org.junit.jupiter.api.assertThrows<kotlinx.coroutines.CancellationException> {
            work.run(TrainingSessionId("ts-hc-1"))
        }
    }

    @Test
    @DisplayName("Given enabled prefs when run succeeds then pushes session")
    fun successPushesSession() = runTest {
        seedCompletedSession()
        preferences.setPreferences(HealthConnectPreferences(enabled = true))

        val outcome = work.run(TrainingSessionId("ts-hc-1"))

        assertEquals(HealthConnectSyncWork.Outcome.Success, outcome)
        assertEquals(1, syncPort.pushedSessions.size)
    }

    @Test
    @DisplayName("Missing session is a terminal failure with the original throwable")
    fun missingSessionIsTerminalWithThrowable() = runTest {
        preferences.setPreferences(HealthConnectPreferences(enabled = true))

        val outcome = work.run(TrainingSessionId("missing"))

        assertTrue(outcome is HealthConnectSyncWork.Outcome.TerminalFailure)
        val terminal = outcome as HealthConnectSyncWork.Outcome.TerminalFailure
        assertTrue(terminal.error is IllegalStateException)
        assertTrue(terminal.error.message!!.contains("missing"))
    }

    @Test
    @DisplayName("reportTerminalFailure records sticky sync error for UI")
    fun reportTerminalFailurePersistsError() = runTest {
        val result = work.reportTerminalFailure("sync blew up")

        assertTrue(result.isSuccess)
        assertEquals("sync blew up", syncStatus.currentPendingError)
    }

    @Test
    @DisplayName("STR-263: failing sticky write returns failure as a value")
    fun reportTerminalFailureForwardsFailure() = runTest {
        val cause = IllegalStateException("datastore blip")
        syncStatus.failure = cause

        val result = work.reportTerminalFailure("sync blew up")

        assertTrue(result.isFailure)
        assertSame(cause, result.exceptionOrNull())
        assertEquals(null, syncStatus.currentPendingError)
    }
}
