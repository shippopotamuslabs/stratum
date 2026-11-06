package com.stratum.core.infrastructure.work

import com.stratum.core.testing.healthconnect.FakeHealthConnectPreferencesRepository
import com.stratum.core.testing.healthconnect.FakeHealthConnectSyncPort
import com.stratum.core.testing.healthconnect.FakeHealthConnectSyncStatusPort
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.application.healthconnect.HealthSyncFailureKind
import com.stratum.domain.healthconnect.HealthConnectPreferences
import com.stratum.domain.healthconnect.HealthFailureKind
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
        assertEquals(HealthSyncFailureKind.Terminal, terminal.kind)
        assertEquals(
            "Health Connect SDK is not available on this device",
            terminal.message,
        )
    }

    @Test
    @DisplayName("Given user-actionable push failure when run then returns terminal failure")
    fun userActionableFailureIsTerminal() = runTest {
        seedCompletedSession()
        preferences.setPreferences(HealthConnectPreferences(enabled = true))
        syncPort.pushFailureKind = HealthSyncFailureKind.UserActionable
        syncPort.pushFailureMessage = "Health Connect write permission is not granted"

        val outcome = work.run(TrainingSessionId("ts-hc-1"))

        assertTrue(outcome is HealthConnectSyncWork.Outcome.TerminalFailure)
        val terminal = outcome as HealthConnectSyncWork.Outcome.TerminalFailure
        assertEquals(HealthSyncFailureKind.UserActionable, terminal.kind)
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
        assertEquals(HealthSyncFailureKind.Transient, retryable.kind)
        assertSame(syncPort.pushFailure, retryable.cause)
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
    @DisplayName("Missing session is a terminal failure with the original throwable as cause")
    fun missingSessionIsTerminalWithThrowable() = runTest {
        preferences.setPreferences(HealthConnectPreferences(enabled = true))

        val outcome = work.run(TrainingSessionId("missing"))

        assertTrue(outcome is HealthConnectSyncWork.Outcome.TerminalFailure)
        val terminal = outcome as HealthConnectSyncWork.Outcome.TerminalFailure
        assertEquals(null, terminal.kind)
        assertTrue(terminal.cause is IllegalStateException)
        assertTrue(terminal.message!!.contains("missing"))
    }

    @Test
    @DisplayName("reportTerminalFailure records sticky sync error for UI")
    fun reportTerminalFailurePersistsError() = runTest {
        work.reportTerminalFailure(HealthFailureKind.Unavailable)

        assertEquals(HealthFailureKind.Unavailable, syncStatus.currentPendingError)
    }

    @Test
    @DisplayName("STR-263: failing sticky write returns the failure as a diagnostic")
    fun reportTerminalFailureForwardsFailure() = runTest {
        val cause = IllegalStateException("datastore blip")
        syncStatus.failure = cause

        val outcome = work.reportTerminalFailure(HealthFailureKind.Unavailable)

        assertTrue(outcome is com.stratum.application.healthconnect.HealthConnectWriteOutcome.Failed)
        assertSame(
            cause,
            ((outcome as com.stratum.application.healthconnect.HealthConnectWriteOutcome.Failed)
                .diagnostics.single()
                as com.stratum.application.healthconnect.HealthSyncDiagnostic.OperationFailed).cause,
        )
        assertEquals(null, syncStatus.currentPendingError)
    }
}
