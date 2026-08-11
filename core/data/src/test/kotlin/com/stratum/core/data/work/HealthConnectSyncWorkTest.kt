package com.stratum.core.data.work

import com.stratum.core.testing.healthconnect.FakeHealthConnectPreferencesRepository
import com.stratum.core.testing.healthconnect.FakeHealthConnectSyncPort
import com.stratum.core.testing.healthconnect.FakeHealthConnectSyncStatusPort
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.healthconnect.HealthConnectPreferences
import com.stratum.domain.training.identity.TrainingSessionId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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
    @DisplayName("Given permission denied when run then returns terminal failure")
    fun permissionDeniedIsTerminal() = runTest {
        seedCompletedSession()
        preferences.setPreferences(HealthConnectPreferences(enabled = true))
        syncPort.hasPermission = false

        val outcome = work.run(TrainingSessionId("ts-hc-1"))

        assertTrue(outcome is HealthConnectSyncWork.Outcome.TerminalFailure)
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
    }

    @Test
    @DisplayName("Given transient push failure when run then returns retryable failure")
    fun transientFailureIsRetryable() = runTest {
        seedCompletedSession()
        preferences.setPreferences(HealthConnectPreferences(enabled = true))
        syncPort.pushFailure = IllegalStateException("network blip")

        val outcome = work.run(TrainingSessionId("ts-hc-1"))

        assertTrue(outcome is HealthConnectSyncWork.Outcome.RetryableFailure)
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
    @DisplayName("reportTerminalFailure records sticky sync error for UI")
    fun reportTerminalFailurePersistsError() = runTest {
        work.reportTerminalFailure("sync blew up")

        assertEquals("sync blew up", syncStatus.currentPendingError)
    }
}
